package kr.co.mybrain.ai;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 사용자가 선택한 AI 공급자와 모델, 개인정보 보호 옵션을 관리합니다.
 * API 키는 이 파일에 저장하지 않고 SecureApiKeyStore에서 별도로 암호화합니다.
 */
public final class AiSettings {
    public static final String PROVIDER_LOCAL = "LOCAL";
    public static final String PROVIDER_OPENAI = "OPENAI";
    public static final String PROVIDER_GEMINI = "GEMINI";

    public static final String DEFAULT_OPENAI_MODEL = "gpt-5-mini";
    public static final String DEFAULT_GEMINI_MODEL = "gemini-3.5-flash";

    private static final String PREFS = "mybrain_ai_settings";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_OPENAI_MODEL = "openai_model";
    private static final String KEY_GEMINI_MODEL = "gemini_model";
    private static final String KEY_CONFIRM_CLOUD = "confirm_cloud";
    private static final String KEY_FALLBACK_LOCAL = "fallback_local";

    public String provider = PROVIDER_LOCAL;
    public String openAiModel = DEFAULT_OPENAI_MODEL;
    public String geminiModel = DEFAULT_GEMINI_MODEL;
    public boolean confirmBeforeCloud = true;
    public boolean fallbackToLocal = true;

    private AiSettings() {
    }

    /** 기기에 저장된 설정을 읽고 손상된 값은 안전한 기본값으로 복구합니다. */
    public static AiSettings load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AiSettings settings = new AiSettings();
        settings.provider = normalizeProvider(prefs.getString(KEY_PROVIDER, PROVIDER_LOCAL));
        settings.openAiModel = normalizeModel(
                prefs.getString(KEY_OPENAI_MODEL, DEFAULT_OPENAI_MODEL), DEFAULT_OPENAI_MODEL);
        settings.geminiModel = normalizeModel(
                prefs.getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL), DEFAULT_GEMINI_MODEL);
        settings.confirmBeforeCloud = prefs.getBoolean(KEY_CONFIRM_CLOUD, true);
        settings.fallbackToLocal = prefs.getBoolean(KEY_FALLBACK_LOCAL, true);
        return settings;
    }

    /** 현재 설정을 저장합니다. API 키는 별도 암호화 저장소에서 처리합니다. */
    public void save(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PROVIDER, normalizeProvider(provider))
                .putString(KEY_OPENAI_MODEL, normalizeModel(openAiModel, DEFAULT_OPENAI_MODEL))
                .putString(KEY_GEMINI_MODEL, normalizeModel(geminiModel, DEFAULT_GEMINI_MODEL))
                .putBoolean(KEY_CONFIRM_CLOUD, confirmBeforeCloud)
                .putBoolean(KEY_FALLBACK_LOCAL, fallbackToLocal)
                .apply();
    }

    public boolean isCloudProvider() {
        return PROVIDER_OPENAI.equals(provider) || PROVIDER_GEMINI.equals(provider);
    }

    public String providerLabel() {
        if (PROVIDER_OPENAI.equals(provider)) return "GPT";
        if (PROVIDER_GEMINI.equals(provider)) return "Gemini";
        return "로컬";
    }

    public String selectedModel() {
        if (PROVIDER_OPENAI.equals(provider)) return openAiModel;
        if (PROVIDER_GEMINI.equals(provider)) return geminiModel;
        return "기기 내부 규칙 분석";
    }

    public static String normalizeProvider(String value) {
        if (PROVIDER_OPENAI.equals(value)) return PROVIDER_OPENAI;
        if (PROVIDER_GEMINI.equals(value)) return PROVIDER_GEMINI;
        return PROVIDER_LOCAL;
    }

    private static String normalizeModel(String value, String fallback) {
        String model = value == null ? "" : value.trim();
        return model.isEmpty() ? fallback : model;
    }
}
