package com.seongho.brainassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS dday_items (
                id TEXT NOT NULL PRIMARY KEY,
                inputId TEXT NOT NULL,
                transactionId TEXT NOT NULL,
                title TEXT NOT NULL,
                targetDateEpochDay INTEGER NOT NULL,
                category TEXT NOT NULL,
                importance INTEGER NOT NULL,
                isPinned INTEGER NOT NULL,
                showElapsedDays INTEGER NOT NULL,
                archiveAfterDays INTEGER NOT NULL,
                recurrenceRule TEXT,
                linkedTaskId TEXT,
                linkedCalendarId TEXT,
                reminderOffsetsCsv TEXT NOT NULL,
                status TEXT NOT NULL,
                deletedAtEpochMs INTEGER,
                updatedAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_dday_items_targetDateEpochDay " +
                "ON dday_items(targetDateEpochDay)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS widget_configs (
                widgetId INTEGER NOT NULL PRIMARY KEY,
                widgetType TEXT NOT NULL,
                sizeClass TEXT NOT NULL,
                calendarId TEXT,
                filtersCsv TEXT NOT NULL,
                themeMode TEXT NOT NULL,
                maskSensitivePreview INTEGER NOT NULL,
                updatedAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS widget_snapshots (
                widgetId INTEGER NOT NULL PRIMARY KEY,
                widgetType TEXT NOT NULL,
                payloadJson TEXT NOT NULL,
                generatedAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recurrence_masters (
                id TEXT NOT NULL PRIMARY KEY,
                inputId TEXT NOT NULL,
                transactionId TEXT NOT NULL,
                title TEXT NOT NULL,
                startDateEpochDay INTEGER NOT NULL,
                startMinuteOfDay INTEGER NOT NULL,
                durationMinutes INTEGER NOT NULL,
                zoneId TEXT NOT NULL,
                frequency TEXT NOT NULL,
                interval INTEGER NOT NULL,
                weekdaysCsv TEXT NOT NULL,
                dayOfMonth INTEGER,
                ordinal INTEGER,
                ordinalWeekdayIso INTEGER,
                endType TEXT NOT NULL,
                endValue INTEGER,
                exclusionKindsCsv TEXT NOT NULL,
                exclusionPolicy TEXT NOT NULL,
                googleCalendarId TEXT NOT NULL,
                remoteSeriesId TEXT,
                syncState TEXT NOT NULL,
                deletedAtEpochMs INTEGER,
                updatedAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recurrence_masters_transactionId ON recurrence_masters(transactionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recurrence_masters_startDateEpochDay ON recurrence_masters(startDateEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recurrence_masters_remoteSeriesId ON recurrence_masters(remoteSeriesId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recurrence_exceptions (
                id TEXT NOT NULL PRIMARY KEY,
                masterId TEXT NOT NULL,
                originalStartEpochMs INTEGER NOT NULL,
                kind TEXT NOT NULL,
                effectiveStartEpochMs INTEGER,
                effectiveEndEpochMs INTEGER,
                titleOverride TEXT,
                remoteEventId TEXT,
                syncState TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recurrence_exceptions_masterId_originalStartEpochMs ON recurrence_exceptions(masterId, originalStartEpochMs)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS exclusion_sources (
                id TEXT NOT NULL PRIMARY KEY,
                calendarId TEXT NOT NULL,
                displayName TEXT NOT NULL,
                kind TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                lastRefreshedAtEpochMs INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exclusion_sources_calendarId ON exclusion_sources(calendarId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS exclusion_dates (
                sourceId TEXT NOT NULL,
                remoteEventId TEXT NOT NULL,
                dateEpochDay INTEGER NOT NULL,
                year INTEGER NOT NULL,
                title TEXT NOT NULL,
                approved INTEGER NOT NULL,
                PRIMARY KEY(sourceId, remoteEventId, dateEpochDay)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exclusion_dates_sourceId_year ON exclusion_dates(sourceId, year)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recurrence_undo_operations (
                id TEXT NOT NULL PRIMARY KEY,
                scope TEXT NOT NULL,
                createdAtEpochMs INTEGER NOT NULL,
                undoneAtEpochMs INTEGER
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recurrence_undo_master_snapshots (
                operationId TEXT NOT NULL,
                phase TEXT NOT NULL,
                id TEXT NOT NULL,
                inputId TEXT NOT NULL,
                transactionId TEXT NOT NULL,
                title TEXT NOT NULL,
                startDateEpochDay INTEGER NOT NULL,
                startMinuteOfDay INTEGER NOT NULL,
                durationMinutes INTEGER NOT NULL,
                zoneId TEXT NOT NULL,
                frequency TEXT NOT NULL,
                interval INTEGER NOT NULL,
                weekdaysCsv TEXT NOT NULL,
                dayOfMonth INTEGER,
                ordinal INTEGER,
                ordinalWeekdayIso INTEGER,
                endType TEXT NOT NULL,
                endValue INTEGER,
                exclusionKindsCsv TEXT NOT NULL,
                exclusionPolicy TEXT NOT NULL,
                googleCalendarId TEXT NOT NULL,
                remoteSeriesId TEXT,
                syncState TEXT NOT NULL,
                deletedAtEpochMs INTEGER,
                updatedAtEpochMs INTEGER NOT NULL,
                PRIMARY KEY(operationId, phase, id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recurrence_undo_master_snapshots_operationId ON recurrence_undo_master_snapshots(operationId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recurrence_undo_exception_snapshots (
                operationId TEXT NOT NULL,
                phase TEXT NOT NULL,
                id TEXT NOT NULL,
                masterId TEXT NOT NULL,
                originalStartEpochMs INTEGER NOT NULL,
                kind TEXT NOT NULL,
                effectiveStartEpochMs INTEGER,
                effectiveEndEpochMs INTEGER,
                titleOverride TEXT,
                remoteEventId TEXT,
                syncState TEXT NOT NULL,
                PRIMARY KEY(operationId, phase, id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recurrence_undo_exception_snapshots_operationId ON recurrence_undo_exception_snapshots(operationId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recurrence_outbox (
                id TEXT NOT NULL PRIMARY KEY,
                masterId TEXT NOT NULL,
                exceptionId TEXT,
                operation TEXT NOT NULL,
                createdAtEpochMs INTEGER NOT NULL,
                attemptCount INTEGER NOT NULL,
                lastError TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recurrence_outbox_masterId ON recurrence_outbox(masterId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recurrence_outbox_exceptionId ON recurrence_outbox(exceptionId)")
    }
}
