package com.seongho.brainassistant.core.sync

import com.seongho.brainassistant.core.calendar.RecurrenceCalendarGateway
import com.seongho.brainassistant.core.calendar.RemoteSeries
import com.seongho.brainassistant.core.database.RecurrenceOutboxOperation
import com.seongho.brainassistant.core.model.ExclusionPolicy
import com.seongho.brainassistant.core.model.RecurrenceFrequency
import com.seongho.brainassistant.core.model.RecurrenceMaster
import com.seongho.brainassistant.core.model.RecurrenceRule
import com.seongho.brainassistant.core.model.SyncState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceSyncEngineTest {
    @Test
    fun upsertSeriesDeletesOutboxOnlyAfterGatewaySuccessAndPersistsRemoteId() = kotlinx.coroutines.test.runTest {
        val store = FakeStore(master())
        val gateway = FakeGateway()

        val result = RecurrenceSyncEngine(store, gateway).processNext()

        assertEquals(RecurrenceSyncResult.SYNCED, result)
        assertEquals("remote-1", store.master.remoteSeriesId)
        assertTrue(store.deleted)
    }

    @Test
    fun failedGatewayKeepsOutboxAndRecordsRetryableFailure() = kotlinx.coroutines.test.runTest {
        val store = FakeStore(master())
        val gateway = FakeGateway(fail = true)

        val result = RecurrenceSyncEngine(store, gateway).processNext()

        assertEquals(RecurrenceSyncResult.RETRY, result)
        assertEquals(1, store.failedAttempts)
        assertEquals(null, store.master.remoteSeriesId)
        assertTrue(!store.deleted)
    }

    private fun master() = RecurrenceMaster(
        id = "master-1", inputId = "input-1", transactionId = "tx-1", title = "방과후수업",
        startDate = LocalDate.of(2026, 8, 3), startTime = LocalTime.of(9, 0), durationMinutes = 180,
        zoneId = ZoneId.of("Asia/Seoul"), rule = RecurrenceRule(RecurrenceFrequency.WEEKLY),
        exclusionKinds = emptySet(), exclusionPolicy = ExclusionPolicy.SKIP, syncState = SyncState.PENDING,
        updatedAt = Instant.parse("2026-08-02T00:00:00Z"),
    )

    private class FakeStore(initial: RecurrenceMaster) : RecurrenceSyncStore {
        var master = initial
        var deleted = false
        var failedAttempts = 0
        override suspend fun next(): RecurrenceSyncCommand? = RecurrenceSyncCommand("row-1", master.id, null, RecurrenceOutboxOperation.UPSERT_SERIES)
        override suspend fun master(id: String): RecurrenceMaster? = master.takeIf { it.id == id }
        override suspend fun exceptions(masterId: String) = emptyList<com.seongho.brainassistant.core.model.RecurrenceException>()
        override suspend fun updateMaster(master: RecurrenceMaster) { this.master = master }
        override suspend fun delete(commandId: String) { deleted = true }
        override suspend fun markFailed(commandId: String, error: String) { failedAttempts++ }
    }

    private class FakeGateway(private val fail: Boolean = false) : RecurrenceCalendarGateway {
        override suspend fun upsertSeries(master: RecurrenceMaster, rrule: String, exdates: List<Instant>): RemoteSeries {
            if (fail) error("calendar unavailable")
            return RemoteSeries("remote-1", Instant.parse("2026-08-02T01:00:00Z"))
        }
        override suspend fun deleteSeries(calendarId: String, remoteSeriesId: String) = Unit
        override suspend fun upsertDetachedOccurrence(master: RecurrenceMaster, occurrence: com.seongho.brainassistant.core.model.RecurrenceOccurrence) =
            com.seongho.brainassistant.core.calendar.RemoteOccurrence("detached", Instant.now())
        override suspend fun deleteDetachedOccurrence(calendarId: String, remoteEventId: String) = Unit
    }
}
