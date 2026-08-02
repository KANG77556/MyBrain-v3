package com.seongho.brainassistant.core.sync

import com.seongho.brainassistant.core.database.AppDatabase
import com.seongho.brainassistant.core.database.RecurrenceOutboxEntity
import com.seongho.brainassistant.core.database.toDomain
import com.seongho.brainassistant.core.database.toEntity
import com.seongho.brainassistant.core.model.RecurrenceException
import com.seongho.brainassistant.core.model.RecurrenceMaster

class RoomRecurrenceSyncStore(private val database: AppDatabase) : RecurrenceSyncStore {
    override suspend fun next(): RecurrenceSyncCommand? = database.recurrenceOutboxDao().pending(1).firstOrNull()?.toCommand()
    override suspend fun master(id: String): RecurrenceMaster? = database.recurrenceMasterDao().get(id)?.toDomain()
    override suspend fun exceptions(masterId: String): List<RecurrenceException> = database.recurrenceExceptionDao().listForMaster(masterId).map { it.toDomain() }
    override suspend fun updateMaster(master: RecurrenceMaster) = database.recurrenceMasterDao().upsert(master.toEntity())
    override suspend fun delete(commandId: String) = database.recurrenceOutboxDao().delete(commandId)
    override suspend fun markFailed(commandId: String, error: String) = database.recurrenceOutboxDao().markFailed(commandId, error)

    private fun RecurrenceOutboxEntity.toCommand() = RecurrenceSyncCommand(id, masterId, exceptionId, operation)
}
