package com.seongho.brainassistant.data

import com.seongho.brainassistant.core.model.OccurrenceKey
import com.seongho.brainassistant.core.model.RecurrenceDraft
import com.seongho.brainassistant.core.model.RecurrenceEnd
import com.seongho.brainassistant.core.model.RecurrenceException
import com.seongho.brainassistant.core.model.RecurrenceExceptionKind
import com.seongho.brainassistant.core.model.RecurrenceFrequency
import com.seongho.brainassistant.core.model.RecurrenceMaster
import com.seongho.brainassistant.core.model.RecurrenceMutation
import com.seongho.brainassistant.core.model.RecurrenceMutationKind
import com.seongho.brainassistant.core.model.RecurrenceRule
import com.seongho.brainassistant.core.model.RecurrenceScope
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceMutationPolicyTest {
    private val policy = RecurrenceMutationPolicy()
    private val occurrenceStart = Instant.parse("2026-08-17T00:00:00Z")
    private val now = Instant.parse("2026-08-02T05:00:00Z")

    @Test
    fun thisOccurrenceDeleteCreatesCancellationException() {
        val plan = policy.plan(master(), emptyList(), mutation(RecurrenceScope.THIS_OCCURRENCE, RecurrenceMutationKind.DELETE), now)

        assertEquals(listOf("master-1"), plan.upsertMasters.map { it.id })
        assertEquals(RecurrenceExceptionKind.CANCELLED, plan.upsertExceptions.single().kind)
        assertEquals(occurrenceStart, plan.upsertExceptions.single().key.originalStartAt)
    }

    @Test
    fun thisAndFollowingUpdateSplitsMasterAndRekeysFollowingExceptions() {
        val following = RecurrenceException(
            id = "future-exception",
            key = OccurrenceKey("master-1", Instant.parse("2026-08-24T00:00:00Z")),
            kind = RecurrenceExceptionKind.CANCELLED,
        )

        val plan = policy.plan(
            master(),
            listOf(following),
            mutation(RecurrenceScope.THIS_AND_FOLLOWING, RecurrenceMutationKind.UPDATE, replacement()),
            now,
        )

        val old = plan.upsertMasters.first { it.id == "master-1" }
        val split = plan.upsertMasters.first { it.id == "master-2" }
        assertEquals(RecurrenceEnd.Until(LocalDate.of(2026, 8, 16)), old.rule.end)
        assertEquals(LocalDate.of(2026, 8, 17), split.startDate)
        assertEquals("master-2", plan.upsertExceptions.single().key.masterId)
    }

    @Test
    fun allOccurrencesUpdateKeepsIdentityAndReportsIncompatibleExceptions() {
        val oldException = RecurrenceException(
            id = "old-exception",
            key = OccurrenceKey("master-1", occurrenceStart),
            kind = RecurrenceExceptionKind.MODIFIED,
            effectiveStartAt = occurrenceStart.plusSeconds(3600),
            effectiveEndAt = occurrenceStart.plusSeconds(7200),
        )

        val plan = policy.plan(
            master(),
            listOf(oldException),
            mutation(RecurrenceScope.ALL_OCCURRENCES, RecurrenceMutationKind.UPDATE, replacement()),
            now,
        )

        assertEquals("master-1", plan.upsertMasters.single().id)
        assertEquals("변경 수업", plan.upsertMasters.single().title)
        assertEquals(listOf("old-exception"), plan.orphanedExceptionIds)
    }

    @Test
    fun allOccurrencesDeleteSoftDeletesMaster() {
        val plan = policy.plan(master(), emptyList(), mutation(RecurrenceScope.ALL_OCCURRENCES, RecurrenceMutationKind.DELETE), now)

        assertEquals(now, plan.upsertMasters.single().deletedAt)
        assertTrue(plan.upsertExceptions.isEmpty())
    }

    private fun mutation(
        scope: RecurrenceScope,
        kind: RecurrenceMutationKind,
        replacement: RecurrenceDraft? = null,
    ) = RecurrenceMutation(OccurrenceKey("master-1", occurrenceStart), scope, kind, replacement)

    private fun master() = RecurrenceMaster(
        id = "master-1",
        inputId = "input-1",
        transactionId = "tx-1",
        title = "기존 수업",
        startDate = LocalDate.of(2026, 8, 10),
        startTime = LocalTime.of(9, 0),
        durationMinutes = 180,
        zoneId = ZoneId.of("Asia/Seoul"),
        rule = RecurrenceRule(RecurrenceFrequency.WEEKLY),
        exclusionKinds = emptySet(),
        exclusionPolicy = com.seongho.brainassistant.core.model.ExclusionPolicy.SKIP,
        updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
    )

    private fun replacement() = RecurrenceDraft(
        localId = "master-2",
        title = "변경 수업",
        startDate = LocalDate.of(2026, 8, 17),
        startTime = LocalTime.of(10, 0),
        durationMinutes = 120,
        rule = RecurrenceRule(RecurrenceFrequency.WEEKLY),
        confidence = 1.0,
    )
}
