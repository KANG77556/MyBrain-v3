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

    private static final Pattern TIME_RANGE = Pattern.compile("(오전|오후|낮|밤)?\\s*(\\d{1,2})(?:시|:)(?:(\\d{1,2})분?)?\\s*(?:부터|~|～|-)\\s*(오전|오후|낮|밤)?\\s*(\\d{1,2})(?:시|:)(?:(\\d{1,2})분?)?");
    private static final Pattern SINGLE_TIME = Pattern.compile("(오전|오후|낮|밤)?\\s*(\\d{1,2})(?:시|:)(?:(\\d{1,2})분?)?");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일");
    private static final Pattern WEEKDAY_RANGE = Pattern.compile("(다음\\s*주|이번\\s*주)?\\s*(월요일|화요일|수요일|목요일|금요일|토요일|일요일|월|화|수|목|금|토|일)\\s*(?:부터|~|～|-)\\s*(월요일|화요일|수요일|목요일|금요일|토요일|일요일|월|화|수|목|금|토|일)");
    private static final Pattern REMINDER = Pattern.compile("(?:알림\\s*)?(\\d+)\\s*(분|시간)\\s*전(?:\\s*알림)?");

    private KoreanNaturalLanguageParser() { }

    public static ParsedWorkItem parse(String rawText, ZoneId zoneId) {
        String text = rawText == null ? "" : rawText.trim();
        String lower = text.toLowerCase(Locale.KOREA);
        ParsedWorkItem result = new ParsedWorkItem();
        result.sourceText = text;
        result.title = cleanTitle(text);

        boolean hasDateWord = containsAny(lower, "오늘", "내일", "모레", "다음 주", "다음주", "이번 주", "이번주", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일") || MONTH_DAY.matcher(text).find();
        boolean hasTimeWord = TIME_RANGE.matcher(text).find() || SINGLE_TIME.matcher(text).find();
        boolean taskWord = containsAny(lower, "해야", "할 일", "제출", "보내", "준비", "확인", "전화", "구매", "처리", "완료");
        boolean scheduleWord = containsAny(lower, "회의", "수업", "약속", "출장", "행사", "상담", "일정", "예약", "방문");

        if (hasDateWord || hasTimeWord || scheduleWord) result.type = WorkItemEntity.TYPE_SCHEDULE;
        else if (taskWord) result.type = WorkItemEntity.TYPE_TASK;
        else result.type = WorkItemEntity.TYPE_MEMO;

        LocalDate today = LocalDate.now(zoneId);
        LocalDate[] rangeDates = resolveWeekdayRange(text, today);
        LocalDate date = rangeDates == null ? resolveDate(text, today) : rangeDates[0];
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

        if (containsAny(lower, "알림 없음", "알림없이", "알림 없이")) {
            result.reminderExplicitlyDisabled = true;
        } else {
            Matcher reminder = REMINDER.matcher(text);
            if (reminder.find() && result.startAt != null) {
                long amount = Long.parseLong(reminder.group(1));
                long minutes = "시간".equals(reminder.group(2)) ? amount * 60L : amount;
                result.reminderAt = result.startAt - minutes * 60_000L;
            }
        }

        if (rangeDates != null && result.type.equals(WorkItemEntity.TYPE_SCHEDULE)) {
            boolean skipWeekends = rangeDates[0].getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue()
                    && rangeDates[1].getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue();
            result.repeatRule = "RANGE_DAILY|" + rangeDates[1].toEpochDay() + "|" + (skipWeekends ? "1" : "0");
        } else if (containsAny(lower, "평일마다", "평일")) result.repeatRule = "WEEKDAYS";
        else if (containsAny(lower, "매일", "매일마다")) result.repeatRule = "DAILY";
        else if (containsAny(lower, "매주", "매주마다")) result.repeatRule = "WEEKLY";
        else if (containsAny(lower, "매월", "매달")) result.repeatRule = "MONTHLY";

        if (containsAny(lower, "긴급", "중요", "꼭", "반드시")) result.priority = "HIGH";
        result.confidence = rangeDates != null ? 0.96f : (result.type.equals(WorkItemEntity.TYPE_MEMO) ? 0.62f : (hasDateWord || hasTimeWord ? 0.88f : 0.76f));
        return result;
    }

    private static LocalDate[] resolveWeekdayRange(String text, LocalDate today) {
        Matcher matcher = WEEKDAY_RANGE.matcher(text);
        if (!matcher.find()) return null;
        DayOfWeek first = weekdayValue(matcher.group(2));
        DayOfWeek last = weekdayValue(matcher.group(3));
        if (first == null || last == null) return null;

        String weekWord = matcher.group(1) == null ? "" : matcher.group(1).replace(" ", "");
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if ("다음주".equals(weekWord)) monday = monday.plusWeeks(1);
        LocalDate start = monday.plusDays(first.getValue() - 1L);
        LocalDate end = monday.plusDays(last.getValue() - 1L);
        if (end.isBefore(start)) end = end.plusWeeks(1);
        return new LocalDate[]{start, end};
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
        if (("오후".equals(amPm) || "낮".equals(amPm) || "밤".equals(amPm)) && hour < 12) hour += 12;
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

    private static DayOfWeek weekdayValue(String value) {
        if (value == null) return null;
        if (value.startsWith("월")) return DayOfWeek.MONDAY;
        if (value.startsWith("화")) return DayOfWeek.TUESDAY;
        if (value.startsWith("수")) return DayOfWeek.WEDNESDAY;
        if (value.startsWith("목")) return DayOfWeek.THURSDAY;
        if (value.startsWith("금")) return DayOfWeek.FRIDAY;
        if (value.startsWith("토")) return DayOfWeek.SATURDAY;
        if (value.startsWith("일")) return DayOfWeek.SUNDAY;
        return null;
    }

    private static long atZone(LocalDateTime value, ZoneId zoneId) {
        return value.atZone(zoneId).toInstant().toEpochMilli();
    }

    private static String cleanTitle(String text) {
        String[] lines = text.split("\\R");
        for (String line : lines) {
            String candidate = line.trim();
            if (!candidate.isEmpty() && !looksLikeOnlyScheduleDetails(candidate)) {
                return candidate.length() > 60 ? candidate.substring(0, 60) : candidate;
            }
        }
        String cleaned = text.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
    }

    private static boolean looksLikeOnlyScheduleDetails(String value) {
        return WEEKDAY_RANGE.matcher(value).find() || TIME_RANGE.matcher(value).find()
                || REMINDER.matcher(value).find() || value.startsWith("알림");
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }
}
