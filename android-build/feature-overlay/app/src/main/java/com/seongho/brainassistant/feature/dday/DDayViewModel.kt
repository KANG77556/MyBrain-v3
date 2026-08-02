package com.seongho.brainassistant.feature.dday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seongho.brainassistant.core.model.DDayCategory
import com.seongho.brainassistant.core.model.DDayItem
import com.seongho.brainassistant.data.BrainRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val DEFAULT_REMINDERS = setOf(7, 3, 1, 0)

data class DDayEditorState(
    val editingId: String? = null,
    val inputId: String? = null,
    val transactionId: String? = null,
    val title: String = "",
    val targetDate: String = "",
    val category: DDayCategory = DDayCategory.CUSTOM,
    val importance: Int = 2,
    val isPinned: Boolean = false,
    val showElapsedDays: Boolean = true,
    val archiveAfterDays: Int = 7,
    val recurrenceRule: String = "",
    val reminderOffsets: Set<Int> = DEFAULT_REMINDERS,
)

data class DDayUiState(
    val today: LocalDate,
    val items: List<DDayItem> = emptyList(),
    val editorVisible: Boolean = false,
    val editor: DDayEditorState = DDayEditorState(),
    val isSaving: Boolean = false,
    val message: String? = null,
)

sealed interface DDayAction {
    data object Back : DDayAction
    data object Add : DDayAction
    data class Edit(val id: String) : DDayAction
    data class Delete(val id: String) : DDayAction
    data object CloseEditor : DDayAction
    data class ChangeTitle(val value: String) : DDayAction
    data class ChangeTargetDate(val value: String) : DDayAction
    data class ChangeCategory(val value: DDayCategory) : DDayAction
    data class ChangeImportance(val value: Int) : DDayAction
    data object TogglePinned : DDayAction
    data object ToggleShowElapsed : DDayAction
    data class ChangeArchiveAfterDays(val value: Int) : DDayAction
    data class ChangeRecurrenceRule(val value: String) : DDayAction
    data class ToggleReminder(val daysBefore: Int) : DDayAction
    data object Save : DDayAction
}

class DDayViewModel(
    private val repository: BrainRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val today = clock.instant().atZone(zoneId).toLocalDate()
    private val _state = MutableStateFlow(DDayUiState(today = today))
    val state: StateFlow<DDayUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(dispatcher) {
            repository.observeDDays(today).collect { items ->
                _state.update {
                    it.copy(
                        items = items.sortedWith(
                            compareByDescending<DDayItem> { item -> item.isPinned }
                                .thenBy { item -> item.targetDate }
                                .thenByDescending { item -> item.importance },
                        ),
                    )
                }
            }
        }
    }

    fun onAction(action: DDayAction) {
        when (action) {
            DDayAction.Back -> Unit
            DDayAction.Add -> _state.update {
                it.copy(
                    editorVisible = true,
                    editor = DDayEditorState(),
                    message = null,
                )
            }
            is DDayAction.Edit -> openEditor(action.id)
            is DDayAction.Delete -> delete(action.id)
            DDayAction.CloseEditor -> _state.update {
                it.copy(editorVisible = false, editor = DDayEditorState(), message = null)
            }
            is DDayAction.ChangeTitle -> updateEditor { it.copy(title = action.value) }
            is DDayAction.ChangeTargetDate -> updateEditor { it.copy(targetDate = action.value) }
            is DDayAction.ChangeCategory -> updateEditor { it.copy(category = action.value) }
            is DDayAction.ChangeImportance -> updateEditor {
                it.copy(importance = action.value.coerceIn(1, 3))
            }
            DDayAction.TogglePinned -> updateEditor { it.copy(isPinned = !it.isPinned) }
            DDayAction.ToggleShowElapsed -> updateEditor {
                it.copy(showElapsedDays = !it.showElapsedDays)
            }
            is DDayAction.ChangeArchiveAfterDays -> updateEditor {
                it.copy(archiveAfterDays = action.value.coerceIn(0, 365))
            }
            is DDayAction.ChangeRecurrenceRule -> updateEditor {
                it.copy(recurrenceRule = action.value)
            }
            is DDayAction.ToggleReminder -> updateEditor { editor ->
                val updated = editor.reminderOffsets.toMutableSet().apply {
                    if (!add(action.daysBefore)) remove(action.daysBefore)
                }
                editor.copy(reminderOffsets = updated)
            }
            DDayAction.Save -> save()
        }
    }

    private fun openEditor(id: String) {
        val item = state.value.items.firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(
                editorVisible = true,
                editor = DDayEditorState(
                    editingId = item.id,
                    inputId = item.inputId,
                    transactionId = item.transactionId,
                    title = item.title,
                    targetDate = item.targetDate.toString(),
                    category = item.category,
                    importance = item.importance,
                    isPinned = item.isPinned,
                    showElapsedDays = item.showElapsedDays,
                    archiveAfterDays = item.archiveAfterDays,
                    recurrenceRule = item.recurrenceRule.orEmpty(),
                    reminderOffsets = item.reminderOffsets,
                ),
                message = null,
            )
        }
    }

    private fun updateEditor(transform: (DDayEditorState) -> DDayEditorState) {
        _state.update { current ->
            current.copy(editor = transform(current.editor), message = null)
        }
    }

    private fun save() = viewModelScope.launch(dispatcher) {
        val editor = state.value.editor
        val title = editor.title.trim()
        if (title.isBlank()) {
            _state.update { it.copy(message = "제목을 입력해 주세요.") }
            return@launch
        }
        val date = runCatching { LocalDate.parse(editor.targetDate.trim()) }.getOrNull()
        if (date == null) {
            _state.update { it.copy(message = "날짜를 YYYY-MM-DD 형식으로 입력해 주세요.") }
            return@launch
        }

        val id = editor.editingId ?: UUID.randomUUID().toString()
        val existing = state.value.items.firstOrNull { it.id == id }
        val item = DDayItem(
            id = id,
            inputId = editor.inputId ?: "manual-$id",
            transactionId = editor.transactionId ?: "manual-${UUID.randomUUID()}",
            title = title,
            targetDate = date,
            category = editor.category,
            importance = editor.importance.coerceIn(1, 3),
            isPinned = editor.isPinned,
            showElapsedDays = editor.showElapsedDays,
            archiveAfterDays = editor.archiveAfterDays.coerceIn(0, 365),
            recurrenceRule = editor.recurrenceRule.trim().ifBlank { null },
            linkedTaskId = existing?.linkedTaskId,
            linkedCalendarId = existing?.linkedCalendarId,
            reminderOffsets = editor.reminderOffsets.ifEmpty { DEFAULT_REMINDERS },
            updatedAt = clock.instant(),
        )

        _state.update { it.copy(isSaving = true, message = null) }
        runCatching { repository.saveDDay(item) }
            .onSuccess {
                _state.update {
                    it.copy(
                        editorVisible = false,
                        editor = DDayEditorState(),
                        isSaving = false,
                        message = null,
                    )
                }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        isSaving = false,
                        message = error.message ?: "D-Day 저장에 실패했습니다.",
                    )
                }
            }
    }

    private fun delete(id: String) = viewModelScope.launch(dispatcher) {
        runCatching { repository.softDeleteDDay(id, clock.instant()) }
            .onFailure { error ->
                _state.update { it.copy(message = error.message ?: "D-Day 삭제에 실패했습니다.") }
            }
    }
}
