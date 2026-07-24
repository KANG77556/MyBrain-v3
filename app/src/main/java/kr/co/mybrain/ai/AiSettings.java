package kr.co.mybrain.ai;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.regex.Pattern;

/**
 * 사용자가 선택한 분석 방식과 모델, 개인정보 보호 옵션을 관리합니다.
 * API 키는 이 파일에 저장하지 않고 SecureApiKeyStore에서 별도로 암호화합니다.
 */
public final class AiSettings {
    /** 휴대전화 내부에서 실행되는 Ollama 서버입니다. 기본 기능이 아닌 고급 실험 기능입니다. */
    public static final String PROVIDER_OLLAMA = "OLLAMA";
    /** 인터넷과 생성형 AI 없이 작동하는 안정적인 기기 내부 규칙 분석기입니다. */
    public static final String PROVIDER_LOCAL = "LOCAL";
    public static final String PROVIDER_OPENAI = "OPENAI";
    public static final String PROVIDER_GEMINI = "GEMINI";

    public static final String DEFAULT_OLLAMA_MODEL = "qwen3:0.6b";
    public static final String DEFAULT_OLLAMA_BASE_URL = "http://127.0.0.1:11434";
    public static final String DEFAULT_OPENAI_MODEL = "gpt-5-mini";
    public static final String DEFAULT_GEMINI_MODEL = "gemini-3.5-flash";

    /** 같은 휴대전화 안의 Ollama만 연결하도록 localhost 주소만 허용합니다. */
    private static final Pattern OLLAMA_LOCAL_URL = Pattern.compile(
            "^http://(?:127\\.0\\.0\\.1|localhost)(?::(?:[1-9]\\d{0,4}))?$",
            Pattern.CASE_INSENSITIVE);

    private static final String PREFS = "mybrain_ai_settings";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_OLLAMA_MODEL = "ollama_model";
    private static final String KEY_OLLAMA_BASE_URL = "ollama_base_url";
    private static final String KEY_OPENAI_MODEL = "openai_model";
    private static final String KEY_GEMINI_MODEL = "gemini_model";
    private static final String KEY_CONFIRM_CLOUD = "confirm_cloud";
    private static final String KEY_FALLBACK_LOCAL = "fallback_local";
    private static final String KEY_EXPERIMENTAL_OLLAMA = "experimental_ollama";

    /** 새 설치에서는 가장 빠르고 안정적인 기기 내부 규칙 분석을 기본값으로 사용합니다. */
    public String provider = PROVIDER_LOCAL;
    public String ollamaModel = DEFAULT_OLLAMA_MODEL;
    public String ollamaBaseUrl = DEFAULT_OLLAMA_BASE_URL;
    public String openAiModel = DEFAULT_OPENAI_MODEL;
    public String geminiModel = DEFAULT_GEMINI_MODEL;
    public boolean confirmBeforeCloud = true;
    public boolean fallbackToLocal = true;
    public boolean experimentalOllamaEnabled = false;

    private AiSettings() {
    }

    /** 기기에 저장된 설정을 읽고 손상된 값은 안전한 기본값으로 복구합니다. */
    public static AiSettings load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AiSettings settings = new AiSettings();
        settings.experimentalOllamaEnabled = prefs.getBoolean(KEY_EXPERIMENTAL_OLLAMA, false);

        String storedProvider = normalizeProvider(prefs.getString(KEY_PROVIDER, PROVIDER_LOCAL));
        // 이전 버전에서 Ollama가 기본으로 저장됐더라도 실험 기능을 직접 켜지 않았다면 안전 모드로 이동합니다.
        if (PROVIDER_OLLAMA.equals(storedProvider) && !settings.experimentalOllamaEnabled) {
            storedProvider = PROVIDER_LOCAL;
        }
        settings.provider = storedProvider;

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
        String safeProvider = normalizeProvider(provider);
        if (PROVIDER_OLLAMA.equals(safeProvider) && !experimentalOllamaEnabled) {
            safeProvider = PROVIDER_LOCAL;
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PROVIDER, safeProvider)
                .putString(KEY_OLLAMA_MODEL, normalizeModel(ollamaModel, DEFAULT_OLLAMA_MODEL))
                .putString(KEY_OLLAMA_BASE_URL, normalizeOllamaBaseUrl(ollamaBaseUrl))
                .putString(KEY_OPENAI_MODEL, normalizeModel(openAiModel, DEFAULT_OPENAI_MODEL))
                .putString(KEY_GEMINI_MODEL, normalizeModel(geminiModel, DEFAULT_GEMINI_MODEL))
                .putBoolean(KEY_CONFIRM_CLOUD, confirmBeforeCloud)
                .putBoolean(KEY_FALLBACK_LOCAL, fallbackToLocal)
                .putBoolean(KEY_EXPERIMENTAL_OLLAMA, experimentalOllamaEnabled)
                .apply();

        provider = safeProvider;
    }

    /** 실제 언어 모델을 호출하는 공급자인지 확인합니다. */
    public boolean isModelProvider() {
        return PROVIDER_OPENAI.equals(provider)
                || PROVIDER_GEMINI.equals(provider)
                || (PROVIDER_OLLAMA.equals(provider) && experimentalOllamaEnabled);
    }

    /** 입력 내용이 외부 회사 서버로 전송되는 공급자인지 확인합니다. */
    public boolean isCloudProvider() {
        return PROVIDER_OPENAI.equals(provider) || PROVIDER_GEMINI.equals(provider);
    }

    public boolean isOllamaProvider() {
        return PROVIDER_OLLAMA.equals(provider) && experimentalOllamaEnabled;
    }

    public String providerLabel() {
        if (PROVIDER_OPENAI.equals(provider)) return "GPT";
        if (PROVIDER_GEMINI.equals(provider)) return "Gemini";
        if (PROVIDER_OLLAMA.equals(provider) && experimentalOllamaEnabled) return "Ollama 실험";
        return "기본 규칙";
    }

    public String selectedModel() {
        if (PROVIDER_OPENAI.equals(provider)) return openAiModel;
        if (PROVIDER_GEMINI.equals(provider)) return geminiModel;
        if (PROVIDER_OLLAMA.equals(provider) && experimentalOllamaEnabled) return ollamaModel;
        return "기기 내부 규칙 분석";
    }

    public static String normalizeProvider(String value) {
        if (PROVIDER_OPENAI.equals(value)) return PROVIDER_OPENAI;
        if (PROVIDER_GEMINI.equals(value)) return PROVIDER_GEMINI;
        if (PROVIDER_OLLAMA.equals(value)) return PROVIDER_OLLAMA;
        return PROVIDER_LOCAL;
    }

    /** 외부 네트워크 노출을 막기 위해 같은 휴대전화의 주소만 허용합니다. */
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
