package kr.co.mybrain.v2.settings;

import android.content.Context;
import android.content.SharedPreferences;

public final class AiSettings {
    public static final String PROVIDER_OPENAI = "OPENAI";
    public static final String PROVIDER_GEMINI = "GEMINI";
    public static final String DEFAULT_OPENAI_MODEL = "gpt-5-mini";
    public static final String DEFAULT_GEMINI_MODEL = "gemini-3.6-flash";

    private static final String PREFS = "mybrain_v2_ai_settings";
    public String provider = PROVIDER_OPENAI;
    public String openAiModel = DEFAULT_OPENAI_MODEL;
    public String geminiModel = DEFAULT_GEMINI_MODEL;

    private AiSettings() {}

    public static AiSettings load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AiSettings value = new AiSettings();
        value.provider = normalizeProvider(prefs.getString("provider", PROVIDER_OPENAI));
        value.openAiModel = normalizeModel(prefs.getString("openai_model", DEFAULT_OPENAI_MODEL), DEFAULT_OPENAI_MODEL);
        value.geminiModel = normalizeModel(prefs.getString("gemini_model", DEFAULT_GEMINI_MODEL), DEFAULT_GEMINI_MODEL);
        return value;
    }

    public void save(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("provider", normalizeProvider(provider))
                .putString("openai_model", normalizeModel(openAiModel, DEFAULT_OPENAI_MODEL))
                .putString("gemini_model", normalizeModel(geminiModel, DEFAULT_GEMINI_MODEL))
                .apply();
    }

    public String selectedModel() {
        return PROVIDER_GEMINI.equals(provider) ? geminiModel : openAiModel;
    }

    public String providerLabel() {
        return PROVIDER_GEMINI.equals(provider) ? "Gemini" : "GPT";
    }

    public static String normalizeProvider(String value) {
        return PROVIDER_GEMINI.equals(value) ? PROVIDER_GEMINI : PROVIDER_OPENAI;
    }

    public static String normalizeModel(String value, String fallback) {
        String model = value == null ? "" : value.trim();
        return model.isEmpty() ? fallback : model;
    }

    public static void saveConnectionResult(Context context, String provider, boolean success, String message, long testedAt) {
        String prefix = PROVIDER_GEMINI.equals(normalizeProvider(provider)) ? "gemini" : "openai";
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(prefix + "_last_at", testedAt)
                .putBoolean(prefix + "_last_ok", success)
                .putString(prefix + "_last_message", sanitize(message))
                .apply();
    }

    public static ConnectionRecord loadConnectionRecord(Context context, String provider) {
        String prefix = PROVIDER_GEMINI.equals(normalizeProvider(provider)) ? "gemini" : "openai";
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new ConnectionRecord(
                prefs.getLong(prefix + "_last_at", 0L),
                prefs.getBoolean(prefix + "_last_ok", false),
                prefs.getString(prefix + "_last_message", ""));
    }

    private static String sanitize(String value) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return text.length() <= 240 ? text : text.substring(0, 240) + "…";
    }

    public static final class ConnectionRecord {
        public final long testedAt;
        public final boolean success;
        public final String message;
        ConnectionRecord(long testedAt, boolean success, String message) {
            this.testedAt = testedAt;
            this.success = success;
            this.message = message == null ? "" : message;
        }
    }
}
