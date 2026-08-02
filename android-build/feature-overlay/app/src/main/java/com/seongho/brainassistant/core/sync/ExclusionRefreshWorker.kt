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
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ExclusionRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as BrainAssistantApp
        val year = LocalDate.now(ZoneId.of("Asia/Seoul")).year
        return try {
            val summary = app.container.exclusionRefreshEngine.refresh(setOf(year, year + 1))
            if (summary.failed == 0) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK = "exclusion_calendar_refresh"
        private const val IMMEDIATE_WORK = "exclusion_calendar_refresh_now"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ExclusionRefreshWorker>(1, TimeUnit.DAYS).build(),
            )
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ExclusionRefreshWorker>().build(),
            )
        }
    }
}
