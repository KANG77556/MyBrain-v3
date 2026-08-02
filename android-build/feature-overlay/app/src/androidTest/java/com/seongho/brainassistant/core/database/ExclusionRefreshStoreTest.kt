package com.seongho.brainassistant.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seongho.brainassistant.core.model.ExclusionDate
import com.seongho.brainassistant.core.model.ExclusionKind
import com.seongho.brainassistant.core.model.ExclusionPolicy
import com.seongho.brainassistant.core.model.ExclusionSource
import com.seongho.brainassistant.core.model.RecurrenceEnd
import com.seongho.brainassistant.core.model.RecurrenceFrequency
import com.seongho.brainassistant.core.model.RecurrenceMaster
import com.seongho.brainassistant.core.model.RecurrenceRule
import com.seongho.brainassistant.core.model.SyncState
import com.seongho.brainassistant.core.sync.RoomExclusionCacheStore
import com.seongho.brainassistant.core.sync.RoomRecurrenceExclusionRecalculator
import com.seongho.brainassistant.core.sync.RoomExclusionSettingsStore
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExclusionRefreshStoreTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun mergingDiscoveryPreservesUserEnabledSchoolSourceAndReplacesOneYear() = runBlocking {
        val store = RoomExclusionCacheStore(database)
        database.exclusionSourceDao().upsert(source(enabled = true).toEntity())
        database.exclusionDateDao().upsertAll(
            listOf(exclusionDate(LocalDate.of(2026, 8, 10)).toEntity()),
        )

        store.mergeDiscoveredSources(listOf(source(enabled = false)))
        store.replaceSourceYear(
            "school",
            2026,
            listOf(exclusionDate(LocalDate.of(2026, 8, 10)).copy(approved = false)),
        )

        assertTrue(store.enabledSources().single().enabled)
        assertEquals(
            listOf(LocalDate.of(2026, 8, 10).toEpochDay()),
            database.exclusionDateDao().listForSourceYear("school", 2026).map { it.dateEpochDay },
        )
        assertTrue(database.exclusionDateDao().listForSourceYear("school", 2026).single().approved)
    }

    @Test
    fun recalculationCreatesMovedExceptionAndSeriesOutbox() = runBlocking {
        database.exclusionSourceDao().upsert(source(enabled = true).toEntity())
        database.exclusionDateDao().upsertAll(listOf(exclusionDate(LocalDate.of(2026, 8, 10)).toEntity()))
        database.recurrenceMasterDao().upsert(master().toEntity())

        RoomRecurrenceExclusionRecalculator(database).recalculate(2026)

        val exception = database.recurrenceExceptionDao().listForMaster("master-1").single()
        assertEquals("MOVED", exception.kind)
        assertEquals(Instant.parse("2026-08-11T00:00:00Z").toEpochMilli(), exception.effectiveStartEpochMs)
        assertEquals("UPSERT_SERIES", database.recurrenceOutboxDao().pending().single().operation.name)
    }

    @Test
    fun savingEmptyCandidateSelectionClearsPriorSchoolApproval() = runBlocking {
        database.exclusionSourceDao().upsert(source(enabled = true).toEntity())
        database.exclusionDateDao().upsertAll(listOf(exclusionDate(LocalDate.of(2026, 8, 10)).toEntity()))

        RoomExclusionSettingsStore(database).saveSelections(
            enabledSourceIds = setOf("school"),
            approvedCandidates = emptySet(),
            startYear = 2026,
            endYear = 2027,
        )

        assertTrue(!database.exclusionDateDao().listForSourceYear("school", 2026).single().approved)
    }

    private fun source(enabled: Boolean) = ExclusionSource(
        id = "school",
        calendarId = "2",
        displayName = "학교 학사일정",
        kind = ExclusionKind.SCHOOL_CALENDAR,
        enabled = enabled,
    )

    private fun exclusionDate(date: LocalDate) = ExclusionDate(
        sourceId = "school",
        remoteEventId = "vacation",
        date = date,
        title = "여름방학",
        approved = true,
    )

    private fun master() = RecurrenceMaster(
        id = "master-1",
        inputId = "input-1",
        transactionId = "tx-1",
        title = "방과후수업",
        startDate = LocalDate.of(2026, 8, 10),
        startTime = LocalTime.of(9, 0),
        durationMinutes = 180,
        zoneId = ZoneId.of("Asia/Seoul"),
        rule = RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            weekdays = setOf(DayOfWeek.MONDAY),
            end = RecurrenceEnd.Until(LocalDate.of(2026, 8, 31)),
        ),
        exclusionKinds = setOf(ExclusionKind.SCHOOL_CALENDAR),
        exclusionPolicy = ExclusionPolicy.MOVE_TO_NEXT_WEEKDAY,
        syncState = SyncState.PENDING,
        updatedAt = Instant.parse("2026-08-02T00:00:00Z"),
    )
}
