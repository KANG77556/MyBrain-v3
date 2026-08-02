package com.seongho.brainassistant.feature.calendar

import com.seongho.brainassistant.core.model.RecurrenceScope
import com.seongho.brainassistant.core.model.OccurrenceKey
import com.seongho.brainassistant.core.model.RecurrenceOccurrence
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceScopePolicyTest {
    @Test
    fun scopeOptionsRequireAnExplicitChoiceBeforeMutation() {
        assertEquals(
            listOf(
                RecurrenceScopeOption(RecurrenceScope.THIS_OCCURRENCE, "이번 일정만"),
                RecurrenceScopeOption(RecurrenceScope.THIS_AND_FOLLOWING, "이후 일정"),
                RecurrenceScopeOption(RecurrenceScope.ALL_OCCURRENCES, "전체 반복 일정"),
            ),
            recurrenceScopeOptions(),
        )
    }

    @Test
    fun recurringOccurrencesAreShownOnTheirEffectiveDate() {
        val occurrence = RecurrenceOccurrence(
            key = OccurrenceKey("series", Instant.parse("2026-08-03T00:00:00Z")),
            title = "방과후수업",
            startAt = Instant.parse("2026-08-03T00:00:00Z"),
            endAt = Instant.parse("2026-08-03T03:00:00Z"),
        )

        assertEquals(listOf(occurrence), recurringForDate(LocalDate.of(2026, 8, 3), listOf(occurrence)))
    }
}
