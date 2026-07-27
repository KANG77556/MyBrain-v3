package kr.co.mybrain.v2.ui;

/** 일정 목록을 우선 보여주기 위한 달력 접기 정책입니다. */
public final class CalendarCompactPolicy {
    private CalendarCompactPolicy() {}

    public static boolean startCollapsed(boolean tablet, boolean hasFocusedItem) {
        if (hasFocusedItem) return true;
        return !tablet;
    }

    public static int toggleHeightDp(boolean largeTouchTargets) {
        return largeTouchTargets ? 52 : 48;
    }

    public static String toggleLabel(boolean expanded, String selectedDateLabel) {
        if (expanded) return "달력 접기";
        String date = selectedDateLabel == null ? "" : selectedDateLabel.trim();
        return date.isEmpty() ? "달력 펼치기" : "달력 펼치기 · " + date;
    }
}