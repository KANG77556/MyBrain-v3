package com.seongho.brainassistant.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurrenceMasterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecurrenceMasterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<RecurrenceMasterEntity>)

    @Query("SELECT * FROM recurrence_masters WHERE id = :id LIMIT 1")
    suspend fun get(id: String): RecurrenceMasterEntity?

    @Query(
        """
        SELECT * FROM recurrence_masters
        WHERE deletedAtEpochMs IS NULL
          AND startDateEpochDay <= :endEpochDay
          AND (endType != 'UNTIL' OR endValue >= :startEpochDay)
        ORDER BY startDateEpochDay, startMinuteOfDay, id
        """,
    )
    suspend fun listOverlapping(startEpochDay: Long, endEpochDay: Long): List<RecurrenceMasterEntity>

    @Query(
        """
        SELECT * FROM recurrence_masters
        WHERE deletedAtEpochMs IS NULL
          AND startDateEpochDay <= :endEpochDay
          AND (endType != 'UNTIL' OR endValue >= :startEpochDay)
        ORDER BY startDateEpochDay, startMinuteOfDay, id
        """,
    )
    fun observeOverlapping(startEpochDay: Long, endEpochDay: Long): Flow<List<RecurrenceMasterEntity>>

    @Query("DELETE FROM recurrence_masters WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface RecurrenceExceptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecurrenceExceptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<RecurrenceExceptionEntity>)

    @Query("SELECT * FROM recurrence_exceptions WHERE masterId = :masterId ORDER BY originalStartEpochMs")
    suspend fun listForMaster(masterId: String): List<RecurrenceExceptionEntity>

    @Query("SELECT * FROM recurrence_exceptions ORDER BY masterId, originalStartEpochMs")
    fun observeAll(): Flow<List<RecurrenceExceptionEntity>>

    @Query("DELETE FROM recurrence_exceptions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM recurrence_exceptions WHERE masterId = :masterId")
    suspend fun deleteForMaster(masterId: String)
}

@Dao
interface ExclusionSourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ExclusionSourceEntity)

    @Query("SELECT * FROM exclusion_sources WHERE enabled = 1 ORDER BY displayName")
    suspend fun listEnabled(): List<ExclusionSourceEntity>
}

@Dao
interface ExclusionDateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ExclusionDateEntity>)

    @Query("SELECT * FROM exclusion_dates WHERE sourceId = :sourceId AND year = :year ORDER BY dateEpochDay, remoteEventId")
    suspend fun listForSourceYear(sourceId: String, year: Int): List<ExclusionDateEntity>

    @Query("DELETE FROM exclusion_dates WHERE sourceId = :sourceId AND year = :year")
    suspend fun deleteForSourceYear(sourceId: String, year: Int)

    @Transaction
    suspend fun replaceForSourceYear(sourceId: String, year: Int, rows: List<ExclusionDateEntity>) {
        require(rows.all { it.sourceId == sourceId && it.year == year })
        deleteForSourceYear(sourceId, year)
        upsertAll(rows)
    }
}

@Dao
interface RecurrenceUndoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOperation(operation: RecurrenceUndoOperationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMasterSnapshots(rows: List<RecurrenceUndoMasterSnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExceptionSnapshots(rows: List<RecurrenceUndoExceptionSnapshotEntity>)

    @Query("SELECT * FROM recurrence_undo_operations WHERE id = :operationId LIMIT 1")
    suspend fun getOperation(operationId: String): RecurrenceUndoOperationEntity?

    @Query("SELECT * FROM recurrence_undo_master_snapshots WHERE operationId = :operationId AND phase = :phase")
    suspend fun listMasterSnapshots(operationId: String, phase: UndoSnapshotPhase): List<RecurrenceUndoMasterSnapshotEntity>

    @Query("SELECT * FROM recurrence_undo_exception_snapshots WHERE operationId = :operationId AND phase = :phase")
    suspend fun listExceptionSnapshots(operationId: String, phase: UndoSnapshotPhase): List<RecurrenceUndoExceptionSnapshotEntity>

    @Query("UPDATE recurrence_undo_operations SET undoneAtEpochMs = :undoneAtEpochMs WHERE id = :operationId")
    suspend fun markUndone(operationId: String, undoneAtEpochMs: Long)
}

@Dao
interface RecurrenceOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecurrenceOutboxEntity)

    @Query("SELECT * FROM recurrence_outbox ORDER BY createdAtEpochMs, id LIMIT :limit")
    suspend fun pending(limit: Int = 50): List<RecurrenceOutboxEntity>

    @Query("DELETE FROM recurrence_outbox WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM recurrence_outbox WHERE masterId = :masterId")
    suspend fun deleteForMaster(masterId: String)

    @Query("UPDATE recurrence_outbox SET attemptCount = attemptCount + 1, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: String, error: String)
}
