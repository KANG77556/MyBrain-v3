package kr.co.mybrain.ai;

import android.content.Context;

/**
 * 자동 추천 모드에서 기기 내부 규칙 분석과 클라우드 AI 중 사용할 방식을 결정합니다.
 * 날짜와 시간이 단순한 문장은 기기에서 처리하고, 여러 행동이 섞인 문장만 AI를 사용합니다.
 */
public final class HybridAnalysisRouter {
    private static final String[] ACTION_WORDS = {
            "회의", "수업", "상담", "면담", "출장", "행사", "방문", "예약", "약속",
            "제출", "작성", "확인", "준비", "전달", "연락", "전화", "신청", "처리",
            "보고", "정리", "완료", "보내", "만들", "검토", "공유", "메모"
    };

    private static final String[] CONNECTOR_WORDS = {
            "그리고", "그다음", "그 후", "또한", "또 ", "하고", "해서", "한 뒤",
            "하면서", "뿐만 아니라", "와 함께", "및 "
    };

    private HybridAnalysisRouter() {
    }

    /**
     * 현재 설정과 문장 난이도를 기준으로 실제 분석 공급자를 선택합니다.
     * 자동 추천이 아니면 사용자가 선택한 공급자를 그대로 반환합니다.
     */
    public static Decision resolve(Context context, AiSettings settings, String text) {
        if (settings == null) {
            return new Decision(AiSettings.PROVIDER_LOCAL, "설정이 없어 기본 규칙을 사용합니다.");
        }
        if (!settings.isAutoProvider()) {
            return new Decision(settings.provider, settings.providerLabel() + " 직접 선택");
        }
        if (!shouldUseCloud(text)) {
            return new Decision(AiSettings.PROVIDER_LOCAL,
                    "날짜·시간 중심의 단순 문장이라 기기 내부 규칙으로 처리합니다.");
        }

        String preferred = AiSettings.normalizeCloudProvider(settings.preferredCloudProvider);
        String preferredKey = keyName(preferred);
        if (SecureApiKeyStore.has(context, preferredKey)) {
            return new Decision(preferred,
                    "여러 행동이 포함돼 우선 AI인 " + providerLabel(preferred) + "를 사용합니다.");
        }

        String alternative = AiSettings.PROVIDER_OPENAI.equals(preferred)
                ? AiSettings.PROVIDER_GEMINI : AiSettings.PROVIDER_OPENAI;
        if (SecureApiKeyStore.has(context, keyName(alternative))) {
            return new Decision(alternative,
                    "우선 AI 키가 없어 등록된 " + providerLabel(alternative) + "를 사용합니다.");
        }

        return new Decision(AiSettings.PROVIDER_LOCAL,
                "등록된 클라우드 API 키가 없어 기본 규칙으로 처리합니다.");
    }

    /** 여러 사건이나 행동을 나눠야 하는 문장인지 보수적으로 판단합니다. */
    public static boolean shouldUseCloud(String rawText) {
        String text = rawText == null ? "" : rawText.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) return false;

        int actionCount = countContained(text, ACTION_WORDS);
        int connectorCount = countContained(text, CONNECTOR_WORDS);
        int separatorCount = countCharacter(text, ',')
                + countCharacter(text, ';')
                + countCharacter(text, '·');

        // 지나치게 긴 문장은 단순 규칙만으로 내용을 잃을 가능성이 높습니다.
        if (text.length() >= 110 && actionCount >= 1) return true;

        // 서로 다른 행동이 두 개 이상이고 연결 표현이나 나열 기호가 있으면 AI가 분리합니다.
        if (actionCount >= 2 && (connectorCount >= 1 || separatorCount >= 1)) return true;

        // 행동 표현이 세 개 이상이면 연결어가 생략돼도 복합 문장으로 봅니다.
        return actionCount >= 3;
    }

    private static int countContained(String text, String[] words) {
        int count = 0;
        for (String word : words) {
            if (text.contains(word)) count++;
        }
        return count;
    }

    private static int countCharacter(String text, char value) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == value) count++;
        }
        return count;
    }

    public static String keyName(String provider) {
        return AiSettings.PROVIDER_GEMINI.equals(provider)
                ? SecureApiKeyStore.KEY_GEMINI : SecureApiKeyStore.KEY_OPENAI;
    }

    public static String providerLabel(String provider) {
        return AiSettings.PROVIDER_GEMINI.equals(provider) ? "Gemini" : "GPT";
    }

    /** 자동 선택 결과와 사용자 안내 문구를 함께 전달합니다. */
    public static final class Decision {
        public final String provider;
        public final String reason;

        public Decision(String provider, String reason) {
            this.provider = provider;
            this.reason = reason == null ? "" : reason;
        }

        public boolean usesCloud() {
            return AiSettings.PROVIDER_OPENAI.equals(provider)
                    || AiSettings.PROVIDER_GEMINI.equals(provider);
        }
    }
}
