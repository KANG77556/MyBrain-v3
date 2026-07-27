package kr.co.mybrain.v2.assistant;

import java.util.Locale;

/** AI 분석 진행·완료 상태를 화면 문구와 분리해 일관되게 판단합니다. */
public final class AnalysisUiPolicy {
    private AnalysisUiPolicy() {}

    public static String providerFromStatus(String status) {
        String value = status == null ? "" : status.trim();
        if (value.startsWith("Gemini") || value.contains(" Gemini")) return "GEMINI";
        if (value.startsWith("GPT") || value.contains(" GPT")) return "OPENAI";
        return "LOCAL";
    }

    public static boolean isCloudRunning(String status) {
        String value = status == null ? "" : status;
        return value.contains("정밀 분석 중") || value.contains("요청 중");
    }

    public static boolean isCloudSuccess(String status) {
        String value = status == null ? "" : status;
        return value.contains("정밀 분석 완료") || value.contains("최근 분석 결과 재사용");
    }

    public static boolean isTerminal(String status) {
        String value = status == null ? "" : status;
        return isCloudSuccess(value)
                || value.contains("자동 전환")
                || value.contains("연결 실패")
                || value.contains("분석 취소")
                || value.contains("인터넷 연결이 없어")
                || value.contains("기기 분석 완료")
                || value.contains("저장 완료");
    }

    public static String elapsed(long elapsedMs) {
        long safe = Math.max(0L, elapsedMs);
        if (safe < 1000L) return safe + "ms";
        return String.format(Locale.KOREA, "%.1f초", safe / 1000.0);
    }
}