package com.seongho.brainassistant.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InputDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InputEntity)
}

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NoteEntity)

    @Query("SELECT * FROM notes WHERE status = 'ACTIVE' ORDER BY updatedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 10): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE status = 'DELETED' ORDER BY deletedAtEpochMs DESC")
    fun observeTrash(): Flow<List<NoteEntity>>

    @Query("UPDATE notes SET status = 'DELETED', deletedAtEpochMs = :deletedAt, updatedAtEpochMs = :deletedAt WHERE transactionId = :transactionId")
    suspend fun softDeleteByTransaction(transactionId: String, deletedAt: Long)

    @Query("UPDATE notes SET status = 'ACTIVE', deletedAtEpochMs = NULL, updatedAtEpochMs = :updatedAt WHERE id = :id")
    suspend fun restore(id: String, updatedAt: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deletePermanently(id: String)

    @Query("DELETE FROM notes WHERE status = 'DELETED' AND deletedAtEpochMs < :cutoff")
    suspend fun purgeBefore(cutoff: Long): Int
}

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaskEntity)

    @Query("SELECT * FROM tasks WHERE status = 'ACTIVE' AND (dueAtEpochMs IS NULL OR dueAtEpochMs < :endEpochMs) ORDER BY priority DESC, dueAtEpochMs ASC")
    fun observeActiveBefore(endEpochMs: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'DELETED' ORDER BY deletedAtEpochMs DESC")
    fun observeTrash(): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET status = 'DELETED', deletedAtEpochMs = :deletedAt, updatedAtEpochMs = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("UPDATE tasks SET status = 'DELETED', deletedAtEpochMs = :deletedAt, updatedAtEpochMs = :deletedAt WHERE transactionId = :transactionId")
    suspend fun softDeleteByTransaction(transactionId: String, deletedAt: Long)

    @Query("UPDATE tasks SET status = 'ACTIVE', deletedAtEpochMs = NULL, updatedAtEpochMs = :updatedAt WHERE id = :id")
    suspend fun restore(id: String, updatedAt: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deletePermanently(id: String)

    @Query("DELETE FROM tasks WHERE status = 'DELETED' AND deletedAtEpochMs < :cutoff")
    suspend fun purgeBefore(cutoff: Long): Int
}

@Dao
interface CalendarDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CalendarEntity)

    @Query("SELECT * FROM calendar_items WHERE id = :id LIMIT 1")
    suspend fun get(id: String): CalendarEntity?

    @Query("SELECT * FROM calendar_items WHERE googleEventId = :googleEventId LIMIT 1")
    suspend fun getByGoogleEventId(googleEventId: String): CalendarEntity?

    @Query("SELECT * FROM calendar_items WHERE transactionId = :transactionId")
    suspend fun listByTransaction(transactionId: String): List<CalendarEntity>

    @Query("SELECT * FROM calendar_items WHERE deletedAtEpochMs IS NULL AND startAtEpochMs < :endEpochMs AND endAtEpochMs > :startEpochMs ORDER BY startAtEpochMs")
    fun observeRange(startEpochMs: Long, endEpochMs: Long): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendar_items WHERE deletedAtEpochMs IS NULL AND startAtEpochMs < :endEpochMs AND endAtEpochMs > :startEpochMs ORDER BY startAtEpochMs")
    suspend fun listRange(startEpochMs: Long, endEpochMs: Long): List<CalendarEntity>

    @Query("SELECT * FROM calendar_items WHERE deletedAtEpochMs IS NOT NULL ORDER BY deletedAtEpochMs DESC")
    fun observeTrash(): Flow<List<CalendarEntity>>

    @Query("UPDATE calendar_items SET deletedAtEpochMs = :deletedAt, syncState = 'PENDING', updatedAtEpochMs = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("UPDATE calendar_items SET deletedAtEpochMs = :deletedAt, syncState = 'PENDING', updatedAtEpochMs = :deletedAt WHERE transactionId = :transactionId")
    suspend fun softDeleteByTransaction(transactionId: String, deletedAt: Long)

    @Query("UPDATE calendar_items SET deletedAtEpochMs = NULL, syncState = 'PENDING', updatedAtEpochMs = :updatedAt WHERE id = :id")
    suspend fun restore(id: String, updatedAt: Long)

    @Query("UPDATE calendar_items SET deletedAtEpochMs = :deletedAt, syncState = 'SYNCED', updatedAtEpochMs = :deletedAt WHERE id = :id")
    suspend fun applyRemoteDeletion(id: String, deletedAt: Long)

    @Query("DELETE FROM calendar_items WHERE id = :id")
    suspend fun deletePermanently(id: String)

    @Query("DELETE FROM calendar_items WHERE deletedAtEpochMs IS NOT NULL AND deletedAtEpochMs < :cutoff")
    suspend fun purgeBefore(cutoff: Long): Int
}

@Dao
interface AnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AnalysisEntity)
}

@Dao
interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncOutboxEntity)

    @Query("SELECT * FROM sync_outbox ORDER BY createdAtEpochMs LIMIT :limit")
    suspend fun pending(limit: Int = 50): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE sync_outbox SET attemptCount = attemptCount + 1, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: String, error: String)

    @Query("DELETE FROM sync_outbox WHERE localCalendarId = :localCalendarId")
    suspend fun deleteByLocalCalendarId(localCalendarId: String)
}

@Dao
interface DDayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DDayEntity)

    @Query("SELECT * FROM dday_items WHERE id = :id LIMIT 1")
    suspend fun get(id: String): DDayEntity?

    @Query(
        """
        SELECT * FROM dday_items
        WHERE status = 'ACTIVE' AND deletedAtEpochMs IS NULL
        ORDER BY isPinned DESC, targetDateEpochDay ASC, importance DESC
        """,
    )
    fun observeActive(): Flow<List<DDayEntity>>

    @Query(
        """
        SELECT * FROM dday_items
        WHERE status = 'ACTIVE'
          AND deletedAtEpochMs IS NULL
          AND targetDateEpochDay >= :oldestVisibleEpochDay
          AND (showElapsedDays = 1 OR targetDateEpochDay >= :todayEpochDay)
        ORDER BY
          isPinned DESC,
          CASE WHEN targetDateEpochDay >= :todayEpochDay THEN 0 ELSE 1 END,
          ABS(targetDateEpochDay - :todayEpochDay),
          importance DESC
        LIMIT 1
        """,
    )
    suspend fun getRepresentative(
        todayEpochDay: Long,
        oldestVisibleEpochDay: Long,
    ): DDayEntity?

    @Query("UPDATE dday_items SET status = 'DELETED', deletedAtEpochMs = :deletedAt, updatedAtEpochMs = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("UPDATE dday_items SET status = 'ACTIVE', deletedAtEpochMs = NULL, updatedAtEpochMs = :updatedAt WHERE id = :id")
    suspend fun restore(id: String, updatedAt: Long)

    @Query("DELETE FROM dday_items WHERE id = :id")
    suspend fun deletePermanently(id: String)

    @Query("DELETE FROM dday_items WHERE status = 'DELETED' AND deletedAtEpochMs < :cutoff")
    suspend fun purgeBefore(cutoff: Long): Int
}

@Dao
interface WidgetConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WidgetConfigEntity)

    @Query("SELECT * FROM widget_configs WHERE widgetId = :widgetId LIMIT 1")
    suspend fun get(widgetId: Int): WidgetConfigEntity?

    @Query("SELECT * FROM widget_configs WHERE widgetId = :widgetId LIMIT 1")
    fun observe(widgetId: Int): Flow<WidgetConfigEntity?>

    @Query("SELECT * FROM widget_configs WHERE widgetType = :widgetType ORDER BY widgetId")
    suspend fun listByType(widgetType: String): List<WidgetConfigEntity>

    @Query("DELETE FROM widget_configs WHERE widgetId = :widgetId")
    suspend fun delete(widgetId: Int)
}

@Dao
interface WidgetSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WidgetSnapshotEntity)

    @Query("SELECT * FROM widget_snapshots WHERE widgetId = :widgetId LIMIT 1")
    suspend fun get(widgetId: Int): WidgetSnapshotEntity?

    @Query("SELECT * FROM widget_snapshots WHERE widgetId = :widgetId LIMIT 1")
    fun observe(widgetId: Int): Flow<WidgetSnapshotEntity?>

    @Query("DELETE FROM widget_snapshots WHERE widgetId = :widgetId")
    suspend fun delete(widgetId: Int)

    @Query("DELETE FROM widget_snapshots WHERE generatedAtEpochMs < :cutoff")
    suspend fun deleteGeneratedBefore(cutoff: Long): Int
}
