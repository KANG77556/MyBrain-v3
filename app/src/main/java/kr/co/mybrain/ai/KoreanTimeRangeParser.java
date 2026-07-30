package kr.co.mybrain.ai;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** '오전 9시부터 12시까지', '오전 9:00~오후 12:00' 형식의 시작·종료 시각을 분석합니다. */
public final class KoreanTimeRangeParser {
    private static final Pattern COLON_RANGE = Pattern.compile(
            "(?:(오전|오후)\\s*)?(\\d{1,2}):(\\d{2})\\s*(?:~|〜|–|—|부터|-)\\s*"
                    + "(?:(오전|오후)\\s*)?(\\d{1,2}):(\\d{2})(?:\\s*까지)?");
    private static final Pattern HOUR_RANGE = Pattern.compile(
            "(?:(오전|오후)\\s*)?(\\d{1,2})시(?:\\s*(\\d{1,2})분)?\\s*(?:부터|~|〜|–|—|-)\\s*"
                    + "(?:(오전|오후)\\s*)?(\\d{1,2})시(?:\\s*(\\d{1,2})분)?(?:\\s*까지)?");

    private KoreanTimeRangeParser() { }

    public static Range parse(String raw) {
        String text = raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
        Matcher colon = COLON_RANGE.matcher(text);
        if (colon.find()) {
            String start = format(colon.group(1), colon.group(2), colon.group(3));
            String end = format(inherit(colon.group(4), colon.group(1)), colon.group(5), colon.group(6));
            return valid(start, end);
        }
        Matcher hour = HOUR_RANGE.matcher(text);
        if (hour.find()) {
            String start = format(hour.group(1), hour.group(2), emptyToZero(hour.group(3)));
            String end = format(inherit(hour.group(4), hour.group(1)), hour.group(5), emptyToZero(hour.group(6)));
            return valid(start, end);
        }
        return new Range("", "");
    }

    private static String inherit(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String emptyToZero(String value) {
        return value == null || value.isEmpty() ? "0" : value;
    }

    private static String format(String meridiem, String hourText, String minuteText) {
        try {
            int hour = Integer.parseInt(hourText);
            int minute = Integer.parseInt(minuteText);
            if ("오후".equals(meridiem) && hour < 12) hour += 12;
            if ("오전".equals(meridiem) && hour == 12) hour = 0;
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return "";
            return String.format(Locale.KOREA, "%02d:%02d", hour, minute);
        } catch (Exception ignored) {
            return "";
        }
    }

    /** Android 저장소에 의존하지 않는 순수 파서 검증입니다. */
    private static Range valid(String start, String end) {
        int startMinute = minuteOfDay(start);
        int endMinute = minuteOfDay(end);
        return startMinute >= 0 && endMinute > startMinute
                ? new Range(start, end) : new Range("", "");
    }

    private static int minuteOfDay(String value) {
        if (value == null || !value.matches("\\d{2}:\\d{2}")) return -1;
        try {
            int hour = Integer.parseInt(value.substring(0, 2));
            int minute = Integer.parseInt(value.substring(3, 5));
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return -1;
            return hour * 60 + minute;
        } catch (Exception ignored) {
            return -1;
        }
    }

    public static final class Range {
        public final String startTime;
        public final String endTime;
        public Range(String startTime, String endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }
        public boolean isValid() { return !startTime.isEmpty() && !endTime.isEmpty(); }
    }
}
