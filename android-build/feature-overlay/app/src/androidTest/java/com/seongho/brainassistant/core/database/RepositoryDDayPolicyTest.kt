package com.seongho.brainassistant.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seongho.brainassistant.core.model.ItemStatus
import com.seongho.brainassistant.core.model.ItemType
import com.seongho.brainassistant.core.model.ParsedItem
import com.seongho.brainassistant.data.BrainRepository
import com.seongho.brainassistant.data.RoomBrainRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryDDayPolicyTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: BrainRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomBrainRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun parsedDDayIsStoredInDedicatedTableInsteadOfTaskTable() = runBlocking {
        val targetDate = LocalDate.of(2026, 8, 20)

        val persisted = repository.saveParsedItems(
            inputId = "input-dday",
            items = listOf(
                ParsedItem(
                    localId = "dday-report",
                    type = ItemType.D_DAY,
                    title = "교육청 보고서 제출",
                    targetDate = targetDate,
                    priority = 3,
                    reminderOffsets = setOf(7, 3, 1, 0),
                ),
            ),
            transactionId = "tx-dday",
        )

        assertFalse(persisted.tasks.any { it.id == "dday-report" })
        val stored = database.ddayDao().get("dday-report")
        assertNotNull(stored)
        assertEquals(targetDate.toEpochDay(), stored?.targetDateEpochDay)
        assertEquals("7,3,1,0", stored?.reminderOffsetsCsv)
    }

    @Test
    fun transactionUndoMovesDDayToTrashAndRestoreMakesItActiveAgain() = runBlocking {
        repository.saveParsedItems(
            inputId = "input-dday",
            items = listOf(
                ParsedItem(
                    localId = "dday-trip",
                    type = ItemType.D_DAY,
                    title = "가족여행",
                    targetDate = LocalDate.of(2026, 8, 15),
                ),
            ),
            transactionId = "tx-trip",
        )

        repository.softDeleteByTransaction("tx-trip", Instant.parse("2026-08-01T00:00:00Z"))

        val deleted = database.ddayDao().get("dday-trip")
        assertEquals(ItemStatus.DELETED.name, deleted?.status)
        val trashItem = repository.observeTrash().first().first { it.id == "dday-trip" }
        assertEquals(ItemType.D_DAY, trashItem.type)

        repository.restoreItem(trashItem)

        val restored = database.ddayDao().get("dday-trip")
        assertEquals(ItemStatus.ACTIVE.name, restored?.status)
        assertTrue(repository.observeTrash().first().none { it.id == "dday-trip" })
    }
}
