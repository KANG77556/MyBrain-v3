package kr.co.mybrain.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * 휴대전화 Ollama, GPT(OpenAI Responses API), Gemini(generateContent REST API)를 공통 형식으로 호출합니다.
 * API 키는 호출할 때만 메모리에서 사용하며 로그나 오류 문구에 포함하지 않습니다.
 */
public final class CloudAiAnalyzer {
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int CLOUD_READ_TIMEOUT_MS = 90_000;
    private static final int OLLAMA_READ_TIMEOUT_MS = 120_000;
    private static final Pattern MODEL_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{2,100}");

    private CloudAiAnalyzer() {
    }

    /** 기존 호출 코드와의 호환성을 유지하는 기본 분석 함수입니다. */
    public static AiAnalysisResult analyze(AiSettings settings, String apiKey,
                                           String userText) throws Exception {
        return analyze(settings, apiKey, userText, new RequestControl());
    }

    /**
     * 선택된 AI 공급자로 문장을 분석합니다.
     * RequestControl을 전달하면 화면의 취소 버튼이 실제 HTTP 연결까지 중단할 수 있습니다.
     */
    public static AiAnalysisResult analyze(AiSettings settings, String apiKey,
                                           String userText, RequestControl control) throws Exception {
        RequestControl activeControl = control == null ? new RequestControl() : control;
        try {
            activeControl.throwIfCancelled();
            if (settings == null || !settings.isModelProvider()) {
                throw new IllegalArgumentException("사용할 AI 모델이 선택되지 않았습니다.");
            }

            String key = apiKey == null ? "" : apiKey.trim();
            String input = userText == null ? "" : userText.trim();
            if (input.isEmpty()) throw new IllegalArgumentException("분석할 내용이 없습니다.");
            if (settings.isCloudProvider() && key.isEmpty()) {
                throw new IllegalArgumentException("API 키가 등록되지 않았습니다.");
            }

            String output;
            if (AiSettings.PROVIDER_OLLAMA.equals(settings.provider)) {
                output = requestOllama(settings.ollamaBaseUrl, settings.ollamaModel,
                        input, activeControl);
            } else if (AiSettings.PROVIDER_OPENAI.equals(settings.provider)) {
                output = requestOpenAi(settings.openAiModel, key, input, activeControl);
            } else {
                output = requestGemini(settings.geminiModel, key, input, activeControl);
            }
            activeControl.throwIfCancelled();
            return parseAnalysis(output, input);
        } catch (AnalysisCancelledException e) {
            throw e;
        } catch (Exception e) {
            if (activeControl.isCancelled() || Thread.currentThread().isInterrupted()) {
                throw new AnalysisCancelledException();
            }
            throw e;
        }
    }

    /** 같은 휴대전화에서 실행 중인 Ollama의 짧은 구조화 JSON 출력을 사용합니다. */
    private static String requestOllama(String baseUrl, String model, String input,
                                        RequestControl control) throws Exception {
        validateModel(model);
        if (!AiSettings.isAllowedOllamaBaseUrl(baseUrl)) {
            throw new IllegalArgumentException("Ollama 주소는 같은 휴대전화의 localhost만 사용할 수 있습니다.");
        }

        JSONObject systemMessage = new JSONObject()
                .put("role", "system")
                .put("content", systemInstruction());
        JSONObject userMessage = new JSONObject()
                .put("role", "user")
                .put("content", input);

        // 휴대전화 발열과 대기 시간을 줄이기 위해 문맥과 최대 출력 길이를 제한합니다.
        JSONObject options = new JSONObject()
                .put("temperature", 0)
                .put("num_ctx", 2048)
                .put("num_predict", 180);

        JSONObject body = new JSONObject()
                .put("model", model)
                .put("messages", new JSONArray().put(systemMessage).put(userMessage))
                .put("stream", false)
                .put("think", false)
                .put("format", "json")
                .put("options", options)
                // 연속 입력 시 모델을 다시 적재하지 않도록 잠시 메모리에 유지합니다.
                .put("keep_alive", "5m");

        String endpoint = AiSettings.normalizeOllamaBaseUrl(baseUrl) + "/api/chat";
        try {
            String response = postJson(
                    openConnection(endpoint, OLLAMA_READ_TIMEOUT_MS),
                    body.toString(), "Ollama", control);
            JSONObject root = new JSONObject(response);
            JSONObject message = root.optJSONObject("message");
            String content = message == null ? "" : message.optString("content", "").trim();
            if (content.isEmpty()) {
                throw new AiServiceException("Ollama 응답에서 분석 결과를 찾지 못했습니다.");
            }
            return content;
        } catch (ConnectException e) {
            throw new AiServiceException(
                    "Ollama Server가 실행되지 않았습니다. Ollama Server 앱에서 서비스를 먼저 시작하세요.");
        } catch (java.net.SocketTimeoutException e) {
            throw new AiServiceException(
                    "Ollama 분석 시간이 초과됐습니다. gemma3:1b처럼 더 작은 모델을 사용해 보세요.");
        }
    }

    private static String requestOpenAi(String model, String apiKey, String input,
                                        RequestControl control) throws Exception {
        validateModel(model);
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("store", false);
        body.put("instructions", systemInstruction());
        body.put("input", input);

        HttpURLConnection connection = openConnection(
                "https://api.openai.com/v1/responses", CLOUD_READ_TIMEOUT_MS);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        String response = postJson(connection, body.toString(), "GPT", control);
        JSONObject root = new JSONObject(response);

        String direct = root.optString("output_text", "").trim();
        if (!direct.isEmpty()) return direct;

        StringBuilder outputText = new StringBuilder();
        JSONArray output = root.optJSONArray("output");
        if (output != null) {
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i);
                if (item == null) continue;
                JSONArray content = item.optJSONArray("content");
                if (content == null) continue;
                for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.optJSONObject(j);
                    if (part == null || !"output_text".equals(part.optString("type"))) continue;
                    String text = part.optString("text", "");
                    if (!text.isEmpty()) outputText.append(text);
                }
            }
        }
        if (outputText.length() == 0) {
            throw new AiServiceException("GPT 응답에서 분석 결과를 찾지 못했습니다.");
        }
        return outputText.toString();
    }

    private static String requestGemini(String model, String apiKey, String input,
                                        RequestControl control) throws Exception {
        validateModel(model);
        String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8.name());
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                + encodedModel + ":generateContent";

        JSONObject systemPart = new JSONObject().put("text", systemInstruction());
        JSONObject systemInstruction = new JSONObject()
                .put("parts", new JSONArray().put(systemPart));
        JSONObject userPart = new JSONObject().put("text", input);
        JSONObject userContent = new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(userPart));

        JSONObject generationConfig = new JSONObject()
                .put("responseMimeType", "application/json");
        JSONObject body = new JSONObject()
                .put("system_instruction", systemInstruction)
                .put("contents", new JSONArray().put(userContent))
                .put("generationConfig", generationConfig);

        HttpURLConnection connection = openConnection(endpoint, CLOUD_READ_TIMEOUT_MS);
        connection.setRequestProperty("x-goog-api-key", apiKey);
        String response = postJson(connection, body.toString(), "Gemini", control);
        JSONObject root = new JSONObject(response);
        JSONArray candidates = root.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw new AiServiceException("Gemini 응답에서 분석 결과를 찾지 못했습니다.");
        }

        JSONObject firstCandidate = candidates.optJSONObject(0);
        JSONObject content = firstCandidate == null ? null : firstCandidate.optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        if (parts == null) {
            throw new AiServiceException("Gemini 응답 형식을 읽을 수 없습니다.");
        }

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part != null) text.append(part.optString("text", ""));
        }
        if (text.length() == 0) throw new AiServiceException("Gemini가 빈 응답을 반환했습니다.");
        return text.toString();
    }

    private static HttpURLConnection openConnection(String endpoint, int readTimeoutMs) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(readTimeoutMs);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private static String postJson(HttpURLConnection connection, String json,
                                   String providerLabel, RequestControl control) throws Exception {
        control.attach(connection);
        try {
            control.throwIfCancelled();
            try (OutputStream output = connection.getOutputStream()) {
                output.write(json.getBytes(StandardCharsets.UTF_8));
                output.flush();
            }

            control.throwIfCancelled();
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readAll(stream, control);
            control.throwIfCancelled();
            if (status < 200 || status >= 300) {
                throw new AiServiceException(buildHttpError(providerLabel, status, response));
            }
            return response;
        } catch (Exception e) {
            if (control.isCancelled() || Thread.currentThread().isInterrupted()) {
                throw new AnalysisCancelledException();
            }
            throw e;
        } finally {
            control.detach(connection);
            connection.disconnect();
        }
    }

    private static String readAll(InputStream stream, RequestControl control) throws Exception {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                control.throwIfCancelled();
                result.append(line);
            }
        }
        return result.toString();
    }

    private static String buildHttpError(String providerLabel, int status, String response) {
        String message = providerLabel + " AI 요청 실패 (HTTP " + status + ")";
        try {
            JSONObject root = new JSONObject(response == null ? "" : response);
            Object error = root.opt("error");
            String detail = "";
            if (error instanceof JSONObject) {
                detail = ((JSONObject) error).optString("message", "").trim();
            } else if (error instanceof String) {
                detail = ((String) error).trim();
            }
            if (!detail.isEmpty()) message += "\n" + limit(detail, 240);
        } catch (JSONException ignored) {
            // 공급자가 JSON이 아닌 오류 페이지를 반환하면 상태 코드만 안내합니다.
        }
        return message;
    }

    /** AI가 반환한 JSON을 검증하고 날짜·시간·반복 값을 앱 형식으로 정리합니다. */
    private static AiAnalysisResult parseAnalysis(String rawOutput, String originalInput) throws Exception {
        String jsonText = extractJsonObject(rawOutput);
        JSONObject json = new JSONObject(jsonText);
        AiAnalysisResult result = new AiAnalysisResult();
        result.type = normalizeType(json.optString("type", "메모"));
        result.content = cleanText(json.optString("content", ""));
        if (result.content.isEmpty()) result.content = cleanText(originalInput);
        result.title = cleanText(json.optString("title", ""));
        if (result.title.isEmpty()) result.title = limit(result.content, 40);
        result.title = limit(result.title, 60);
        result.date = normalizeDate(json.optString("date", ""));
        result.time = normalizeTime(json.optString("time", ""));
        result.repeatType = normalizeRepeat(json.optString("repeatType", "NONE"));
        return result;
    }

    private static String extractJsonObject(String value) throws AiServiceException {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AiServiceException("AI 응답이 JSON 형식이 아닙니다.");
        }
        return text.substring(start, end + 1);
    }

    private static String systemInstruction() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd EEEE HH:mm", Locale.KOREA);
        format.setTimeZone(TimeZone.getDefault());
        String now = format.format(new Date());
        return "당신은 MyBrain AI의 한국어 일정 분석기입니다. 현재 기기 시각은 " + now + "입니다. "
                + "사용자 문장을 분석하여 아래 JSON 객체 하나만 반환하세요. 설명, 마크다운, 코드블록은 금지합니다. "
                + "필드 형식: {\"type\":\"일정|할 일|메모\",\"title\":\"짧은 제목\","
                + "\"content\":\"날짜와 시간 표현을 제거한 핵심 내용\",\"date\":\"yyyy-MM-dd 또는 빈 문자열\","
                + "\"time\":\"HH:mm 또는 빈 문자열\",\"repeatType\":\"NONE|DAILY|WEEKLY|MONTHLY|WEEKDAYS\"}. "
                + "회의·약속·방문·예약처럼 특정 시점의 사건은 일정, 제출·준비·처리처럼 해야 하는 행동은 할 일, "
                + "그 외 기록은 메모로 분류하세요. 모호한 값은 추측하지 말고 빈 문자열 또는 NONE을 사용하세요.";
    }

    private static void validateModel(String model) {
        String value = model == null ? "" : model.trim();
        if (!MODEL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("모델 이름 형식이 올바르지 않습니다.");
        }
    }

    private static String normalizeType(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.KOREA);
        if (text.contains("할") || text.contains("task")) return "할 일";
        if (text.contains("일정") || text.contains("schedule") || text.contains("event")) return "일정";
        return "메모";
    }

    private static String normalizeRepeat(String value) {
        String text = value == null ? "NONE" : value.trim().toUpperCase(Locale.US);
        if ("DAILY".equals(text) || "WEEKLY".equals(text)
                || "MONTHLY".equals(text) || "WEEKDAYS".equals(text)) return text;
        return "NONE";
    }

    private static String normalizeDate(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return "";
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
            format.setLenient(false);
            Date date = format.parse(text);
            return date == null ? "" : format.format(date);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String normalizeTime(String value) {
        String text = value == null ? "" : value.trim();
        if (!text.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) return "";
        return text;
    }

    private static String cleanText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static String limit(String value, int maxLength) {
        String text = value == null ? "" : value;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }

    /** 현재 실행 중인 HTTP 연결과 사용자 취소 상태를 관리합니다. */
    public static final class RequestControl {
        private volatile HttpURLConnection connection;
        private volatile boolean cancelled;

        /** 실행 중인 요청을 중단하고 연결을 즉시 닫습니다. */
        public void cancel() {
            cancelled = true;
            HttpURLConnection active = connection;
            if (active != null) active.disconnect();
        }

        public boolean isCancelled() {
            return cancelled;
        }

        private void attach(HttpURLConnection value) throws AnalysisCancelledException {
            if (cancelled) {
                value.disconnect();
                throw new AnalysisCancelledException();
            }
            connection = value;
            if (cancelled) {
                value.disconnect();
                throw new AnalysisCancelledException();
            }
        }

        private void detach(HttpURLConnection value) {
            if (connection == value) connection = null;
        }

        private void throwIfCancelled() throws AnalysisCancelledException {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                cancelled = true;
                throw new AnalysisCancelledException();
            }
        }
    }

    /** 사용자가 취소 버튼을 눌렀을 때만 사용하는 정상적인 종료 예외입니다. */
    public static final class AnalysisCancelledException extends Exception {
        public AnalysisCancelledException() {
            super("AI 분석을 취소했습니다.");
        }
    }

    /** 화면에 보여도 되는 안전한 오류만 전달하는 예외입니다. */
    public static final class AiServiceException extends Exception {
        public AiServiceException(String message) {
            super(message);
        }
    }
}
