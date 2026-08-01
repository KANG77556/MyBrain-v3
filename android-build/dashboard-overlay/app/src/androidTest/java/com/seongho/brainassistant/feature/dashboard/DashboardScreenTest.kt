package com.seongho.brainassistant.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seongho.brainassistant.core.model.CalendarItem
import com.seongho.brainassistant.core.model.DDayCategory
import com.seongho.brainassistant.core.model.DDayItem
import com.seongho.brainassistant.core.model.NoteItem
import com.seongho.brainassistant.core.model.SyncState
import com.seongho.brainassistant.core.model.TaskItem
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DashboardScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun emptyDashboardUsesCompactMobileLayout() {
        composeRule.setContent { DashboardScreen(state = DashboardUiState(), onAction = {}) }

        composeRule.onNodeWithText("오늘의 브레인 비서").assertIsDisplayed()
        composeRule.onNodeWithTag("summary-metric-events").assertIsDisplayed()
        composeRule.onNodeWithTag("summary-metric-tasks").assertIsDisplayed()
        composeRule.onNodeWithTag("summary-metric-urgent").assertIsDisplayed()
        composeRule.onNodeWithTag("summary-metric-sync").assertIsDisplayed()
        composeRule.onNodeWithText("오늘 일정").assertIsDisplayed()
        composeRule.onNodeWithText("오늘 예정된 일정이 없습니다.").assertIsDisplayed()
        composeRule.onNodeWithText("추천 집중 작업").assertIsDisplayed()
        composeRule.onNodeWithText("대표 D-Day").assertDoesNotExist()
        composeRule.onNodeWithText("긴급 할 일").assertDoesNotExist()
        composeRule.onNodeWithText("최근 메모").assertDoesNotExist()
        composeRule.onNodeWithText("표시할 항목이 없습니다.").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("휴지통").assertDoesNotExist()
        composeRule.onNodeWithTag("quick-capture-dock").assertIsDisplayed()
        composeRule.onNodeWithText("무엇을 기록할까요?").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("음성 입력").assertIsDisplayed()
    }

    @Test
    fun representativeDDayCardShowsTitleAndDateDistance() {
        val state = DashboardUiState(
            displayDate = LocalDate.of(2026, 8, 1),
            representativeDDay = DDayItem(
                id = "dday-1",
                inputId = "input-dday",
                transactionId = "transaction-dday",
                title = "교육청 보고서 제출",
                targetDate = LocalDate.of(2026, 8, 20),
                category = DDayCategory.DEADLINE,
            ),
        )

        composeRule.setContent { DashboardScreen(state = state, onAction = {}) }

        composeRule.onNodeWithText("대표 D-Day").assertIsDisplayed()
        composeRule.onNodeWithText("교육청 보고서 제출").assertIsDisplayed()
        composeRule.onNodeWithText("D-19").assertIsDisplayed()
        composeRule.onNodeWithText("8월 20일").assertIsDisplayed()
    }

    @Test
    fun populatedDashboardShowsUrgentTasksEventsAndRecentNotes() {
        val state = DashboardUiState(
            summary = DashboardSummary(eventCount = 1, taskCount = 1, overdueCount = 0, pendingSyncCount = 1),
            urgentTasks = listOf(
                TaskItem(
                    inputId = "input-1",
                    transactionId = "transaction-1",
                    title = "수행평가 채점",
                    dueAt = Instant.parse("2026-08-01T02:00:00Z"),
                    priority = 5,
                    estimatedMinutes = 30,
                ),
            ),
            events = listOf(
                CalendarItem(
                    inputId = "input-2",
                    transactionId = "transaction-2",
                    title = "교직원 회의",
                    startAt = Instant.parse("2026-08-01T00:00:00Z"),
                    endAt = Instant.parse("2026-08-01T01:00:00Z"),
                    syncState = SyncState.PENDING,
                ),
            ),
            notes = listOf(
                NoteItem(
                    inputId = "input-3",
                    transactionId = "transaction-3",
                    title = "학부모 상담 메모",
                    body = "상담 내용",
                ),
            ),
        )

        composeRule.setContent { DashboardScreen(state = state, onAction = {}) }

        composeRule.onNodeWithText("긴급 할 일").assertIsDisplayed()
        composeRule.onNodeWithText("수행평가 채점", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("교직원 회의", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("최근 메모").assertIsDisplayed()
        composeRule.onNodeWithText("학부모 상담 메모").assertIsDisplayed()
    }

    @Test
    fun settingsActionIsAvailableFromCompactAppBar() {
        var lastAction: DashboardAction? = null
        composeRule.setContent {
            DashboardScreen(state = DashboardUiState(), onAction = { lastAction = it })
        }

        composeRule.onNodeWithContentDescription("설정").performClick()

        assertEquals(DashboardAction.OpenSettings, lastAction)
    }
}
