package com.seongho.brainassistant.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.seongho.brainassistant.BrainAssistantApp
import java.util.concurrent.TimeUnit

class RecurrenceSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val engine = (applicationContext as BrainAssistantApp).container.recurrenceSyncEngine
        return when (engine.processNext()) {
            RecurrenceSyncResult.EMPTY, RecurrenceSyncResult.SYNCED -> Result.success()
            RecurrenceSyncResult.RETRY -> Result.retry()
        }
    }

    companion object {
        private const val PERIODIC = "recurrence_calendar_sync"
        private const val IMMEDIATE = "recurrence_calendar_sync_now"
        fun schedule(context: Context) = WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<RecurrenceSyncWorker>(15, TimeUnit.MINUTES).build(),
        )
        fun syncNow(context: Context) = WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<RecurrenceSyncWorker>().build(),
        )
    }
}
