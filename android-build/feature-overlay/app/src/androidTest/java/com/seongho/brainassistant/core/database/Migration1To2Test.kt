package com.seongho.brainassistant.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
    fun migrationCreatesDdayWidgetConfigAndSnapshotTables() {
        helper.createDatabase(TEST_DB, 1).close()

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db ->
            assertTrue(db.query("SELECT * FROM dday_items").use { it.columnCount > 0 })
            assertTrue(db.query("SELECT * FROM widget_configs").use { it.columnCount > 0 })
            assertTrue(db.query("SELECT * FROM widget_snapshots").use { it.columnCount > 0 })
        }
    }

    private companion object {
        const val TEST_DB = "migration-1-2-test"
    }
}
