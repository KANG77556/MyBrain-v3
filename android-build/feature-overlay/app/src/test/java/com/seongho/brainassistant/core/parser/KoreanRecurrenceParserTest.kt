package com.seongho.brainassistant.core.parser

import com.seongho.brainassistant.core.model.ExclusionKind.KOREAN_PUBLIC_HOLIDAY
import com.seongho.brainassistant.core.model.ExclusionKind.SCHOOL_CALENDAR
import com.seongho.brainassistant.core.model.ExclusionPolicy.MOVE_TO_NEXT_WEEKDAY
import com.seongho.brainassistant.core.model.ExclusionPolicy.SKIP
import com.seongho.brainassistant.core.model.RecurrenceEnd
import com.seongho.brainassistant.core.model.RecurrenceFrequency.MONTHLY
import com.seongho.brainassistant.core.model.RecurrenceFrequency.WEEKLY
import com.seongho.brainassistant.core.model.RecurrenceFrequency.YEARLY
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.DayOfWeek.THURSDAY
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KoreanRecurrenceParserTest {
    private val parser = KoreanRecurrenceParser()
    private val reference = ZonedDateTime.of(
        2026,
        8,
        2,
        12,
        0,
        0,
        0,
        ZoneId.of("Asia/Seoul"),
    )

    @Test
    fun parsesNextMondayThroughFridayTimeRange() {
        val draft = requireNotNull(
            parser.parse("다음주 월요일부터 금요일까지 9시부터 12시까지 방과후수업", reference),
        )

        assertEquals(LocalDate.of(2026, 8, 3), draft.startDate)
        assertEquals(setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), draft.rule.weekdays)
        assertEquals(LocalTime.of(9, 0), draft.startTime)
        assertEquals(180, draft.durationMinutes)
        assertEquals(RecurrenceEnd.Until(LocalDate.of(2026, 8, 7)), draft.rule.end)
        assertEquals("방과후수업", draft.title)
    }

    @Test
    fun parsesWeeklyMultipleWeekdays() {
        val draft = requireNotNull(parser.parse("매주 월수금 9시부터 10시까지 방과후수업", reference))

        assertEquals(LocalDate.of(2026, 8, 3), draft.startDate)
        assertEquals(WEEKLY, draft.rule.frequency)
        assertEquals(1, draft.rule.interval)
        assertEquals(setOf(MONDAY, WEDNESDAY, FRIDAY), draft.rule.weekdays)
        assertEquals(RecurrenceEnd.Never, draft.rule.end)
    }

    @Test
    fun parsesBiweeklyWeekday() {
        val draft = requireNotNull(parser.parse("격주 화요일 14시부터 15시까지 교무회의", reference))

        assertEquals(LocalDate.of(2026, 8, 4), draft.startDate)
        assertEquals(WEEKLY, draft.rule.frequency)
        assertEquals(2, draft.rule.interval)
        assertEquals(setOf(TUESDAY), draft.rule.weekdays)
    }

    @Test
    fun parsesMonthlyDayOfMonth() {
        val draft = requireNotNull(parser.parse("매월 15일 10시부터 11시까지 상담", reference))

        assertEquals(LocalDate.of(2026, 8, 15), draft.startDate)
        assertEquals(MONTHLY, draft.rule.frequency)
        assertEquals(15, draft.rule.dayOfMonth)
    }

    @Test
    fun parsesLastWeekdayOfEveryMonth() {
        val draft = requireNotNull(parser.parse("매월 마지막 금요일 16시부터 17시까지 회의", reference))

        assertEquals(LocalDate.of(2026, 8, 28), draft.startDate)
        assertEquals(MONTHLY, draft.rule.frequency)
        assertEquals(5, draft.rule.ordinal)
        assertEquals(FRIDAY, draft.rule.ordinalWeekday)
    }

    @Test
    fun parsesYearlyMonthAndDay() {
        val draft = requireNotNull(parser.parse("매년 8월 15일 10시부터 11시까지 기념식", reference))

        assertEquals(LocalDate.of(2026, 8, 15), draft.startDate)
        assertEquals(YEARLY, draft.rule.frequency)
    }

    @Test
    fun parsesOccurrenceCountTermination() {
        val draft = requireNotNull(parser.parse("매주 수요일 9시부터 10시까지 수업 10회", reference))

        assertEquals(RecurrenceEnd.Count(10), draft.rule.end)
    }

    @Test
    fun parsesUntilEndOfNextMonth() {
        val draft = requireNotNull(parser.parse("매주 월요일 9시부터 10시까지 수업 다음 달 말까지", reference))

        assertEquals(RecurrenceEnd.Until(LocalDate.of(2026, 9, 30)), draft.rule.end)
    }

    @Test
    fun parsesPublicHolidayAndSchoolVacationExclusions() {
        val draft = requireNotNull(
            parser.parse("매주 월요일 9시부터 10시까지 수업 공휴일과 방학 제외", reference),
        )

        assertEquals(setOf(KOREAN_PUBLIC_HOLIDAY, SCHOOL_CALENDAR), draft.exclusionKinds)
        assertEquals(SKIP, draft.exclusionPolicy)
    }

    @Test
    fun parsesMoveToNextWeekdayPolicy() {
        val draft = requireNotNull(
            parser.parse("매주 월요일 9시부터 10시까지 수업 공휴일은 다음 평일로 이동", reference),
        )

        assertEquals(setOf(KOREAN_PUBLIC_HOLIDAY), draft.exclusionKinds)
        assertEquals(MOVE_TO_NEXT_WEEKDAY, draft.exclusionPolicy)
    }

    @Test
    fun returnsNullWhenTextHasNoRecurrenceMarker() {
        assertNull(parser.parse("내일 9시부터 10시까지 상담", reference))
    }
}
