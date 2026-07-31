package com.seongho.brainassistant.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "inputs")
data class InputEntity(
    @PrimaryKey val id: String,
    val rawText: String,
    val source: String,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "notes", indices = [Index("inputId"), Index("transactionId")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val inputId: String,
    val transactionId: String,
    val title: String,
    val body: String,
    val status: String,
    val deletedAtEpochMs: Long?,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "tasks", indices = [Index("inputId"), Index("transactionId")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val inputId: String,
    val transactionId: String,
    val itemType: String,
    val title: String,
    val dueAtEpochMs: Long?,
    val priority: Int,
    val estimatedMinutes: Int?,
    val status: String,
    val deletedAtEpochMs: Long?,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "calendar_items", indices = [Index("inputId"), Index("transactionId"), Index(value = ["googleEventId"], unique = false)])
data class CalendarEntity(
    @PrimaryKey val id: String,
    val inputId: String,
    val transactionId: String,
    val title: String,
    val startAtEpochMs: Long,
    val endAtEpochMs: Long,
    val googleCalendarId: String,
    val googleEventId: String?,
    val externalEtag: String?,
    val externalUpdatedAtEpochMs: Long?,
    val syncState: String,
    val deletedAtEpochMs: Long?,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "analysis_records", indices = [Index("inputId")])
data class AnalysisEntity(
    @PrimaryKey val id: String,
    val inputId: String,
    val analyzer: String,
    val confidence: Double,
    val clarificationCsv: String,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "sync_outbox", indices = [Index("localCalendarId")])
data class SyncOutboxEntity(
    @PrimaryKey val id: String,
    val localCalendarId: String,
    val operation: String,
    val createdAtEpochMs: Long,
    val attemptCount: Int,
    val lastError: String?,
)

@Entity(tableName = "dday_items", indices = [Index("targetDateEpochDay")])
data class DDayEntity(
    @PrimaryKey val id: String,
    val inputId: String,
    val transactionId: String,
    val title: String,
    val targetDateEpochDay: Long,
    val category: String,
    val importance: Int,
    val isPinned: Boolean,
    val showElapsedDays: Boolean,
    val archiveAfterDays: Int,
    val recurrenceRule: String?,
    val linkedTaskId: String?,
    val linkedCalendarId: String?,
    val reminderOffsetsCsv: String,
    val status: String,
    val deletedAtEpochMs: Long?,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "widget_configs")
data class WidgetConfigEntity(
    @PrimaryKey val widgetId: Int,
    val widgetType: String,
    val sizeClass: String,
    val calendarId: String?,
    val filtersCsv: String,
    val themeMode: String,
    val maskSensitivePreview: Boolean,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "widget_snapshots")
data class WidgetSnapshotEntity(
    @PrimaryKey val widgetId: Int,
    val widgetType: String,
    val payloadJson: String,
    val generatedAtEpochMs: Long,
)
