package kr.co.mybrain.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR·음성 원문을 저장 전에 안전하게 정리하는 기기 내부 처리기입니다.
 *
 * 1. 주민등록번호·전화번호·이메일을 마스킹합니다.
 * 2. 제출기한·신청기간·행사일시가 포함된 문장을 우선 추출합니다.
 * 3. 긴 문서는 핵심 정리를 위에 배치하고 전체 마스킹 원문을 아래에 보관합니다.
 */
public final class OcrPrivacyProcessor {

    private static final Pattern RESIDENT_NUMBER = Pattern.compile(
            "(?<!\\d)(\\d{6})[-\\s]?(\\d)\\d{6}(?!\\d)");
    private static final Pattern MOBILE_PHONE = Pattern.compile(
            "(?<!\\d)(01[016789])[-\\s]?(\\d{3,4})[-\\s]?(\\d{4})(?!\\d)");
    private static final Pattern LOCAL_PHONE = Pattern.compile(
            "(?<!\\d)(0\\d{1,2})[-\\s]?(\\d{3,4})[-\\s]?(\\d{4})(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![A-Z0-9._%+-])([A-Z0-9._%+-])([A-Z0-9._%+-]*)(@[A-Z0-9.-]+\\.[A-Z]{2,})(?![A-Z0-9._%+-])");
    private static final Pattern DATE_TOKEN = Pattern.compile(
            "(?:\\d{4}[년./-]\\s*)?\\d{1,2}[월./-]\\s*\\d{1,2}일?|"
                    + "(?:오늘|내일|모레|이번\\s*주|다음\\s*주|월요일|화요일|수요일|목요일|금요일|토요일|일요일)");
    private static final Pattern TIME_TOKEN = Pattern.compile(
            "(?:(?:오전|오후)\\s*)?\\d{1,2}(?:시|:)\\s*(?:\\d{1,2}분?)?");

    private static final String[] DEADLINE_WORDS = {
            "제출", "마감", "기한", "신청", "접수", "회신", "등록", "까지", "기간"
    };
    private static final String[] EVENT_WORDS = {
            "일시", "행사", "회의", "교육", "연수", "수업", "상담", "출장", "방문", "장소"
    };

    private OcrPrivacyProcessor() { }

    /** 처리 결과를 화면과 저장 단계에서 함께 사용합니다. */
    public static final class ProcessedText {
        public final String storedText;
        public final String summary;
        public final String fullMaskedText;
        public final String deadlineSummary;
        public final int maskedCount;
        public final boolean summarized;

        private ProcessedText(String storedText, String summary, String fullMaskedText,
                              String deadlineSummary, int maskedCount, boolean summarized) {
            this.storedText = storedText;
            this.summary = summary;
            this.fullMaskedText = fullMaskedText;
            this.deadlineSummary = deadlineSummary;
            this.maskedCount = maskedCount;
            this.summarized = summarized;
        }
    }

    /**
     * 입력 원문을 마스킹하고, OCR 문서는 길이에 따라 핵심 정리를 생성합니다.
     * 음성 입력은 짧은 경우가 많아 원문 구조를 가능한 그대로 유지합니다.
     */
    public static ProcessedText process(String raw, String source) {
        String normalized = normalize(raw);
        MaskResult masked = maskPersonalInformation(normalized);
        List<String> lines = lines(masked.text);
        List<ScoredLine> important = scoreLines(lines);
        String deadline = buildDeadlineSummary(important);

        boolean ocrSource = source != null && (source.contains("촬영") || source.contains("OCR")
                || source.contains("사진") || source.contains("문서"));
        boolean longDocument = ocrSource && (masked.text.length() > 850 || lines.size() > 16);

        String summary = longDocument ? buildSummary(lines, important) : masked.text;
        if (summary.trim().isEmpty()) summary = masked.text;

        String stored = longDocument
                ? summary + "\n\n[전체 원문]\n" + masked.text
                : masked.text;

        return new ProcessedText(stored.trim(), summary.trim(), masked.text.trim(),
                deadline.trim(), masked.count, longDocument);
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        return raw.replace("\r", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static MaskResult maskPersonalInformation(String value) {
        MaskResult result = new MaskResult(value, 0);
        result = replace(result, RESIDENT_NUMBER, matcher ->
                matcher.group(1) + "-" + matcher.group(2) + "******");
        result = replace(result, MOBILE_PHONE, matcher ->
                matcher.group(1) + "-****-" + matcher.group(3));
        result = replace(result, LOCAL_PHONE, matcher ->
                matcher.group(1) + "-****-" + matcher.group(3));
        result = replace(result, EMAIL, matcher ->
                matcher.group(1) + "***" + matcher.group(3));
        return result;
    }

    private interface ReplacementFactory {
        String replacement(Matcher matcher);
    }

    private static MaskResult replace(MaskResult source, Pattern pattern,
                                      ReplacementFactory factory) {
        Matcher matcher = pattern.matcher(source.text);
        StringBuffer output = new StringBuffer();
        int count = source.count;
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement(factory.replacement(matcher)));
            count++;
        }
        matcher.appendTail(output);
        return new MaskResult(output.toString(), count);
    }

    private static List<String> lines(String value) {
        List<String> result = new ArrayList<>();
        for (String line : value.split("\\n")) {
            String cleaned = line.replaceAll("\\s+", " ").trim();
            if (!cleaned.isEmpty()) result.add(cleaned);
        }
        return result;
    }

    private static List<ScoredLine> scoreLines(List<String> lines) {
        List<ScoredLine> scored = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int score = 0;
            for (String word : DEADLINE_WORDS) if (line.contains(word)) score += 5;
            for (String word : EVENT_WORDS) if (line.contains(word)) score += 2;
            if (DATE_TOKEN.matcher(line).find()) score += 4;
            if (TIME_TOKEN.matcher(line).find()) score += 3;
            if (line.length() >= 8 && line.length() <= 100) score += 1;
            if (score > 0) scored.add(new ScoredLine(index, line, score));
        }
        Collections.sort(scored, (left, right) -> {
            int byScore = Integer.compare(right.score, left.score);
            return byScore != 0 ? byScore : Integer.compare(left.index, right.index);
        });
        return scored;
    }

    private static String buildDeadlineSummary(List<ScoredLine> important) {
        StringBuilder result = new StringBuilder();
        int added = 0;
        for (ScoredLine line : important) {
            boolean deadline = containsAny(line.text, DEADLINE_WORDS);
            if (!deadline && line.score < 7) continue;
            if (result.length() > 0) result.append("\n");
            result.append("• ").append(line.text);
            if (++added >= 3) break;
        }
        return result.toString();
    }

    private static String buildSummary(List<String> lines, List<ScoredLine> important) {
        if (lines.isEmpty()) return "";

        Set<Integer> selected = new HashSet<>();
        selected.add(0);
        for (ScoredLine line : important) {
            selected.add(line.index);
            if (selected.size() >= 5) break;
        }

        for (int index = 1; index < lines.size() && selected.size() < 6; index++) {
            String line = lines.get(index);
            if (line.length() >= 8 && line.length() <= 120) selected.add(index);
        }

        List<Integer> order = new ArrayList<>(selected);
        Collections.sort(order);
        StringBuilder summary = new StringBuilder();
        for (int index : order) {
            String line = lines.get(index);
            if (summary.length() > 0) summary.append("\n");
            summary.append(line);
            if (summary.length() > 620) break;
        }
        if (summary.length() > 700) return summary.substring(0, 700).trim() + "…";
        return summary.toString().trim();
    }

    private static boolean containsAny(String value, String[] words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }

    private static final class MaskResult {
        final String text;
        final int count;

        MaskResult(String text, int count) {
            this.text = text;
            this.count = count;
        }
    }

    private static final class ScoredLine {
        final int index;
        final String text;
        final int score;

        ScoredLine(int index, String text, int score) {
            this.index = index;
            this.text = text;
            this.score = score;
        }
    }
}
