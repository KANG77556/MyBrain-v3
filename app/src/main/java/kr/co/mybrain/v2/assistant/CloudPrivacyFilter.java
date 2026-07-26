package kr.co.mybrain.v2.assistant;

import java.util.regex.Pattern;

/** 외부 AI 전송 전에 명확한 개인정보 형식을 최소한으로 마스킹합니다. */
public final class CloudPrivacyFilter {
    private static final Pattern RESIDENT_NUMBER = Pattern.compile("\\b(\\d{6})[- ]?[1-8]\\d{6}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(01[016789])[- ]?(\\d{3,4})[- ]?(\\d{4})(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    private CloudPrivacyFilter() {}

    public static FilteredText filter(String source) {
        String original = source == null ? "" : source;
        String filtered = RESIDENT_NUMBER.matcher(original).replaceAll("$1-*******");
        filtered = PHONE.matcher(filtered).replaceAll("$1-****-$3");
        filtered = EMAIL.matcher(filtered).replaceAll("***@***");
        return new FilteredText(filtered, !filtered.equals(original));
    }

    public static final class FilteredText {
        public final String text;
        public final boolean masked;

        FilteredText(String text, boolean masked) {
            this.text = text;
            this.masked = masked;
        }
    }
}
