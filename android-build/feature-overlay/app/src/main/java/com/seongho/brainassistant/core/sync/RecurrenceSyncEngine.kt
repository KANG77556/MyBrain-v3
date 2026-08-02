package com.seongho.brainassistant.core.sync

import com.seongho.brainassistant.core.calendar.RecurrenceCalendarGateway
import com.seongho.brainassistant.core.calendar.RecurrenceRRuleMapper
import com.seongho.brainassistant.core.database.RecurrenceOutboxOperation
import com.seongho.brainassistant.core.model.RecurrenceException
import com.seongho.brainassistant.core.model.RecurrenceExceptionKind
import com.seongho.brainassistant.core.model.RecurrenceMaster
import com.seongho.brainassistant.core.model.RecurrenceOccurrence
import java.time.Instant

data class RecurrenceSyncCommand(
    val id: String,
    val masterId: String,
    val exceptionId: String?,
    val operation: RecurrenceOutboxOperation,
)

interface RecurrenceSyncStore {
    suspend fun next(): RecurrenceSyncCommand?
    suspend fun master(id: String): RecurrenceMaster?
    suspend fun exceptions(masterId: String): List<RecurrenceException>
    suspend fun updateMaster(master: RecurrenceMaster)
    suspend fun delete(commandId: String)
    suspend fun markFailed(commandId: String, error: String)
}

enum class RecurrenceSyncResult { EMPTY, SYNCED, RETRY }

class RecurrenceSyncEngine(
    private val store: RecurrenceSyncStore,
    private val gateway: RecurrenceCalendarGateway,
    private val mapper: RecurrenceRRuleMapper = RecurrenceRRuleMapper(),
) {
    suspend fun processNext(): RecurrenceSyncResult {
        val command = store.next() ?: return RecurrenceSyncResult.EMPTY
        val master = store.master(command.masterId)
        if (master == null) {
            store.delete(command.id)
            return RecurrenceSyncResult.SYNCED
        }
        return try {
            val exceptions = store.exceptions(master.id)
            when (command.operation) {
                RecurrenceOutboxOperation.UPSERT_SERIES -> {
                    val mapped = mapper.map(master, exceptions.map { it.key.originalStartAt })
                    val remote = gateway.upsertSeries(master, mapped.rrule, exceptions.map { it.key.originalStartAt })
                    store.updateMaster(master.copy(remoteSeriesId = remote.id, updatedAt = remote.updatedAt))
                }
                RecurrenceOutboxOperation.DELETE_SERIES -> {
                    master.remoteSeriesId?.let { gateway.deleteSeries(master.googleCalendarId, it) }
                }
                RecurrenceOutboxOperation.UPSERT_DETACHED_OCCURRENCE -> {
                    val exception = exceptions.firstOrNull { it.id == command.exceptionId }
                    if (exception?.effectiveStartAt != null && exception.effectiveEndAt != null) {
                        gateway.upsertDetachedOccurrence(master, RecurrenceOccurrence(
                            key = exception.key,
                            title = exception.titleOverride ?: master.title,
                            startAt = exception.effectiveStartAt,
                            endAt = exception.effectiveEndAt,
                            kind = exception.kind,
                        ))
                    }
                }
                RecurrenceOutboxOperation.DELETE_DETACHED_OCCURRENCE -> {
                    exceptions.firstOrNull { it.id == command.exceptionId }?.remoteEventId?.let {
                        gateway.deleteDetachedOccurrence(master.googleCalendarId, it)
                    }
                }
            }
            store.delete(command.id)
            RecurrenceSyncResult.SYNCED
        } catch (error: Exception) {
            store.markFailed(command.id, error.message ?: error.javaClass.simpleName)
            RecurrenceSyncResult.RETRY
        }
    }
}
