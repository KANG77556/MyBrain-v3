package com.seongho.brainassistant.core.calendar

import com.seongho.brainassistant.core.model.ExclusionKind
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCalendarExclusionGatewayTest {
    @Test
    fun discoveryKeepsVisibleGoogleHolidayAndSchoolSourcesOnly() = runTest {
        val reader = FakeCalendarProviderReader(
            sources = listOf(
                DeviceCalendarSource("1", "대한민국의 공휴일", "com.google", true),
                DeviceCalendarSource("2", "학교 학사일정", "com.google", true),
                DeviceCalendarSource("3", "개인 일정", "com.google", true),
                DeviceCalendarSource("4", "학교 일정", "local", true),
                DeviceCalendarSource("5", "숨긴 학교 일정", "com.google", false),
            ),
        )

        val sources = DeviceCalendarExclusionGateway(reader).discoverSources()

        assertEquals(listOf("1", "2"), sources.map { it.calendarId })
        assertEquals(ExclusionKind.KOREAN_PUBLIC_HOLIDAY, sources[0].kind)
        assertTrue(sources[0].enabled)
        assertEquals(ExclusionKind.SCHOOL_CALENDAR, sources[1].kind)
        assertFalse(sources[1].enabled)
    }

    @Test
    fun schoolCalendarLoadsOnlyRecognizedCandidateEvents() = runTest {
        val reader = FakeCalendarProviderReader(
            events = listOf(
                CalendarExclusionEvent("vacation", "여름방학", day("2026-08-02T15:00:00Z"), day("2026-08-03T15:00:00Z")),
                CalendarExclusionEvent("meeting", "수학과 협의회", day("2026-08-03T00:00:00Z"), day("2026-08-03T01:00:00Z")),
            ),
        )
        val source = com.seongho.brainassistant.core.model.ExclusionSource(
            id = "device:2",
            calendarId = "2",
            displayName = "학교 학사일정",
            kind = ExclusionKind.SCHOOL_CALENDAR,
            enabled = true,
        )

        val rows = DeviceCalendarExclusionGateway(reader).loadDates(source, 2026)

        assertEquals(listOf("vacation"), rows.map { it.remoteEventId }.distinct())
        assertTrue(rows.none { it.approved })
        assertEquals(listOf(2026), reader.requestedYears)
    }

    @Test
    fun publicHolidayCalendarLoadsEveryProviderEventAsApproved() = runTest {
        val reader = FakeCalendarProviderReader(
            events = listOf(
                CalendarExclusionEvent("holiday", "광복절", day("2026-08-14T15:00:00Z"), day("2026-08-15T15:00:00Z")),
                CalendarExclusionEvent("substitute", "대체공휴일", day("2026-08-16T15:00:00Z"), day("2026-08-17T15:00:00Z")),
            ),
        )
        val source = com.seongho.brainassistant.core.model.ExclusionSource(
            id = "device:1",
            calendarId = "1",
            displayName = "대한민국의 공휴일",
            kind = ExclusionKind.KOREAN_PUBLIC_HOLIDAY,
            enabled = true,
        )

        val rows = DeviceCalendarExclusionGateway(reader).loadDates(source, 2026)

        assertEquals(listOf("holiday", "substitute"), rows.map { it.remoteEventId })
        assertTrue(rows.all { it.approved })
    }

    private fun day(value: String) = Instant.parse(value)

    private class FakeCalendarProviderReader(
        private val sources: List<DeviceCalendarSource> = emptyList(),
        private val events: List<CalendarExclusionEvent> = emptyList(),
    ) : CalendarProviderReader {
        val requestedYears = mutableListOf<Int>()

        override suspend fun listCalendars(): List<DeviceCalendarSource> = sources

        override suspend fun listEvents(calendarId: String, year: Int): List<CalendarExclusionEvent> {
            requestedYears += year
            return events
        }
    }
}
