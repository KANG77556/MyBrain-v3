package com.seongho.brainassistant.data

import com.seongho.brainassistant.core.calendar.ConflictChecker
import com.seongho.brainassistant.core.model.AnalysisResult
import com.seongho.brainassistant.core.model.ClarificationField
import com.seongho.brainassistant.core.model.ItemType
import com.seongho.brainassistant.core.model.ParsedItem
import com.seongho.brainassistant.core.model.RecurrenceDraft
import com.seongho.brainassistant.core.model.RecurrenceFrequency
import com.seongho.brainassistant.core.model.RecurrenceRule
import com.seongho.brainassistant.core.parser.InputAnalyzer
import com.seongho.brainassistant.testing.FakeBrainRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureUseCaseTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = ZonedDateTime.of(2026, 7, 31, 22, 0, 0, 0, zone)
    private val clock = Clock.fixed(now.toInstant(), zone)

    @Test
    fun autoSavesOnlyHighConfidenceNonConflictingResult() = runTest {
        val repository = FakeBrainRepository()
        val analyzer = InputAnalyzer {
            AnalysisResult(
                items = listOf(
                    ParsedItem(
                        type = ItemType.EVENT,
                        title = "상담",
                        startAt = now.plusDays(1).withHour(15).toInstant(),
                        endAt = now.plusDays(1).withHour(16).toInstant(),
                    ),
                ),
                confidence = 0.92,
                clarificationFields = emptySet(),
                analyzer = "FAKE",
            )
        }
        val useCase = CaptureUseCase(repository, analyzer, ConflictChecker(), clock)

        val outcome = useCase.capture("내일 오후 3시 상담", now)

        assertTrue(outcome is CaptureResult.AutoSaved)
        assertEquals(1, repository.inputs.size)
        assertEquals(1, repository.calendars.size)
    }

    @Test
    fun highConfidenceDDayWithoutDateIsRoutedToReviewInsteadOfSaveFailure() = runTest {
        val repository = FakeBrainRepository()
        val analyzer = InputAnalyzer {
            AnalysisResult(
                items = listOf(
                    ParsedItem(
                        type = ItemType.D_DAY,
                        title = "자격증 시험",
                    ),
                ),
                confidence = 0.95,
                clarificationFields = emptySet(),
                analyzer = "FAKE",
            )
        }
        val useCase = CaptureUseCase(repository, analyzer, ConflictChecker(), clock)

        val outcome = useCase.capture("자격증 시험 디데이", now)

        assertTrue(outcome is CaptureResult.NeedsReview)
        assertTrue(ClarificationField.DATE in (outcome as CaptureResult.NeedsReview).clarificationFields)
        assertTrue(repository.dDays.isEmpty())
    }

    @Test
    fun routesVagueResultToReview() = runTest {
        val repository = FakeBrainRepository()
        val analyzer = InputAnalyzer {
            AnalysisResult(emptyList(), 0.70, setOf(ClarificationField.DATE), "FAKE")
        }
        val useCase = CaptureUseCase(repository, analyzer, ConflictChecker(), clock)

        val outcome = useCase.capture("다음 주쯤 상담", now)

        assertTrue(outcome is CaptureResult.NeedsReview)
        assertEquals(1, repository.inputs.size)
    }

    @Test
    fun oneSentenceCanSaveMultipleOrganizedItemsInOneTransaction() = runTest {
        val repository = FakeBrainRepository()
        val analyzer = InputAnalyzer {
            AnalysisResult(
                items = listOf(
                    ParsedItem(type = ItemType.TASK, title = "수행평가 채점", dueAt = now.plusDays(1).toInstant()),
                    ParsedItem(
                        type = ItemType.EVENT,
                        title = "학부모 상담",
                        startAt = now.plusDays(2).withHour(15).toInstant(),
                        endAt = now.plusDays(2).withHour(16).toInstant(),
                    ),
                    ParsedItem(type = ItemType.NOTE, title = "상담 준비", body = "성적표 확인"),
                ),
                confidence = 0.93,
                clarificationFields = emptySet(),
                analyzer = "FAKE",
            )
        }
        val useCase = CaptureUseCase(repository, analyzer, ConflictChecker(), clock)

        val outcome = useCase.capture("채점하고 상담 잡고 성적표 확인 메모", now)

        assertTrue(outcome is CaptureResult.AutoSaved)
        val saved = outcome as CaptureResult.AutoSaved
        assertEquals(1, repository.tasks.size)
        assertEquals(1, repository.calendars.size)
        assertEquals(1, repository.notes.size)
        assertEquals(saved.transactionId, repository.tasks.single().transactionId)
        assertEquals(saved.transactionId, repository.calendars.single().transactionId)
        assertEquals(saved.transactionId, repository.notes.single().transactionId)
    }

    @Test
    fun undoSoftDeletesWholeTransaction() = runTest {
        val repository = FakeBrainRepository()
        val analyzer = InputAnalyzer {
            AnalysisResult(
                listOf(ParsedItem(type = ItemType.TASK, title = "채점")),
                0.91,
                emptySet(),
                "FAKE",
            )
        }
        val useCase = CaptureUseCase(repository, analyzer, ConflictChecker(), clock)
        val saved = useCase.capture("채점", now) as CaptureResult.AutoSaved

        useCase.undo(saved.transactionId)

        assertTrue(repository.tasks.isEmpty())
    }

    @Test
    fun recurringInputAlwaysRoutesToReviewWithDraft() = runTest {
        val repository = FakeBrainRepository()
        val recurrence = RecurrenceDraft(
            title = "방과후수업",
            startDate = LocalDate.of(2026, 8, 3),
            startTime = LocalTime.of(9, 0),
            durationMinutes = 180,
            rule = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY),
            confidence = 0.90,
        )
        val analyzer = InputAnalyzer {
            AnalysisResult(
                items = emptyList(),
                confidence = 0.90,
                clarificationFields = emptySet(),
                analyzer = "FAKE",
                recurrences = listOf(recurrence),
            )
        }
        val useCase = CaptureUseCase(repository, analyzer, ConflictChecker(), clock)

        val outcome = useCase.capture("매주 월요일 9시부터 12시까지 방과후수업", now)

        assertTrue(outcome is CaptureResult.NeedsReview)
        outcome as CaptureResult.NeedsReview
        assertEquals(listOf(recurrence), outcome.recurrences)
        assertEquals("반복 일정의 전체 날짜를 확인해 주세요.", outcome.message)
        assertTrue(repository.calendars.isEmpty())
    }
}
