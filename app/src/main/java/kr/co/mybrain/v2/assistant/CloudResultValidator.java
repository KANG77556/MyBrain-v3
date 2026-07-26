package kr.co.mybrain.v2.assistant;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Pattern;

import kr.co.mybrain.v2.data.WorkItemEntity;

/**
 * 클라우드 AI 결과가 원문에 없는 날짜·반복·알림을 만들어 내지 않도록
 * 기기 분석 결과와 원문 단서를 이용해 보수적으로 보정합니다.
 */
public final class CloudResultValidator {
    private static final Pattern DATE_NUMBER = Pattern.compile("\\d{1,2}월\\s*\\d{1,2}일|\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}");
    private static final Pattern TIME_NUMBER = Pattern.compile("(오전|오후|아침|낮|저녁|밤)?\\s*\\d{1,2}(?:시|:)(?:\\d{1,2}분?)?");
    private static final Pattern REMINDER_NUMBER = Pattern.compile("\\d+\\s*(분|시간|일|주)\\s*전");

    private CloudResultValidator() {}

    public static ValidationResult validate(
            String originalText,
            ParsedWorkItem cloud,
            ParsedWorkItem baseline,
            ZoneId zoneId) {
        String text = originalText == null ? "" : originalText.trim();
        String lower = text.toLowerCase(Locale.KOREA);
        ParsedWorkItem result = copyOf(cloud == null ? baseline : cloud);
        ParsedWorkItem local = baseline == null ? new ParsedWorkItem() : baseline;
        int corrections = 0;
        StringBuilder summary = new StringBuilder();

        result.sourceText = text;
        if (isBlank(result.title) || isGenericTitle(result.title)) {
            result.title = isBlank(local.title) ? compactTitle(text) : local.title;
            corrections = add(summary, corrections, "제목 보완");
        }
        if (result.title.length() > 120) {
            result.title = result.title.substring(0, 120);
            corrections = add(summary, corrections, "제목 길이 제한");
        }

        boolean temporalSignal = hasTemporalSignal(text);
        boolean repeatSignal = hasRepeatSignal(lower);
        boolean reminderSignal = hasReminderSignal(text, lower);
        boolean pastSignal = containsAny(lower, "어제", "지난", "전날", "기록", "완료", "했음", "다녀옴");

        if (!temporalSignal && local.startAt == null && result.startAt != null) {
            result.startAt = null;
            result.endAt = null;
            result.reminderAt = null;
            result.allDay = false;
            corrections = add(summary, corrections, "근거 없는 날짜 제거");
        }

        if (local.startAt != null && result.startAt != null && local.confidence >= 0.85f) {
            LocalDate localDate = Instant.ofEpochMilli(local.startAt).atZone(zoneId).toLocalDate();
            LocalDate cloudDate = Instant.ofEpochMilli(result.startAt).atZone(zoneId).toLocalDate();
            long dayDifference = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(localDate, cloudDate));
            if (dayDifference >= 2 && hasExplicitDateSignal(text)) {
                result.startAt = local.startAt;
                result.endAt = local.endAt;
                result.allDay = local.allDay;
                corrections = add(summary, corrections, "날짜 재확인");
            }
        }

        long now = System.currentTimeMillis();
        if (result.startAt != null && result.startAt < now - 3_600_000L && !pastSignal) {
            if (local.startAt != null && local.startAt >= now - 3_600_000L) {
                result.startAt = local.startAt;
                result.endAt = local.endAt;
                result.allDay = local.allDay;
            } else {
                result.startAt = null;
                result.endAt = null;
                result.allDay = false;
            }
            result.reminderAt = null;
            corrections = add(summary, corrections, "과거 시각 방지");
        }

        if (result.startAt != null && result.endAt != null) {
            long duration = result.endAt - result.startAt;
            boolean rangeSignal = containsAny(lower, "부터", "까지", "~", "～", "기간", "며칠", "주간");
            if (duration <= 0L) {
                result.endAt = local.endAt != null && local.endAt > result.startAt
                        ? local.endAt : result.startAt + 3_600_000L;
                corrections = add(summary, corrections, "종료 시각 보정");
            } else if (duration > 7L * 24L * 3_600_000L && !rangeSignal) {
                result.endAt = local.endAt != null && local.endAt > result.startAt
                        ? local.endAt : result.startAt + 3_600_000L;
                corrections = add(summary, corrections, "과도한 기간 축소");
            }
        }

        if (local.repeatRule != null && (local.repeatRule.startsWith("RANGE_DAILY|")
                || local.repeatRule.startsWith("RANGE_DAYS|"))) {
            if (!local.repeatRule.equals(result.repeatRule)) {
                result.repeatRule = local.repeatRule;
                corrections = add(summary, corrections, "기간 반복 복원");
            }
        } else if (!repeatSignal && result.repeatRule != null && !"NONE".equals(result.repeatRule)) {
            result.repeatRule = "NONE";
            corrections = add(summary, corrections, "근거 없는 반복 제거");
        }

        if (containsAny(lower, "알림 없음", "알림없이", "알림 없이")) {
            if (!result.reminderExplicitlyDisabled || result.reminderAt != null) {
                corrections = add(summary, corrections, "알림 없음 반영");
            }
            result.reminderExplicitlyDisabled = true;
            result.reminderAt = null;
        } else if (!reminderSignal && local.reminderAt == null && result.reminderAt != null) {
            result.reminderAt = null;
            corrections = add(summary, corrections, "근거 없는 알림 제거");
        }

        if (!sameType(result.type, local.type) && local.confidence >= 0.85f && result.confidence < 0.90f) {
            result.type = local.type;
            corrections = add(summary, corrections, "분류 재확인");
        }
        if (WorkItemEntity.TYPE_SCHEDULE.equals(result.type)
                && result.startAt == null && !containsScheduleWord(lower)
                && !WorkItemEntity.TYPE_SCHEDULE.equals(local.type)) {
            result.type = local.type;
            corrections = add(summary, corrections, "일정 분류 보정");
        }

        result.priority = normalizePriority(result.priority, local.priority);
        result.repeatRule = normalizeRepeat(result.repeatRule, local.repeatRule);
        result.confidence = Math.max(0.35f,
                Math.min(0.99f, result.confidence - Math.min(0.20f, corrections * 0.03f)));
        return new ValidationResult(result, corrections,
                corrections == 0 ? "검증 통과" : summary.toString());
    }

    private static boolean hasTemporalSignal(String text) {
        String lower = text.toLowerCase(Locale.KOREA);
        return hasExplicitDateSignal(text)
                || TIME_NUMBER.matcher(text).find()
                || containsAny(lower, "오늘", "내일", "모레", "이번 주", "이번주", "다음 주", "다음주",
                "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일",
                "이번 달", "이번달", "다음 달", "다음달", "정오", "자정", "점심");
    }

    private static boolean hasExplicitDateSignal(String text) {
        return DATE_NUMBER.matcher(text).find();
    }

    private static boolean hasRepeatSignal(String lower) {
        return containsAny(lower, "매일", "평일", "매주", "매월", "매달", "반복", "마다", "월수금", "화목");
    }

    private static boolean hasReminderSignal(String text, String lower) {
        return REMINDER_NUMBER.matcher(text).find()
                || containsAny(lower, "알려줘", "알림", "미리", "리마인드");
    }

    private static boolean containsScheduleWord(String lower) {
        return containsAny(lower, "회의", "수업", "약속", "예약", "행사", "출장", "상담", "방문", "연수", "일정");
    }

    private static String normalizePriority(String value, String fallback) {
        if ("LOW".equals(value) || "NORMAL".equals(value) || "HIGH".equals(value)) return value;
        return "LOW".equals(fallback) || "HIGH".equals(fallback) ? fallback : "NORMAL";
    }

    private static String normalizeRepeat(String value, String fallback) {
        if (value != null && (value.startsWith("RANGE_DAILY|") || value.startsWith("RANGE_DAYS|"))) return value;
        if ("NONE".equals(value) || "DAILY".equals(value) || "WEEKDAYS".equals(value)
                || "WEEKLY".equals(value) || "MONTHLY".equals(value)) return value;
        return fallback == null ? "NONE" : fallback;
    }

    private static int add(StringBuilder summary, int count, String message) {
        if (summary.length() > 0) summary.append(" · ");
        summary.append(message);
        return count + 1;
    }

    private static boolean sameType(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isGenericTitle(String value) {
        String text = value == null ? "" : value.trim();
        return "일정".equals(text) || "할 일".equals(text) || "메모".equals(text)
                || "새 일정".equals(text) || "새 할 일".equals(text);
    }

    private static String compactTitle(String text) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return value.length() <= 60 ? value : value.substring(0, 60) + "…";
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static ParsedWorkItem copyOf(ParsedWorkItem source) {
        ParsedWorkItem copy = new ParsedWorkItem();
        if (source == null) return copy;
        copy.type = source.type;
        copy.title = source.title;
        copy.sourceText = source.sourceText;
        copy.startAt = source.startAt;
        copy.endAt = source.endAt;
        copy.reminderAt = source.reminderAt;
        copy.reminderExplicitlyDisabled = source.reminderExplicitlyDisabled;
        copy.allDay = source.allDay;
        copy.repeatRule = source.repeatRule;
        copy.priority = source.priority;
        copy.confidence = source.confidence;
        copy.aiProvider = source.aiProvider;
        return copy;
    }

    public static final class ValidationResult {
        public final ParsedWorkItem item;
        public final int corrections;
        public final String summary;

        ValidationResult(ParsedWorkItem item, int corrections, String summary) {
            this.item = item;
            this.corrections = corrections;
            this.summary = summary == null ? "" : summary;
        }
    }
}
