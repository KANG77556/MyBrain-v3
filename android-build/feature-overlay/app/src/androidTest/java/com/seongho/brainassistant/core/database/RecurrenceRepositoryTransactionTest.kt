package com.seongho.brainassistant.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seongho.brainassistant.core.model.ItemType
import com.seongho.brainassistant.core.model.ParsedItem
import com.seongho.brainassistant.core.model.RecurrenceDraft
import com.seongho.brainassistant.core.model.RecurrenceFrequency
import com.seongho.brainassistant.core.model.OccurrenceKey
import com.seongho.brainassistant.core.model.RecurrenceMutation
import com.seongho.brainassistant.core.model.RecurrenceMutationKind
import com.seongho.brainassistant.core.model.RecurrenceRule
import com.seongho.brainassistant.core.model.RecurrenceScope
import com.seongho.brainassistant.data.RoomBrainRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecurrenceRepositoryTransactionTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomBrainRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomBrainRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun reviewedOrdinaryItemsAndRecurrenceCommitAtomically() = runBlocking {
        val result = repository.confirmReviewedBatch(
            inputId = "input-1",
            items = listOf(ParsedItem(type = ItemType.NOTE, title = "준비물", body = "교재 확인")),
            recurrences = listOf(recurrenceDraft()),
        )

        assertEquals(1, rowCount("notes"))
        assertEquals(1, rowCount("recurrence_masters"))
        assertEquals(1, rowCount("recurrence_undo_operations"))
        assertEquals(1, rowCount("recurrence_undo_master_snapshots"))
        assertEquals(1, rowCount("recurrence_outbox"))
        assertNotNull(result.recurrenceCommit)
        assertEquals(result.transactionId, result.ordinaryItems.transactionId)
    }

    @Test
    fun outboxFailureRollsBackOrdinaryMasterAndUndoRows() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_recurrence_outbox
            BEFORE INSERT ON recurrence_outbox
            BEGIN SELECT RAISE(ABORT, 'forced outbox failure'); END
            """.trimIndent(),
        )

        runCatching {
            repository.confirmReviewedBatch(
                inputId = "input-2",
                items = listOf(ParsedItem(type = ItemType.NOTE, title = "롤백 메모")),
                recurrences = listOf(recurrenceDraft()),
            )
        }

        assertEquals(0, rowCount("notes"))
        assertEquals(0, rowCount("recurrence_masters"))
        assertEquals(0, rowCount("recurrence_undo_operations"))
        assertEquals(0, rowCount("recurrence_undo_master_snapshots"))
        assertEquals(0, rowCount("recurrence_outbox"))
    }

    @Test
    fun allOccurrencesMutationAndUndoRestoreOriginalMaster() = runBlocking {
        val saved = repository.confirmReviewedBatch("input-3", emptyList(), listOf(recurrenceDraft()))
        val originalStart = Instant.parse("2026-08-10T00:00:00Z")
        val replacement = recurrenceDraft().copy(title = "변경 수업", startTime = LocalTime.of(10, 0))

        val commit = repository.mutateRecurrence(
            RecurrenceMutation(
                key = OccurrenceKey("series-1", originalStart),
                scope = RecurrenceScope.ALL_OCCURRENCES,
                kind = RecurrenceMutationKind.UPDATE,
                replacement = replacement,
            ),
        )

        assertEquals("변경 수업", database.recurrenceMasterDao().get("series-1")?.title)
        assertEquals(1, database.recurrenceUndoDao().listMasterSnapshots(commit.operationId, UndoSnapshotPhase.BEFORE).size)
        assertEquals(1, database.recurrenceUndoDao().listMasterSnapshots(commit.operationId, UndoSnapshotPhase.AFTER).size)

        repository.undoRecurrence(commit.operationId)

        assertEquals("방과후수업", database.recurrenceMasterDao().get("series-1")?.title)
        assertNotNull(database.recurrenceUndoDao().getOperation(commit.operationId)?.undoneAtEpochMs)
        assertNotNull(saved.recurrenceCommit)
    }

    @Test
    fun undoingReviewedBatchRemovesSeriesAndSoftDeletesOrdinaryItems() = runBlocking {
        val saved = repository.confirmReviewedBatch(
            "input-4",
            listOf(ParsedItem(type = ItemType.NOTE, title = "함께 취소할 메모")),
            listOf(recurrenceDraft()),
        )

        repository.undoRecurrence(requireNotNull(saved.recurrenceCommit).operationId)

        assertEquals(0, rowCount("recurrence_masters"))
        assertEquals(0, rowCount("recurrence_outbox"))
        assertEquals("DELETED", singleString("SELECT status FROM notes LIMIT 1"))
    }

    @Test
    fun recurringOccurrenceRangeIsExpandedFromMasterInsteadOfStoredRows() = runBlocking {
        repository.confirmReviewedBatch("input-5", emptyList(), listOf(recurrenceDraft()))

        val occurrences = repository.observeRecurringOccurrences(
            Instant.parse("2026-08-09T15:00:00Z"),
            Instant.parse("2026-08-15T15:00:00Z"),
        ).first()

        assertEquals(5, occurrences.size)
        assertEquals("방과후수업", occurrences.first().title)
    }

    private fun recurrenceDraft() = RecurrenceDraft(
        localId = "series-1",
        title = "방과후수업",
        startDate = LocalDate.of(2026, 8, 10),
        startTime = LocalTime.of(9, 0),
        durationMinutes = 180,
        rule = RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            weekdays = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
        ),
        confidence = 0.98,
    )

    private fun rowCount(table: String): Int = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM $table")
        .use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private fun singleString(sql: String): String = database.openHelper.readableDatabase
        .query(sql)
        .use { cursor -> cursor.moveToFirst(); cursor.getString(0) }
}
