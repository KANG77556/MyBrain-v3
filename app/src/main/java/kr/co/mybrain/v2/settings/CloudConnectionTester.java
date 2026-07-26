package kr.co.mybrain.v2.settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class CloudConnectionTester {
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;
    private static final Pattern MODEL_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{2,100}");

    private CloudConnectionTester() {}

    public static String test(String provider, String model, String credential) throws Exception {
        String normalizedProvider = AiSettings.normalizeProvider(provider);
        String normalizedModel = model == null ? "" : model.trim();
        String normalizedCredential = credential == null ? "" : credential.trim();
        if (!MODEL_PATTERN.matcher(normalizedModel).matches()) {
            throw new ConnectionTestException("모델 이름 형식이 올바르지 않습니다.");
        }
        if (normalizedCredential.isEmpty()) {
            throw new ConnectionTestException("등록된 인증 정보가 없습니다.");
        }
        return AiSettings.PROVIDER_GEMINI.equals(normalizedProvider)
                ? testGemini(normalizedModel, normalizedCredential)
                : testOpenAi(normalizedModel, normalizedCredential);
    }

    private static String testOpenAi(String model, String credential) throws Exception {
        HttpURLConnection connection = openGet("https://api.openai.com/v1/models/" + encode(model));
        connection.setRequestProperty("Authorization", "Bearer " + credential);
        JSONObject root = new JSONObject(execute(connection, "GPT"));
        String id = root.optString("id", "").trim();
        if (id.isEmpty()) throw new ConnectionTestException("GPT 모델 정보를 확인하지 못했습니다.");
        return "GPT 연결 성공 · " + id;
    }

    private static String testGemini(String model, String credential) throws Exception {
        HttpURLConnection connection = openGet(
                "https://generativelanguage.googleapis.com/v1beta/models/" + encode(stripModelPrefix(model)));
        connection.setRequestProperty("x-goog-api-key", credential);
        JSONObject root = new JSONObject(execute(connection, "Gemini"));
        String name = root.optString("name", "").trim();
        if (name.startsWith("models/")) name = name.substring("models/".length());
        JSONArray methods = root.optJSONArray("supportedGenerationMethods");
        if (methods != null && methods.length() > 0 && !contains(methods, "generateContent")) {
            throw new ConnectionTestException("선택한 Gemini 모델은 문장 분석 기능을 지원하지 않습니다.");
        }
        return "Gemini 연결 성공 · " + (name.isEmpty() ? model : name);
    }

    private static HttpURLConnection openGet(String endpoint) throws Exception {
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

    private static String buildHttpError(String providerLabel, int status, String response) {
        String friendly;
        switch (status) {
            case 400: friendly = "요청 형식 또는 모델 이름을 확인하세요."; break;
            case 401: friendly = "등록 정보가 올바르지 않거나 만료됐습니다."; break;
            case 403: friendly = "이 계정에 모델 접근 권한이 없습니다."; break;
            case 404: friendly = "선택한 모델을 찾을 수 없습니다."; break;
            case 429: friendly = "사용량 또는 결제 한도를 확인하세요."; break;
            default: friendly = "네트워크 상태를 확인한 뒤 다시 시도하세요."; break;
        }
        String detail = extractError(response);
        return providerLabel + " 연결 실패 (HTTP " + status + ")\n" + friendly
                + (detail.isEmpty() ? "" : "\n" + detail);
    }

    private static String extractError(String response) {
        try {
            JSONObject root = new JSONObject(response == null ? "" : response);
            Object error = root.opt("error");
            String detail = error instanceof JSONObject
                    ? ((JSONObject) error).optString("message", "")
                    : error instanceof String ? (String) error : "";
            detail = detail.replaceAll("\\s+", " ").trim();
            return detail.length() <= 180 ? detail : detail.substring(0, 180) + "…";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean contains(JSONArray values, String expected) {
        for (int i = 0; i < values.length(); i++) {
            if (expected.equals(values.optString(i, ""))) return true;
        }
        return false;
    }

    private static String stripModelPrefix(String value) {
        return value != null && value.startsWith("models/") ? value.substring(7) : value;
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
    }

    public static final class ConnectionTestException extends Exception {
        public ConnectionTestException(String message) { super(message); }
    }
}
