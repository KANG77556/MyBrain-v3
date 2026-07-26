package kr.co.mybrain.v2.assistant;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kr.co.mybrain.v2.data.WorkItemEntity;

/** 인터넷 없이 동작하는 한국어 일정·할 일·메모 기본 분석기입니다. */
public final class KoreanNaturalLanguageParser {

    private static final Pattern TIME_RANGE = Pattern.compile("(오전|오후)?\\s*(\\d{1,2})(?:시|:)(?:(\\d{1,2})분?)?\\s*(?:부터|~|-)\\s*(오전|오후)?\\s*(\\d{1,2})(?:시|:)(?:(\\d{1,2})분?)?");
    private static final Pattern SINGLE_TIME = Pattern.compile("(오전|오후)?\\s*(\\d{1,2})시(?:\\s*(\\d{1,2})분)?");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일");

    private KoreanNaturalLanguageParser() { }

    public static ParsedWorkItem parse(String rawText, ZoneId zoneId) {
        String text = rawText == null ? "" : rawText.trim();
        String lower = text.toLowerCase(Locale.KOREA);
        ParsedWorkItem result = new ParsedWorkItem();
        result.sourceText = text;
        result.title = cleanTitle(text);

        boolean hasDateWord = containsAny(lower, "오늘", "내일", "모레", "다음 주", "이번 주", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일") || MONTH_DAY.matcher(text).find();
        boolean hasTimeWord = SINGLE_TIME.matcher(text).find() || TIME_RANGE.matcher(text).find();
        boolean taskWord = containsAny(lower, "해야", "할 일", "제출", "보내", "준비", "확인", "전화", "구매", "처리", "완료");
        boolean scheduleWord = containsAny(lower, "회의", "수업", "약속", "출장", "행사", "상담", "일정", "예약", "방문");

        if (hasDateWord || hasTimeWord || scheduleWord) result.type = WorkItemEntity.TYPE_SCHEDULE;
        else if (taskWord) result.type = WorkItemEntity.TYPE_TASK;
        else result.type = WorkItemEntity.TYPE_MEMO;

        LocalDate date = resolveDate(text, LocalDate.now(zoneId));
        LocalTime[] times = resolveTimes(text);
        if (date != null && result.type.equals(WorkItemEntity.TYPE_SCHEDULE)) {
            if (times[0] == null) {
                result.startAt = atZone(date.atStartOfDay(), zoneId);
                result.endAt = atZone(date.plusDays(1).atStartOfDay(), zoneId);
                result.allDay = true;
            } else {
                LocalDateTime start = LocalDateTime.of(date, times[0]);
                LocalDateTime end = LocalDateTime.of(date, times[1] == null ? times[0].plusHours(1) : times[1]);
                if (!end.isAfter(start)) end = end.plusDays(1);
                result.startAt = atZone(start, zoneId);
                result.endAt = atZone(end, zoneId);
            }
        } else if (date != null && result.type.equals(WorkItemEntity.TYPE_TASK)) {
            result.startAt = atZone(LocalDateTime.of(date, times[0] == null ? LocalTime.of(9, 0) : times[0]), zoneId);
        }

        if (containsAny(lower, "매일", "매일마다")) result.repeatRule = "DAILY";
        else if (containsAny(lower, "평일마다", "평일")) result.repeatRule = "WEEKDAYS";
        else if (containsAny(lower, "매주", "매주마다")) result.repeatRule = "WEEKLY";
        else if (containsAny(lower, "매월", "매달")) result.repeatRule = "MONTHLY";

        if (containsAny(lower, "긴급", "중요", "꼭", "반드시")) result.priority = "HIGH";
        result.confidence = result.type.equals(WorkItemEntity.TYPE_MEMO) ? 0.62f : (hasDateWord || hasTimeWord ? 0.88f : 0.76f);
        return result;
    }

    private static LocalDate resolveDate(String text, LocalDate today) {
        if (text.contains("모레")) return today.plusDays(2);
        if (text.contains("내일")) return today.plusDays(1);
        if (text.contains("오늘")) return today;

        Matcher monthDay = MONTH_DAY.matcher(text);
        if (monthDay.find()) {
            int month = Integer.parseInt(monthDay.group(1));
            int day = Integer.parseInt(monthDay.group(2));
            LocalDate candidate = LocalDate.of(today.getYear(), month, day);
            if (candidate.isBefore(today.minusDays(1))) candidate = candidate.plusYears(1);
            return candidate;
        }

        DayOfWeek target = weekday(text);
        if (target != null) {
            boolean nextWeek = text.contains("다음 주") || text.contains("다음주");
            LocalDate base = nextWeek ? today.plusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : today;
            return base.with(TemporalAdjusters.nextOrSame(target));
        }
        return null;
    }

    private static LocalTime[] resolveTimes(String text) {
        Matcher range = TIME_RANGE.matcher(text);
        if (range.find()) {
            LocalTime start = time(range.group(1), range.group(2), range.group(3));
            String endAmPm = range.group(4) == null ? range.group(1) : range.group(4);
            LocalTime end = time(endAmPm, range.group(5), range.group(6));
            return new LocalTime[]{start, end};
        }
        Matcher single = SINGLE_TIME.matcher(text);
        if (single.find()) return new LocalTime[]{time(single.group(1), single.group(2), single.group(3)), null};
        return new LocalTime[]{null, null};
    }

    private static LocalTime time(String amPm, String hourText, String minuteText) {
        int hour = Integer.parseInt(hourText);
        int minute = minuteText == null ? 0 : Integer.parseInt(minuteText);
        if ("오후".equals(amPm) && hour < 12) hour += 12;
        if ("오전".equals(amPm) && hour == 12) hour = 0;
        return LocalTime.of(Math.min(hour, 23), Math.min(minute, 59));
    }

    private static DayOfWeek weekday(String text) {
        if (text.contains("월요일")) return DayOfWeek.MONDAY;
        if (text.contains("화요일")) return DayOfWeek.TUESDAY;
        if (text.contains("수요일")) return DayOfWeek.WEDNESDAY;
        if (text.contains("목요일")) return DayOfWeek.THURSDAY;
        if (text.contains("금요일")) return DayOfWeek.FRIDAY;
        if (text.contains("토요일")) return DayOfWeek.SATURDAY;
        if (text.contains("일요일")) return DayOfWeek.SUNDAY;
        return null;
    }

    private static long atZone(LocalDateTime value, ZoneId zoneId) {
        return value.atZone(zoneId).toInstant().toEpochMilli();
    }

    private static String cleanTitle(String text) {
        String cleaned = text.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }
}
