package kr.co.mybrain.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
 * GPT(OpenAI Responses API)와 Gemini(generateContent REST API)를 공통 형식으로 호출합니다.
 * API 키는 호출할 때만 메모리에서 사용하며 로그나 오류 문구에 포함하지 않습니다.
 */
public final class CloudAiAnalyzer {
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final Pattern MODEL_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{2,100}");

    private CloudAiAnalyzer() {
    }

    /** 선택된 공급자로 문장을 분석하고 앱 공통 결과로 변환합니다. */
    public static AiAnalysisResult analyze(AiSettings settings, String apiKey, String userText) throws Exception {
        if (settings == null || !settings.isCloudProvider()) {
            throw new IllegalArgumentException("클라우드 AI 공급자가 선택되지 않았습니다.");
        }
        String key = apiKey == null ? "" : apiKey.trim();
        String input = userText == null ? "" : userText.trim();
        if (key.isEmpty()) throw new IllegalArgumentException("API 키가 등록되지 않았습니다.");
        if (input.isEmpty()) throw new IllegalArgumentException("분석할 내용이 없습니다.");

        String output;
        if (AiSettings.PROVIDER_OPENAI.equals(settings.provider)) {
            output = requestOpenAi(settings.openAiModel, key, input);
        } else {
            output = requestGemini(settings.geminiModel, key, input);
        }
        return parseAnalysis(output, input);
    }

    private static String requestOpenAi(String model, String apiKey, String input) throws Exception {
        validateModel(model);
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("store", false);
        body.put("instructions", systemInstruction());
        body.put("input", input);

        HttpURLConnection connection = openConnection("https://api.openai.com/v1/responses");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        String response = postJson(connection, body.toString());
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
        if (outputText.length() == 0) throw new AiServiceException("GPT 응답에서 분석 결과를 찾지 못했습니다.");
        return outputText.toString();
    }

    private static String requestGemini(String model, String apiKey, String input) throws Exception {
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
                .put("temperature", 0.1)
                .put("responseMimeType", "application/json");
        JSONObject body = new JSONObject()
                .put("system_instruction", systemInstruction)
                .put("contents", new JSONArray().put(userContent))
                .put("generationConfig", generationConfig);

        HttpURLConnection connection = openConnection(endpoint);
        connection.setRequestProperty("x-goog-api-key", apiKey);
        String response = postJson(connection, body.toString());
        JSONObject root = new JSONObject(response);
        JSONArray candidates = root.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw new AiServiceException("Gemini 응답에서 분석 결과를 찾지 못했습니다.");
        }
        JSONObject content = candidates.optJSONObject(0).optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        if (parts == null) throw new AiServiceException("Gemini 응답 형식을 읽을 수 없습니다.");

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part != null) text.append(part.optString("text", ""));
        }
        if (text.length() == 0) throw new AiServiceException("Gemini가 빈 응답을 반환했습니다.");
        return text.toString();
    }

    private static HttpURLConnection openConnection(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private static String postJson(HttpURLConnection connection, String json) throws Exception {
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(json.getBytes(StandardCharsets.UTF_8));
                output.flush();
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readAll(stream);
            if (status < 200 || status >= 300) {
                throw new AiServiceException(buildHttpError(status, response));
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

    private static String buildHttpError(int status, String response) {
        String message = "클라우드 AI 요청 실패 (HTTP " + status + ")";
        try {
            JSONObject root = new JSONObject(response == null ? "" : response);
            JSONObject error = root.optJSONObject("error");
            String detail = error == null ? "" : error.optString("message", "").trim();
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
        if (start < 0 || end <= start) throw new AiServiceException("AI 응답이 JSON 형식이 아닙니다.");
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

    /** 화면에 보여도 되는 안전한 오류만 전달하는 예외입니다. */
    public static final class AiServiceException extends Exception {
        public AiServiceException(String message) {
            super(message);
        }
    }
}
