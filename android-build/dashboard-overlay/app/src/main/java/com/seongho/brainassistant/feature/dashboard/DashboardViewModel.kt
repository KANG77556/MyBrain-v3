package com.seongho.brainassistant.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seongho.brainassistant.core.model.CalendarItem
import com.seongho.brainassistant.core.model.NoteItem
import com.seongho.brainassistant.core.model.SyncState
import com.seongho.brainassistant.core.model.TaskItem
import com.seongho.brainassistant.data.BrainRepository
import com.seongho.brainassistant.feature.capture.CaptureViewModel
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardSummary(
    val eventCount: Int = 0,
    val taskCount: Int = 0,
    val overdueCount: Int = 0,
    val pendingSyncCount: Int = 0,
)

data class DashboardUiState(
    val summary: DashboardSummary = DashboardSummary(),
    val urgentTasks: List<TaskItem> = emptyList(),
    val events: List<CalendarItem> = emptyList(),
    val focusSuggestion: String = "우선순위가 높은 할 일을 선택해 집중해 보세요.",
    val notes: List<NoteItem> = emptyList(),
    val inputText: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,
    val canUndo: Boolean = false,
)

internal enum class DashboardSection {
    SUMMARY,
    URGENT_TASKS,
    EVENTS,
    FOCUS,
    RECENT_NOTES,
}

internal fun visibleDashboardSections(state: DashboardUiState): List<DashboardSection> = buildList {
    add(DashboardSection.SUMMARY)
    if (state.urgentTasks.isNotEmpty()) add(DashboardSection.URGENT_TASKS)
    add(DashboardSection.EVENTS)
    add(DashboardSection.FOCUS)
    if (state.notes.isNotEmpty()) add(DashboardSection.RECENT_NOTES)
}

sealed interface DashboardAction {
    data class ChangeInput(val value: String) : DashboardAction
    data object Submit : DashboardAction
    data object Voice : DashboardAction
    data object Undo : DashboardAction
    data object OpenTrash : DashboardAction
    data object OpenSettings : DashboardAction
}

class DashboardViewModel(
    repository: BrainRepository,
    private val captureViewModel: CaptureViewModel,
    clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    val state: StateFlow<DashboardUiState> = combine(
        repository.observeToday(clock.instant()),
        captureViewModel.state,
    ) { today, capture ->
        val activeTasks = today.tasks.filter { it.status.name == "ACTIVE" }
        val urgent = activeTasks.sortedWith(compareByDescending<TaskItem> { it.priority }.thenBy { it.dueAt }).take(5)
        DashboardUiState(
            summary = DashboardSummary(
                eventCount = today.events.size,
                taskCount = activeTasks.size,
                overdueCount = today.overdueCount,
                pendingSyncCount = today.events.count { it.syncState == SyncState.PENDING || it.syncState == SyncState.FAILED },
            ),
            urgentTasks = urgent,
            events = today.events.sortedBy(CalendarItem::startAt),
            focusSuggestion = focusSuggestion(urgent),
            notes = today.notes.take(5),
            inputText = capture.inputText,
            isSaving = capture.isSaving,
            message = capture.message,
            canUndo = capture.lastTransactionId != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun updateInput(value: String) = captureViewModel.updateInput(value)
    fun submit() = captureViewModel.submit()
    fun undo() = captureViewModel.undoLastSave()

    private fun focusSuggestion(tasks: List<TaskItem>): String {
        val task = tasks.firstOrNull() ?: return "오늘 할 일이 모두 정리되었습니다."
        val duration = task.estimatedMinutes?.let { Duration.ofMinutes(it.toLong()) }
        return if (duration != null) "${task.title} · 약 ${duration.toMinutes()}분 집중을 권장합니다."
        else "${task.title}부터 처리하는 것을 권장합니다."
    }
}
