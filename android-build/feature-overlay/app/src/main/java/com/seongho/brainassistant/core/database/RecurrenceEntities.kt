package com.seongho.brainassistant.core.database

import androidx.room.Entity
import androidx.room.Index

enum class UndoSnapshotPhase { BEFORE, AFTER }

enum class RecurrenceOutboxOperation {
    UPSERT_SERIES,
    DELETE_SERIES,
    UPSERT_DETACHED_OCCURRENCE,
    DELETE_DETACHED_OCCURRENCE,
}

@Entity(
    tableName = "recurrence_masters",
    primaryKeys = ["id"],
    indices = [Index("transactionId"), Index("startDateEpochDay"), Index("remoteSeriesId")],
)
data class RecurrenceMasterEntity(
    val id: String,
    val inputId: String,
    val transactionId: String,
    val title: String,
    val startDateEpochDay: Long,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val zoneId: String,
    val frequency: String,
    val interval: Int,
    val weekdaysCsv: String,
    val dayOfMonth: Int?,
    val ordinal: Int?,
    val ordinalWeekdayIso: Int?,
    val endType: String,
    val endValue: Long?,
    val exclusionKindsCsv: String,
    val exclusionPolicy: String,
    val googleCalendarId: String,
    val remoteSeriesId: String?,
    val syncState: String,
    val deletedAtEpochMs: Long?,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "recurrence_exceptions",
    primaryKeys = ["id"],
    indices = [Index(value = ["masterId", "originalStartEpochMs"], unique = true)],
)
data class RecurrenceExceptionEntity(
    val id: String,
    val masterId: String,
    val originalStartEpochMs: Long,
    val kind: String,
    val effectiveStartEpochMs: Long?,
    val effectiveEndEpochMs: Long?,
    val titleOverride: String?,
    val remoteEventId: String?,
    val syncState: String,
)

@Entity(tableName = "exclusion_sources", primaryKeys = ["id"], indices = [Index("calendarId")])
data class ExclusionSourceEntity(
    val id: String,
    val calendarId: String,
    val displayName: String,
    val kind: String,
    val enabled: Boolean,
    val lastRefreshedAtEpochMs: Long?,
)

@Entity(
    tableName = "exclusion_dates",
    primaryKeys = ["sourceId", "remoteEventId", "dateEpochDay"],
    indices = [Index(value = ["sourceId", "year"])],
)
data class ExclusionDateEntity(
    val sourceId: String,
    val remoteEventId: String,
    val dateEpochDay: Long,
    val year: Int,
    val title: String,
    val approved: Boolean,
)

@Entity(tableName = "recurrence_undo_operations", primaryKeys = ["id"])
data class RecurrenceUndoOperationEntity(
    val id: String,
    val scope: String,
    val createdAtEpochMs: Long,
    val undoneAtEpochMs: Long?,
)

@Entity(
    tableName = "recurrence_undo_master_snapshots",
    primaryKeys = ["operationId", "phase", "id"],
    indices = [Index("operationId")],
)
data class RecurrenceUndoMasterSnapshotEntity(
    val operationId: String,
    val phase: UndoSnapshotPhase,
    val id: String,
    val inputId: String,
    val transactionId: String,
    val title: String,
    val startDateEpochDay: Long,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val zoneId: String,
    val frequency: String,
    val interval: Int,
    val weekdaysCsv: String,
    val dayOfMonth: Int?,
    val ordinal: Int?,
    val ordinalWeekdayIso: Int?,
    val endType: String,
    val endValue: Long?,
    val exclusionKindsCsv: String,
    val exclusionPolicy: String,
    val googleCalendarId: String,
    val remoteSeriesId: String?,
    val syncState: String,
    val deletedAtEpochMs: Long?,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "recurrence_undo_exception_snapshots",
    primaryKeys = ["operationId", "phase", "id"],
    indices = [Index("operationId")],
)
data class RecurrenceUndoExceptionSnapshotEntity(
    val operationId: String,
    val phase: UndoSnapshotPhase,
    val id: String,
    val masterId: String,
    val originalStartEpochMs: Long,
    val kind: String,
    val effectiveStartEpochMs: Long?,
    val effectiveEndEpochMs: Long?,
    val titleOverride: String?,
    val remoteEventId: String?,
    val syncState: String,
)

@Entity(
    tableName = "recurrence_outbox",
    primaryKeys = ["id"],
    indices = [Index("masterId"), Index("exceptionId")],
)
data class RecurrenceOutboxEntity(
    val id: String,
    val masterId: String,
    val exceptionId: String?,
    val operation: RecurrenceOutboxOperation,
    val createdAtEpochMs: Long,
    val attemptCount: Int,
    val lastError: String?,
)
