package kr.co.mybrain.v2.assistant;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kr.co.mybrain.v2.data.WorkItemEntity;

/** 인터넷 없이 동작하는 한국어 일정·할 일·메모 분석기입니다. */
public final class KoreanNaturalLanguageParser {
    private static final Pattern TIME_RANGE = Pattern.compile("(오전|오후|아침|낮|저녁|밤)?\\s*(\\d{1,2})(?:시|:)(?:(\\d{1,2})분?)?\\s*(?:부터|~|～|-)\\s*(오전|오후|아침|낮|저녁|밤)?\\s*(\\d{1,2})(?:시|:)(?:(\\d{1,2})분?)?");
    private static final Pattern SINGLE_TIME = Pattern.compile("(오전|오후|아침|낮|저녁|밤)?\\s*(\\d{1,2})(?:시|:)(?:(\\d{1,2})분?)?");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일");
    private static final Pattern DATE_RANGE = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일\\s*(?:부터|~|～|-)\\s*(?:(\\d{1,2})월\\s*)?(\\d{1,2})일(?:까지)?");
    private static final Pattern WEEKDAY_RANGE = Pattern.compile("(다음\\s*주|이번\\s*주)?\\s*(월요일|화요일|수요일|목요일|금요일|토요일|일요일|월|화|수|목|금|토|일)\\s*(?:부터|~|～|-)\\s*(월요일|화요일|수요일|목요일|금요일|토요일|일요일|월|화|수|목|금|토|일)");
    private static final Pattern REMINDER = Pattern.compile("(?:알림\\s*)?(\\d+)\\s*(분|시간|일|주)\\s*전(?:에?\\s*(?:알려줘|알림))?");
    private static final Pattern DAYS_LATER = Pattern.compile("(\\d+)\\s*일\\s*(?:뒤|후)");
    private static final Pattern WEEKS_LATER = Pattern.compile("(\\d+)\\s*주\\s*(?:뒤|후)");
    private static final Pattern MONTHS_LATER = Pattern.compile("(\\d+)\\s*개월\\s*(?:뒤|후)");

    private KoreanNaturalLanguageParser() { }

    public static ParsedWorkItem parse(String rawText, ZoneId zoneId) {
        String text = rawText == null ? "" : rawText.trim();
        String lower = text.toLowerCase(Locale.KOREA);
        ParsedWorkItem result = new ParsedWorkItem();
        result.sourceText = text;
        result.title = cleanTitle(text);

        boolean hasDateWord = containsAny(lower, "오늘", "내일", "모레", "글피", "다음 주", "다음주", "이번 주", "이번주",
                "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일", "다음 달", "다음달",
                "이번 달", "이번달", "월말", "주말") || MONTH_DAY.matcher(text).find()
                || DAYS_LATER.matcher(text).find() || WEEKS_LATER.matcher(text).find() || MONTHS_LATER.matcher(text).find();
        boolean hasTimeWord = TIME_RANGE.matcher(text).find() || SINGLE_TIME.matcher(text).find()
                || containsAny(lower, "정오", "점심", "자정", "새벽", "아침", "오전", "오후", "저녁", "밤", "퇴근 후", "퇴근후");
        boolean taskWord = containsAny(lower, "해야", "할 일", "제출", "보내", "준비", "확인", "전화", "구매", "처리", "완료");
        boolean scheduleWord = containsAny(lower, "회의", "수업", "방과후", "약속", "출장", "행사", "상담", "일정", "예약", "방문", "감독", "연수");
        if (hasDateWord || hasTimeWord || scheduleWord) result.type = WorkItemEntity.TYPE_SCHEDULE;
        else if (taskWord) result.type = WorkItemEntity.TYPE_TASK;
        else result.type = WorkItemEntity.TYPE_MEMO;

        LocalDate today = LocalDate.now(zoneId);
        LocalDate[] rangeDates = resolveDateRange(text, today);
        if (rangeDates == null) rangeDates = resolveWeekdayRange(text, today);
        LocalDate date = rangeDates == null ? resolveDate(text, today) : rangeDates[0];
        LocalTime[] times = resolveTimes(text);

        if (date != null && WorkItemEntity.TYPE_SCHEDULE.equals(result.type)) {
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
        } else if (date != null && WorkItemEntity.TYPE_TASK.equals(result.type)) {
            result.startAt = atZone(LocalDateTime.of(date, times[0] == null ? LocalTime.of(9, 0) : times[0]), zoneId);
        }

        if (containsAny(lower, "알림 없음", "알림없이", "알림 없이")) result.reminderExplicitlyDisabled = true;
        else {
            Matcher reminder = REMINDER.matcher(text);
            if (reminder.find() && result.startAt != null) {
                long amount = Long.parseLong(reminder.group(1));
                long minutes;
                switch (reminder.group(2)) {
                    case "시간": minutes = amount * 60L; break;
                    case "일": minutes = amount * 1440L; break;
                    case "주": minutes = amount * 10080L; break;
                    default: minutes = amount;
                }
                result.reminderAt = result.startAt - minutes * 60_000L;
            }
        }

        Set<DayOfWeek> selectedDays = selectedWeekdays(text);
        boolean excludeWeekends = containsAny(lower, "주말 제외", "평일", "월~금", "월요일~금요일");
        boolean excludeHoliday = containsAny(lower, "공휴일 제외", "공휴일은 제외");
        if (rangeDates != null && WorkItemEntity.TYPE_SCHEDULE.equals(result.type)) {
            int mask = selectedDays.isEmpty() ? (excludeWeekends ? 31 : 127) : weekdayMask(selectedDays);
            result.repeatRule = "RANGE_DAYS|" + rangeDates[1].toEpochDay() + "|" + mask + "|" + (excludeHoliday ? "1" : "0");
        } else if (containsAny(lower, "평일마다", "평일")) result.repeatRule = "WEEKDAYS";
        else if (containsAny(lower, "매일", "매일마다")) result.repeatRule = "DAILY";
        else if (containsAny(lower, "매주", "매주마다")) result.repeatRule = "WEEKLY";
        else if (containsAny(lower, "매월", "매달")) result.repeatRule = "MONTHLY";

        if (containsAny(lower, "긴급", "중요", "꼭", "반드시")) result.priority = "HIGH";
        boolean inferredDaypart = containsAny(lower, "퇴근 후", "퇴근후", "아침에", "오전에", "점심에", "오후에", "저녁에", "밤에", "새벽에")
                && !SINGLE_TIME.matcher(text).find();
        result.confidence = rangeDates != null ? 0.97f
                : (WorkItemEntity.TYPE_MEMO.equals(result.type) ? 0.62f
                : (inferredDaypart ? 0.82f : (hasDateWord || hasTimeWord ? 0.92f : 0.78f)));
        return result;
    }

    private static LocalDate[] resolveDateRange(String text, LocalDate today) {
        Matcher m = DATE_RANGE.matcher(text);
        if (m.find()) {
            int sm = Integer.parseInt(m.group(1)); int sd = Integer.parseInt(m.group(2));
            int em = m.group(3) == null ? sm : Integer.parseInt(m.group(3)); int ed = Integer.parseInt(m.group(4));
            LocalDate start = safeDate(today.getYear(), sm, sd);
            if (start == null) return null;
            if (start.isBefore(today.minusDays(1))) start = start.plusYears(1);
            LocalDate end = safeDate(start.getYear(), em, ed);
            if (end == null) return null;
            if (end.isBefore(start)) end = end.plusYears(1);
            return new LocalDate[]{start, end};
        }
        if (containsAny(text, "다음 달", "다음달")) {
            YearMonth ym = YearMonth.from(today).plusMonths(1);
            if (containsAny(text, "첫째 주", "첫째주")) {
                LocalDate start = ym.atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
                return new LocalDate[]{start, start.plusDays(6)};
            }
        }
        return null;
    }

    private static LocalDate[] resolveWeekdayRange(String text, LocalDate today) {
        Matcher matcher = WEEKDAY_RANGE.matcher(text);
        if (!matcher.find()) return null;
        DayOfWeek first = weekdayValue(matcher.group(2)); DayOfWeek last = weekdayValue(matcher.group(3));
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
        if (text.contains("글피")) return today.plusDays(3);
        if (text.contains("모레")) return today.plusDays(2);
        if (text.contains("내일")) return today.plusDays(1);
        if (text.contains("오늘")) return today;

        Matcher days = DAYS_LATER.matcher(text);
        if (days.find()) return today.plusDays(Long.parseLong(days.group(1)));
        Matcher weeks = WEEKS_LATER.matcher(text);
        if (weeks.find()) return today.plusWeeks(Long.parseLong(weeks.group(1)));
        Matcher months = MONTHS_LATER.matcher(text);
        if (months.find()) return today.plusMonths(Long.parseLong(months.group(1)));

        if (containsAny(text, "이번 달 말", "이번달 말", "이번 달말", "이번달말", "월말")) {
            return YearMonth.from(today).atEndOfMonth();
        }
        if (containsAny(text, "다음 달 말", "다음달 말", "다음 달말", "다음달말")) {
            return YearMonth.from(today).plusMonths(1).atEndOfMonth();
        }
        if (containsAny(text, "이번 주말", "이번주말")) {
            return today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        }
        if (containsAny(text, "다음 주말", "다음주말")) {
            LocalDate nextMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1);
            return nextMonday.plusDays(5);
        }

        Matcher monthDay = MONTH_DAY.matcher(text);
        if (monthDay.find()) {
            LocalDate candidate = safeDate(today.getYear(), Integer.parseInt(monthDay.group(1)), Integer.parseInt(monthDay.group(2)));
            if (candidate == null) return null;
            return candidate.isBefore(today.minusDays(1)) ? candidate.plusYears(1) : candidate;
        }
        DayOfWeek target = weekday(text);
        if (target != null) {
            boolean nextWeek = text.contains("다음 주") || text.contains("다음주");
            boolean thisWeek = text.contains("이번 주") || text.contains("이번주");
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (nextWeek) return monday.plusWeeks(1).plusDays(target.getValue() - 1L);
            if (thisWeek) return monday.plusDays(target.getValue() - 1L);
            return today.with(TemporalAdjusters.nextOrSame(target));
        }
        return null;
    }

    private static LocalTime[] resolveTimes(String text) {
        String normalized = text.replace("정오", "오후 12시").replace("점심", "오후 12시")
                .replace("자정", "오전 12시").replace("퇴근 후", "오후 6시").replace("퇴근후", "오후 6시");
        Matcher range = TIME_RANGE.matcher(normalized);
        if (range.find()) {
            LocalTime start = time(range.group(1), range.group(2), range.group(3));
            String endAmPm = range.group(4) == null ? range.group(1) : range.group(4);
            return new LocalTime[]{start, time(endAmPm, range.group(5), range.group(6))};
        }
        Matcher single = SINGLE_TIME.matcher(normalized);
        if (single.find()) return new LocalTime[]{time(single.group(1), single.group(2), single.group(3)), null};
        if (containsAny(normalized, "새벽", "새벽에")) return new LocalTime[]{LocalTime.of(6, 0), null};
        if (containsAny(normalized, "아침", "아침에")) return new LocalTime[]{LocalTime.of(8, 0), null};
        if (containsAny(normalized, "오전", "오전에")) return new LocalTime[]{LocalTime.of(9, 0), null};
        if (containsAny(normalized, "점심에")) return new LocalTime[]{LocalTime.of(12, 0), null};
        if (containsAny(normalized, "오후", "오후에")) return new LocalTime[]{LocalTime.of(14, 0), null};
        if (containsAny(normalized, "저녁", "저녁에")) return new LocalTime[]{LocalTime.of(18, 0), null};
        if (containsAny(normalized, "밤", "밤에")) return new LocalTime[]{LocalTime.of(21, 0), null};
        return new LocalTime[]{null, null};
    }

    private static LocalTime time(String amPm, String hourText, String minuteText) {
        int hour = Integer.parseInt(hourText); int minute = minuteText == null ? 0 : Integer.parseInt(minuteText);
        if (("오후".equals(amPm) || "낮".equals(amPm) || "저녁".equals(amPm) || "밤".equals(amPm)) && hour < 12) hour += 12;
        if (("오전".equals(amPm) || "아침".equals(amPm)) && hour == 12) hour = 0;
        return LocalTime.of(Math.min(hour, 23), Math.min(minute, 59));
    }

    private static Set<DayOfWeek> selectedWeekdays(String text) {
        Set<DayOfWeek> days = new LinkedHashSet<>();
        String compact = text.replace("요일", "").replace(" ", "");
        if (compact.contains("월수금")) { days.add(DayOfWeek.MONDAY); days.add(DayOfWeek.WEDNESDAY); days.add(DayOfWeek.FRIDAY); }
        if (compact.contains("화목")) { days.add(DayOfWeek.TUESDAY); days.add(DayOfWeek.THURSDAY); }
        if (text.contains("월요일")) days.add(DayOfWeek.MONDAY);
        if (text.contains("화요일")) days.add(DayOfWeek.TUESDAY);
        if (text.contains("수요일")) days.add(DayOfWeek.WEDNESDAY);
        if (text.contains("목요일")) days.add(DayOfWeek.THURSDAY);
        if (text.contains("금요일")) days.add(DayOfWeek.FRIDAY);
        if (text.contains("토요일")) days.add(DayOfWeek.SATURDAY);
        if (text.contains("일요일")) days.add(DayOfWeek.SUNDAY);
        return days;
    }

    private static int weekdayMask(Set<DayOfWeek> days) {
        int mask = 0; for (DayOfWeek day : days) mask |= 1 << (day.getValue() - 1); return mask;
    }

    private static DayOfWeek weekday(String text) {
        for (DayOfWeek d : DayOfWeek.values()) if (text.contains(koreanDay(d) + "요일")) return d;
        return null;
    }

    private static DayOfWeek weekdayValue(String value) {
        if (value == null) return null;
        if (value.startsWith("월")) return DayOfWeek.MONDAY; if (value.startsWith("화")) return DayOfWeek.TUESDAY;
        if (value.startsWith("수")) return DayOfWeek.WEDNESDAY; if (value.startsWith("목")) return DayOfWeek.THURSDAY;
        if (value.startsWith("금")) return DayOfWeek.FRIDAY; if (value.startsWith("토")) return DayOfWeek.SATURDAY;
        if (value.startsWith("일")) return DayOfWeek.SUNDAY; return null;
    }

    private static LocalDate safeDate(int year, int month, int day) {
        try { return LocalDate.of(year, month, day); }
        catch (RuntimeException ignored) { return null; }
    }

    private static String koreanDay(DayOfWeek day) { return new String[]{"월","화","수","목","금","토","일"}[day.getValue()-1]; }
    private static long atZone(LocalDateTime value, ZoneId zoneId) { return value.atZone(zoneId).toInstant().toEpochMilli(); }

    private static String cleanTitle(String text) {
        for (String line : text.split("\\R")) {
            String candidate = line.trim();
            if (!candidate.isEmpty() && !looksLikeOnlyScheduleDetails(candidate)) return candidate.length() > 60 ? candidate.substring(0, 60) : candidate;
        }
        String cleaned = text.replaceAll("\\s+", " ").trim(); return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
    }

    private static boolean looksLikeOnlyScheduleDetails(String value) {
        return WEEKDAY_RANGE.matcher(value).find() || DATE_RANGE.matcher(value).find() || TIME_RANGE.matcher(value).find()
                || REMINDER.matcher(value).find() || value.startsWith("알림") || value.contains("공휴일 제외") || value.contains("주말 제외");
    }

    private static boolean containsAny(String text, String... values) { for (String value : values) if (text.contains(value)) return true; return false; }
}
