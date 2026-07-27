package kr.co.mybrain.v2.ui;

/** 캘린더 전체 스크롤과 상세 목록의 최소 안전 여백을 계산합니다. */
public final class CalendarDetailLayoutPolicy {
    private CalendarDetailLayoutPolicy() {}

    public static int calendarHeightDp(boolean tablet) {
        return tablet ? 320 : 286;
    }

    public static int bottomContentPaddingDp(boolean oneHandMode, int textScalePercent) {
        int result = oneHandMode ? 144 : 112;
        if (textScalePercent >= 115) result += 24;
        return result;
    }

    public static int detailTopOffsetDp(int textScalePercent) {
        return textScalePercent >= 115 ? 16 : 10;
    }
}
