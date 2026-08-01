package com.seongho.brainassistant.core.parser

import com.seongho.brainassistant.core.model.AnalysisRequest
import com.seongho.brainassistant.core.model.ClarificationField
import com.seongho.brainassistant.core.model.ItemType
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedInputAnalyzerTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val analyzer = RuleBasedInputAnalyzer()
    private val reference = ZonedDateTime.of(2026, 7, 31, 22, 0, 0, 0, seoul)

    @Test
    fun parsesCompositeTeacherSentence() = runTest {
        val result = analyzer.analyze(
            AnalysisRequest(
                rawText = "다음 주 화요일까지 수행평가 채점하고 금요일 오후 3시에 학부모 상담, 상담 전에 성적표 확인",
                referenceTime = reference,
            ),
        )

        assertEquals(3, result.items.size)
        assertEquals(ItemType.TASK, result.items[0].type)
        assertEquals("수행평가 채점", result.items[0].title)
        assertEquals(ItemType.EVENT, result.items[1].type)
        assertEquals(15, result.items[1].startAt!!.atZone(seoul).hour)
        assertEquals(ItemType.TASK, result.items[2].type)
        assertEquals("성적표 확인", result.items[2].title)
        assertTrue(result.confidence >= 0.85)
    }

    @Test
    fun eventWithDateButNoTimeRequiresTimeReview() = runTest {
        val result = analyzer.analyze(AnalysisRequest("내일 학부모 상담", reference))

        assertEquals(ItemType.EVENT, result.items.single().type)
        assertTrue(ClarificationField.TIME in result.clarificationFields)
        assertTrue(result.confidence < 0.85)
    }

    @Test
    fun eventWithTimeButNoDateRequiresDateReview() = runTest {
        val result = analyzer.analyze(AnalysisRequest("오후 3시 학부모 상담", reference))

        assertEquals(ItemType.EVENT, result.items.single().type)
        assertTrue(ClarificationField.DATE in result.clarificationFields)
        assertTrue(result.confidence < 0.85)
    }

    @Test
    fun parsesDDayTitleAndTargetDate() = runTest {
        val result = analyzer.analyze(AnalysisRequest("8월 20일 교육청 보고서 제출 디데이", reference))
        val item = result.items.single()

        assertEquals(ItemType.D_DAY, item.type)
        assertEquals("교육청 보고서 제출", item.title)
        assertEquals(LocalDate.of(2026, 8, 20), item.targetDate)
        assertTrue(result.clarificationFields.isEmpty())
        assertTrue(result.confidence >= 0.85)
    }

    @Test
    fun ddayWithoutDateRequiresDateReview() = runTest {
        val result = analyzer.analyze(AnalysisRequest("자격증 시험 디데이", reference))

        assertEquals(ItemType.D_DAY, result.items.single().type)
        assertTrue(ClarificationField.DATE in result.clarificationFields)
        assertTrue(result.confidence < 0.85)
    }

    @Test
    fun asksForDateWhenPhraseIsVague() = runTest {
        val result = analyzer.analyze(AnalysisRequest("다음 주쯤 상담 잡아줘", reference))

        assertTrue(ClarificationField.DATE in result.clarificationFields)
        assertTrue(result.confidence < 0.85)
    }

    @Test
    fun handlesMonthBoundaryAndAfternoon() = runTest {
        val result = analyzer.analyze(AnalysisRequest("내일 오후 3시 상담", reference))
        val start = result.items.single().startAt!!.atZone(seoul)

        assertEquals(2026, start.year)
        assertEquals(8, start.monthValue)
        assertEquals(1, start.dayOfMonth)
        assertEquals(15, start.hour)
    }
}
