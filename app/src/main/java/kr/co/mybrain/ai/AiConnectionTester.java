package kr.co.mybrain.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * GPT와 Gemini API 키·모델 연결 상태를 실제 문장 전송 없이 확인합니다.
 * 모델 정보 조회 API만 사용하므로 일정·메모 내용은 외부로 전송하지 않습니다.
 */
public final class AiConnectionTester {
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final Pattern MODEL_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{2,100}");

    private AiConnectionTester() {
    }

    /** 선택한 공급자의 API 키와 모델 접근 가능 여부를 확인합니다. */
    public static String test(String provider, String model, String apiKey) throws Exception {
        String normalizedProvider = AiSettings.normalizeProvider(provider);
        String normalizedModel = model == null ? "" : model.trim();
        String normalizedKey = apiKey == null ? "" : apiKey.trim();

        if (AiSettings.PROVIDER_LOCAL.equals(normalizedProvider)) {
            return "로컬 분석은 인터넷 연결이 필요하지 않습니다.";
        }
        if (!MODEL_PATTERN.matcher(normalizedModel).matches()) {
            throw new IllegalArgumentException("모델 이름 형식이 올바르지 않습니다.");
        }
        if (normalizedKey.isEmpty()) {
            throw new IllegalArgumentException("API 키가 등록되지 않았습니다.");
        }

        if (AiSettings.PROVIDER_OPENAI.equals(normalizedProvider)) {
            return testOpenAi(normalizedModel, normalizedKey);
        }
        return testGemini(normalizedModel, normalizedKey);
    }

    /** OpenAI 모델 조회 API로 키와 모델 사용 가능 여부를 확인합니다. */
    private static String testOpenAi(String model, String apiKey) throws Exception {
        String endpoint = "https://api.openai.com/v1/models/" + encodePath(model);
        HttpURLConnection connection = openGetConnection(endpoint);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        String response = execute(connection, "GPT");

        JSONObject root = new JSONObject(response);
        String modelId = root.optString("id", "").trim();
        if (modelId.isEmpty()) {
            throw new ConnectionTestException("GPT 모델 정보를 확인하지 못했습니다.");
        }
        return "GPT 연결 성공 · " + modelId;
    }

    /** Gemini 모델 조회 API로 키와 모델의 generateContent 지원 여부를 확인합니다. */
    private static String testGemini(String model, String apiKey) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + encodePath(model);
        HttpURLConnection connection = openGetConnection(endpoint);
        connection.setRequestProperty("x-goog-api-key", apiKey);
        String response = execute(connection, "Gemini");

        JSONObject root = new JSONObject(response);
        String name = root.optString("name", "").trim();
        if (name.startsWith("models/")) name = name.substring("models/".length());
        if (name.isEmpty()) name = model;

        JSONArray methods = root.optJSONArray("supportedGenerationMethods");
        if (methods != null && methods.length() > 0 && !contains(methods, "generateContent")) {
            throw new ConnectionTestException("선택한 Gemini 모델은 문장 분석 기능을 지원하지 않습니다.");
        }
        return "Gemini 연결 성공 · " + name;
    }

    private static HttpURLConnection openGetConnection(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private static String execute(HttpURLConnection connection, String providerLabel) throws Exception {
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readAll(stream);
            if (status < 200 || status >= 300) {
                throw new ConnectionTestException(buildHttpError(providerLabel, status, response));
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    /** 공급자의 원문 오류에서 API 키 같은 민감한 값은 제외하고 짧은 안내만 반환합니다. */
    private static String buildHttpError(String providerLabel, int status, String response) {
        String friendly;
        switch (status) {
            case 400:
                friendly = "요청 형식 또는 모델 이름을 확인하세요.";
                break;
            case 401:
                friendly = "API 키가 올바르지 않거나 만료됐습니다.";
                break;
            case 403:
                friendly = "이 API 키에 모델 접근 권한이 없습니다.";
                break;
            case 404:
                friendly = "선택한 모델을 찾을 수 없습니다.";
                break;
            case 429:
                friendly = "사용량 또는 결제 한도를 확인하세요.";
                break;
            default:
                friendly = "잠시 후 다시 시도하세요.";
                break;
        }

        String detail = extractErrorMessage(response);
        String message = providerLabel + " 연결 실패 (HTTP " + status + ")\n" + friendly;
        if (!detail.isEmpty()) message += "\n" + limit(detail, 180);
        return message;
    }

    private static String extractErrorMessage(String response) {
        try {
            JSONObject root = new JSONObject(response == null ? "" : response);
            JSONObject error = root.optJSONObject("error");
            if (error == null) return "";
            return error.optString("message", "").trim();
        } catch (JSONException ignored) {
            return "";
        }
    }

    private static boolean contains(JSONArray values, String expected) {
        for (int i = 0; i < values.length(); i++) {
            if (expected.equals(values.optString(i, ""))) return true;
        }
        return false;
    }

    private static String encodePath(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
    }

    private static String limit(String value, int maxLength) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }

    /** 화면에 표시해도 되는 안전한 연결 테스트 예외입니다. */
    public static final class ConnectionTestException extends Exception {
        public ConnectionTestException(String message) {
            super(message);
        }
    }
}
