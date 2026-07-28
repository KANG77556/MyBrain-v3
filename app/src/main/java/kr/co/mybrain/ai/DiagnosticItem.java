package kr.co.mybrain.ai;

/** 기기 진단 화면에서 사용하는 개인정보 비포함 결과 모델입니다. */
public final class DiagnosticItem {
    public enum Type { APP_INFO, DEVICE_INFO, NOTIFICATION_PERMISSION, EXACT_ALARM_PERMISSION, MICROPHONE_PERMISSION, BATTERY_OPTIMIZATION, WIDGET_SUPPORT, DATABASE }
    public enum Status { CHECKING, NORMAL, ACTION_REQUIRED, UNSUPPORTED, ERROR }
    public enum Action { COPY_APP_INFO, OPEN_NOTIFICATION_SETTINGS, OPEN_EXACT_ALARM_SETTINGS, REQUEST_MICROPHONE_PERMISSION, OPEN_BATTERY_SETTINGS, REQUEST_WIDGET_PIN, RETRY_DATABASE_CHECK }
    public final Type type;
    public final String title;
    public final String description;
    public final Status status;
    public final Action action;
    public DiagnosticItem(Type type, String title, String description, Status status, Action action) {
        this.type = type; this.title = title; this.description = description; this.status = status; this.action = action;
    }
    public static DiagnosticItem error(Type type, String title) {
        return new DiagnosticItem(type, title, "상태를 확인하는 중 오류가 발생했습니다.", Status.ERROR, null);
    }
}
