package kr.co.mybrain.ai;

/** Android API 호출과 분리된 진단 상태 판정 규칙입니다. */
public final class DiagnosticStatusResolver {
    private DiagnosticStatusResolver() { }
    public static DiagnosticItem.Status permission(boolean granted) {
        return granted ? DiagnosticItem.Status.NORMAL : DiagnosticItem.Status.ACTION_REQUIRED;
    }
    public static DiagnosticItem.Status exactAlarm(boolean supported, boolean allowed) {
        return supported ? permission(allowed) : DiagnosticItem.Status.UNSUPPORTED;
    }
    public static DiagnosticItem.Status widget(boolean pinSupported) {
        return pinSupported ? DiagnosticItem.Status.NORMAL : DiagnosticItem.Status.ACTION_REQUIRED;
    }
}
