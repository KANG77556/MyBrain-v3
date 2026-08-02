package com.seongho.brainassistant.navigation

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.seongho.brainassistant.app.AppContainer
import com.seongho.brainassistant.core.auth.CredentialManagerGoogleAuthGateway
import com.seongho.brainassistant.core.calendar.ExclusionCalendarClassifier
import com.seongho.brainassistant.core.calendar.SchoolExclusionCategory
import com.seongho.brainassistant.core.model.ExclusionKind
import com.seongho.brainassistant.core.sync.ExclusionCandidateKey
import com.seongho.brainassistant.core.sync.ExclusionRefreshWorker
import com.seongho.brainassistant.feature.auth.AuthAction
import com.seongho.brainassistant.feature.auth.AuthScreen
import com.seongho.brainassistant.feature.auth.AuthViewModel
import com.seongho.brainassistant.feature.calendar.CalendarAction
import com.seongho.brainassistant.feature.calendar.CalendarScreen
import com.seongho.brainassistant.feature.calendar.CalendarViewModel
import com.seongho.brainassistant.feature.capture.AndroidSpeechRecognizerAdapter
import com.seongho.brainassistant.feature.capture.CaptureViewModel
import com.seongho.brainassistant.feature.capture.SpeechInputController
import com.seongho.brainassistant.feature.dashboard.DashboardAction
import com.seongho.brainassistant.feature.dashboard.DashboardScreen
import com.seongho.brainassistant.feature.dashboard.DashboardViewModel
import com.seongho.brainassistant.feature.review.ReviewAction
import com.seongho.brainassistant.feature.review.ReviewItemUi
import com.seongho.brainassistant.feature.review.ReviewScreen
import com.seongho.brainassistant.feature.review.ReviewUiState
import com.seongho.brainassistant.feature.settings.SettingsAction
import com.seongho.brainassistant.feature.settings.ExclusionCalendarAction
import com.seongho.brainassistant.feature.settings.ExclusionCalendarScreen
import com.seongho.brainassistant.feature.settings.ExclusionCalendarUiState
import com.seongho.brainassistant.feature.settings.ExclusionCandidateUi
import com.seongho.brainassistant.feature.settings.ExclusionSourceUi
import com.seongho.brainassistant.feature.settings.SettingsScreen
import com.seongho.brainassistant.feature.settings.SettingsViewModel
import com.seongho.brainassistant.feature.trash.TrashAction
import com.seongho.brainassistant.feature.trash.TrashScreen
import com.seongho.brainassistant.feature.trash.TrashViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

private object Routes {
    const val AUTH = "auth"
    const val DASHBOARD = "dashboard"
    const val CALENDAR = "calendar"
    const val REVIEW = "review/{inputId}"
    const val TRASH = "trash"
    const val SETTINGS = "settings"
    const val EXCLUSIONS = "settings/exclusions"
}

@Composable
fun AppNavHost(
    container: AppContainer,
    activity: Activity,
) {
    val navController = rememberNavController()
    val captureViewModel: CaptureViewModel = viewModel(factory = factory { CaptureViewModel(container.captureUseCase) })
    val dashboardViewModel: DashboardViewModel = viewModel(factory = factory { DashboardViewModel(container.repository, captureViewModel) })
    val calendarViewModel: CalendarViewModel = viewModel(factory = factory { CalendarViewModel(container.repository) })
    val authViewModel: AuthViewModel = viewModel(factory = factory { AuthViewModel(CredentialManagerGoogleAuthGateway(activity)) })
    val trashViewModel: TrashViewModel = viewModel(factory = factory { TrashViewModel(container.repository) })
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory { SettingsViewModel(container.settings, calendarConnected = false) })

    val authState by authViewModel.state.collectAsState()
    val captureState by captureViewModel.state.collectAsState()
    val dashboardState by dashboardViewModel.state.collectAsState()
    val calendarState by calendarViewModel.state.collectAsState()
    val trashState by trashViewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()

    val speechController = remember { SpeechInputController(AndroidSpeechRecognizerAdapter(activity)) }
    val speechState by speechController.state.collectAsState()
    DisposableEffect(Unit) { onDispose { speechController.destroy() } }
    LaunchedEffect(speechState.text) {
        if (speechState.text.isNotBlank()) captureViewModel.applySpeechText(speechState.text)
    }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speechController.start() else speechController.onPermissionDenied()
    }
    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        authViewModel.onCalendarPermissionResult(result.values.all { it })
    }

    LaunchedEffect(authState.canContinue) {
        if (authState.canContinue && navController.currentDestination?.route == Routes.AUTH) {
            navController.navigate(Routes.DASHBOARD) { popUpTo(Routes.AUTH) { inclusive = true } }
        }
    }
    LaunchedEffect(captureState.pendingReview?.inputId) {
        captureState.pendingReview?.let { review ->
            val route = "review/${review.inputId}"
            if (navController.currentDestination?.route != Routes.REVIEW) navController.navigate(route)
        }
    }

    NavHost(navController = navController, startDestination = Routes.AUTH) {
        composable(Routes.AUTH) {
            AuthScreen(authState) { action ->
                when (action) {
                    AuthAction.SignIn -> authViewModel.signIn()
                    AuthAction.UseLocalMode -> authViewModel.useLocalMode()
                    AuthAction.RetryCalendarPermission -> calendarPermission.launch(
                        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                    )
                    AuthAction.SignOut -> authViewModel.signOut()
                }
            }
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(dashboardState) { action ->
                when (action) {
                    is DashboardAction.ChangeInput -> dashboardViewModel.updateInput(action.value)
                    DashboardAction.Submit -> dashboardViewModel.submit()
                    DashboardAction.Voice -> {
                        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            speechController.start()
                        } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    DashboardAction.Undo -> dashboardViewModel.undo()
                    DashboardAction.OpenCalendar -> navController.navigate(Routes.CALENDAR)
                    DashboardAction.OpenTrash -> navController.navigate(Routes.TRASH)
                    DashboardAction.OpenSettings -> navController.navigate(Routes.SETTINGS)
                }
            }
        }
        composable(Routes.CALENDAR) {
            CalendarScreen(calendarState) { action ->
                if (action == CalendarAction.Back) {
                    navController.popBackStack()
                } else {
                    calendarViewModel.onAction(action)
                }
            }
        }
        composable(
            route = Routes.REVIEW,
            arguments = listOf(navArgument("inputId") { type = NavType.StringType }),
        ) {
            val pending = captureState.pendingReview
            if (pending == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                var state by remember(pending.inputId) {
                    mutableStateOf(
                        ReviewUiState(
                            inputId = pending.inputId,
                            originalText = pending.originalText,
                            items = pending.items.map(ReviewItemUi::from),
                            recurrences = pending.recurrences,
                            clarificationFields = pending.clarificationFields,
                            conflictMessage = pending.message,
                        )
                    )
                }
                ReviewScreen(state) { action ->
                    when (action) {
                        is ReviewAction.ChangeType -> state = state.copy(items = state.items.map { if (it.localId == action.localId) it.copy(type = action.value) else it })
                        is ReviewAction.ChangeTitle -> state = state.copy(items = state.items.map { if (it.localId == action.localId) it.copy(title = action.value) else it })
                        is ReviewAction.ChangeStartAt -> state = state.copy(items = state.items.map { if (it.localId == action.localId) it.copy(startAt = action.value) else it })
                        is ReviewAction.ChangeEndAt -> state = state.copy(items = state.items.map { if (it.localId == action.localId) it.copy(endAt = action.value) else it })
                        is ReviewAction.ChangeDueAt -> state = state.copy(items = state.items.map { if (it.localId == action.localId) it.copy(dueAt = action.value) else it })
                        is ReviewAction.ChangePriority -> state = state.copy(items = state.items.map { if (it.localId == action.localId) it.copy(priority = action.value.coerceIn(1, 3)) else it })
                        is ReviewAction.RemoveItem -> state = state.copy(items = state.items.filterNot { it.localId == action.localId })
                        ReviewAction.Save -> {
                            val parsed = state.items.map(ReviewItemUi::toParsedItem)
                            val invalid = parsed.any { it.title.isBlank() || (it.type.name == "EVENT" && it.startAt == null) }
                            if (invalid) state = state.copy(message = "제목과 일정 시간을 확인해 주세요.")
                            else {
                                captureViewModel.confirmReviewBatch(parsed, pending.recurrences)
                                navController.popBackStack()
                            }
                        }
                        ReviewAction.Cancel -> {
                            captureViewModel.cancelReview()
                            navController.popBackStack()
                        }
                    }
                }
            }
        }
        composable(Routes.TRASH) {
            TrashScreen(trashState.items, trashState.pendingPermanentDelete) { action ->
                if (action == TrashAction.Back) navController.popBackStack() else trashViewModel.onAction(action)
            }
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settingsState.copy(
                    calendarStatusLabel = if (authState.calendarConnected) "연결됨" else "로컬 모드",
                )
            ) { action ->
                when (action) {
                    SettingsAction.Back -> navController.popBackStack()
                    SettingsAction.OpenTrash -> navController.navigate(Routes.TRASH)
                    SettingsAction.OpenExclusions -> navController.navigate(Routes.EXCLUSIONS)
                    else -> settingsViewModel.onAction(action)
                }
            }
        }
        composable(Routes.EXCLUSIONS) {
            val scope = rememberCoroutineScope()
            val currentYear = remember { LocalDate.now(ZoneId.of("Asia/Seoul")).year }
            var state by remember { mutableStateOf(ExclusionCalendarUiState()) }
            LaunchedEffect(Unit) {
                val sources = container.exclusionSettingsStore.loadSources()
                val classifier = ExclusionCalendarClassifier()
                state = ExclusionCalendarUiState(
                    sources = sources.map { source ->
                        ExclusionSourceUi(
                            id = source.id,
                            title = source.displayName,
                            kindLabel = if (source.kind == ExclusionKind.KOREAN_PUBLIC_HOLIDAY) "공휴일" else "학교",
                            enabled = source.enabled,
                        )
                    },
                    candidates = container.exclusionSettingsStore
                        .loadSchoolCandidates(currentYear, currentYear + 1)
                        .map { candidate ->
                            ExclusionCandidateUi(
                                sourceId = candidate.sourceId,
                                remoteEventId = candidate.remoteEventId,
                                title = candidate.title,
                                date = candidate.date,
                                categoryLabel = when (classifier.classifySchoolEvent(candidate.title)) {
                                    SchoolExclusionCategory.VACATION -> "방학"
                                    SchoolExclusionCategory.DISCRETIONARY_CLOSURE -> "재량휴업"
                                    SchoolExclusionCategory.SCHOOL_EVENT -> "학교행사"
                                    null -> "학교 일정"
                                },
                                approved = candidate.approved,
                            )
                        },
                )
            }
            ExclusionCalendarScreen(state) { action ->
                when (action) {
                    is ExclusionCalendarAction.ToggleSource -> state = state.copy(
                        sources = state.sources.map { if (it.id == action.sourceId) it.copy(enabled = action.enabled) else it },
                    )
                    is ExclusionCalendarAction.ToggleCandidate -> state = state.copy(
                        candidates = state.candidates.map {
                            if (it.sourceId == action.sourceId && it.remoteEventId == action.remoteEventId && it.date == action.date) {
                                it.copy(approved = action.approved)
                            } else it
                        },
                    )
                    ExclusionCalendarAction.Save -> scope.launch {
                        state = state.copy(isSaving = true)
                        container.exclusionSettingsStore.saveSelections(
                            enabledSourceIds = state.sources.filter { it.enabled }.mapTo(mutableSetOf()) { it.id },
                            approvedCandidates = state.candidates.filter { it.approved }.mapTo(mutableSetOf()) {
                                ExclusionCandidateKey(it.sourceId, it.remoteEventId, it.date)
                            },
                            startYear = currentYear,
                            endYear = currentYear + 1,
                        )
                        ExclusionRefreshWorker.refreshNow(activity)
                        navController.popBackStack()
                    }
                    ExclusionCalendarAction.Back -> navController.popBackStack()
                }
            }
        }
    }
}

private fun <T : ViewModel> factory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
