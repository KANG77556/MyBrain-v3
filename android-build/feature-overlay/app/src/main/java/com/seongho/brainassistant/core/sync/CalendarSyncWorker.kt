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
import com.seongho.brainassistant.core.calendar.CalendarAuthorizationRequiredException
import com.seongho.brainassistant.core.calendar.CalendarGateway
import com.seongho.brainassistant.core.model.OutboxOperation
import com.seongho.brainassistant.data.BrainRepository
import java.io.IOException
import java.util.concurrent.TimeUnit

class CalendarSyncEngine(private val repository: BrainRepository, private val gateway: CalendarGateway) {
    suspend fun sync(): SyncSummary {
        var success = 0; var failed = 0; var authRequired = false
        val operations = repository.pendingOutbox()
        for (operation in operations) {
            val local = repository.getCalendar(operation.localCalendarId)
            try {
                when (operation.operation) {
                    OutboxOperation.CREATE -> {
                        if (local == null || local.deletedAt != null) { repository.markOutboxDone(operation.id); continue }
                        repository.markCalendarSynced(local.id, gateway.insert(local.googleCalendarId, local, operation.id))
                    }
                    OutboxOperation.UPDATE -> {
                        if (local == null || local.deletedAt != null) { repository.markOutboxDone(operation.id); continue }
                        val remote = local.googleEventId?.let { gateway.update(local.googleCalendarId, it, local, local.externalEtag) }
                            ?: gateway.insert(local.googleCalendarId, local, operation.id)
                        repository.markCalendarSynced(local.id, remote)
                    }
                    OutboxOperation.DELETE -> if (local?.googleEventId != null) gateway.delete(local.googleCalendarId, local.googleEventId)
                }
                repository.markOutboxDone(operation.id); success++
            } catch (_: CalendarAuthorizationRequiredException) { repository.markOutboxFailed(operation.id, "AUTH_REQUIRED"); authRequired = true; failed++; break
            } catch (_: SecurityException) { repository.markOutboxFailed(operation.id, "CALENDAR_PERMISSION"); authRequired = true; failed++; break
            } catch (_: IOException) { repository.markOutboxFailed(operation.id, "NETWORK"); failed++
            } catch (error: Exception) { repository.markOutboxFailed(operation.id, error.javaClass.simpleName); failed++ }
        }
        return SyncSummary(operations.size, success, failed, authRequired)
    }
}

class CalendarSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as BrainAssistantApp
        val summary = CalendarSyncEngine(app.container.repository, app.container.calendarGateway).sync()
        return when { summary.authRequired -> Result.failure(); summary.failed > 0 -> Result.retry(); else -> Result.success() }
    }

}

fun interface CalendarSyncTrigger {
    fun enqueue()
}

object CalendarSyncScheduler {
    const val immediateWorkName = "calendar-sync-now"
    const val periodicWorkName = "calendar-sync-periodic"

    fun enqueueNow(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            immediateWorkName,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CalendarSyncWorker>().build(),
        )
    }

    fun schedulePeriodic(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            periodicWorkName,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CalendarSyncWorker>(15, TimeUnit.MINUTES).build(),
        )
    }
}
