package kr.co.mybrain.v2.settings;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 클라우드 AI 호출 횟수, 토큰 사용량, 응답시간과 추정 비용을 기기 내부에 누적합니다.
 * API 키, 입력 문장, 응답 원문은 저장하지 않습니다.
 */
public final class AiUsageStore {
    private static final String PREFS = "mybrain_v2_ai_usage";
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

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
        String month = monthKey();
        String monthPrefix = prefix + "_" + month + "_";
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        int safeInput = Math.max(0, inputTokens);
        int safeOutput = Math.max(0, outputTokens);
        int safeTotal = Math.max(0, totalTokens);
        long safeElapsed = Math.max(0L, elapsedMs);
        AiBudgetSettings budget = AiBudgetSettings.load(context);
        long estimatedCostWon = success
                ? AiPricingCatalog.estimateWon(provider, model, safeInput, safeOutput, budget.wonPerUsd)
                : 0L;
        boolean priced = estimatedCostWon >= 0L;
        long storedCost = priced ? estimatedCostWon : 0L;

        long previousCombinedCost = monthlyCost(prefs, "openai", month)
                + monthlyCost(prefs, "gemini", month);

        long requests = prefs.getLong(prefix + "_requests", 0L) + 1L;
        long successes = prefs.getLong(prefix + "_successes", 0L) + (success ? 1L : 0L);
        long failures = prefs.getLong(prefix + "_failures", 0L) + (success ? 0L : 1L);
        long input = prefs.getLong(prefix + "_input_tokens", 0L) + safeInput;
        long output = prefs.getLong(prefix + "_output_tokens", 0L) + safeOutput;
        long total = prefs.getLong(prefix + "_total_tokens", 0L) + safeTotal;
        long elapsed = prefs.getLong(prefix + "_elapsed_ms", 0L) + safeElapsed;
        long cumulativeCost = prefs.getLong(prefix + "_estimated_cost_won", 0L) + storedCost;
        long pricedRequests = prefs.getLong(prefix + "_priced_requests", 0L)
                + (success && priced ? 1L : 0L);
        long unknownPricing = prefs.getLong(prefix + "_unknown_pricing_requests", 0L)
                + (success && !priced ? 1L : 0L);

        long monthlyRequests = prefs.getLong(monthPrefix + "requests", 0L) + 1L;
        long monthlySuccesses = prefs.getLong(monthPrefix + "successes", 0L) + (success ? 1L : 0L);
        long monthlyInput = prefs.getLong(monthPrefix + "input_tokens", 0L) + safeInput;
        long monthlyOutput = prefs.getLong(monthPrefix + "output_tokens", 0L) + safeOutput;
        long monthlyTotal = prefs.getLong(monthPrefix + "total_tokens", 0L) + safeTotal;
        long monthlyCost = prefs.getLong(monthPrefix + "estimated_cost_won", 0L) + storedCost;
        long monthlyPriced = prefs.getLong(monthPrefix + "priced_requests", 0L)
                + (success && priced ? 1L : 0L);
        long monthlyUnknown = prefs.getLong(monthPrefix + "unknown_pricing_requests", 0L)
                + (success && !priced ? 1L : 0L);

        prefs.edit()
                .putLong(prefix + "_requests", requests)
                .putLong(prefix + "_successes", successes)
                .putLong(prefix + "_failures", failures)
                .putLong(prefix + "_input_tokens", input)
                .putLong(prefix + "_output_tokens", output)
                .putLong(prefix + "_total_tokens", total)
                .putLong(prefix + "_elapsed_ms", elapsed)
                .putLong(prefix + "_estimated_cost_won", cumulativeCost)
                .putLong(prefix + "_priced_requests", pricedRequests)
                .putLong(prefix + "_unknown_pricing_requests", unknownPricing)
                .putLong(prefix + "_last_at", System.currentTimeMillis())
                .putLong(prefix + "_last_elapsed_ms", safeElapsed)
                .putInt(prefix + "_last_input_tokens", safeInput)
                .putInt(prefix + "_last_output_tokens", safeOutput)
                .putInt(prefix + "_last_total_tokens", safeTotal)
                .putLong(prefix + "_last_estimated_cost_won", estimatedCostWon)
                .putInt(prefix + "_last_corrections", Math.max(0, corrections))
                .putBoolean(prefix + "_last_success", success)
                .putBoolean(prefix + "_last_masked", privacyMasked)
                .putString(prefix + "_last_model", sanitize(model, 100))
                .putString(prefix + "_last_message", sanitize(message, 180))
                .putLong(monthPrefix + "requests", monthlyRequests)
                .putLong(monthPrefix + "successes", monthlySuccesses)
                .putLong(monthPrefix + "input_tokens", monthlyInput)
                .putLong(monthPrefix + "output_tokens", monthlyOutput)
                .putLong(monthPrefix + "total_tokens", monthlyTotal)
                .putLong(monthPrefix + "estimated_cost_won", monthlyCost)
                .putLong(monthPrefix + "priced_requests", monthlyPriced)
                .putLong(monthPrefix + "unknown_pricing_requests", monthlyUnknown)
                .apply();

        AiModelUsageStore.record(
                context, provider, model, success, safeInput, safeOutput, safeTotal,
                safeElapsed, estimatedCostWon);
        AiBudgetNotifier.maybeNotify(
                context, previousCombinedCost, previousCombinedCost + storedCost, budget);
    }

    public static Summary load(Context context, String provider) {
        String prefix = prefix(provider);
        String currentMonth = monthKey();
        String monthPrefix = prefix + "_" + currentMonth + "_";
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new Summary(
                prefs.getLong(prefix + "_requests", 0L),
                prefs.getLong(prefix + "_successes", 0L),
                prefs.getLong(prefix + "_failures", 0L),
                prefs.getLong(prefix + "_input_tokens", 0L),
                prefs.getLong(prefix + "_output_tokens", 0L),
                prefs.getLong(prefix + "_total_tokens", 0L),
                prefs.getLong(prefix + "_elapsed_ms", 0L),
                prefs.getLong(prefix + "_estimated_cost_won", 0L),
                prefs.getLong(prefix + "_priced_requests", 0L),
                prefs.getLong(prefix + "_unknown_pricing_requests", 0L),
                prefs.getLong(prefix + "_last_at", 0L),
                prefs.getLong(prefix + "_last_elapsed_ms", 0L),
                prefs.getInt(prefix + "_last_input_tokens", 0),
                prefs.getInt(prefix + "_last_output_tokens", 0),
                prefs.getInt(prefix + "_last_total_tokens", 0),
                prefs.getLong(prefix + "_last_estimated_cost_won", 0L),
                prefs.getInt(prefix + "_last_corrections", 0),
                prefs.getBoolean(prefix + "_last_success", false),
                prefs.getBoolean(prefix + "_last_masked", false),
                prefs.getString(prefix + "_last_model", ""),
                prefs.getString(prefix + "_last_message", ""),
                currentMonth,
                prefs.getLong(monthPrefix + "requests", 0L),
                prefs.getLong(monthPrefix + "successes", 0L),
                prefs.getLong(monthPrefix + "input_tokens", 0L),
                prefs.getLong(monthPrefix + "output_tokens", 0L),
                prefs.getLong(monthPrefix + "total_tokens", 0L),
                prefs.getLong(monthPrefix + "estimated_cost_won", 0L),
                prefs.getLong(monthPrefix + "priced_requests", 0L),
                prefs.getLong(monthPrefix + "unknown_pricing_requests", 0L));
    }

    public static CombinedSummary loadCombined(Context context) {
        Summary openAi = load(context, AiSettings.PROVIDER_OPENAI);
        Summary gemini = load(context, AiSettings.PROVIDER_GEMINI);
        return new CombinedSummary(
                openAi.monthKey,
                openAi.monthlyRequests + gemini.monthlyRequests,
                openAi.monthlySuccesses + gemini.monthlySuccesses,
                openAi.monthlyInputTokens + gemini.monthlyInputTokens,
                openAi.monthlyOutputTokens + gemini.monthlyOutputTokens,
                openAi.monthlyTotalTokens + gemini.monthlyTotalTokens,
                openAi.monthlyEstimatedCostWon + gemini.monthlyEstimatedCostWon,
                openAi.monthlyUnknownPricingRequests + gemini.monthlyUnknownPricingRequests);
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

    private static long monthlyCost(SharedPreferences prefs, String prefix, String month) {
        return prefs.getLong(prefix + "_" + month + "_estimated_cost_won", 0L);
    }

    private static String monthKey() {
        return YearMonth.now(ZoneId.systemDefault()).format(MONTH_FORMAT);
    }

    private static String prefix(String provider) {
        return AiSettings.PROVIDER_GEMINI.equals(AiSettings.normalizeProvider(provider))
                ? "gemini" : "openai";
    }

    private static String sanitize(String value, int maxLength) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }

    public static final class CombinedSummary {
        public final String monthKey;
        public final long monthlyRequests;
        public final long monthlySuccesses;
        public final long monthlyInputTokens;
        public final long monthlyOutputTokens;
        public final long monthlyTotalTokens;
        public final long monthlyEstimatedCostWon;
        public final long monthlyUnknownPricingRequests;

        CombinedSummary(String monthKey, long monthlyRequests, long monthlySuccesses,
                        long monthlyInputTokens, long monthlyOutputTokens, long monthlyTotalTokens,
                        long monthlyEstimatedCostWon, long monthlyUnknownPricingRequests) {
            this.monthKey = monthKey;
            this.monthlyRequests = monthlyRequests;
            this.monthlySuccesses = monthlySuccesses;
            this.monthlyInputTokens = monthlyInputTokens;
            this.monthlyOutputTokens = monthlyOutputTokens;
            this.monthlyTotalTokens = monthlyTotalTokens;
            this.monthlyEstimatedCostWon = monthlyEstimatedCostWon;
            this.monthlyUnknownPricingRequests = monthlyUnknownPricingRequests;
        }
    }

    public static final class Summary {
        public final long requests;
        public final long successes;
        public final long failures;
        public final long inputTokens;
        public final long outputTokens;
        public final long totalTokens;
        public final long totalElapsedMs;
        public final long estimatedCostWon;
        public final long pricedRequests;
        public final long unknownPricingRequests;
        public final long lastAt;
        public final long lastElapsedMs;
        public final int lastInputTokens;
        public final int lastOutputTokens;
        public final int lastTotalTokens;
        public final long lastEstimatedCostWon;
        public final int lastCorrections;
        public final boolean lastSuccess;
        public final boolean lastPrivacyMasked;
        public final String lastModel;
        public final String lastMessage;
        public final String monthKey;
        public final long monthlyRequests;
        public final long monthlySuccesses;
        public final long monthlyInputTokens;
        public final long monthlyOutputTokens;
        public final long monthlyTotalTokens;
        public final long monthlyEstimatedCostWon;
        public final long monthlyPricedRequests;
        public final long monthlyUnknownPricingRequests;

        Summary(long requests, long successes, long failures,
                long inputTokens, long outputTokens, long totalTokens, long totalElapsedMs,
                long estimatedCostWon, long pricedRequests, long unknownPricingRequests,
                long lastAt, long lastElapsedMs, int lastInputTokens, int lastOutputTokens,
                int lastTotalTokens, long lastEstimatedCostWon, int lastCorrections,
                boolean lastSuccess, boolean lastPrivacyMasked, String lastModel, String lastMessage,
                String monthKey, long monthlyRequests, long monthlySuccesses,
                long monthlyInputTokens, long monthlyOutputTokens, long monthlyTotalTokens,
                long monthlyEstimatedCostWon, long monthlyPricedRequests,
                long monthlyUnknownPricingRequests) {
            this.requests = requests;
            this.successes = successes;
            this.failures = failures;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.totalTokens = totalTokens;
            this.totalElapsedMs = totalElapsedMs;
            this.estimatedCostWon = estimatedCostWon;
            this.pricedRequests = pricedRequests;
            this.unknownPricingRequests = unknownPricingRequests;
            this.lastAt = lastAt;
            this.lastElapsedMs = lastElapsedMs;
            this.lastInputTokens = lastInputTokens;
            this.lastOutputTokens = lastOutputTokens;
            this.lastTotalTokens = lastTotalTokens;
            this.lastEstimatedCostWon = lastEstimatedCostWon;
            this.lastCorrections = lastCorrections;
            this.lastSuccess = lastSuccess;
            this.lastPrivacyMasked = lastPrivacyMasked;
            this.lastModel = lastModel == null ? "" : lastModel;
            this.lastMessage = lastMessage == null ? "" : lastMessage;
            this.monthKey = monthKey;
            this.monthlyRequests = monthlyRequests;
            this.monthlySuccesses = monthlySuccesses;
            this.monthlyInputTokens = monthlyInputTokens;
            this.monthlyOutputTokens = monthlyOutputTokens;
            this.monthlyTotalTokens = monthlyTotalTokens;
            this.monthlyEstimatedCostWon = monthlyEstimatedCostWon;
            this.monthlyPricedRequests = monthlyPricedRequests;
            this.monthlyUnknownPricingRequests = monthlyUnknownPricingRequests;
        }

        public long averageElapsedMs() {
            return requests <= 0 ? 0L : totalElapsedMs / requests;
        }
    }
}
