package com.seongho.brainassistant.data

import com.seongho.brainassistant.core.model.OccurrenceKey
import com.seongho.brainassistant.core.model.RecurrenceDraft
import com.seongho.brainassistant.core.model.RecurrenceEnd
import com.seongho.brainassistant.core.model.RecurrenceException
import com.seongho.brainassistant.core.model.RecurrenceExceptionKind
import com.seongho.brainassistant.core.model.RecurrenceMaster
import com.seongho.brainassistant.core.model.RecurrenceMutation
import com.seongho.brainassistant.core.model.RecurrenceMutationKind
import com.seongho.brainassistant.core.model.RecurrenceScope
import com.seongho.brainassistant.core.model.SyncState
import java.time.Duration
import java.time.Instant

data class RecurrenceMutationPlan(
    val upsertMasters: List<RecurrenceMaster>,
    val upsertExceptions: List<RecurrenceException>,
    val deleteExceptionIds: List<String> = emptyList(),
    val orphanedExceptionIds: List<String> = emptyList(),
)

class RecurrenceMutationPolicy {
    fun plan(
        master: RecurrenceMaster,
        exceptions: List<RecurrenceException>,
        command: RecurrenceMutation,
        now: Instant,
    ): RecurrenceMutationPlan {
        require(command.key.masterId == master.id)
        return when (command.scope) {
            RecurrenceScope.THIS_OCCURRENCE -> thisOccurrence(master, command, now)
            RecurrenceScope.THIS_AND_FOLLOWING -> thisAndFollowing(master, exceptions, command, now)
            RecurrenceScope.ALL_OCCURRENCES -> allOccurrences(master, exceptions, command, now)
        }
    }

    private fun thisOccurrence(
        master: RecurrenceMaster,
        command: RecurrenceMutation,
        now: Instant,
    ): RecurrenceMutationPlan {
        val exception = when (command.kind) {
            RecurrenceMutationKind.DELETE -> RecurrenceException(
                id = exceptionId(command.key),
                key = command.key,
                kind = RecurrenceExceptionKind.CANCELLED,
            )
            RecurrenceMutationKind.UPDATE -> {
                val replacement = requireNotNull(command.replacement)
                val start = replacement.startDate.atTime(replacement.startTime)
                    .atZone(replacement.zoneId).toInstant()
                RecurrenceException(
                    id = exceptionId(command.key),
                    key = command.key,
                    kind = RecurrenceExceptionKind.MODIFIED,
                    effectiveStartAt = start,
                    effectiveEndAt = start.plus(Duration.ofMinutes(replacement.durationMinutes.toLong())),
                    titleOverride = replacement.title,
                )
            }
        }
        return RecurrenceMutationPlan(
            upsertMasters = listOf(master.copy(updatedAt = now)),
            upsertExceptions = listOf(exception),
        )
    }

    private fun thisAndFollowing(
        master: RecurrenceMaster,
        exceptions: List<RecurrenceException>,
        command: RecurrenceMutation,
        now: Instant,
    ): RecurrenceMutationPlan {
        val splitDate = command.key.originalStartAt.atZone(master.zoneId).toLocalDate()
        val shortened = master.copy(
            rule = master.rule.copy(end = RecurrenceEnd.Until(splitDate.minusDays(1))),
            syncState = SyncState.PENDING,
            updatedAt = now,
        )
        val following = exceptions.filter { !it.key.originalStartAt.isBefore(command.key.originalStartAt) }
        if (command.kind == RecurrenceMutationKind.DELETE) {
            return RecurrenceMutationPlan(
                upsertMasters = listOf(shortened),
                upsertExceptions = emptyList(),
                deleteExceptionIds = following.map(RecurrenceException::id),
            )
        }
        val replacement = requireNotNull(command.replacement)
        val split = replacement.toMaster(master, replacement.localId, now)
        return RecurrenceMutationPlan(
            upsertMasters = listOf(shortened, split),
            upsertExceptions = following.map { it.copy(key = it.key.copy(masterId = split.id)) },
        )
    }

    private fun allOccurrences(
        master: RecurrenceMaster,
        exceptions: List<RecurrenceException>,
        command: RecurrenceMutation,
        now: Instant,
    ): RecurrenceMutationPlan = when (command.kind) {
        RecurrenceMutationKind.DELETE -> RecurrenceMutationPlan(
            upsertMasters = listOf(master.copy(deletedAt = now, syncState = SyncState.PENDING, updatedAt = now)),
            upsertExceptions = emptyList(),
        )
        RecurrenceMutationKind.UPDATE -> {
            val replacement = requireNotNull(command.replacement)
            RecurrenceMutationPlan(
                upsertMasters = listOf(replacement.toMaster(master, master.id, now)),
                upsertExceptions = emptyList(),
                orphanedExceptionIds = exceptions.map(RecurrenceException::id),
            )
        }
    }

    private fun RecurrenceDraft.toMaster(base: RecurrenceMaster, id: String, now: Instant) = RecurrenceMaster(
        id = id,
        inputId = base.inputId,
        transactionId = base.transactionId,
        title = title,
        startDate = startDate,
        startTime = startTime,
        durationMinutes = durationMinutes,
        zoneId = zoneId,
        rule = rule,
        exclusionKinds = exclusionKinds,
        exclusionPolicy = exclusionPolicy,
        googleCalendarId = base.googleCalendarId,
        remoteSeriesId = if (id == base.id) base.remoteSeriesId else null,
        syncState = SyncState.PENDING,
        deletedAt = null,
        updatedAt = now,
    )

    private fun exceptionId(key: OccurrenceKey): String =
        "${key.masterId}:${key.originalStartAt.toEpochMilli()}"
}
