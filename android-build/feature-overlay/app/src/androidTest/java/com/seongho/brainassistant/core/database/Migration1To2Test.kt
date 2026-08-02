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
class Migration1To2Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrationPreservesVersionOneDataAndCreatesDdayWidgetTables() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO inputs(id, rawText, source, createdAtEpochMs)
                VALUES('input-1', '내일 회의', 'TEXT', 1000)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db ->
            assertEquals(
                "내일 회의",
                db.query("SELECT rawText FROM inputs WHERE id = 'input-1'").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getString(0)
                },
            )
            assertTrue(db.query("SELECT * FROM dday_items").use { it.columnCount > 0 })
            assertTrue(db.query("SELECT * FROM widget_configs").use { it.columnCount > 0 })
            assertTrue(db.query("SELECT * FROM widget_snapshots").use { it.columnCount > 0 })
        }
    }

    private companion object {
        const val TEST_DB = "migration-1-2-test"
    }
}
