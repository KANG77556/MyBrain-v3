package kr.co.mybrain.v2.settings;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 모델별 요청 횟수, 실제 응답시간, 토큰과 예상 비용을 기기 안에 저장합니다. */
public final class AiModelUsageStore {
    private static final String PREFS = "mybrain_v2_ai_model_usage";
    private static final String KEY_IDS = "model_ids";
    private static final int MAX_MODELS = 30;

    private AiModelUsageStore() {}

    public static synchronized void record(
            Context context,
            String provider,
            String model,
            boolean success,
            int inputTokens,
            int outputTokens,
            int totalTokens,
            long elapsedMs,
            long estimatedCostWon) {
        String safeProvider = AiSettings.normalizeProvider(provider);
        String safeModel = sanitizeModel(model);
        if (safeModel.isEmpty()) return;
        String id = hash(safeProvider + "|" + safeModel);
        String prefix = "m_" + id + "_";
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        Set<String> ids = new HashSet<>(prefs.getStringSet(KEY_IDS, Collections.emptySet()));
        if (!ids.contains(id) && ids.size() >= MAX_MODELS) return;
        ids.add(id);

        long requests = prefs.getLong(prefix + "requests", 0L) + 1L;
        long successes = prefs.getLong(prefix + "successes", 0L) + (success ? 1L : 0L);
        long failures = prefs.getLong(prefix + "failures", 0L) + (success ? 0L : 1L);
        long input = prefs.getLong(prefix + "input", 0L) + Math.max(0, inputTokens);
        long output = prefs.getLong(prefix + "output", 0L) + Math.max(0, outputTokens);
        long total = prefs.getLong(prefix + "total", 0L) + Math.max(0, totalTokens);
        long elapsed = prefs.getLong(prefix + "elapsed", 0L) + Math.max(0L, elapsedMs);
        boolean priced = success && estimatedCostWon >= 0L;
        long cost = prefs.getLong(prefix + "cost", 0L) + (priced ? estimatedCostWon : 0L);
        long pricedRequests = prefs.getLong(prefix + "priced", 0L) + (priced ? 1L : 0L);

        prefs.edit()
                .putStringSet(KEY_IDS, ids)
                .putString(prefix + "provider", safeProvider)
                .putString(prefix + "model", safeModel)
                .putLong(prefix + "requests", requests)
                .putLong(prefix + "successes", successes)
                .putLong(prefix + "failures", failures)
                .putLong(prefix + "input", input)
                .putLong(prefix + "output", output)
                .putLong(prefix + "total", total)
                .putLong(prefix + "elapsed", elapsed)
                .putLong(prefix + "cost", cost)
                .putLong(prefix + "priced", pricedRequests)
                .putLong(prefix + "last_at", System.currentTimeMillis())
                .apply();
    }

    public static List<ModelSummary> loadAll(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> ids = prefs.getStringSet(KEY_IDS, Collections.emptySet());
        List<ModelSummary> result = new ArrayList<>();
        for (String id : ids) {
            String prefix = "m_" + id + "_";
            String provider = prefs.getString(prefix + "provider", "");
            String model = prefs.getString(prefix + "model", "");
            if (provider == null || provider.isEmpty() || model == null || model.isEmpty()) continue;
            result.add(new ModelSummary(
                    provider,
                    model,
                    prefs.getLong(prefix + "requests", 0L),
                    prefs.getLong(prefix + "successes", 0L),
                    prefs.getLong(prefix + "failures", 0L),
                    prefs.getLong(prefix + "input", 0L),
                    prefs.getLong(prefix + "output", 0L),
                    prefs.getLong(prefix + "total", 0L),
                    prefs.getLong(prefix + "elapsed", 0L),
                    prefs.getLong(prefix + "cost", 0L),
                    prefs.getLong(prefix + "priced", 0L),
                    prefs.getLong(prefix + "last_at", 0L)));
        }
        result.sort(Comparator.comparingLong((ModelSummary item) -> item.requests).reversed()
                .thenComparing(item -> item.provider)
                .thenComparing(item -> item.model));
        return result;
    }

    public static ModelSummary find(Context context, String provider, String model) {
        String targetProvider = AiSettings.normalizeProvider(provider);
        String targetModel = sanitizeModel(model);
        for (ModelSummary item : loadAll(context)) {
            if (item.provider.equals(targetProvider) && item.model.equals(targetModel)) return item;
        }
        return null;
    }

    private static String sanitizeModel(String value) {
        String text = AiPricingCatalog.normalizeModel(value).replaceAll("[^a-z0-9._:-]", "");
        return text.length() <= 100 ? text : text.substring(0, 100);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < 10; i++) result.append(String.format("%02x", digest[i]));
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    public static final class ModelSummary {
        public final String provider;
        public final String model;
        public final long requests;
        public final long successes;
        public final long failures;
        public final long inputTokens;
        public final long outputTokens;
        public final long totalTokens;
        public final long totalElapsedMs;
        public final long estimatedCostWon;
        public final long pricedRequests;
        public final long lastAt;

        ModelSummary(String provider, String model, long requests, long successes, long failures,
                     long inputTokens, long outputTokens, long totalTokens, long totalElapsedMs,
                     long estimatedCostWon, long pricedRequests, long lastAt) {
            this.provider = provider;
            this.model = model;
            this.requests = requests;
            this.successes = successes;
            this.failures = failures;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.totalTokens = totalTokens;
            this.totalElapsedMs = totalElapsedMs;
            this.estimatedCostWon = estimatedCostWon;
            this.pricedRequests = pricedRequests;
            this.lastAt = lastAt;
        }

        public long averageElapsedMs() {
            return requests <= 0L ? 0L : totalElapsedMs / requests;
        }

        public long averageCostWon() {
            return pricedRequests <= 0L ? -1L : estimatedCostWon / pricedRequests;
        }

        public double successRate() {
            return requests <= 0L ? 0.0 : successes * 100.0 / requests;
        }
    }
}
