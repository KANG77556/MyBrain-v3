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
