package kr.co.mybrain.v2.assistant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 같은 AI 분석을 짧은 시간 안에 반복 호출하지 않도록 최근 정상 결과를 메모리에만 보관합니다.
 * 원문은 키로 저장하지 않고 SHA-256 지문으로 변환하며 앱 프로세스가 끝나면 모두 사라집니다.
 */
public final class AiAnalysisCache {
    static final long TTL_MS = 3L * 60L * 1000L;
    private static final int MAX_ENTRIES = 8;

    private static final LinkedHashMap<String, AiAnalysisCache.Entry> CACHE =
            new LinkedHashMap<String, AiAnalysisCache.Entry>(MAX_ENTRIES, 0.75f, true) {
                @Override protected boolean removeEldestEntry(
                        Map.Entry<String, AiAnalysisCache.Entry> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };

    private AiAnalysisCache() {}

    public static String createKey(
            String provider,
            String model,
            String originalText,
            ZoneId zoneId,
            ParsedWorkItem localBaseline) {
        String normalizedText = originalText == null ? "" : originalText.trim().replaceAll("\\s+", " ");
        String payload = safe(provider).toLowerCase(Locale.ROOT) + "\n"
                + safe(model).toLowerCase(Locale.ROOT) + "\n"
                + normalizedText + "\n"
                + (zoneId == null ? "" : zoneId.getId()) + "\n"
                + baselineSignature(localBaseline);
        return sha256(payload);
    }

    public static synchronized Entry get(String key) {
        return getAt(key, System.currentTimeMillis());
    }

    public static synchronized void put(
            String key,
            ParsedWorkItem item,
            String validationSummary,
            String providerLabel) {
        putAt(key, item, validationSummary, providerLabel, System.currentTimeMillis());
    }

    public static synchronized void clear() {
        CACHE.clear();
    }

    static synchronized Entry getAt(String key, long now) {
        if (key == null || key.isEmpty()) return null;
        Entry stored = CACHE.get(key);
        if (stored == null) return null;
        if (now - stored.savedAt > TTL_MS || now < stored.savedAt) {
            CACHE.remove(key);
            return null;
        }
        return stored.copy();
    }

    static synchronized void putAt(
            String key,
            ParsedWorkItem item,
            String validationSummary,
            String providerLabel,
            long now) {
        if (key == null || key.isEmpty() || item == null) return;
        CACHE.put(key, new Entry(
                copyItem(item),
                safe(validationSummary),
                safe(providerLabel),
                now));
    }

    static synchronized int sizeForTest() {
        return CACHE.size();
    }

    private static String baselineSignature(ParsedWorkItem item) {
        if (item == null) return "none";
        return safe(item.type) + '|'
                + safe(item.title) + '|'
                + value(item.startAt) + '|'
                + value(item.endAt) + '|'
                + value(item.reminderAt) + '|'
                + item.allDay + '|'
                + safe(item.repeatRule) + '|'
                + safe(item.priority);
    }

    private static String value(Long value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return result.toString();
        } catch (Exception impossible) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static ParsedWorkItem copyItem(ParsedWorkItem source) {
        ParsedWorkItem copy = new ParsedWorkItem();
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

    public static final class Entry {
        public final ParsedWorkItem item;
        public final String validationSummary;
        public final String providerLabel;
        public final long savedAt;

        Entry(ParsedWorkItem item, String validationSummary, String providerLabel, long savedAt) {
            this.item = item;
            this.validationSummary = validationSummary;
            this.providerLabel = providerLabel;
            this.savedAt = savedAt;
        }

        Entry copy() {
            return new Entry(copyItem(item), validationSummary, providerLabel, savedAt);
        }
    }
}
