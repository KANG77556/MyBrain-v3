package kr.co.mybrain.v2.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.ZoneId;

public class AiAnalysisCacheTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Before public void setUp() {
        AiAnalysisCache.clear();
    }

    @After public void tearDown() {
        AiAnalysisCache.clear();
    }

    @Test public void sameMeaningfulInputCreatesSameHashedKey() {
        ParsedWorkItem baseline = item("SCHEDULE", "교무회의", 1000L);
        String first = AiAnalysisCache.createKey("GEMINI", "gemini-test", " 내일  교무회의 ", SEOUL, baseline);
        String second = AiAnalysisCache.createKey("gemini", "GEMINI-TEST", "내일 교무회의", SEOUL, baseline);

        assertEquals(first, second);
        assertEquals(64, first.length());
        assertNotEquals("내일 교무회의", first);
    }

    @Test public void changedLocalBaselineCreatesDifferentKey() {
        String first = AiAnalysisCache.createKey(
                "GEMINI", "model", "10분 뒤 알림", SEOUL, item("TASK", "알림", 1000L));
        String second = AiAnalysisCache.createKey(
                "GEMINI", "model", "10분 뒤 알림", SEOUL, item("TASK", "알림", 2000L));

        assertNotEquals(first, second);
    }

    @Test public void cachedItemIsReturnedAsIndependentCopy() {
        ParsedWorkItem source = item("MEMO", "원본", null);
        AiAnalysisCache.putAt("key", source, "검증 통과", "Gemini", 1000L);

        AiAnalysisCache.Entry first = AiAnalysisCache.getAt("key", 1001L);
        assertNotNull(first);
        first.item.title = "수정됨";

        AiAnalysisCache.Entry second = AiAnalysisCache.getAt("key", 1002L);
        assertNotNull(second);
        assertEquals("원본", second.item.title);
    }

    @Test public void expiredEntryIsRemoved() {
        AiAnalysisCache.putAt("key", item("TASK", "제출", null), "정상", "GPT", 1000L);

        assertNotNull(AiAnalysisCache.getAt("key", 1000L + AiAnalysisCache.TTL_MS));
        assertNull(AiAnalysisCache.getAt("key", 1001L + AiAnalysisCache.TTL_MS));
        assertEquals(0, AiAnalysisCache.sizeForTest());
    }

    @Test public void cacheKeepsOnlyEightRecentEntries() {
        for (int i = 0; i < 9; i++) {
            AiAnalysisCache.putAt("key-" + i, item("MEMO", "항목 " + i, null), "정상", "Gemini", i);
        }

        assertEquals(8, AiAnalysisCache.sizeForTest());
        assertNull(AiAnalysisCache.getAt("key-0", 9L));
        assertNotNull(AiAnalysisCache.getAt("key-8", 9L));
    }

    private ParsedWorkItem item(String type, String title, Long startAt) {
        ParsedWorkItem item = new ParsedWorkItem();
        item.type = type;
        item.title = title;
        item.sourceText = title;
        item.startAt = startAt;
        item.repeatRule = "NONE";
        item.priority = "NORMAL";
        item.confidence = 0.9f;
        item.aiProvider = "GEMINI";
        return item;
    }
}
