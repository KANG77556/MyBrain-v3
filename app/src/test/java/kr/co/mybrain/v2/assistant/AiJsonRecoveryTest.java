package kr.co.mybrain.v2.assistant;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AiJsonRecoveryTest {
    @Test public void repairsMissingClosingBrace() {
        String raw = "{\"category\":\"SCHEDULE\",\"title\":\"교무회의\",\"startAt\":\"2026-07-28T09:00:00+09:00\",\"endAt\":null";
        AiJsonRecovery.Recovery result = AiJsonRecovery.recover(raw);
        assertTrue(result.recovered);
        assertEquals(raw + "}", result.json);
    }

    @Test public void isolatesCodeFenceAndTrailingExplanation() {
        String raw = "```json\n{\"type\":\"TASK\",\"title\":\"서류 제출\"}\n```\n완료";
        AiJsonRecovery.Recovery result = AiJsonRecovery.recover(raw);
        assertEquals("{\"type\":\"TASK\",\"title\":\"서류 제출\"}", result.json);
    }

    @Test public void closesTruncatedStringAndObject() {
        String raw = "{\"type\":\"MEMO\",\"title\":\"회의 준비";
        AiJsonRecovery.Recovery result = AiJsonRecovery.recover(raw);
        assertEquals("{\"type\":\"MEMO\",\"title\":\"회의 준비\"}", result.json);
    }

    @Test public void keepsCompleteObjectUnchanged() {
        String raw = "{\"type\":\"MEMO\",\"title\":\"기록\"}";
        AiJsonRecovery.Recovery result = AiJsonRecovery.recover(raw);
        assertFalse(result.recovered);
        assertEquals(raw, result.json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTextWithoutObject() {
        AiJsonRecovery.recover("분석 결과 없음");
    }
}
