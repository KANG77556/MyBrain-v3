package com.seongho.brainassistant.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        InputEntity::class,
        NoteEntity::class,
        TaskEntity::class,
        CalendarEntity::class,
        AnalysisEntity::class,
        SyncOutboxEntity::class,
        DDayEntity::class,
        WidgetConfigEntity::class,
        WidgetSnapshotEntity::class,
        RecurrenceMasterEntity::class,
        RecurrenceExceptionEntity::class,
        ExclusionSourceEntity::class,
        ExclusionDateEntity::class,
        RecurrenceUndoOperationEntity::class,
        RecurrenceUndoMasterSnapshotEntity::class,
        RecurrenceUndoExceptionSnapshotEntity::class,
        RecurrenceOutboxEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inputDao(): InputDao
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao
    abstract fun calendarDao(): CalendarDao
    abstract fun analysisDao(): AnalysisDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun ddayDao(): DDayDao
    abstract fun widgetConfigDao(): WidgetConfigDao
    abstract fun widgetSnapshotDao(): WidgetSnapshotDao
    abstract fun recurrenceMasterDao(): RecurrenceMasterDao
    abstract fun recurrenceExceptionDao(): RecurrenceExceptionDao
    abstract fun exclusionSourceDao(): ExclusionSourceDao
    abstract fun exclusionDateDao(): ExclusionDateDao
    abstract fun recurrenceUndoDao(): RecurrenceUndoDao
    abstract fun recurrenceOutboxDao(): RecurrenceOutboxDao
}
