package com.seongho.brainassistant.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecurrenceDaoTest {
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
    fun masterDaoListsOnlySeriesOverlappingRange() = runBlocking {
        val dao = database.recurrenceMasterDao()
        dao.upsert(masterEntity("active", 20_000, "UNTIL", 20_100))
        dao.upsert(masterEntity("ended", 19_000, "UNTIL", 19_999))
        dao.upsert(masterEntity("future", 21_000, "NEVER", null))
        dao.upsert(masterEntity("deleted", 20_000, "NEVER", null, deletedAtEpochMs = 5L))

        assertEquals(listOf("active"), dao.listOverlapping(20_010, 20_020).map { it.id })
    }

    @Test
    fun exclusionDateDaoReplacesRowsOnlyForSourceAndYear() = runBlocking {
        val dao = database.exclusionDateDao()
        dao.upsertAll(
            listOf(
                exclusion("school", "old-2026", 2026, 20_000),
                exclusion("school", "keep-2027", 2027, 20_400),
                exclusion("holiday", "keep-other", 2026, 20_001),
            ),
        )

        dao.replaceForSourceYear(
            sourceId = "school",
            year = 2026,
            rows = listOf(exclusion("school", "new-2026", 2026, 20_002)),
        )

        assertEquals(listOf("new-2026"), dao.listForSourceYear("school", 2026).map { it.remoteEventId })
        assertEquals(listOf("keep-2027"), dao.listForSourceYear("school", 2027).map { it.remoteEventId })
        assertEquals(listOf("keep-other"), dao.listForSourceYear("holiday", 2026).map { it.remoteEventId })
    }

    private fun masterEntity(
        id: String,
        startDateEpochDay: Long,
        endType: String,
        endValue: Long?,
        deletedAtEpochMs: Long? = null,
    ) = RecurrenceMasterEntity(
        id = id,
        inputId = "input-$id",
        transactionId = "tx-$id",
        title = id,
        startDateEpochDay = startDateEpochDay,
        startMinuteOfDay = 540,
        durationMinutes = 60,
        zoneId = "Asia/Seoul",
        frequency = "WEEKLY",
        interval = 1,
        weekdaysCsv = "1",
        dayOfMonth = null,
        ordinal = null,
        ordinalWeekdayIso = null,
        endType = endType,
        endValue = endValue,
        exclusionKindsCsv = "",
        exclusionPolicy = "SKIP",
        googleCalendarId = "primary",
        remoteSeriesId = null,
        syncState = "PENDING",
        deletedAtEpochMs = deletedAtEpochMs,
        updatedAtEpochMs = 1L,
    )

    private fun exclusion(sourceId: String, remoteId: String, year: Int, day: Long) = ExclusionDateEntity(
        sourceId = sourceId,
        remoteEventId = remoteId,
        dateEpochDay = day,
        year = year,
        title = remoteId,
        approved = true,
    )
}
