package com.seongho.brainassistant.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seongho.brainassistant.core.model.ParsedItem
import com.seongho.brainassistant.core.model.RecurrenceDraft
import com.seongho.brainassistant.data.CaptureResult
import com.seongho.brainassistant.data.CaptureUseCase
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CaptureUiState(
    val inputText: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,
    val pendingReview: CaptureResult.NeedsReview? = null,
    val lastTransactionId: String? = null,
)

class CaptureViewModel(
    private val captureUseCase: CaptureUseCase,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
) : ViewModel() {
    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()
    fun updateInput(text: String) = _state.update { it.copy(inputText = text, message = null) }
    fun applySpeechText(text: String) = updateInput(text)
    fun submit() {
        val text = state.value.inputText
        if (text.isBlank() || state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, message = null) }
            when (val result = captureUseCase.capture(text, clock.instant().atZone(zoneId))) {
                is CaptureResult.AutoSaved -> _state.update { it.copy(inputText = "", isSaving = false, message = "저장했습니다. 실행 취소할 수 있습니다.", lastTransactionId = result.transactionId, pendingReview = null) }
                is CaptureResult.NeedsReview -> _state.update { it.copy(isSaving = false, pendingReview = result, message = result.message) }
                is CaptureResult.Failed -> _state.update { it.copy(isSaving = false, message = result.message) }
            }
        }
    }
    fun confirmReview(items: List<ParsedItem>) = confirm(items, emptyList())
    fun confirmReviewBatch(items: List<ParsedItem>, recurrences: List<RecurrenceDraft>) = confirm(items, recurrences)
    private fun confirm(items: List<ParsedItem>, recurrences: List<RecurrenceDraft>) {
        val review = state.value.pendingReview ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            runCatching {
                if (recurrences.isEmpty()) captureUseCase.confirm(review.inputId, items)
                else captureUseCase.confirmBatch(review.inputId, items, recurrences).ordinaryItems
            }.onSuccess { persisted ->
                _state.update { it.copy(inputText = "", isSaving = false, pendingReview = null, message = "확인한 내용을 저장했습니다.", lastTransactionId = persisted.transactionId) }
            }.onFailure { error -> _state.update { it.copy(isSaving = false, message = error.message ?: "저장에 실패했습니다.") } }
        }
    }
    fun cancelReview() = _state.update { it.copy(pendingReview = null, message = "원문은 보존하고 자동 분류를 취소했습니다.") }
    fun undoLastSave() { val id = state.value.lastTransactionId ?: return; viewModelScope.launch { captureUseCase.undo(id); _state.update { it.copy(lastTransactionId = null, message = "방금 저장한 내용을 휴지통으로 옮겼습니다.") } } }
    fun clearMessage() = _state.update { it.copy(message = null) }
}
