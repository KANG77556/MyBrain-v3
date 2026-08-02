package com.seongho.brainassistant.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seongho.brainassistant.core.auth.AuthSessionRepository
import com.seongho.brainassistant.core.auth.GoogleAuthGateway
import com.seongho.brainassistant.core.auth.PersistedAuthSession
import com.seongho.brainassistant.core.auth.SignedInUser
import com.seongho.brainassistant.core.settings.UserSettingsRepository
import com.seongho.brainassistant.core.settings.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val calendarConnected: Boolean = false,
    val localMode: Boolean = false,
    val user: SignedInUser? = null,
    val message: String? = null,
) {
    val canContinue: Boolean get() = localMode || isSignedIn
}

sealed interface AuthAction {
    data object SignIn : AuthAction
    data object UseLocalMode : AuthAction
    data object RetryCalendarPermission : AuthAction
    data object SignOut : AuthAction
}

class AuthViewModel(
    private val gateway: GoogleAuthGateway,
    private val sessionRepository: AuthSessionRepository,
    private val settingsRepository: UserSettingsRepository,
) : ViewModel() {
    constructor(gateway: GoogleAuthGateway) : this(gateway, NoOpAuthSessionRepository, DefaultUserSettingsRepository)
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(settingsRepository.settings, sessionRepository.session) { settings, session ->
                settings.keepSignedIn to session
            }.collect { (keepSignedIn, session) ->
                if (keepSignedIn && session != null && !_state.value.isSignedIn) {
                    _state.update {
                        it.copy(
                            isSignedIn = true,
                            localMode = false,
                            user = SignedInUser(session.email, session.displayName),
                            message = null,
                        )
                    }
                    authorizeCalendar()
                }
                if (!keepSignedIn && _state.value.isSignedIn) {
                    _state.update { it.copy(isSignedIn = false, calendarConnected = false, user = null) }
                }
            }
        }
    }

    fun signIn() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, message = null) }
        runCatching { gateway.signIn() }
            .onSuccess { user ->
                if (settingsRepository.settings.first().keepSignedIn) {
                    sessionRepository.save(PersistedAuthSession(user.email, user.displayName))
                }
                _state.update { it.copy(isLoading = false, isSignedIn = true, user = user, localMode = false) }
                authorizeCalendar()
            }
            .onFailure { error ->
                _state.update { it.copy(isLoading = false, message = error.message ?: "Google 로그인에 실패했습니다.") }
            }
    }

    fun authorizeCalendar() = viewModelScope.launch {
        runCatching { gateway.authorizeCalendar() }
            .onSuccess { result ->
                _state.update { it.copy(calendarConnected = result.connected, message = result.message) }
            }
            .onFailure {
                _state.update {
                    it.copy(
                        calendarConnected = false,
                        message = "Google Calendar 연결 없이 로컬 모드로 사용할 수 있습니다.",
                    )
                }
            }
    }

    fun onCalendarPermissionResult(granted: Boolean) {
        _state.update { it.copy(calendarConnected = granted, localMode = granted || it.localMode) }
    }

    fun useLocalMode() {
        _state.update { it.copy(localMode = true, isSignedIn = false, calendarConnected = false, message = "로컬 모드로 시작합니다.") }
    }

    fun signOut() = viewModelScope.launch {
        runCatching { gateway.signOut() }
        sessionRepository.clear()
        _state.value = AuthUiState(message = "로그아웃했습니다.")
    }
}

private object NoOpAuthSessionRepository : AuthSessionRepository {
    override val session = flowOf<PersistedAuthSession?>(null)
    override suspend fun save(session: PersistedAuthSession) = Unit
    override suspend fun clear() = Unit
}

private object DefaultUserSettingsRepository : UserSettingsRepository {
    override val settings = flowOf(UserSettings())
    override suspend fun setBriefingTime(hour: Int, minute: Int) = Unit
    override suspend fun setQuietHours(startHour: Int, endHour: Int) = Unit
    override suspend fun setMaskSensitivePreview(enabled: Boolean) = Unit
    override suspend fun setThemeMode(mode: com.seongho.brainassistant.core.settings.ThemeMode) = Unit
    override suspend fun setKeepSignedIn(enabled: Boolean) = Unit
}
