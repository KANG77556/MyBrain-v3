package com.seongho.brainassistant.feature.dashboard

import com.seongho.brainassistant.core.model.NoteItem
import com.seongho.brainassistant.core.model.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardLayoutPolicyTest {
    @Test
    fun emptyDashboardKeepsOnlyEssentialSections() {
        assertEquals(
            listOf(DashboardSection.SUMMARY, DashboardSection.EVENTS, DashboardSection.FOCUS),
            visibleDashboardSections(DashboardUiState()),
        )
    }

    @Test
    fun populatedDashboardAddsUrgentTasksAndRecentNotes() {
        val state = DashboardUiState(
            urgentTasks = listOf(
                TaskItem(
                    inputId = "input-1",
                    transactionId = "transaction-1",
                    title = "수행평가 채점",
                    dueAt = null,
                    priority = 5,
                    estimatedMinutes = 30,
                ),
            ),
            notes = listOf(
                NoteItem(
                    inputId = "input-2",
                    transactionId = "transaction-2",
                    title = "상담 메모",
                    body = "내용",
                ),
            ),
        )

        assertEquals(
            listOf(
                DashboardSection.SUMMARY,
                DashboardSection.URGENT_TASKS,
                DashboardSection.EVENTS,
                DashboardSection.FOCUS,
                DashboardSection.RECENT_NOTES,
            ),
            visibleDashboardSections(state),
        )
    }
}
