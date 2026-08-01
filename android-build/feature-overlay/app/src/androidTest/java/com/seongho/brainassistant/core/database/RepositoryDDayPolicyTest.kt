package com.seongho.brainassistant.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seongho.brainassistant.core.model.DDayCategory
import com.seongho.brainassistant.core.model.DDayItem
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
import org.junit.Assert.fail
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
        assertEquals(
            setOf(0, 1, 3, 7),
            stored?.reminderOffsetsCsv?.split(",")?.map(String::toInt)?.toSet(),
        )
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

    @Test
    fun repositoryExposesOnlyVisibleDDaysAndRepresentative() = runBlocking {
        val today = LocalDate.of(2026, 8, 20)
        val now = Instant.parse("2026-08-20T00:00:00Z")

        repository.saveDDay(
            DDayItem(
                id = "expired-pinned",
                inputId = "input-expired",
                transactionId = "tx-expired",
                title = "노출 기간이 지난 일정",
                targetDate = today.minusDays(10),
                category = DDayCategory.DEADLINE,
                importance = 3,
                isPinned = true,
                showElapsedDays = true,
                archiveAfterDays = 7,
                updatedAt = now,
            ),
        )
        repository.saveDDay(
            DDayItem(
                id = "still-visible",
                inputId = "input-visible",
                transactionId = "tx-visible",
                title = "아직 표시할 일정",
                targetDate = today.minusDays(5),
                category = DDayCategory.TRAVEL,
                importance = 2,
                showElapsedDays = true,
                archiveAfterDays = 7,
                updatedAt = now,
            ),
        )

        assertEquals(
            listOf("still-visible"),
            repository.observeDDays(today).first().map { it.id },
        )
        assertEquals(
            "still-visible",
            repository.getRepresentativeDDay(today)?.id,
        )
    }

    @Test
    fun secondEventFailureRollsBackWholeMixedBatch() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER fail_second_event BEFORE INSERT ON calendar_items
               WHEN NEW.title = '저장 실패'
               BEGIN SELECT RAISE(ABORT, 'forced batch failure'); END""",
        )

        try {
            repository.saveParsedItems(
                inputId = "input-rollback",
                transactionId = "tx-rollback",
                items = listOf(
                    ParsedItem(localId = "note-before", type = ItemType.NOTE, title = "준비 메모"),
                    ParsedItem(localId = "task-before", type = ItemType.TASK, title = "보고서 제출"),
                    ParsedItem(
                        localId = "event-ok",
                        type = ItemType.EVENT,
                        title = "교무회의",
                        startAt = Instant.parse("2026-08-03T01:00:00Z"),
                    ),
                    ParsedItem(
                        localId = "event-fail",
                        type = ItemType.EVENT,
                        title = "저장 실패",
                        startAt = Instant.parse("2026-08-03T02:00:00Z"),
                    ),
                ),
            )
            fail("두 번째 일정 저장은 실패해야 합니다.")
        } catch (_: Exception) {
            // Expected: the database trigger aborts the transaction.
        }

        assertEquals(0, tableCount("notes"))
        assertEquals(0, tableCount("tasks"))
        assertEquals(0, tableCount("calendar_items"))
        assertEquals(0, tableCount("sync_outbox"))
    }

    @Test
    fun undoMixedBatchDeletesAllItemsAndPendingCreateOutbox() = runBlocking {
        val transactionId = "tx-mixed-undo"
        repository.saveParsedItems(
            inputId = "input-mixed-undo",
            transactionId = transactionId,
            items = listOf(
                ParsedItem(localId = "note-undo", type = ItemType.NOTE, title = "준비 메모"),
                ParsedItem(localId = "task-undo", type = ItemType.TASK, title = "보고서 제출"),
                ParsedItem(
                    localId = "event-undo",
                    type = ItemType.EVENT,
                    title = "교무회의",
                    startAt = Instant.parse("2026-08-03T01:00:00Z"),
                ),
                ParsedItem(
                    localId = "dday-undo",
                    type = ItemType.D_DAY,
                    title = "개학",
                    targetDate = LocalDate.of(2026, 8, 20),
                ),
            ),
        )
        assertEquals(1, repository.pendingOutbox().size)

        repository.softDeleteByTransaction(transactionId, Instant.parse("2026-08-01T00:00:00Z"))

        assertEquals(
            setOf("note-undo", "task-undo", "event-undo", "dday-undo"),
            repository.observeTrash().first().map { it.id }.toSet(),
        )
        assertTrue(repository.pendingOutbox().isEmpty())
    }

    private fun tableCount(table: String): Int =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM $table")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
}
