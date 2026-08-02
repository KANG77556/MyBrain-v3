package com.seongho.brainassistant.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seongho.brainassistant.core.model.ClarificationField
import com.seongho.brainassistant.core.model.ItemType
import com.seongho.brainassistant.core.model.ParsedItem
import com.seongho.brainassistant.data.BrainRepository
import com.seongho.brainassistant.data.CaptureResult
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewItemUi(
    val localId: String,
    val batchId: String?,
    val batchIndex: Int?,
    val type: ItemType,
    val title: String,
    val body: String,
    val startAt: String,
    val endAt: String,
    val dueAt: String,
    val targetDate: String,
    val priority: Int,
    val estimatedMinutes: Int?,
    val reminderOffsets: Set<Int>,
    val linkedLocalIds: Set<String>,
    val sourceStart: Int?,
    val sourceEnd: Int?,
) {
    fun toParsedItem(): ParsedItem = ParsedItem(
        localId = localId,
        batchId = batchId,
        batchIndex = batchIndex,
        type = type,
        title = title.trim(),
        body = body.trim(),
        startAt = startAt.parseInstantOrNull(),
        endAt = endAt.parseInstantOrNull(),
        dueAt = dueAt.parseInstantOrNull(),
        targetDate = targetDate.parseLocalDateOrNull(),
        priority = priority.coerceIn(1, 3),
        estimatedMinutes = estimatedMinutes,
        reminderOffsets = reminderOffsets,
        linkedLocalIds = linkedLocalIds,
        sourceStart = sourceStart,
        sourceEnd = sourceEnd,
    )

    companion object {
        fun from(item: ParsedItem) = ReviewItemUi(
            localId = item.localId,
            batchId = item.batchId,
            batchIndex = item.batchIndex,
            type = item.type,
            title = item.title,
            body = item.body,
            startAt = item.startAt?.toString().orEmpty(),
            endAt = item.endAt?.toString().orEmpty(),
            dueAt = item.dueAt?.toString().orEmpty(),
            targetDate = item.targetDate?.toString().orEmpty(),
            priority = item.priority,
            estimatedMinutes = item.estimatedMinutes,
            reminderOffsets = item.reminderOffsets,
            linkedLocalIds = item.linkedLocalIds,
            sourceStart = item.sourceStart,
            sourceEnd = item.sourceEnd,
        )
    }
}

data class ReviewUiState(
    val inputId: String = "",
    val originalText: String = "",
    val items: List<ReviewItemUi> = emptyList(),
    val clarificationFields: Set<ClarificationField> = emptySet(),
    val conflictMessage: String? = null,
    val isSaving: Boolean = false,
    val message: String? = null,
) {
    val isValid: Boolean get() = items.isNotEmpty() && items.all(ReviewItemUi::isValid)
}

private fun ReviewItemUi.isValid(): Boolean =
    title.isNotBlank() && when (type) {
        ItemType.EVENT -> startAt.parseInstantOrNull() != null
        ItemType.D_DAY -> targetDate.parseLocalDateOrNull() != null || dueAt.parseInstantOrNull() != null
        else -> true
    }

sealed interface ReviewAction {
    data class ChangeType(val localId: String, val value: ItemType) : ReviewAction
    data class ChangeTitle(val localId: String, val value: String) : ReviewAction
    data class ChangeStartAt(val localId: String, val value: String) : ReviewAction
    data class ChangeEndAt(val localId: String, val value: String) : ReviewAction
    data class ChangeDueAt(val localId: String, val value: String) : ReviewAction
    data class ChangeTargetDate(val localId: String, val value: String) : ReviewAction
    data class ChangePriority(val localId: String, val value: Int) : ReviewAction
    data class RemoveItem(val localId: String) : ReviewAction
    data object Save : ReviewAction
    data object Cancel : ReviewAction
}

sealed interface ReviewEvent {
    data class Saved(val items: List<ParsedItem>) : ReviewEvent
    data object Cancelled : ReviewEvent
}

class ReviewViewModel(
    private val repository: BrainRepository,
    review: CaptureResult.NeedsReview,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ReviewUiState(
            inputId = review.inputId,
            originalText = review.originalText,
            items = review.items.map(ReviewItemUi::from),
            clarificationFields = review.clarificationFields,
            conflictMessage = review.message.takeUnless { it == "확인이 필요한 항목이 있습니다." },
        )
    )
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ReviewEvent>()
    val events: SharedFlow<ReviewEvent> = _events.asSharedFlow()

    fun onAction(action: ReviewAction) {
        when (action) {
            is ReviewAction.ChangeType -> updateItem(action.localId) { it.copy(type = action.value) }
            is ReviewAction.ChangeTitle -> updateItem(action.localId) { it.copy(title = action.value) }
            is ReviewAction.ChangeStartAt -> updateItem(action.localId) { it.copy(startAt = action.value) }
            is ReviewAction.ChangeEndAt -> updateItem(action.localId) { it.copy(endAt = action.value) }
            is ReviewAction.ChangeDueAt -> updateItem(action.localId) { it.copy(dueAt = action.value) }
            is ReviewAction.ChangeTargetDate -> updateItem(action.localId) { it.copy(targetDate = action.value) }
            is ReviewAction.ChangePriority -> updateItem(action.localId) { it.copy(priority = action.value.coerceIn(1, 3)) }
            is ReviewAction.RemoveItem -> _state.update { current ->
                current.copy(items = current.items.filterNot { it.localId == action.localId }, message = null)
            }
            ReviewAction.Save -> save()
            ReviewAction.Cancel -> viewModelScope.launch { _events.emit(ReviewEvent.Cancelled) }
        }
    }

    private fun updateItem(id: String, transform: (ReviewItemUi) -> ReviewItemUi) {
        _state.update { current ->
            current.copy(
                items = current.items.map { if (it.localId == id) transform(it) else it },
                message = null,
            )
        }
    }

    private fun save() = viewModelScope.launch {
        val current = state.value
        val parsed = current.items.map(ReviewItemUi::toParsedItem)
        if (!current.isValid) {
            _state.update { it.copy(message = "제목과 날짜·시간을 확인해 주세요.") }
            return@launch
        }
        _state.update { it.copy(isSaving = true, message = null) }
        runCatching { repository.confirmReviewedItems(current.inputId, parsed) }
            .onSuccess {
                _state.update { state -> state.copy(isSaving = false) }
                _events.emit(ReviewEvent.Saved(parsed))
            }
            .onFailure { error ->
                _state.update { it.copy(isSaving = false, message = error.message ?: "저장에 실패했습니다.") }
            }
    }
}

private fun String.parseInstantOrNull(): Instant? = trim().takeIf(String::isNotBlank)?.let {
    runCatching { Instant.parse(it) }.getOrNull()
}

private fun String.parseLocalDateOrNull(): LocalDate? = trim().takeIf(String::isNotBlank)?.let {
    runCatching { LocalDate.parse(it) }.getOrNull()
}
