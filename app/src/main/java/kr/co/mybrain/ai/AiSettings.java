package kr.co.mybrain.ai;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.regex.Pattern;

/**
 * 사용자가 선택한 분석 방식과 클라우드 AI 모델, 개인정보 보호 옵션을 관리합니다.
 * API 키는 이 파일에 저장하지 않고 SecureApiKeyStore에서 별도로 암호화합니다.
 */
public final class AiSettings {
    /** 규칙 분석을 먼저 사용하고 복잡한 문장만 등록된 클라우드 AI로 보냅니다. */
    public static final String PROVIDER_AUTO = "AUTO";
    /** 이전 버전과 내부 호환을 위해 남겨 둔 휴대전화 Ollama 공급자 값입니다. */
    public static final String PROVIDER_OLLAMA = "OLLAMA";
    /** 네트워크 없이 동작하는 기기 내부 규칙 분석기입니다. */
    public static final String PROVIDER_LOCAL = "LOCAL";
    public static final String PROVIDER_OPENAI = "OPENAI";
    public static final String PROVIDER_GEMINI = "GEMINI";

    public static final String DEFAULT_OLLAMA_MODEL = "qwen3:1.7b";
    public static final String DEFAULT_OLLAMA_BASE_URL = "http://127.0.0.1:11434";
    public static final String DEFAULT_OPENAI_MODEL = "gpt-5-mini";
    public static final String DEFAULT_GEMINI_MODEL = "gemini-3.5-flash";

    /** 이전 Ollama 설정 데이터의 주소를 안전하게 읽기 위한 localhost 검증식입니다. */
    private static final Pattern OLLAMA_LOCAL_URL = Pattern.compile(
            "^http://(?:127\\.0\\.0\\.1|localhost)(?::(?:[1-9]\\d{0,4}))?$",
            Pattern.CASE_INSENSITIVE);

    private static final String PREFS = "mybrain_ai_settings";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_PREFERRED_CLOUD = "preferred_cloud_provider";
    private static final String KEY_OLLAMA_MODEL = "ollama_model";
    private static final String KEY_OLLAMA_BASE_URL = "ollama_base_url";
    private static final String KEY_OPENAI_MODEL = "openai_model";
    private static final String KEY_GEMINI_MODEL = "gemini_model";
    private static final String KEY_CONFIRM_CLOUD = "confirm_cloud";
    private static final String KEY_FALLBACK_LOCAL = "fallback_local";

    /** 새 설치에서는 빠르고 안정적인 자동 추천 방식을 기본으로 사용합니다. */
    public String provider = PROVIDER_AUTO;
    /** 자동 추천에서 두 키가 모두 등록됐을 때 먼저 사용할 클라우드 AI입니다. */
    public String preferredCloudProvider = PROVIDER_OPENAI;
    public String ollamaModel = DEFAULT_OLLAMA_MODEL;
    public String ollamaBaseUrl = DEFAULT_OLLAMA_BASE_URL;
    public String openAiModel = DEFAULT_OPENAI_MODEL;
    public String geminiModel = DEFAULT_GEMINI_MODEL;
    public boolean confirmBeforeCloud = true;
    public boolean fallbackToLocal = true;

    private AiSettings() {
    }

    /** 기기에 저장된 설정을 읽고, 이전 Ollama 기본값은 자동 추천 방식으로 안전하게 전환합니다. */
    public static AiSettings load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AiSettings settings = new AiSettings();

        String storedProvider = normalizeProvider(prefs.getString(KEY_PROVIDER, PROVIDER_AUTO));
        settings.provider = PROVIDER_OLLAMA.equals(storedProvider) ? PROVIDER_AUTO : storedProvider;
        settings.preferredCloudProvider = normalizeCloudProvider(
                prefs.getString(KEY_PREFERRED_CLOUD, PROVIDER_OPENAI));
        settings.ollamaModel = normalizeModel(
                prefs.getString(KEY_OLLAMA_MODEL, DEFAULT_OLLAMA_MODEL), DEFAULT_OLLAMA_MODEL);
        settings.ollamaBaseUrl = normalizeOllamaBaseUrl(
                prefs.getString(KEY_OLLAMA_BASE_URL, DEFAULT_OLLAMA_BASE_URL));
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
                .putString(KEY_PREFERRED_CLOUD, normalizeCloudProvider(preferredCloudProvider))
                .putString(KEY_OLLAMA_MODEL, normalizeModel(ollamaModel, DEFAULT_OLLAMA_MODEL))
                .putString(KEY_OLLAMA_BASE_URL, normalizeOllamaBaseUrl(ollamaBaseUrl))
                .putString(KEY_OPENAI_MODEL, normalizeModel(openAiModel, DEFAULT_OPENAI_MODEL))
                .putString(KEY_GEMINI_MODEL, normalizeModel(geminiModel, DEFAULT_GEMINI_MODEL))
                .putBoolean(KEY_CONFIRM_CLOUD, confirmBeforeCloud)
                .putBoolean(KEY_FALLBACK_LOCAL, fallbackToLocal)
                .apply();
    }

    /** 자동 추천 또는 실제 언어 모델을 사용하는 분석 방식인지 확인합니다. */
    public boolean isModelProvider() {
        return PROVIDER_AUTO.equals(provider)
                || PROVIDER_OLLAMA.equals(provider)
                || PROVIDER_OPENAI.equals(provider)
                || PROVIDER_GEMINI.equals(provider);
    }

    /** 입력 내용이 외부 회사 서버로 전송되는 공급자인지 확인합니다. */
    public boolean isCloudProvider() {
        return PROVIDER_OPENAI.equals(provider) || PROVIDER_GEMINI.equals(provider);
    }

    public boolean isAutoProvider() {
        return PROVIDER_AUTO.equals(provider);
    }

    public boolean isOllamaProvider() {
        return PROVIDER_OLLAMA.equals(provider);
    }

    public String providerLabel() {
        if (PROVIDER_AUTO.equals(provider)) return "자동 추천";
        if (PROVIDER_OLLAMA.equals(provider)) return "Ollama";
        if (PROVIDER_OPENAI.equals(provider)) return "GPT";
        if (PROVIDER_GEMINI.equals(provider)) return "Gemini";
        return "기본 규칙";
    }

    public String selectedModel() {
        if (PROVIDER_AUTO.equals(provider)) return "규칙 우선 · 필요 시 클라우드 AI";
        if (PROVIDER_OLLAMA.equals(provider)) return ollamaModel;
        if (PROVIDER_OPENAI.equals(provider)) return openAiModel;
        if (PROVIDER_GEMINI.equals(provider)) return geminiModel;
        return "기기 내부 규칙 분석";
    }

    public String preferredCloudLabel() {
        return PROVIDER_GEMINI.equals(preferredCloudProvider) ? "Gemini" : "GPT";
    }

    public static String normalizeProvider(String value) {
        if (PROVIDER_AUTO.equals(value)) return PROVIDER_AUTO;
        if (PROVIDER_OLLAMA.equals(value)) return PROVIDER_OLLAMA;
        if (PROVIDER_OPENAI.equals(value)) return PROVIDER_OPENAI;
        if (PROVIDER_GEMINI.equals(value)) return PROVIDER_GEMINI;
        if (PROVIDER_LOCAL.equals(value)) return PROVIDER_LOCAL;
        return PROVIDER_AUTO;
    }

    public static String normalizeCloudProvider(String value) {
        return PROVIDER_GEMINI.equals(value) ? PROVIDER_GEMINI : PROVIDER_OPENAI;
    }

    /** 이전 데이터 호환을 위해 같은 휴대전화의 localhost 주소만 허용합니다. */
    public static boolean isAllowedOllamaBaseUrl(String value) {
        if (value == null) return false;
        String url = removeTrailingSlash(value.trim());
        return OLLAMA_LOCAL_URL.matcher(url).matches();
    }

    public static String normalizeOllamaBaseUrl(String value) {
        String url = removeTrailingSlash(value == null ? "" : value.trim());
        return isAllowedOllamaBaseUrl(url) ? url : DEFAULT_OLLAMA_BASE_URL;
    }

    private static String removeTrailingSlash(String value) {
        String result = value == null ? "" : value;
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static String normalizeModel(String value, String fallback) {
        String model = value == null ? "" : value.trim();
        return model.isEmpty() ? fallback : model;
    }
}
