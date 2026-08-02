package com.seongho.brainassistant

import android.app.Application
import com.seongho.brainassistant.app.AppContainer
import com.seongho.brainassistant.core.notification.NotificationChannels
import com.seongho.brainassistant.core.sync.ExclusionRefreshWorker
import com.seongho.brainassistant.core.sync.TrashPurgeWorker

class BrainAssistantApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.create(this)
        container.notificationScheduler.scheduleMorningBriefing()
        TrashPurgeWorker.schedule(this)
        ExclusionRefreshWorker.schedule(this)
    }
}
