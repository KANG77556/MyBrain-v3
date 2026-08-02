package com.seongho.brainassistant.feature.dashboard

import com.seongho.brainassistant.core.model.DDayCategory
import com.seongho.brainassistant.core.model.DDayItem
import com.seongho.brainassistant.core.model.NoteItem
import com.seongho.brainassistant.core.model.TaskItem
import java.time.LocalDate
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
    fun representativeDDayAppearsImmediatelyAfterSummary() {
        val state = DashboardUiState(
            representativeDDay = dDay(
                id = "exam",
                title = "자격증 시험",
                targetDate = LocalDate.of(2026, 8, 10),
            ),
        )

        assertEquals(
            listOf(
                DashboardSection.SUMMARY,
                DashboardSection.D_DAY,
                DashboardSection.EVENTS,
                DashboardSection.FOCUS,
            ),
            visibleDashboardSections(state),
        )
    }

    @Test
    fun representativePolicyPrefersPinnedThenUpcomingClosestAndImportance() {
        val today = LocalDate.of(2026, 8, 1)
        val items = listOf(
            dDay("past", "지난 행사", today.minusDays(1), importance = 3),
            dDay("near", "가까운 마감", today.plusDays(2), importance = 1),
            dDay("important", "중요한 마감", today.plusDays(2), importance = 3),
            dDay("pinned", "고정 기념일", today.plusDays(30), importance = 1, isPinned = true),
        )

        assertEquals("pinned", selectRepresentativeDDay(today, items)?.id)
        assertEquals(
            "important",
            selectRepresentativeDDay(today, items.filterNot { it.isPinned })?.id,
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

    private fun dDay(
        id: String,
        title: String,
        targetDate: LocalDate,
        importance: Int = 2,
        isPinned: Boolean = false,
    ) = DDayItem(
        id = id,
        inputId = "input-$id",
        transactionId = "transaction-$id",
        title = title,
        targetDate = targetDate,
        category = DDayCategory.EVENT,
        importance = importance,
        isPinned = isPinned,
    )
}
