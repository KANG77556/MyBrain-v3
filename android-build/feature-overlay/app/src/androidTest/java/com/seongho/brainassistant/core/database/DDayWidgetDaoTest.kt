package com.seongho.brainassistant.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DDayWidgetDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ddayDaoStoresSelectsRepresentativeAndRestoresSoftDelete() = runBlocking {
        val dao = database.ddayDao()
        val today = 20_000L
        val now = 1_700_000_000_000L
        val pinned = ddayEntity(
            id = "pinned",
            targetDateEpochDay = today + 10,
            isPinned = true,
            importance = 2,
            updatedAtEpochMs = now,
        )
        val closer = ddayEntity(
            id = "closer",
            targetDateEpochDay = today + 1,
            isPinned = false,
            importance = 5,
            updatedAtEpochMs = now,
        )

        dao.upsert(closer)
        dao.upsert(pinned)

        assertEquals(listOf("pinned", "closer"), dao.observeActive().first().map { it.id })
        assertEquals("pinned", dao.getRepresentative(today)?.id)

        dao.softDelete("pinned", now + 1)
        assertNull(dao.getRepresentative(today)?.takeIf { it.id == "pinned" })

        dao.restore("pinned", now + 2)
        assertEquals("pinned", dao.get("pinned")?.id)
    }

    @Test
    fun representativeSkipsElapsedItemPastItsOwnArchiveWindow() = runBlocking {
        val dao = database.ddayDao()
        val today = 20_000L
        val now = 1_700_000_000_000L
        val expiredPinned = ddayEntity(
            id = "expired-pinned",
            targetDateEpochDay = today - 10,
            isPinned = true,
            importance = 3,
            archiveAfterDays = 7,
            updatedAtEpochMs = now,
        )
        val stillVisible = ddayEntity(
            id = "still-visible",
            targetDateEpochDay = today - 5,
            isPinned = false,
            importance = 2,
            archiveAfterDays = 7,
            updatedAtEpochMs = now,
        )

        dao.upsert(expiredPinned)
        dao.upsert(stillVisible)

        assertEquals("still-visible", dao.getRepresentative(today)?.id)
    }

    @Test
    fun widgetDaosReplaceConfigAndManageSnapshotCache() = runBlocking {
        val configDao = database.widgetConfigDao()
        val snapshotDao = database.widgetSnapshotDao()
        val first = WidgetConfigEntity(
            widgetId = 7,
            widgetType = "DDAY",
            sizeClass = "COMPACT",
            calendarId = null,
            filtersCsv = "ACTIVE",
            themeMode = "SYSTEM",
            maskSensitivePreview = true,
            updatedAtEpochMs = 100L,
        )
        val replaced = first.copy(themeMode = "DARK", updatedAtEpochMs = 200L)
        val snapshot = WidgetSnapshotEntity(
            widgetId = 7,
            widgetType = "DDAY",
            payloadJson = "{\"title\":\"보고서\"}",
            generatedAtEpochMs = 300L,
        )

        configDao.upsert(first)
        configDao.upsert(replaced)
        snapshotDao.upsert(snapshot)

        assertEquals("DARK", configDao.get(7)?.themeMode)
        assertEquals(snapshot, snapshotDao.get(7))

        snapshotDao.delete(7)
        assertNull(snapshotDao.get(7))

        configDao.delete(7)
        assertNull(configDao.get(7))
    }

    private fun ddayEntity(
        id: String,
        targetDateEpochDay: Long,
        isPinned: Boolean,
        importance: Int,
        archiveAfterDays: Int = 7,
        updatedAtEpochMs: Long,
    ) = DDayEntity(
        id = id,
        inputId = "input-$id",
        transactionId = "tx-$id",
        title = id,
        targetDateEpochDay = targetDateEpochDay,
        category = "DEADLINE",
        importance = importance,
        isPinned = isPinned,
        showElapsedDays = true,
        archiveAfterDays = archiveAfterDays,
        recurrenceRule = null,
        linkedTaskId = null,
        linkedCalendarId = null,
        reminderOffsetsCsv = "7,3,1,0",
        status = "ACTIVE",
        deletedAtEpochMs = null,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}
