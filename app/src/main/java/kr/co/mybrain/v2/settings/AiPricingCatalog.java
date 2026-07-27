package kr.co.mybrain.v2.settings;

import java.util.Locale;

/**
 * 앱 버전에 포함된 텍스트 토큰 단가표입니다.
 * 단가는 1백만 토큰당 USD 기준이며 실제 공급자 청구서와 다를 수 있습니다.
 */
public final class AiPricingCatalog {
    public static final String PRICE_EFFECTIVE_DATE = "2026-07-26";

    private AiPricingCatalog() {}

    public static Price resolve(String provider, String model) {
        String normalizedProvider = AiSettings.normalizeProvider(provider);
        String value = model == null ? "" : model.trim().toLowerCase(Locale.US);
        if (AiSettings.PROVIDER_GEMINI.equals(normalizedProvider)) {
            if (value.startsWith("models/")) value = value.substring(7);
            if (value.startsWith("gemini-3.6-flash")) return new Price(1.50, 7.50, true);
            if (value.startsWith("gemini-3.5-flash-lite")) return new Price(0.30, 2.50, true);
            if (value.startsWith("gemini-2.5-flash")) return new Price(0.30, 2.50, true);
            return Price.unknown();
        }
        if (value.startsWith("gpt-5.4-mini")) return new Price(0.75, 4.50, true);
        if (value.startsWith("gpt-5.4-nano")) return new Price(0.20, 1.25, true);
        if (value.startsWith("gpt-5-mini")) return new Price(0.25, 2.00, true);
        if (value.startsWith("gpt-5-nano")) return new Price(0.05, 0.40, true);
        if (value.startsWith("gpt-5.1") || value.startsWith("gpt-5-")) return new Price(1.25, 10.00, true);
        return Price.unknown();
    }

    public static long estimateWon(
            String provider, String model, long inputTokens, long outputTokens, int wonPerUsd) {
        Price price = resolve(provider, model);
        if (!price.known || wonPerUsd <= 0) return -1L;
        double usd = Math.max(0L, inputTokens) * price.inputUsdPerMillion / 1_000_000.0
                + Math.max(0L, outputTokens) * price.outputUsdPerMillion / 1_000_000.0;
        if (usd <= 0.0) return 0L;
        return Math.max(1L, Math.round(usd * wonPerUsd));
    }

    public static final class Price {
        public final double inputUsdPerMillion;
        public final double outputUsdPerMillion;
        public final boolean known;

        Price(double inputUsdPerMillion, double outputUsdPerMillion, boolean known) {
            this.inputUsdPerMillion = inputUsdPerMillion;
            this.outputUsdPerMillion = outputUsdPerMillion;
            this.known = known;
        }

        static Price unknown() {
            return new Price(0.0, 0.0, false);
        }
    }
}
