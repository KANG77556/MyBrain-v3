package com.seongho.brainassistant.data

import com.seongho.brainassistant.core.calendar.ConflictChecker
import com.seongho.brainassistant.core.model.AnalysisResult
import com.seongho.brainassistant.core.model.ClarificationField
import com.seongho.brainassistant.core.model.ItemType
import com.seongho.brainassistant.core.model.ParsedItem
import com.seongho.brainassistant.core.parser.InputAnalyzer
import com.seongho.brainassistant.testing.FakeBrainRepository
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureBatchUseCaseTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = ZonedDateTime.of(2026, 8, 2, 10, 0, 0, 0, zone)
    private val clock = Clock.fixed(now.toInstant(), zone)

    @Test
    fun oneAmbiguousItemRoutesWholeBatchToReviewWithoutPartialSave() = runTest {
        val repository = FakeBrainRepository()
        val analyzer = InputAnalyzer {
            AnalysisResult(
                items = listOf(
                    ParsedItem(type = ItemType.TASK, title = "보고서 제출", dueAt = now.plusDays(5).toInstant()),
                    ParsedItem(type = ItemType.EVENT, title = "교무회의"),
                ),
                confidence = 0.91,
                clarificationFields = setOf(ClarificationField.DATE, ClarificationField.TIME),
                analyzer = "FAKE_BATCH",
            )
        }
        val useCase = CaptureUseCase(repository, analyzer, ConflictChecker(), clock)

        val outcome = useCase.capture("금요일까지 보고서 제출하고 교무회의 넣어줘", now)

        assertTrue(outcome is CaptureResult.NeedsReview)
        val review = outcome as CaptureResult.NeedsReview
        assertTrue(review.batch?.requiresReview == true)
        assertEquals("금요일까지 보고서 제출하고 교무회의 넣어줘", review.batch?.originalText)
        assertEquals(2, review.batch?.items?.size)
        assertTrue(repository.tasks.isEmpty())
        assertTrue(repository.calendars.isEmpty())
        assertTrue(repository.notes.isEmpty())
        assertTrue(repository.dDays.isEmpty())
    }

    @Test
    fun validSingleItemRemainsBackwardCompatibleAndAutoSaves() = runTest {
        val repository = FakeBrainRepository()
        val analyzer = InputAnalyzer {
            AnalysisResult(
                items = listOf(ParsedItem(type = ItemType.TASK, title = "채점")),
                confidence = 0.93,
                clarificationFields = emptySet(),
                analyzer = "FAKE_SINGLE",
            )
        }
        val useCase = CaptureUseCase(repository, analyzer, ConflictChecker(), clock)

        val outcome = useCase.capture("채점", now)

        assertTrue(outcome is CaptureResult.AutoSaved)
        val saved = outcome as CaptureResult.AutoSaved
        assertFalse(saved.batch?.requiresReview ?: true)
        assertEquals(1, saved.batch?.items?.size)
        assertEquals(1, repository.tasks.size)
    }
}
