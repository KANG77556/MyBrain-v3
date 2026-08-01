package com.seongho.brainassistant.core.recurrence

import com.seongho.brainassistant.core.model.ExclusionPolicy
import com.seongho.brainassistant.core.model.OccurrenceKey
import com.seongho.brainassistant.core.model.RecurrenceEnd
import com.seongho.brainassistant.core.model.RecurrenceException
import com.seongho.brainassistant.core.model.RecurrenceExceptionKind
import com.seongho.brainassistant.core.model.RecurrenceFrequency
import com.seongho.brainassistant.core.model.RecurrenceMaster
import com.seongho.brainassistant.core.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceEngineTest {
    private val engine = RecurrenceEngine()

    @Test
    fun dailyIntervalProducesEverySecondDay() {
        assertDates(
            master(rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 2)),
            LocalDate.of(2026, 8, 3)..LocalDate.of(2026, 8, 9),
            listOf("2026-08-03", "2026-08-05", "2026-08-07", "2026-08-09"),
        )
    }

    @Test
    fun weekdaysWithinNextWeekProduceFiveOccurrences() {
        assertDates(
            master(
                rule = RecurrenceRule(
                    frequency = RecurrenceFrequency.WEEKLY,
                    weekdays = DayOfWeek.entries.take(5).toSet(),
                ),
            ),
            LocalDate.of(2026, 8, 3)..LocalDate.of(2026, 8, 7),
            listOf("2026-08-03", "2026-08-04", "2026-08-05", "2026-08-06", "2026-08-07"),
        )
    }

    @Test
    fun selectedWeekdaysRepeatFromTheMastersMondayAnchor() {
        assertDates(
            master(
                rule = RecurrenceRule(
                    frequency = RecurrenceFrequency.WEEKLY,
                    weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                ),
            ),
            LocalDate.of(2026, 8, 3)..LocalDate.of(2026, 8, 12),
            listOf("2026-08-03", "2026-08-05", "2026-08-10", "2026-08-12"),
        )
    }

    @Test
    fun biweeklyRuleUsesEveryOtherWeek() {
        assertDates(
            master(
                rule = RecurrenceRule(
                    frequency = RecurrenceFrequency.WEEKLY,
                    interval = 2,
                    weekdays = setOf(DayOfWeek.MONDAY),
                ),
            ),
            LocalDate.of(2026, 8, 3)..LocalDate.of(2026, 8, 24),
            listOf("2026-08-03", "2026-08-17"),
        )
    }

    @Test
    fun monthlyDayRepeatsOnTheRequestedDayOfMonth() {
        assertDates(
            master(
                startDate = LocalDate.of(2026, 1, 15),
                rule = RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, dayOfMonth = 15),
            ),
            LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 3, 31),
            listOf("2026-01-15", "2026-02-15", "2026-03-15"),
        )
    }

    @Test
    fun monthlyFifthOrdinalMeansLastFridayOfEachMonth() {
        assertDates(
            master(
                startDate = LocalDate.of(2026, 8, 1),
                rule = RecurrenceRule(
                    frequency = RecurrenceFrequency.MONTHLY,
                    ordinal = 5,
                    ordinalWeekday = DayOfWeek.FRIDAY,
                ),
            ),
            LocalDate.of(2026, 8, 1)..LocalDate.of(2026, 9, 30),
            listOf("2026-08-28", "2026-09-25"),
        )
    }

    @Test
    fun yearlyRuleRepeatsOnTheOriginalMonthAndDay() {
        assertDates(
            master(
                startDate = LocalDate.of(2026, 8, 3),
                rule = RecurrenceRule(frequency = RecurrenceFrequency.YEARLY),
            ),
            LocalDate.of(2026, 1, 1)..LocalDate.of(2028, 12, 31),
            listOf("2026-08-03", "2027-08-03", "2028-08-03"),
        )
    }

    @Test
    fun yearlyLeapDaySkipsNonLeapYears() {
        assertDates(
            master(
                startDate = LocalDate.of(2024, 2, 29),
                rule = RecurrenceRule(frequency = RecurrenceFrequency.YEARLY),
            ),
            LocalDate.of(2024, 1, 1)..LocalDate.of(2032, 12, 31),
            listOf("2024-02-29", "2028-02-29", "2032-02-29"),
        )
    }

    @Test
    fun monthlyMissingDayIsSkippedInsteadOfBeingClamped() {
        assertDates(
            master(
                startDate = LocalDate.of(2026, 1, 31),
                rule = RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, dayOfMonth = 31),
            ),
            LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 4, 30),
            listOf("2026-01-31", "2026-03-31"),
        )
    }

    @Test
    fun excludedDatesAreSkipped() {
        assertDates(
            master(rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY)),
            LocalDate.of(2026, 8, 3)..LocalDate.of(2026, 8, 5),
            listOf("2026-08-03", "2026-08-05"),
            exclusions = setOf(LocalDate.of(2026, 8, 4)),
        )
    }

    @Test
    fun consecutiveExclusionsMoveAnOccurrenceToTheNextWeekday() {
        assertDates(
            master(
                startDate = LocalDate.of(2026, 8, 6),
                rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, end = RecurrenceEnd.Count(1)),
                exclusionPolicy = ExclusionPolicy.MOVE_TO_NEXT_WEEKDAY,
            ),
            LocalDate.of(2026, 8, 6)..LocalDate.of(2026, 8, 10),
            listOf("2026-08-10"),
            exclusions = setOf(LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 7)),
        )
    }

    @Test
    fun movedOccurrencePastUntilDateIsDropped() {
        assertDates(
            master(
                startDate = LocalDate.of(2026, 8, 6),
                rule = RecurrenceRule(
                    frequency = RecurrenceFrequency.DAILY,
                    end = RecurrenceEnd.Until(LocalDate.of(2026, 8, 6)),
                ),
                exclusionPolicy = ExclusionPolicy.MOVE_TO_NEXT_WEEKDAY,
            ),
            LocalDate.of(2026, 8, 6)..LocalDate.of(2026, 8, 10),
            emptyList(),
            exclusions = setOf(LocalDate.of(2026, 8, 6)),
        )
    }

    @Test
    fun exceptionsMatchTheOriginalStartAndCanModifyOrCancel() {
        val master = master(rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY))
        val occurrences = engine.generate(
            master,
            listOf(
                RecurrenceException(
                    id = "modified",
                    key = OccurrenceKey(master.id, Instant.parse("2026-08-03T00:00:00Z")),
                    kind = RecurrenceExceptionKind.MODIFIED,
                    effectiveStartAt = Instant.parse("2026-08-05T02:00:00Z"),
                    titleOverride = "Rescheduled lesson",
                ),
                RecurrenceException(
                    id = "cancelled",
                    key = OccurrenceKey(master.id, Instant.parse("2026-08-04T00:00:00Z")),
                    kind = RecurrenceExceptionKind.CANCELLED,
                ),
            ),
            emptySet(),
            LocalDate.of(2026, 8, 3)..LocalDate.of(2026, 8, 5),
        )

        assertEquals(listOf("Recurring lesson@2026-08-05T00:00:00Z", "Rescheduled lesson@2026-08-05T02:00:00Z"),
            occurrences.map { "${it.title}@${it.startAt}" })
    }

    @Test
    fun countEndsAfterTenAcceptedOccurrencesDespiteExclusions() {
        assertDates(
            master(
                rule = RecurrenceRule(
                    frequency = RecurrenceFrequency.DAILY,
                    end = RecurrenceEnd.Count(10),
                ),
            ),
            LocalDate.of(2026, 8, 3)..LocalDate.of(2026, 8, 15),
            listOf(
                "2026-08-05", "2026-08-06", "2026-08-07", "2026-08-08", "2026-08-09",
                "2026-08-10", "2026-08-11", "2026-08-12", "2026-08-13", "2026-08-14",
            ),
            exclusions = setOf(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 4)),
        )
    }

    @Test
    fun collidingMovedAndRegularOccurrencesAreBothMarked() {
        val master = master(rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY))
        val occurrences = engine.generate(
            master,
            listOf(
                RecurrenceException(
                    id = "move",
                    key = OccurrenceKey(master.id, Instant.parse("2026-08-03T00:00:00Z")),
                    kind = RecurrenceExceptionKind.MOVED,
                    effectiveStartAt = Instant.parse("2026-08-04T00:00:00Z"),
                ),
            ),
            emptySet(),
            LocalDate.of(2026, 8, 3)..LocalDate.of(2026, 8, 4),
        )

        assertEquals(2, occurrences.size)
        assertEquals(listOf("2026-08-04T00:00:00Z", "2026-08-04T00:00:00Z"), occurrences.map { it.startAt.toString() })
        assertEquals(listOf(COLLISION_REASON, COLLISION_REASON), occurrences.map { it.conflictReason })
    }

    private fun assertDates(
        master: RecurrenceMaster,
        range: ClosedRange<LocalDate>,
        expected: List<String>,
        exclusions: Set<LocalDate> = emptySet(),
    ) {
        val actual = engine.generate(master, emptyList(), exclusions, range)
            .map { it.startAt.atZone(SEOUL).toLocalDate().toString() }

        assertEquals(expected, actual)
    }

    private fun master(
        startDate: LocalDate = LocalDate.of(2026, 8, 3),
        rule: RecurrenceRule,
        exclusionPolicy: ExclusionPolicy = ExclusionPolicy.SKIP,
    ) = RecurrenceMaster(
        id = "master-1",
        inputId = "input-1",
        transactionId = "transaction-1",
        title = "Recurring lesson",
        startDate = startDate,
        startTime = LocalTime.of(9, 0),
        durationMinutes = 50,
        zoneId = SEOUL,
        rule = rule,
        exclusionKinds = emptySet(),
        exclusionPolicy = exclusionPolicy,
        updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
    )

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        const val COLLISION_REASON = "諛섎났 ?쇱젙 ?대룞 寃곌낵媛 寃뱀묩?덈떎."
    }
}
