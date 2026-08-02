package com.seongho.brainassistant.core.calendar

import com.seongho.brainassistant.core.model.ExclusionPolicy
import com.seongho.brainassistant.core.model.RecurrenceEnd
import com.seongho.brainassistant.core.model.RecurrenceFrequency
import com.seongho.brainassistant.core.model.RecurrenceMaster
import com.seongho.brainassistant.core.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecurrenceRRuleMapperTest {
    private val mapper = RecurrenceRRuleMapper()

    @Test
    fun mapsWeeklyDaysAndInclusiveUntilInTheSeriesTimezone() {
        val mapped = mapper.map(
            master(rule = RecurrenceRule(
                frequency = RecurrenceFrequency.WEEKLY,
                weekdays = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                end = RecurrenceEnd.Until(LocalDate.of(2026, 8, 31)),
            )),
            exdates = emptyList(),
        )

        assertEquals("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE,FR;UNTIL=20260831T145959Z", mapped.rrule)
    }

    @Test
    fun mapsLastFridayOfEachMonth() {
        val mapped = mapper.map(
            master(rule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                ordinal = -1,
                ordinalWeekday = DayOfWeek.FRIDAY,
            )),
            exdates = emptyList(),
        )

        assertEquals("FREQ=MONTHLY;INTERVAL=1;BYDAY=-1FR", mapped.rrule)
    }

    @Test
    fun mapsYearlyMonthAndDayFromTheMasterStart() {
        val mapped = mapper.map(
            master(
                startDate = LocalDate.of(2026, 8, 15),
                rule = RecurrenceRule(frequency = RecurrenceFrequency.YEARLY),
            ),
            exdates = emptyList(),
        )

        assertEquals("FREQ=YEARLY;INTERVAL=1;BYMONTH=8;BYMONTHDAY=15", mapped.rrule)
    }

    @Test
    fun finiteCountWithExclusionsUsesComputedUntilAndNeverEmitsCount() {
        val excluded = Instant.parse("2026-08-05T00:00:00Z")
        val mapped = mapper.map(
            master(rule = RecurrenceRule(
                frequency = RecurrenceFrequency.WEEKLY,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                end = RecurrenceEnd.Count(5),
            )),
            exdates = listOf(excluded),
        )

        assertFalse(mapped.rrule.contains("COUNT="))
        assertEquals("20260814T145959Z", mapped.rrule.substringAfter("UNTIL="))
        assertEquals(listOf("20260805T000000Z"), mapped.exdates)
    }

    private fun master(
        startDate: LocalDate = LocalDate.of(2026, 8, 3),
        rule: RecurrenceRule,
    ) = RecurrenceMaster(
        id = "master-1",
        inputId = "input-1",
        transactionId = "transaction-1",
        title = "방과후수업",
        startDate = startDate,
        startTime = LocalTime.of(9, 0),
        durationMinutes = 180,
        zoneId = ZoneId.of("Asia/Seoul"),
        rule = rule,
        exclusionKinds = emptySet(),
        exclusionPolicy = ExclusionPolicy.SKIP,
        updatedAt = Instant.parse("2026-08-02T00:00:00Z"),
    )
}
