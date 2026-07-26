package kr.co.mybrain.v2.assistant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.settings.AiSettings;

/** GPT 또는 Gemini를 호출해 일정·할 일·메모 구조를 추출합니다. */
public final class CloudAiWorkItemAnalyzer {
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 35_000;

    private CloudAiWorkItemAnalyzer() {}

    public static AnalysisResult analyze(
            String provider,
            String model,
            String credential,
            String originalText,
            ZoneId zoneId,
            ParsedWorkItem localBaseline) throws Exception {
        long startedAt = System.nanoTime();
        CloudPrivacyFilter.FilteredText filtered = CloudPrivacyFilter.filter(originalText);
        String prompt = buildPrompt(filtered.text, zoneId);
        String normalizedProvider = AiSettings.normalizeProvider(provider);
        ProviderResponse response = AiSettings.PROVIDER_GEMINI.equals(normalizedProvider)
                ? callGemini(model, credential, prompt)
                : callOpenAi(model, credential, prompt);
        ParsedWorkItem merged = mergeJson(response.jsonText, originalText, localBaseline, zoneId);
        merged.aiProvider = normalizedProvider;
        CloudResultValidator.ValidationResult validated = CloudResultValidator.validate(
                originalText, merged, localBaseline, zoneId);
        validated.item.aiProvider = normalizedProvider;
        long elapsedMs = Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L);
        return new AnalysisResult(
                validated.item,
                filtered.masked,
                elapsedMs,
                response.inputTokens,
                response.outputTokens,
                response.totalTokens,
                response.modelVersion,
                validated.corrections,
                validated.summary);
    }

    private static ProviderResponse callOpenAi(String model, String credential, String prompt) throws Exception {
        JSONObject format = new JSONObject()
                .put("type", "json_schema")
                .put("name", "mybrain_work_item")
                .put("strict", true)
                .put("schema", responseSchema());
        JSONObject body = new JSONObject()
                .put("model", model)
                .put("store", false)
                .put("input", new JSONArray()
                        .put(new JSONObject()
                                .put("role", "system")
                                .put("content", new JSONArray().put(new JSONObject()
                                        .put("type", "input_text")
                                        .put("text", systemInstruction()))))
                        .put(new JSONObject()
                                .put("role", "user")
                                .put("content", new JSONArray().put(new JSONObject()
                                        .put("type", "input_text")
                                        .put("text", prompt)))))
                .put("text", new JSONObject().put("format", format));

        HttpURLConnection connection = openPost("https://api.openai.com/v1/responses");
        connection.setRequestProperty("Authorization", "Bearer " + credential);
        writeBody(connection, body.toString());
        JSONObject response = new JSONObject(execute(connection, "GPT"));
        String outputText = extractOpenAiOutput(response);
        JSONObject usage = response.optJSONObject("usage");
        int inputTokens = usage == null ? 0 : usage.optInt("input_tokens", 0);
        int outputTokens = usage == null ? 0 : usage.optInt("output_tokens", 0);
        int totalTokens = usage == null ? inputTokens + outputTokens
                : usage.optInt("total_tokens", inputTokens + outputTokens);
        return new ProviderResponse(
                outputText,
                inputTokens,
                outputTokens,
                totalTokens,
                response.optString("model", model));
    }

    private static String extractOpenAiOutput(JSONObject response) throws Exception {
        JSONArray output = response.optJSONArray("output");
        if (output != null) {
            for (int i = 0; i < output.length(); i++) {
                JSONObject outputItem = output.optJSONObject(i);
                JSONArray content = outputItem == null ? null : outputItem.optJSONArray("content");
                if (content == null) continue;
                for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.optJSONObject(j);
                    if (part != null && "output_text".equals(part.optString("type"))) {
                        String text = part.optString("text", "").trim();
                        if (!text.isEmpty()) return text;
                    }
                }
            }
        }
        throw new AnalysisException("GPT 분석 결과를 읽지 못했습니다.");
    }

    private static ProviderResponse callGemini(String model, String credential, String prompt) throws Exception {
        String normalizedModel = model.startsWith("models/") ? model.substring(7) : model;
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                + URLEncoder.encode(normalizedModel, StandardCharsets.UTF_8.name()).replace("+", "%20")
                + ":generateContent";
        // Gemini 3.5/3.6 이후 모델과의 호환을 위해 temperature/topP/topK를 보내지 않습니다.
        JSONObject body = new JSONObject()
                .put("systemInstruction", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject().put("text", systemInstruction()))))
                .put("contents", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", prompt)))))
                .put("generationConfig", new JSONObject()
                        .put("maxOutputTokens", 800)
                        .put("responseMimeType", "application/json"));

        HttpURLConnection connection = openPost(endpoint);
        connection.setRequestProperty("x-goog-api-key", credential);
        writeBody(connection, body.toString());
        JSONObject response = new JSONObject(execute(connection, "Gemini"));
        String outputText = extractGeminiOutput(response);
        JSONObject usage = response.optJSONObject("usageMetadata");
        int inputTokens = usage == null ? 0 : usage.optInt("promptTokenCount", 0);
        int outputTokens = usage == null ? 0 : usage.optInt("candidatesTokenCount", 0);
        int totalTokens = usage == null ? inputTokens + outputTokens
                : usage.optInt("totalTokenCount", inputTokens + outputTokens);
        return new ProviderResponse(
                outputText,
                inputTokens,
                outputTokens,
                totalTokens,
                response.optString("modelVersion", model));
    }

    private static String extractGeminiOutput(JSONObject response) throws Exception {
        JSONObject promptFeedback = response.optJSONObject("promptFeedback");
        String blockReason = promptFeedback == null ? "" : promptFeedback.optString("blockReason", "");
        if (!blockReason.isEmpty() && !"BLOCK_REASON_UNSPECIFIED".equals(blockReason)) {
            throw new AnalysisException("Gemini 요청이 차단되었습니다: " + blockReason);
        }
        JSONArray candidates = response.optJSONArray("candidates");
        if (candidates != null && candidates.length() > 0) {
            JSONObject candidate = candidates.optJSONObject(0);
            JSONObject content = candidate == null ? null : candidate.optJSONObject("content");
            JSONArray parts = content == null ? null : content.optJSONArray("parts");
            if (parts != null) {
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < parts.length(); i++) {
                    JSONObject part = parts.optJSONObject(i);
                    if (part != null) text.append(part.optString("text", ""));
                }
                if (!text.toString().trim().isEmpty()) return text.toString().trim();
            }
            String finishReason = candidate == null ? "" : candidate.optString("finishReason", "");
            if (!finishReason.isEmpty()) {
                throw new AnalysisException("Gemini 응답이 완료되지 않았습니다: " + finishReason);
            }
        }
        throw new AnalysisException("Gemini 분석 결과를 읽지 못했습니다.");
    }

    private static ParsedWorkItem mergeJson(
            String jsonText,
            String originalText,
            ParsedWorkItem baseline,
            ZoneId zoneId) throws Exception {
        JSONObject json = new JSONObject(stripCodeFence(jsonText));
        ParsedWorkItem result = copyOf(baseline);
        result.sourceText = originalText;

        String type = json.optString("type", "").toUpperCase();
        if ("SCHEDULE".equals(type)) result.type = WorkItemEntity.TYPE_SCHEDULE;
        else if ("TASK".equals(type)) result.type = WorkItemEntity.TYPE_TASK;
        else if ("MEMO".equals(type)) result.type = WorkItemEntity.TYPE_MEMO;

        String title = json.optString("title", "").trim();
        if (!title.isEmpty()) result.title = title.length() <= 120 ? title : title.substring(0, 120);

        Long start = parseInstant(json.opt("startAt"), zoneId);
        Long end = parseInstant(json.opt("endAt"), zoneId);
        Long reminder = parseInstant(json.opt("reminderAt"), zoneId);
        if (start != null) result.startAt = start;
        if (end != null) result.endAt = end;
        if (reminder != null) result.reminderAt = reminder;
        if (json.has("allDay") && !json.isNull("allDay")) {
            result.allDay = json.optBoolean("allDay", result.allDay);
        }
        if (json.has("reminderExplicitlyDisabled") && !json.isNull("reminderExplicitlyDisabled")) {
            result.reminderExplicitlyDisabled = json.optBoolean("reminderExplicitlyDisabled", false);
            if (result.reminderExplicitlyDisabled) result.reminderAt = null;
        }

        String repeat = json.optString("repeatRule", "").toUpperCase();
        if (isAllowedRepeat(repeat)) result.repeatRule = repeat;
        String priority = json.optString("priority", "").toUpperCase();
        if ("LOW".equals(priority) || "NORMAL".equals(priority) || "HIGH".equals(priority)) {
            result.priority = priority;
        }
        if (json.has("confidence") && !json.isNull("confidence")) {
            result.confidence = (float) Math.max(0.0,
                    Math.min(1.0, json.optDouble("confidence", result.confidence)));
        } else {
            result.confidence = Math.max(result.confidence, 0.82f);
        }
        return result;
    }

    private static ParsedWorkItem copyOf(ParsedWorkItem source) {
        ParsedWorkItem copy = new ParsedWorkItem();
        if (source == null) return copy;
        copy.type = source.type;
        copy.title = source.title;
        copy.sourceText = source.sourceText;
        copy.startAt = source.startAt;
        copy.endAt = source.endAt;
        copy.reminderAt = source.reminderAt;
        copy.reminderExplicitlyDisabled = source.reminderExplicitlyDisabled;
        copy.allDay = source.allDay;
        copy.repeatRule = source.repeatRule;
        copy.priority = source.priority;
        copy.confidence = source.confidence;
        copy.aiProvider = source.aiProvider;
        return copy;
    }

    private static Long parseInstant(Object value, ZoneId zoneId) {
        if (value == null || value == JSONObject.NULL) return null;
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) return null;
        try {
            return Instant.parse(text).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            try {
                return ZonedDateTime.parse(text).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return java.time.LocalDateTime.parse(text).atZone(zoneId).toInstant().toEpochMilli();
                } catch (DateTimeParseException ignoredThird) {
                    return null;
                }
            }
        }
    }

    private static JSONObject responseSchema() throws Exception {
        JSONObject properties = new JSONObject()
                .put("type", new JSONObject().put("type", "string")
                        .put("enum", new JSONArray().put("SCHEDULE").put("TASK").put("MEMO")))
                .put("title", new JSONObject().put("type", "string"))
                .put("startAt", nullableString())
                .put("endAt", nullableString())
                .put("reminderAt", nullableString())
                .put("reminderExplicitlyDisabled", new JSONObject().put("type", "boolean"))
                .put("allDay", new JSONObject().put("type", "boolean"))
                .put("repeatRule", new JSONObject().put("type", "string")
                        .put("enum", new JSONArray().put("NONE").put("DAILY").put("WEEKDAYS").put("WEEKLY").put("MONTHLY")))
                .put("priority", new JSONObject().put("type", "string")
                        .put("enum", new JSONArray().put("LOW").put("NORMAL").put("HIGH")))
                .put("confidence", new JSONObject().put("type", "number").put("minimum", 0).put("maximum", 1));
        return new JSONObject()
                .put("type", "object")
                .put("additionalProperties", false)
                .put("properties", properties)
                .put("required", new JSONArray()
                        .put("type").put("title").put("startAt").put("endAt").put("reminderAt")
                        .put("reminderExplicitlyDisabled").put("allDay").put("repeatRule")
                        .put("priority").put("confidence"));
    }

    private static JSONObject nullableString() throws Exception {
        return new JSONObject().put("type", new JSONArray().put("string").put("null"));
    }

    private static String systemInstruction() {
        return "당신은 한국어 개인 비서 입력 분석기다. 사용자의 문장을 일정(SCHEDULE), 할 일(TASK), 메모(MEMO) 중 하나로 분류하고 JSON만 반환한다. "
                + "사용자가 말하지 않은 날짜, 시간, 알림, 반복, 장소, 우선순위를 만들지 않는다. "
                + "일정은 특정 시각이나 일정 성격의 사건, 할 일은 사용자가 수행해야 하는 행동, 메모는 단순 정보로 구분한다. "
                + "날짜와 시간은 제공된 현재 시각과 시간대를 기준으로 계산하여 ISO-8601 형식으로 반환한다. "
                + "명시되지 않은 정보는 null 또는 기본값으로 두고 제목은 핵심 행동이나 일정명만 짧게 작성한다.";
    }

    private static String buildPrompt(String text, ZoneId zoneId) {
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return "현재 시각: " + now + "\n시간대: " + zoneId.getId() + "\n"
                + "반복 규칙 허용값: NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY\n"
                + "중요도 허용값: LOW, NORMAL, HIGH\n"
                + "원문에 날짜나 시간이 없으면 startAt, endAt, reminderAt은 null로 둔다.\n"
                + "원문에 반복 표현이 없으면 repeatRule은 NONE이다.\n"
                + "사용자 입력:\n" + text;
    }

    private static boolean isAllowedRepeat(String value) {
        return "NONE".equals(value) || "DAILY".equals(value) || "WEEKDAYS".equals(value)
                || "WEEKLY".equals(value) || "MONTHLY".equals(value);
    }

    private static String stripCodeFence(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            int firstBreak = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstBreak >= 0 && lastFence > firstBreak) {
                text = text.substring(firstBreak + 1, lastFence).trim();
            }
        }
        return text;
    }

    private static HttpURLConnection openPost(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private static void writeBody(HttpURLConnection connection, String body) throws Exception {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(data.length);
        try (java.io.OutputStream stream = connection.getOutputStream()) {
            stream.write(data);
        }
    }

    private static String execute(HttpURLConnection connection, String providerLabel) throws Exception {
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readAll(stream);
            if (status < 200 || status >= 300) {
                throw new AnalysisException(buildHttpError(providerLabel, status, response));
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

    private static String buildHttpError(String provider, int status, String response) {
        String friendly;
        switch (status) {
            case 400: friendly = "요청 형식이나 선택한 모델을 확인하세요."; break;
            case 401: friendly = "등록한 연결 정보가 올바르지 않습니다."; break;
            case 403: friendly = "선택한 모델을 사용할 권한이 없습니다."; break;
            case 404: friendly = "선택한 모델을 찾을 수 없습니다."; break;
            case 408: friendly = "서버 응답 시간이 초과됐습니다."; break;
            case 429: friendly = "사용량 또는 결제 한도를 확인하세요."; break;
            default: friendly = "네트워크 상태를 확인하고 다시 시도하세요."; break;
        }
        String detail = extractError(response);
        return provider + " 분석 실패 (HTTP " + status + ") · " + friendly
                + (detail.isEmpty() ? "" : " " + detail);
    }

    private static String extractError(String response) {
        try {
            JSONObject root = new JSONObject(response == null ? "" : response);
            Object error = root.opt("error");
            String message = error instanceof JSONObject
                    ? ((JSONObject) error).optString("message", "")
                    : error instanceof String ? (String) error : "";
            message = message.replaceAll("\\s+", " ").trim();
            return message.length() <= 120 ? message : message.substring(0, 120) + "…";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static final class ProviderResponse {
        final String jsonText;
        final int inputTokens;
        final int outputTokens;
        final int totalTokens;
        final String modelVersion;

        ProviderResponse(String jsonText, int inputTokens, int outputTokens,
                         int totalTokens, String modelVersion) {
            this.jsonText = jsonText;
            this.inputTokens = Math.max(0, inputTokens);
            this.outputTokens = Math.max(0, outputTokens);
            this.totalTokens = Math.max(0, totalTokens);
            this.modelVersion = modelVersion == null ? "" : modelVersion;
        }
    }

    public static final class AnalysisResult {
        public final ParsedWorkItem item;
        public final boolean privacyMasked;
        public final long elapsedMs;
        public final int inputTokens;
        public final int outputTokens;
        public final int totalTokens;
        public final String modelVersion;
        public final int corrections;
        public final String validationSummary;

        AnalysisResult(ParsedWorkItem item, boolean privacyMasked, long elapsedMs,
                       int inputTokens, int outputTokens, int totalTokens,
                       String modelVersion, int corrections, String validationSummary) {
            this.item = item;
            this.privacyMasked = privacyMasked;
            this.elapsedMs = elapsedMs;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.totalTokens = totalTokens;
            this.modelVersion = modelVersion == null ? "" : modelVersion;
            this.corrections = corrections;
            this.validationSummary = validationSummary == null ? "" : validationSummary;
        }
    }

    public static final class AnalysisException extends Exception {
        public AnalysisException(String message) { super(message); }
    }
}
