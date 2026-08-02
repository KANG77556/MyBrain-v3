package com.seongho.brainassistant.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seongho.brainassistant.core.settings.UserSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val briefingHour: Int = 7,
    val briefingMinute: Int = 30,
    val quietStart: Int = 22,
    val quietEnd: Int = 7,
    val maskSensitivePreview: Boolean = true,
    val calendarStatusLabel: String = "확인 필요",
) {
    val briefingTime: String get() = "%02d:%02d".format(briefingHour, briefingMinute)
}

sealed interface SettingsAction {
    data class SetBriefingTime(val hour: Int, val minute: Int) : SettingsAction
    data class SetQuietHours(val start: Int, val end: Int) : SettingsAction
    data class SetMasking(val enabled: Boolean) : SettingsAction
    data object OpenExclusions : SettingsAction
    data object OpenTrash : SettingsAction
    data object Back : SettingsAction
}

class SettingsViewModel(
    private val repository: UserSettingsRepository,
    calendarConnected: Boolean,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = repository.settings.map {
        SettingsUiState(
            briefingHour = it.briefingHour,
            briefingMinute = it.briefingMinute,
            quietStart = it.quietStartHour,
            quietEnd = it.quietEndHour,
            maskSensitivePreview = it.maskSensitivePreview,
            calendarStatusLabel = if (calendarConnected) "연결됨" else "로컬 모드",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onAction(action: SettingsAction) {
        viewModelScope.launch {
            when (action) {
                is SettingsAction.SetBriefingTime -> repository.setBriefingTime(action.hour, action.minute)
                is SettingsAction.SetQuietHours -> repository.setQuietHours(action.start, action.end)
                is SettingsAction.SetMasking -> repository.setMaskSensitivePreview(action.enabled)
                SettingsAction.OpenExclusions, SettingsAction.OpenTrash, SettingsAction.Back -> Unit
            }
        }
    }
}
