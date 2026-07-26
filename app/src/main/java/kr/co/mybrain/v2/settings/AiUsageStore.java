package kr.co.mybrain.v2.settings;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 클라우드 AI 호출 횟수, 토큰 사용량, 응답시간을 기기 내부에 누적합니다.
 * API 키, 입력 문장, 응답 원문은 저장하지 않습니다.
 */
public final class AiUsageStore {
    private static final String PREFS = "mybrain_v2_ai_usage";

    private AiUsageStore() {}

    public static synchronized void record(
            Context context,
            String provider,
            String model,
            boolean success,
            int inputTokens,
            int outputTokens,
            int totalTokens,
            long elapsedMs,
            boolean privacyMasked,
            int corrections,
            String message) {
        String prefix = prefix(provider);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long requests = prefs.getLong(prefix + "_requests", 0L) + 1L;
        long successes = prefs.getLong(prefix + "_successes", 0L) + (success ? 1L : 0L);
        long failures = prefs.getLong(prefix + "_failures", 0L) + (success ? 0L : 1L);
        long input = prefs.getLong(prefix + "_input_tokens", 0L) + Math.max(0, inputTokens);
        long output = prefs.getLong(prefix + "_output_tokens", 0L) + Math.max(0, outputTokens);
        long total = prefs.getLong(prefix + "_total_tokens", 0L) + Math.max(0, totalTokens);
        long elapsed = prefs.getLong(prefix + "_elapsed_ms", 0L) + Math.max(0L, elapsedMs);

        prefs.edit()
                .putLong(prefix + "_requests", requests)
                .putLong(prefix + "_successes", successes)
                .putLong(prefix + "_failures", failures)
                .putLong(prefix + "_input_tokens", input)
                .putLong(prefix + "_output_tokens", output)
                .putLong(prefix + "_total_tokens", total)
                .putLong(prefix + "_elapsed_ms", elapsed)
                .putLong(prefix + "_last_at", System.currentTimeMillis())
                .putLong(prefix + "_last_elapsed_ms", Math.max(0L, elapsedMs))
                .putInt(prefix + "_last_input_tokens", Math.max(0, inputTokens))
                .putInt(prefix + "_last_output_tokens", Math.max(0, outputTokens))
                .putInt(prefix + "_last_total_tokens", Math.max(0, totalTokens))
                .putInt(prefix + "_last_corrections", Math.max(0, corrections))
                .putBoolean(prefix + "_last_success", success)
                .putBoolean(prefix + "_last_masked", privacyMasked)
                .putString(prefix + "_last_model", sanitize(model, 100))
                .putString(prefix + "_last_message", sanitize(message, 180))
                .apply();
    }

    public static Summary load(Context context, String provider) {
        String prefix = prefix(provider);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new Summary(
                prefs.getLong(prefix + "_requests", 0L),
                prefs.getLong(prefix + "_successes", 0L),
                prefs.getLong(prefix + "_failures", 0L),
                prefs.getLong(prefix + "_input_tokens", 0L),
                prefs.getLong(prefix + "_output_tokens", 0L),
                prefs.getLong(prefix + "_total_tokens", 0L),
                prefs.getLong(prefix + "_elapsed_ms", 0L),
                prefs.getLong(prefix + "_last_at", 0L),
                prefs.getLong(prefix + "_last_elapsed_ms", 0L),
                prefs.getInt(prefix + "_last_input_tokens", 0),
                prefs.getInt(prefix + "_last_output_tokens", 0),
                prefs.getInt(prefix + "_last_total_tokens", 0),
                prefs.getInt(prefix + "_last_corrections", 0),
                prefs.getBoolean(prefix + "_last_success", false),
                prefs.getBoolean(prefix + "_last_masked", false),
                prefs.getString(prefix + "_last_model", ""),
                prefs.getString(prefix + "_last_message", ""));
    }

    public static void clear(Context context, String provider) {
        String prefix = prefix(provider) + "_";
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(prefix)) editor.remove(key);
        }
        editor.apply();
    }

    private static String prefix(String provider) {
        return AiSettings.PROVIDER_GEMINI.equals(AiSettings.normalizeProvider(provider))
                ? "gemini" : "openai";
    }

    private static String sanitize(String value, int maxLength) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }

    public static final class Summary {
        public final long requests;
        public final long successes;
        public final long failures;
        public final long inputTokens;
        public final long outputTokens;
        public final long totalTokens;
        public final long totalElapsedMs;
        public final long lastAt;
        public final long lastElapsedMs;
        public final int lastInputTokens;
        public final int lastOutputTokens;
        public final int lastTotalTokens;
        public final int lastCorrections;
        public final boolean lastSuccess;
        public final boolean lastPrivacyMasked;
        public final String lastModel;
        public final String lastMessage;

        Summary(long requests, long successes, long failures,
                long inputTokens, long outputTokens, long totalTokens, long totalElapsedMs,
                long lastAt, long lastElapsedMs, int lastInputTokens, int lastOutputTokens,
                int lastTotalTokens, int lastCorrections, boolean lastSuccess,
                boolean lastPrivacyMasked, String lastModel, String lastMessage) {
            this.requests = requests;
            this.successes = successes;
            this.failures = failures;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.totalTokens = totalTokens;
            this.totalElapsedMs = totalElapsedMs;
            this.lastAt = lastAt;
            this.lastElapsedMs = lastElapsedMs;
            this.lastInputTokens = lastInputTokens;
            this.lastOutputTokens = lastOutputTokens;
            this.lastTotalTokens = lastTotalTokens;
            this.lastCorrections = lastCorrections;
            this.lastSuccess = lastSuccess;
            this.lastPrivacyMasked = lastPrivacyMasked;
            this.lastModel = lastModel == null ? "" : lastModel;
            this.lastMessage = lastMessage == null ? "" : lastMessage;
        }

        public long averageElapsedMs() {
            return requests <= 0 ? 0L : totalElapsedMs / requests;
        }
    }
}
