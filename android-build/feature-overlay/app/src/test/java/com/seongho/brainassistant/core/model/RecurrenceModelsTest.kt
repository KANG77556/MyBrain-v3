package com.seongho.brainassistant.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RecurrenceModelsTest {
    @Test
    fun countMeansAcceptedOccurrences() {
        val end = RecurrenceEnd.Count(10)

        assertEquals(10, end.occurrences)
    }

    @Test
    fun occurrenceIdentityUsesOriginalStart() {
        val key = OccurrenceKey("series-1", Instant.parse("2026-08-03T00:00:00Z"))
        val moved = key.copy()

        assertEquals(key, moved)
    }

    @Test
    fun intervalMustBePositive() {
        assertValidationFailure("반복 간격은 양수여야 합니다.") {
            RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 0)
        }
    }

    @Test
    fun durationMustBePositive() {
        assertValidationFailure("일정 기간은 양수여야 합니다.") {
            recurrenceDraft(durationMinutes = 0)
        }
    }

    @Test
    fun persistedDurationMustBePositive() {
        assertValidationFailure("일정 기간은 양수여야 합니다.") {
            recurrenceMaster(durationMinutes = 0)
        }
    }

    @Test
    fun countMustBePositive() {
        assertValidationFailure("반복 횟수는 양수여야 합니다.") {
            RecurrenceEnd.Count(0)
        }
    }

    @Test
    fun dayOfMonthMustBePositive() {
        assertValidationFailure("월간 일자는 양수여야 합니다.") {
            RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, dayOfMonth = 0)
        }
    }

    @Test
    fun ordinalMustBePositive() {
        assertValidationFailure("월간 순서는 양수여야 합니다.") {
            RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, ordinal = 0)
        }
    }

    @Test
    fun monthlyOrdinalRequiresWeekday() {
        assertValidationFailure("월간 순서 반복에는 요일이 필요합니다.") {
            RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, ordinal = 1)
        }
    }

    private fun recurrenceDraft(durationMinutes: Int) = RecurrenceDraft(
        title = "교무회의",
        startDate = LocalDate.of(2026, 8, 3),
        startTime = LocalTime.of(9, 0),
        durationMinutes = durationMinutes,
        zoneId = ZoneId.of("Asia/Seoul"),
        rule = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY),
        confidence = 0.9,
    )

    private fun recurrenceMaster(durationMinutes: Int) = RecurrenceMaster(
        id = "master-1",
        inputId = "input-1",
        transactionId = "transaction-1",
        title = "교무회의",
        startDate = LocalDate.of(2026, 8, 3),
        startTime = LocalTime.of(9, 0),
        durationMinutes = durationMinutes,
        zoneId = ZoneId.of("Asia/Seoul"),
        rule = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY),
        exclusionKinds = emptySet(),
        exclusionPolicy = ExclusionPolicy.SKIP,
        updatedAt = Instant.parse("2026-08-03T00:00:00Z"),
    )

    private fun assertValidationFailure(expectedMessage: String, block: () -> Unit) {
        try {
            block()
            fail("IllegalArgumentException이 발생해야 합니다.")
        } catch (exception: IllegalArgumentException) {
            assertEquals(expectedMessage, exception.message)
        }
    }
}
