package com.seongho.brainassistant.feature.calendar

import com.seongho.brainassistant.core.model.CalendarItem
import com.seongho.brainassistant.core.model.SyncState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarViewPolicyTest {
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun monthGridAlwaysContainsSixMondayFirstWeeks() {
        val dates = monthGridDates(LocalDate.of(2026, 8, 1))

        assertEquals(42, dates.size)
        assertEquals(LocalDate.of(2026, 7, 27), dates.first())
        assertEquals(LocalDate.of(2026, 9, 6), dates.last())
    }

    @Test
    fun movingAnchorUsesCurrentViewUnit() {
        val anchor = LocalDate.of(2026, 8, 15)

        assertEquals(
            LocalDate.of(2026, 9, 15),
            moveCalendarAnchor(anchor, CalendarViewMode.MONTH, 1),
        )
        assertEquals(
            LocalDate.of(2026, 8, 22),
            moveCalendarAnchor(anchor, CalendarViewMode.WEEK, 1),
        )
        assertEquals(
            LocalDate.of(2026, 8, 16),
            moveCalendarAnchor(anchor, CalendarViewMode.AGENDA, 1),
        )
    }

    @Test
    fun selectedDateAgendaIncludesOverlappingEventsOnly() {
        val selected = LocalDate.of(2026, 8, 20)
        val events = listOf(
            event(
                id = "morning",
                title = "교직원 회의",
                start = "2026-08-20T00:00:00Z",
                end = "2026-08-20T01:00:00Z",
            ),
            event(
                id = "overnight",
                title = "숙박 연수",
                start = "2026-08-19T14:00:00Z",
                end = "2026-08-20T03:00:00Z",
            ),
            event(
                id = "other",
                title = "다른 날 일정",
                start = "2026-08-21T00:00:00Z",
                end = "2026-08-21T01:00:00Z",
            ),
        )

        assertEquals(
            listOf("overnight", "morning"),
            eventsForDate(selected, events, zone).map { it.id },
        )
    }

    private fun event(
        id: String,
        title: String,
        start: String,
        end: String,
    ) = CalendarItem(
        id = id,
        inputId = "input-$id",
        transactionId = "transaction-$id",
        title = title,
        startAt = Instant.parse(start),
        endAt = Instant.parse(end),
        syncState = SyncState.SYNCED,
    )
}
