package kr.co.mybrain.ai;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 한국어로 입력한 요일 범위와 시간 범위를 기기 내부에서 해석합니다.
 * 작은 로컬 AI가 범위 문장을 놓치더라도 일정이 정확하게 생성되도록 하는 안전장치입니다.
 */
public final class KoreanScheduleRangeParser {
    private static final Pattern NEXT_WEEK_DAY_RANGE = Pattern.compile(
            "다음\\s*주\\s*(월|화|수|목|금|토|일)요일\\s*(?:부터|에서|~|-)\\s*"
                    + "(월|화|수|목|금|토|일)요일\\s*까지");

    private static final Pattern TIME_RANGE = Pattern.compile(
            "(?:(오전|오후)\\s*)?(\\d{1,2})(?:\\s*시)?(?:(\\d{1,2})\\s*분)?\\s*부터\\s*"
                    + "(?:(오전|오후)\\s*)?(\\d{1,2})(?:\\s*시)?(?:(\\d{1,2})\\s*분)?\\s*까지");

    private KoreanScheduleRangeParser() {
    }

    /**
     * 지원하는 범위 문장이면 날짜별 일정 목록을 반환하고, 일반 문장이면 빈 목록을 반환합니다.
     */
    public static List<AiAnalysisResult> parse(String rawText, Date 기준시각) {
        String text = rawText == null ? "" : rawText.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) return Collections.emptyList();

        Matcher dayMatcher = NEXT_WEEK_DAY_RANGE.matcher(text);
        Matcher timeMatcher = TIME_RANGE.matcher(text);
        if (!dayMatcher.find() || !timeMatcher.find()) return Collections.emptyList();

        int startDayOffset = dayOffset(dayMatcher.group(1));
        int endDayOffset = dayOffset(dayMatcher.group(2));
        if (startDayOffset < 0 || endDayOffset < 0) return Collections.emptyList();
        if (endDayOffset < startDayOffset) endDayOffset += 7;

        int startHour = parseHour(timeMatcher.group(1), timeMatcher.group(2), false);
        int startMinute = parseMinute(timeMatcher.group(3));
        int endHour = parseEndHour(
                timeMatcher.group(1), timeMatcher.group(4), timeMatcher.group(5));
        int endMinute = parseMinute(timeMatcher.group(6));
        if (!validTime(startHour, startMinute) || !validTime(endHour, endMinute)) {
            return Collections.emptyList();
        }

        String subject = NEXT_WEEK_DAY_RANGE.matcher(text).replaceAll(" ");
        subject = TIME_RANGE.matcher(subject).replaceAll(" ");
        subject = subject.replaceAll("(?:매일|동안)", " ")
                .replaceAll("\\s+", " ").trim();
        if (subject.isEmpty()) subject = "일정";

        Calendar nextWeekMonday = Calendar.getInstance();
        nextWeekMonday.setTime(기준시각 == null ? new Date() : 기준시각);
        int dayOfWeek = nextWeekMonday.get(Calendar.DAY_OF_WEEK);
        int daysSinceMonday = (dayOfWeek + 5) % 7;
        nextWeekMonday.add(Calendar.DAY_OF_MONTH, -daysSinceMonday + 7);

        String startTime = formatTime(startHour, startMinute);
        String endTime = formatTime(endHour, endMinute);
        String rangeText = startTime + "~" + endTime;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);

        List<AiAnalysisResult> results = new ArrayList<>();
        int itemCount = Math.min(8, endDayOffset - startDayOffset + 1);
        for (int index = 0; index < itemCount; index++) {
            Calendar date = (Calendar) nextWeekMonday.clone();
            date.add(Calendar.DAY_OF_MONTH, startDayOffset + index);

            AiAnalysisResult result = new AiAnalysisResult();
            result.type = "일정";
            result.title = subject;
            result.content = subject + " · " + rangeText;
            result.date = dateFormat.format(date.getTime());
            result.time = startTime;
            result.repeatType = "NONE";
            results.add(result);
        }
        return results;
    }

    private static int dayOffset(String day) {
        if ("월".equals(day)) return 0;
        if ("화".equals(day)) return 1;
        if ("수".equals(day)) return 2;
        if ("목".equals(day)) return 3;
        if ("금".equals(day)) return 4;
        if ("토".equals(day)) return 5;
        if ("일".equals(day)) return 6;
        return -1;
    }

    private static int parseHour(String period, String hourText, boolean endTime) {
        try {
            int hour = Integer.parseInt(hourText);
            if (period == null || period.isEmpty()) return hour;
            if ("오전".equals(period)) return hour == 12 ? (endTime ? 12 : 0) : hour;
            if ("오후".equals(period)) return hour == 12 ? 12 : hour + 12;
            return hour;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /** 종료 시각에 오전·오후가 생략되면 시작 시각의 표현을 자연스럽게 이어받습니다. */
    private static int parseEndHour(String startPeriod, String endPeriod, String hourText) {
        String effectivePeriod = endPeriod;
        if ((effectivePeriod == null || effectivePeriod.isEmpty())
                && startPeriod != null && !startPeriod.isEmpty()) {
            effectivePeriod = startPeriod;
        }
        return parseHour(effectivePeriod, hourText, true);
    }

    private static int parseMinute(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean validTime(int hour, int minute) {
        return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
    }

    private static String formatTime(int hour, int minute) {
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }
}
