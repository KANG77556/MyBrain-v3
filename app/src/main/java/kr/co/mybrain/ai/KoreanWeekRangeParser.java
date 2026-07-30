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

/** 이번주·다음주 요일 범위와 시간 범위를 날짜별 일정으로 변환합니다. */
public final class KoreanWeekRangeParser {
    private static final Pattern DAY_RANGE = Pattern.compile(
            "(?:(이번|다음)\\s*주\\s*)?(월|화|수|목|금|토|일)(?:요일)?\\s*"
                    + "(?:부터|에서|~|〜|–|—|-)\\s*"
                    + "(월|화|수|목|금|토|일)(?:요일)?(?:\\s*까지)?");

    private KoreanWeekRangeParser() { }

    public static List<AiAnalysisResult> parse(String rawText, Date referenceTime) {
        String raw = rawText == null ? "" : rawText.trim();
        String normalized = raw.replaceAll("\\s+", " ");
        Matcher matcher = DAY_RANGE.matcher(normalized);
        KoreanTimeRangeParser.Range time = KoreanTimeRangeParser.parse(normalized);
        if (!matcher.find() || !time.isValid()) return Collections.emptyList();

        int startOffset = offset(matcher.group(2));
        int endOffset = offset(matcher.group(3));
        if (startOffset < 0 || endOffset < 0) return Collections.emptyList();
        if (endOffset < startOffset) endOffset += 7;

        Calendar base = Calendar.getInstance();
        base.setTime(referenceTime == null ? new Date() : referenceTime);
        base.set(Calendar.HOUR_OF_DAY, 0);
        base.set(Calendar.MINUTE, 0);
        base.set(Calendar.SECOND, 0);
        base.set(Calendar.MILLISECOND, 0);

        int currentOffset = (base.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        Calendar monday = (Calendar) base.clone();
        monday.add(Calendar.DAY_OF_MONTH, -currentOffset);
        String week = matcher.group(1);
        if ("다음".equals(week) || (week == null && endOffset < currentOffset)) {
            monday.add(Calendar.DAY_OF_MONTH, 7);
        }

        int count = Math.min(31, endOffset - startOffset + 1);
        if (count <= 0) return Collections.emptyList();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
        Calendar endDate = (Calendar) monday.clone();
        endDate.add(Calendar.DAY_OF_MONTH, endOffset);
        String rangeEndDate = format.format(endDate.getTime());
        String title = title(raw);

        List<AiAnalysisResult> output = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Calendar date = (Calendar) monday.clone();
            date.add(Calendar.DAY_OF_MONTH, startOffset + index);
            AiAnalysisResult item = new AiAnalysisResult();
            item.type = "일정";
            item.title = title;
            item.content = raw;
            item.date = format.format(date.getTime());
            item.time = time.startTime;
            item.endTime = time.endTime;
            item.repeatType = "NONE";
            item.rangeEndDate = rangeEndDate;
            item.rangeCount = count;
            output.add(item);
        }
        return output;
    }

    private static String title(String raw) {
        for (String line : raw.split("\\r?\\n")) {
            String value = line.trim();
            if (value.isEmpty()) continue;
            if (DAY_RANGE.matcher(value).find()) continue;
            if (KoreanTimeRangeParser.parse(value).isValid()) continue;
            String compact = value.replaceAll("\\s+", "");
            if (compact.equals("매일") || compact.equals("동안") || compact.startsWith("알림")) continue;
            return value.length() > 36 ? value.substring(0, 36) + "…" : value;
        }
        return "일정";
    }

    private static int offset(String day) {
        if ("월".equals(day)) return 0;
        if ("화".equals(day)) return 1;
        if ("수".equals(day)) return 2;
        if ("목".equals(day)) return 3;
        if ("금".equals(day)) return 4;
        if ("토".equals(day)) return 5;
        if ("일".equals(day)) return 6;
        return -1;
    }
}
