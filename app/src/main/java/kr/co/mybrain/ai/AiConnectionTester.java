package kr.co.mybrain.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 휴대전화 Ollama와 GPT·Gemini의 모델 연결 상태를 확인합니다.
 * Ollama는 모델 목록 확인 후 실제 짧은 분석과 JSON 해석까지 통과해야 성공으로 표시합니다.
 */
public final class AiConnectionTester {
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final Pattern MODEL_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{2,100}");

    private AiConnectionTester() {
    }

    /** 기존 호출부 호환용입니다. */
    public static String test(String provider, String model, String apiKey) throws Exception {
        return test(provider, model, apiKey, AiSettings.DEFAULT_OLLAMA_BASE_URL);
    }

    /** 선택한 공급자의 서버·API 키·모델 접근 가능 여부를 확인합니다. */
    public static String test(String provider, String model, String apiKey,
                              String ollamaBaseUrl) throws Exception {
        String normalizedProvider = AiSettings.normalizeProvider(provider);
        String normalizedModel = model == null ? "" : model.trim();
        String normalizedKey = apiKey == null ? "" : apiKey.trim();

        if (AiSettings.PROVIDER_LOCAL.equals(normalizedProvider)) {
            return "기본 규칙 분석은 서버 연결이 필요하지 않습니다.";
        }
        if (!MODEL_PATTERN.matcher(normalizedModel).matches()) {
            throw new IllegalArgumentException("모델 이름 형식이 올바르지 않습니다.");
        }

        if (AiSettings.PROVIDER_OLLAMA.equals(normalizedProvider)) {
            return testOllama(normalizedModel, ollamaBaseUrl);
        }
        if (normalizedKey.isEmpty()) {
            throw new IllegalArgumentException("API 키가 등록되지 않았습니다.");
        }
        if (AiSettings.PROVIDER_OPENAI.equals(normalizedProvider)) {
            return testOpenAi(normalizedModel, normalizedKey);
        }
        return testGemini(normalizedModel, normalizedKey);
    }

    /**
     * 모델 목록 확인 후 실제 추론을 실행합니다.
     * 서버만 열려 있고 모델이 실행되지 않는 잘못된 성공 표시를 방지합니다.
     */
    private static String testOllama(String model, String baseUrl) throws Exception {
        if (!AiSettings.isAllowedOllamaBaseUrl(baseUrl)) {
            throw new IllegalArgumentException("Ollama 주소는 같은 휴대전화의 localhost만 사용할 수 있습니다.");
        }

        String installedName = findInstalledOllamaModel(model, baseUrl);
        try {
            CloudAiAnalyzer.verifyOllamaInference(baseUrl, model);
            return "Ollama 실제 분석 성공 · " + installedName;
        } catch (CloudAiAnalyzer.AiServiceException e) {
            throw new ConnectionTestException(
                    "서버와 모델 목록은 확인됐지만 실제 문장 분석에 실패했습니다.\n"
                            + safeMessage(e));
        }
    }

    /** Ollama 모델 목록 API에서 선택한 모델이 설치됐는지 확인합니다. */
    private static String findInstalledOllamaModel(String model, String baseUrl) throws Exception {
        String endpoint = AiSettings.normalizeOllamaBaseUrl(baseUrl) + "/api/tags";
        HttpURLConnection connection = openGetConnection(endpoint);
        String response;
        try {
            response = execute(connection, "Ollama");
        } catch (ConnectException e) {
            throw new ConnectionTestException(
                    "Ollama Server가 실행되지 않았습니다. Ollama Server 앱에서 서비스를 먼저 시작하세요.");
        } catch (java.net.SocketTimeoutException e) {
            throw new ConnectionTestException(
                    "Ollama 응답 시간이 초과됐습니다. 서버 실행 상태와 휴대전화 메모리를 확인하세요.");
        }

        JSONObject root = new JSONObject(response);
        JSONArray models = root.optJSONArray("models");
        if (models == null) {
            throw new ConnectionTestException("Ollama 모델 목록을 읽지 못했습니다.");
        }

        for (int i = 0; i < models.length(); i++) {
            JSONObject item = models.optJSONObject(i);
            if (item == null) continue;
            String name = item.optString("name", "").trim();
            String modelName = item.optString("model", "").trim();
            if (sameModel(model, name) || sameModel(model, modelName)) {
                return name.isEmpty() ? model : name;
            }
        }

        throw new ConnectionTestException(
                "Ollama 서버는 실행 중이지만 '" + model
                        + "' 모델이 없습니다. Ollama Server 앱에서 모델을 내려받으세요.");
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
            case 500:
                friendly = "모델 실행 중 오류가 발생했습니다. 메모리와 모델 상태를 확인하세요.";
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
            Object error = root.opt("error");
            if (error instanceof JSONObject) {
                return ((JSONObject) error).optString("message", "").trim();
            }
            return error instanceof String ? ((String) error).trim() : "";
        } catch (JSONException ignored) {
            return "";
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? "알 수 없는 오류" : error.getMessage();
        return message == null || message.trim().isEmpty() ? "알 수 없는 오류" : message.trim();
    }

    private static boolean sameModel(String expected, String actual) {
        if (expected == null || actual == null) return false;
        String left = expected.trim();
        String right = actual.trim();
        if (left.equalsIgnoreCase(right)) return true;
        return !left.contains(":") && (right.equalsIgnoreCase(left + ":latest"));
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
