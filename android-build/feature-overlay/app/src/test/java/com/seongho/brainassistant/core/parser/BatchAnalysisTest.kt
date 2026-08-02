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

        val result = analyzer.analyze(AnalysisRequest(raw, reference))

        assertEquals(listOf(ItemType.EVENT, ItemType.TASK, ItemType.NOTE), result.items.map { it.type })
        assertEquals(listOf(0, 1, 2), result.items.map { it.batchOrder })
        assertEquals(1, result.items.mapNotNull { it.batchId }.toSet().size)
        result.items.forEach { item ->
            assertNotNull(item.sourceStart)
            assertNotNull(item.sourceEnd)
            val source = raw.substring(requireNotNull(item.sourceStart), requireNotNull(item.sourceEnd))
            assertTrue(source.isNotBlank())
        }
        assertEquals("교무회의", result.items[0].title)
        assertEquals("보고서 제출", result.items[1].title)
        assertEquals("제주 여행 준비물", result.items[2].title)
    }

    @Test
    fun singleSentenceStillProducesOneOrderedBatchItem() = runTest {
        val raw = "내일 오후 3시 치과 예약"

        val item = analyzer.analyze(AnalysisRequest(raw, reference)).items.single()

        assertEquals(0, item.batchOrder)
        assertTrue(item.batchId?.isNotBlank() == true)
        assertEquals(0, item.sourceStart)
        assertEquals(raw.length, item.sourceEnd)
    }
}
