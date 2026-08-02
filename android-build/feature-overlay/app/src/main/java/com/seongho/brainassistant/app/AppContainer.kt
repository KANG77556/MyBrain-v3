package com.seongho.brainassistant.app

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.seongho.brainassistant.BuildConfig
import com.seongho.brainassistant.core.calendar.CalendarGateway
import com.seongho.brainassistant.core.calendar.ConflictChecker
import com.seongho.brainassistant.core.calendar.GoogleCalendarGateway
import com.seongho.brainassistant.core.database.AppDatabase
import com.seongho.brainassistant.core.database.MIGRATION_1_2
import com.seongho.brainassistant.core.database.MIGRATION_2_3
import com.seongho.brainassistant.core.logging.SafeLogger
import com.seongho.brainassistant.core.network.AiGatewayClient
import com.seongho.brainassistant.core.network.createAiApi
import com.seongho.brainassistant.core.notification.BriefingContent
import com.seongho.brainassistant.core.notification.NotificationScheduler
import com.seongho.brainassistant.core.parser.HybridInputAnalyzer
import com.seongho.brainassistant.core.parser.InputAnalyzer
import com.seongho.brainassistant.core.parser.RuleBasedInputAnalyzer
import com.seongho.brainassistant.core.settings.DataStoreUserSettingsRepository
import com.seongho.brainassistant.core.settings.SensitivePreviewMasker
import com.seongho.brainassistant.core.settings.UserSettingsRepository
import com.seongho.brainassistant.data.BrainRepository
import com.seongho.brainassistant.data.CaptureUseCase
import com.seongho.brainassistant.data.RoomBrainRepository
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(appContext, AppDatabase::class.java, "brain-assistant.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .build()

    val repository: BrainRepository = RoomBrainRepository(database)
    val settings: UserSettingsRepository = DataStoreUserSettingsRepository(appContext)
    val localAnalyzer: InputAnalyzer = RuleBasedInputAnalyzer()
    val remoteAnalyzer: InputAnalyzer = AiGatewayClient(createAiApi(BuildConfig.AI_GATEWAY_BASE_URL))
    val analyzer: InputAnalyzer = HybridInputAnalyzer(localAnalyzer, remoteAnalyzer)
    val conflictChecker = ConflictChecker()
    val captureUseCase = CaptureUseCase(repository, analyzer, conflictChecker, Clock.systemUTC())
    val calendarGateway: CalendarGateway = GoogleCalendarGateway(appContext)
    val notificationScheduler = NotificationScheduler(appContext, WorkManager.getInstance(appContext))
    val previewMasker = SensitivePreviewMasker()
    val logger = SafeLogger(enabled = BuildConfig.DEBUG)

    private val _briefingBadge = MutableStateFlow<BriefingContent?>(null)
    val briefingBadge: StateFlow<BriefingContent?> = _briefingBadge.asStateFlow()
    fun setBriefingBadge(content: BriefingContent) { _briefingBadge.value = content }
}
