package kr.co.mybrain.ai;

import java.util.List;

/** 진단 결과를 공유 가능한 일반 텍스트로 변환합니다. */
public final class DiagnosticsReportFormatter {
    private DiagnosticsReportFormatter() { }
    public static String format(List<DiagnosticItem> items) {
        StringBuilder out = new StringBuilder("MyBrain AI 기기 진단 결과\n");
        for (DiagnosticItem item : items) out.append(item.title).append(": ").append(statusLabel(item.status)).append(" · ").append(sanitize(item.description)).append("\n");
        return out.toString().trim();
    }
    static String sanitize(String value) {
        if (value == null) return "";
        String lower = value.toLowerCase();
        if (lower.contains("api key") || lower.contains("apikey") || lower.contains("sk-") || lower.contains("aiza")) return "보안상 제외된 정보";
        return value.replace('\t', ' ').replace('\r', ' ').trim();
    }
    private static String statusLabel(DiagnosticItem.Status status) {
        switch (status) {
            case NORMAL: return "정상";
            case ACTION_REQUIRED: return "확인 필요";
            case UNSUPPORTED: return "지원하지 않음";
            case ERROR: return "점검 실패";
            default: return "점검 중";
        }
    }
}
