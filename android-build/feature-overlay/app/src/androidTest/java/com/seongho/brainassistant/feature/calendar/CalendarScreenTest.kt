package com.seongho.brainassistant.feature.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.seongho.brainassistant.core.model.CalendarItem
import com.seongho.brainassistant.core.model.SyncState
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class CalendarScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun monthlyCalendarShowsViewSwitchesAndSelectedDayAgenda() {
        val state = CalendarUiState(
            anchorDate = LocalDate.of(2026, 8, 1),
            selectedDate = LocalDate.of(2026, 8, 20),
            viewMode = CalendarViewMode.MONTH,
            events = listOf(
                CalendarItem(
                    id = "meeting",
                    inputId = "input-meeting",
                    transactionId = "transaction-meeting",
                    title = "교직원 회의",
                    startAt = Instant.parse("2026-08-20T00:00:00Z"),
                    endAt = Instant.parse("2026-08-20T01:00:00Z"),
                    syncState = SyncState.SYNCED,
                ),
            ),
        )

        composeRule.setContent {
            CalendarScreen(state = state, onAction = {})
        }

        composeRule.onNodeWithContentDescription("뒤로").assertIsDisplayed()
        composeRule.onNodeWithText("2026년 8월").assertIsDisplayed()
        composeRule.onNodeWithText("월간").assertIsDisplayed()
        composeRule.onNodeWithText("주간").assertIsDisplayed()
        composeRule.onNodeWithText("목록").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar-month-grid").assertIsDisplayed()
        composeRule.onNodeWithText("8월 20일 목요일").assertIsDisplayed()
        composeRule.onNodeWithText("교직원 회의").assertIsDisplayed()
    }

    @Test
    fun emptySelectedDayShowsClearMessage() {
        composeRule.setContent {
            CalendarScreen(
                state = CalendarUiState(
                    anchorDate = LocalDate.of(2026, 8, 1),
                    selectedDate = LocalDate.of(2026, 8, 20),
                ),
                onAction = {},
            )
        }

        composeRule.onNodeWithText("선택한 날짜에 일정이 없습니다.").assertIsDisplayed()
    }
}
