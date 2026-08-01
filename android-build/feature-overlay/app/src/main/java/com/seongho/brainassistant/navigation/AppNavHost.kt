package com.seongho.brainassistant.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.seongho.brainassistant.BrainAssistantApp
import com.seongho.brainassistant.feature.auth.AuthScreen
import com.seongho.brainassistant.feature.auth.AuthViewModel
import com.seongho.brainassistant.feature.calendar.CalendarAction
import com.seongho.brainassistant.feature.calendar.CalendarScreen
import com.seongho.brainassistant.feature.calendar.CalendarViewModel
import com.seongho.brainassistant.feature.capture.CaptureViewModel
import com.seongho.brainassistant.feature.capture.rememberSpeechInputController
import com.seongho.brainassistant.feature.dashboard.DashboardAction
import com.seongho.brainassistant.feature.dashboard.DashboardScreen
import com.seongho.brainassistant.feature.dashboard.DashboardViewModel
import com.seongho.brainassistant.feature.review.ReviewAction
import com.seongho.brainassistant.feature.review.ReviewScreen
import com.seongho.brainassistant.feature.review.ReviewViewModel
import com.seongho.brainassistant.feature.settings.SettingsAction
import com.seongho.brainassistant.feature.settings.SettingsScreen
import com.seongho.brainassistant.feature.settings.SettingsViewModel
import com.seongho.brainassistant.feature.trash.TrashAction
import com.seongho.brainassistant.feature.trash.TrashScreen
import com.seongho.brainassistant.feature.trash.TrashViewModel
import kotlinx.coroutines.launch

private object Routes {
    const val AUTH = "auth"
    const val DASHBOARD = "dashboard"
    const val CALENDAR = "calendar"
    const val REVIEW = "review"
    const val TRASH = "trash"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(app: BrainAssistantApp) {
    val navController = rememberNavController()
    val container = app.container
    val scope = rememberCoroutineScope()

    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory { initializer { AuthViewModel(container.authGateway) } },
    )
    val captureViewModel: CaptureViewModel = viewModel(
        factory = viewModelFactory { initializer { CaptureViewModel(container.captureUseCase) } },
    )
    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DashboardViewModel(container.repository, captureViewModel) }
        },
    )
    val calendarViewModel: CalendarViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CalendarViewModel(container.repository) }
        },
    )
    val reviewViewModel: ReviewViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ReviewViewModel(container.repository, captureViewModel) }
        },
    )
    val trashViewModel: TrashViewModel = viewModel(
        factory = viewModelFactory { initializer { TrashViewModel(container.repository) } },
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsRepository = container.settingsRepository,
                    calendarGateway = container.calendarGateway,
                    repository = container.repository,
                )
            }
        },
    )

    val authState by authViewModel.state.collectAsState()
    val dashboardState by dashboardViewModel.state.collectAsState()
    val calendarState by calendarViewModel.state.collectAsState()
    val reviewState by reviewViewModel.state.collectAsState()
    val trashState by trashViewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()
    val captureState by captureViewModel.state.collectAsState()
    val speech = rememberSpeechInputController(
        onResult = captureViewModel::appendSpeechText,
        onError = captureViewModel::showMessage,
    )
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) speech.startListening()
        else captureViewModel.showMessage("음성 입력 권한이 필요합니다.")
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        scope.launch {
            container.settingsRepository.setNotificationsGranted(
                granted || Build.VERSION.SDK_INT < 33
            )
        }
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        scope.launch {
            val granted = grants[Manifest.permission.READ_CALENDAR] == true &&
                grants[Manifest.permission.WRITE_CALENDAR] == true
            container.settingsRepository.setDeviceCalendarGranted(granted)
        }
    }

    LaunchedEffect(captureState.pendingReview) {
        if (captureState.pendingReview != null) {
            reviewViewModel.refreshFromCapture()
            navController.navigate(Routes.REVIEW)
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (authState.signedInEmail == null) Routes.AUTH else Routes.DASHBOARD,
    ) {
        composable(Routes.AUTH) {
            AuthScreen(
                state = authState,
                onSignIn = authViewModel::signIn,
                onContinueOffline = {
                    authViewModel.continueOffline()
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onRequestCalendarPermission = {
                    calendarPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR,
                        ),
                    )
                },
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                state = dashboardState,
                onAction = { action ->
                    when (action) {
                        is DashboardAction.ChangeInput -> dashboardViewModel.updateInput(action.value)
                        DashboardAction.Submit -> dashboardViewModel.submit()
                        DashboardAction.Voice -> recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        DashboardAction.Undo -> dashboardViewModel.undo()
                        DashboardAction.OpenCalendar -> navController.navigate(Routes.CALENDAR)
                        DashboardAction.OpenTrash -> navController.navigate(Routes.TRASH)
                        DashboardAction.OpenSettings -> navController.navigate(Routes.SETTINGS)
                    }
                },
            )
        }
        composable(Routes.CALENDAR) {
            CalendarScreen(
                state = calendarState,
                onAction = { action ->
                    when (action) {
                        CalendarAction.Back -> navController.popBackStack()
                        else -> calendarViewModel.onAction(action)
                    }
                },
            )
        }
        composable(Routes.REVIEW) {
            ReviewScreen(
                state = reviewState,
                onAction = { action ->
                    when (action) {
                        is ReviewAction.ChangeTitle -> reviewViewModel.changeTitle(action.index, action.value)
                        is ReviewAction.ChangeStart -> reviewViewModel.changeStart(action.index, action.value)
                        is ReviewAction.ChangeEnd -> reviewViewModel.changeEnd(action.index, action.value)
                        is ReviewAction.ChangeDue -> reviewViewModel.changeDue(action.index, action.value)
                        ReviewAction.Cancel -> {
                            reviewViewModel.cancel()
                            navController.popBackStack()
                        }
                        ReviewAction.Confirm -> {
                            reviewViewModel.confirm()
                            navController.popBackStack()
                        }
                    }
                },
            )
        }
        composable(Routes.TRASH) {
            TrashScreen(
                state = trashState,
                onAction = { action ->
                    when (action) {
                        TrashAction.Back -> navController.popBackStack()
                        is TrashAction.Restore -> trashViewModel.restore(action.item)
                        is TrashAction.DeletePermanently -> trashViewModel.deletePermanently(action.item)
                    }
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                state = settingsState,
                onAction = { action ->
                    when (action) {
                        SettingsAction.Back -> navController.popBackStack()
                        SettingsAction.OpenTrash -> navController.navigate(Routes.TRASH)
                        is SettingsAction.ToggleSync -> settingsViewModel.toggleSync(action.enabled)
                    }
                },
            )
        }
    }
}
