package com.seongho.brainassistant.core.parser

import com.seongho.brainassistant.core.model.AnalysisRequest
import com.seongho.brainassistant.core.model.ItemType
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchAnalysisTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val reference = ZonedDateTime.of(2026, 8, 2, 10, 0, 0, 0, zone)
    private val analyzer = RuleBasedInputAnalyzer()

    @Test
    fun compositeSentencePreservesBatchIdentityOrderAndSourceRanges() = runTest {
        val raw = "다음 주 월요일 10시 교무회의 넣고, 금요일까지 보고서 제출 할 일로 추가하고, 제주 여행 준비물은 메모해줘."

        val batch = analyzer.analyzeBatch(AnalysisRequest(raw, reference))

        assertEquals(raw, batch.originalText)
        assertTrue(batch.id.isNotBlank())
        assertTrue(!batch.requiresReview)
        assertEquals(listOf(ItemType.EVENT, ItemType.TASK, ItemType.NOTE), batch.items.map { it.type })
        assertEquals(listOf(0, 1, 2), batch.items.map { it.batchIndex })
        assertEquals(setOf(batch.id), batch.items.mapNotNull { it.batchId }.toSet())
        batch.items.forEach { item ->
            assertNotNull(item.sourceStart)
            assertNotNull(item.sourceEnd)
            val source = raw.substring(requireNotNull(item.sourceStart), requireNotNull(item.sourceEnd))
            assertTrue(source.isNotBlank())
        }
        assertEquals("교무회의", batch.items[0].title)
        assertEquals("보고서 제출", batch.items[1].title)
        assertEquals("제주 여행 준비물", batch.items[2].title)
    }

    @Test
    fun singleSentenceStillProducesOneOrderedBatchItem() = runTest {
        val raw = "내일 오후 3시 치과 예약"

        val batch = analyzer.analyzeBatch(AnalysisRequest(raw, reference))
        val item = batch.items.single()

        assertTrue(!batch.requiresReview)
        assertEquals(0, item.batchIndex)
        assertEquals(batch.id, item.batchId)
        assertEquals(0, item.sourceStart)
        assertEquals(raw.length, item.sourceEnd)
    }
}
