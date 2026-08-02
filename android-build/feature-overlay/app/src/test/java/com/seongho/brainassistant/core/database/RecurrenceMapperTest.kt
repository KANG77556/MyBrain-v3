package com.seongho.brainassistant.core.database

import com.seongho.brainassistant.core.model.ExclusionKind
import com.seongho.brainassistant.core.model.ExclusionPolicy
import com.seongho.brainassistant.core.model.OccurrenceKey
import com.seongho.brainassistant.core.model.RecurrenceEnd
import com.seongho.brainassistant.core.model.RecurrenceException
import com.seongho.brainassistant.core.model.RecurrenceExceptionKind
import com.seongho.brainassistant.core.model.RecurrenceFrequency
import com.seongho.brainassistant.core.model.RecurrenceMaster
import com.seongho.brainassistant.core.model.RecurrenceRule
import com.seongho.brainassistant.core.model.SyncState
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceMapperTest {
    @Test
    fun masterRoundTripPreservesRuleEndWeekdaysAndCalendarIdentity() {
        val master = master(end = RecurrenceEnd.Until(LocalDate.of(2026, 12, 31)))

        val entity = master.toEntity()

        assertEquals("1,3,5", entity.weekdaysCsv)
        assertEquals("UNTIL", entity.endType)
        assertEquals(master, entity.toDomain())
    }

    @Test
    fun countEndRoundTripPreservesOccurrenceCount() {
        val master = master(end = RecurrenceEnd.Count(12))

        assertEquals(master, master.toEntity().toDomain())
    }

    @Test
    fun exceptionRoundTripPreservesOriginalOccurrenceAndMove() {
        val exception = RecurrenceException(
            id = "exception-1",
            key = OccurrenceKey("master-1", Instant.parse("2026-08-10T00:00:00Z")),
            kind = RecurrenceExceptionKind.MOVED,
            effectiveStartAt = Instant.parse("2026-08-11T00:00:00Z"),
            effectiveEndAt = Instant.parse("2026-08-11T03:00:00Z"),
            titleOverride = "보강 수업",
            remoteEventId = "remote-event-1",
            syncState = SyncState.SYNCED,
        )

        assertEquals(exception, exception.toEntity().toDomain())
    }

    @Test
    fun undoSnapshotsAndOutboxUseStableNormalizedValues() {
        val master = master(end = RecurrenceEnd.Never)
        val exception = RecurrenceException(
            id = "exception-1",
            key = OccurrenceKey(master.id, Instant.parse("2026-08-10T00:00:00Z")),
            kind = RecurrenceExceptionKind.CANCELLED,
        )

        val masterSnapshot = master.toUndoEntity("operation-1", UndoSnapshotPhase.BEFORE)
        val exceptionSnapshot = exception.toUndoEntity("operation-1", UndoSnapshotPhase.BEFORE)
        val outbox = RecurrenceOutboxEntity(
            id = "outbox-1",
            masterId = master.id,
            exceptionId = exception.id,
            operation = RecurrenceOutboxOperation.DELETE_DETACHED_OCCURRENCE,
            createdAtEpochMs = 100L,
            attemptCount = 0,
            lastError = null,
        )

        assertEquals(master, masterSnapshot.toDomain())
        assertEquals(exception, exceptionSnapshot.toDomain())
        assertEquals("DELETE_DETACHED_OCCURRENCE", outbox.operation.name)
    }

    private fun master(end: RecurrenceEnd) = RecurrenceMaster(
        id = "master-1",
        inputId = "input-1",
        transactionId = "transaction-1",
        title = "방과후수업",
        startDate = LocalDate.of(2026, 8, 10),
        startTime = LocalTime.of(9, 0),
        durationMinutes = 180,
        zoneId = ZoneId.of("Asia/Seoul"),
        rule = RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 2,
            weekdays = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            end = end,
        ),
        exclusionKinds = setOf(ExclusionKind.SCHOOL_CALENDAR, ExclusionKind.KOREAN_PUBLIC_HOLIDAY),
        exclusionPolicy = ExclusionPolicy.MOVE_TO_NEXT_WEEKDAY,
        googleCalendarId = "school-calendar",
        remoteSeriesId = "remote-series-1",
        syncState = SyncState.PENDING,
        deletedAt = null,
        updatedAt = Instant.parse("2026-08-02T03:00:00Z"),
    )
}
