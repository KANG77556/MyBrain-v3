package kr.co.mybrain.v2.assistant;

/**
 * AI가 반환한 JSON이 코드펜스·설명 문구를 포함하거나 마지막 괄호가 잘린 경우
 * 알려진 JSON 객체 부분만 안전하게 추출하고 닫는 문자만 보완합니다.
 * Android API에 의존하지 않아 로컬 단위 테스트가 가능합니다.
 */
public final class AiJsonRecovery {
    private AiJsonRecovery() {}

    public static Recovery recover(String raw) {
        String text = stripCodeFence(raw);
        int objectStart = text.indexOf('{');
        if (objectStart < 0) {
            throw new IllegalArgumentException("JSON 객체 시작 문자를 찾지 못했습니다.");
        }
        boolean removedPrefix = objectStart > 0;
        text = text.substring(objectStart).trim();

        Scan scan = scan(text);
        if (scan.completeEnd >= 0) {
            String isolated = text.substring(0, scan.completeEnd + 1).trim();
            boolean changed = removedPrefix || isolated.length() != text.length();
            return new Recovery(isolated, changed, changed ? "설명 문구 제거" : "");
        }

        StringBuilder repaired = new StringBuilder(text);
        if (scan.insideString) {
            if (scan.trailingEscape) repaired.append('\\');
            repaired.append('"');
            if (scan.stringWasKey) repaired.append(":null");
        }

        trimTrailingWhitespace(repaired);
        if (endsWith(repaired, ':')) repaired.append("null");
        trimTrailingComma(repaired);

        for (int i = 0; i < scan.openArrays; i++) repaired.append(']');
        for (int i = 0; i < scan.openObjects; i++) repaired.append('}');

        return new Recovery(repaired.toString(), true, "잘린 JSON 자동 복구");
    }

    private static Scan scan(String text) {
        int objects = 0;
        int arrays = 0;
        int completeEnd = -1;
        boolean inString = false;
        boolean escaped = false;
        boolean stringWasKey = false;
        char previousSignificant = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                    previousSignificant = '"';
                }
                continue;
            }

            if (Character.isWhitespace(c)) continue;
            if (c == '"') {
                inString = true;
                stringWasKey = previousSignificant == '{' || previousSignificant == ',';
                continue;
            }
            if (c == '{') objects++;
            else if (c == '}') {
                if (objects > 0) objects--;
                if (objects == 0 && arrays == 0) {
                    completeEnd = i;
                    break;
                }
            } else if (c == '[') arrays++;
            else if (c == ']' && arrays > 0) arrays--;

            previousSignificant = c;
        }
        return new Scan(objects, arrays, completeEnd, inString, escaped, stringWasKey);
    }

    private static String stripCodeFence(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (!text.startsWith("```")) return text;
        int firstBreak = text.indexOf('\n');
        if (firstBreak < 0) return text.replace("```", "").trim();
        int lastFence = text.lastIndexOf("```");
        if (lastFence > firstBreak) return text.substring(firstBreak + 1, lastFence).trim();
        return text.substring(firstBreak + 1).trim();
    }

    private static void trimTrailingWhitespace(StringBuilder value) {
        while (value.length() > 0 && Character.isWhitespace(value.charAt(value.length() - 1))) {
            value.deleteCharAt(value.length() - 1);
        }
    }

    private static void trimTrailingComma(StringBuilder value) {
        trimTrailingWhitespace(value);
        if (endsWith(value, ',')) value.deleteCharAt(value.length() - 1);
    }

    private static boolean endsWith(StringBuilder value, char expected) {
        return value.length() > 0 && value.charAt(value.length() - 1) == expected;
    }

    public static final class Recovery {
        public final String json;
        public final boolean recovered;
        public final String summary;

        Recovery(String json, boolean recovered, String summary) {
            this.json = json == null ? "" : json;
            this.recovered = recovered;
            this.summary = summary == null ? "" : summary;
        }
    }

    private static final class Scan {
        final int openObjects;
        final int openArrays;
        final int completeEnd;
        final boolean insideString;
        final boolean trailingEscape;
        final boolean stringWasKey;

        Scan(int openObjects, int openArrays, int completeEnd,
             boolean insideString, boolean trailingEscape, boolean stringWasKey) {
            this.openObjects = Math.max(0, openObjects);
            this.openArrays = Math.max(0, openArrays);
            this.completeEnd = completeEnd;
            this.insideString = insideString;
            this.trailingEscape = trailingEscape;
            this.stringWasKey = stringWasKey;
        }
    }
}
