package kr.co.mybrain.ai;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 음성 인식 또는 OCR 원문을 한 건의 편집 초안으로 바꾸는 기기 내부 분석기입니다.
 * 여러 날짜 범위와 복합 업무는 AI 메시지 분석 화면에서 다시 확인하도록 하고,
 * 이 클래스는 사용자가 곧바로 직접 편집하려는 경우의 기본값만 안전하게 제안합니다.
 */
public final class QuickInputParser {
    private static final Pattern EXPLICIT_DATE = Pattern.compile(
            "(?:(\\d{4})[년./-]\\s*)?(\\d{1,2})[월./-]\\s*(\\d{1,2})일?");
    private static final Pattern CLOCK_TIME = Pattern.compile(
            "(?:(오전|오후)\\s*)?(\\d{1,2})(?:\\s*시|:)(?:\\s*(\\d{1,2})\\s*분?)?");
    private static final Pattern KOREAN_HOUR = Pattern.compile(
            "(?:(오전|오후)\\s*)?(\\d{1,2})\\s*시(?:\\s*(\\d{1,2})\\s*분)?");

    private QuickInputParser() { }

    public static AiAnalysisResult parseSingle(String rawText) {
        String raw = rawText == null ? "" : rawText.trim();
        AiAnalysisResult result = new AiAnalysisResult();
        result.content = raw;
        if (raw.isEmpty()) {
            result.title = "새 메모";
            return result;
        }

        List<AiAnalysisResult> range = KoreanScheduleRangeParser.parse(raw, new Date());
        if (!range.isEmpty()) {
            AiAnalysisResult first = range.get(0);
            first.content = raw;
            return first;
        }

        result.date = parseDate(raw);
        result.time = parseTime(raw);
        result.repeatType = parseRepeat(raw);
        result.type = classify(raw, result.date, result.time);
        result.title = makeTitle(raw);
        return result;
    }

    private static String classify(String text, String date, String time) {
        String normalized = text.replaceAll("\\s+", " ");
        if (containsAny(normalized, "제출", "확인", "처리", "작성", "검토", "채점",
                "준비", "해야", "마감", "완료", "전화하기", "보내기")) {
            return "할 일";
        }
        if (!date.isEmpty() || !time.isEmpty()
                || containsAny(normalized, "회의", "수업", "상담", "약속", "연수",
                "출장", "행사", "교육", "면담", "방문")) {
            return "일정";
        }
        return "메모";
    }

    private static String parseDate(String text) {
        Calendar calendar = Calendar.getInstance();
        if (text.contains("모레")) {
            calendar.add(Calendar.DAY_OF_MONTH, 2);
            return formatDate(calendar);
        }
        if (text.contains("내일")) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            return formatDate(calendar);
        }
        if (text.contains("오늘")) return formatDate(calendar);

        Matcher matcher = EXPLICIT_DATE.matcher(text);
        if (matcher.find()) {
            try {
                int year = matcher.group(1) == null ? calendar.get(Calendar.YEAR)
                        : Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int day = Integer.parseInt(matcher.group(3));
                Calendar explicit = Calendar.getInstance();
                explicit.setLenient(false);
                explicit.set(year, month - 1, day, 0, 0, 0);
                explicit.set(Calendar.MILLISECOND, 0);
                explicit.getTime();
                return formatDate(explicit);
            } catch (Exception ignored) { }
        }

        String[] days = {"일", "월", "화", "수", "목", "금", "토"};
        for (int i = 0; i < days.length; i++) {
            String dayText = days[i] + "요일";
            if (!text.contains(dayText)) continue;
            int target = i + 1;
            int current = calendar.get(Calendar.DAY_OF_WEEK);
            int delta = (target - current + 7) % 7;
            if (text.contains("다음 주") || text.contains("다음주")) {
                if (delta == 0) delta = 7;
                else delta += 7;
            }
            calendar.add(Calendar.DAY_OF_MONTH, delta);
            return formatDate(calendar);
        }
        return "";
    }

    private static String parseTime(String text) {
        Matcher matcher = KOREAN_HOUR.matcher(text);
        if (!matcher.find()) {
            matcher = CLOCK_TIME.matcher(text);
            if (!matcher.find()) return "";
        }
        try {
            String period = matcher.group(1);
            int hour = Integer.parseInt(matcher.group(2));
            int minute = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
            if ("오전".equals(period) && hour == 12) hour = 0;
            if ("오후".equals(period) && hour < 12) hour += 12;
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return "";
            return String.format(Locale.KOREA, "%02d:%02d", hour, minute);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String parseRepeat(String text) {
        if (text.contains("평일마다") || text.contains("매주 평일")) return "WEEKDAYS";
        if (text.contains("매일")) return "DAILY";
        if (text.contains("매주") || text.contains("격주")) return "WEEKLY";
        if (text.contains("매월") || text.contains("매달")) return "MONTHLY";
        return "NONE";
    }

    private static String makeTitle(String raw) {
        String firstLine = raw.split("\\r?\\n", 2)[0].trim();
        String cleaned = firstLine
                .replaceAll("(?:(?:오늘|내일|모레|다음\\s*주)\\s*)", " ")
                .replaceAll("(?:(?:오전|오후)\\s*)?\\d{1,2}\\s*시(?:\\s*\\d{1,2}\\s*분)?", " ")
                .replaceAll("(?:(?:\\d{4}년\\s*)?\\d{1,2}월\\s*\\d{1,2}일)", " ")
                .replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) cleaned = firstLine;
        if (cleaned.isEmpty()) cleaned = "새 기록";
        return cleaned.length() > 36 ? cleaned.substring(0, 36) + "…" : cleaned;
    }

    private static boolean containsAny(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }

    private static String formatDate(Calendar calendar) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(calendar.getTime());
    }
}
