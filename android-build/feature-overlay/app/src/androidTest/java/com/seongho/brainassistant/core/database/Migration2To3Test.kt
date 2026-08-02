package com.seongho.brainassistant.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration2To3Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrationPreservesVersionTwoRowsAndCreatesRecurrenceTables() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL("INSERT INTO inputs VALUES('input-2', '기존 입력', 'TEXT', 1000)")
            db.execSQL(
                "INSERT INTO calendar_items VALUES('calendar-2','input-2','tx-2','기존 일정',1000,2000,'primary',NULL,NULL,NULL,'PENDING',NULL,1000)",
            )
            db.execSQL(
                "INSERT INTO dday_items VALUES('dday-2','input-2','tx-2','기존 디데이',21000,'DEADLINE',3,0,1,7,NULL,NULL,NULL,'7,3,1,0','ACTIVE',NULL,1000)",
            )
            db.execSQL(
                "INSERT INTO widget_configs VALUES(2,'DDAY','COMPACT',NULL,'','SYSTEM',1,1000)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3).use { db ->
            assertEquals("기존 입력", db.singleString("SELECT rawText FROM inputs WHERE id='input-2'"))
            assertEquals("기존 일정", db.singleString("SELECT title FROM calendar_items WHERE id='calendar-2'"))
            assertEquals("기존 디데이", db.singleString("SELECT title FROM dday_items WHERE id='dday-2'"))
            assertEquals("DDAY", db.singleString("SELECT widgetType FROM widget_configs WHERE widgetId=2"))

            EXPECTED_TABLES.forEach { table ->
                assertTrue("missing table $table", db.query("SELECT * FROM $table LIMIT 0").use { it.columnCount > 0 })
            }
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.singleString(sql: String): String =
        query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }

    private companion object {
        const val TEST_DB = "migration-2-3-test"
        val EXPECTED_TABLES = listOf(
            "recurrence_masters",
            "recurrence_exceptions",
            "exclusion_sources",
            "exclusion_dates",
            "recurrence_undo_operations",
            "recurrence_undo_master_snapshots",
            "recurrence_undo_exception_snapshots",
            "recurrence_outbox",
        )
    }
}
