package com.seongho.brainassistant.core.database

import com.seongho.brainassistant.core.model.ExclusionDate
import com.seongho.brainassistant.core.model.ExclusionKind
import com.seongho.brainassistant.core.model.ExclusionPolicy
import com.seongho.brainassistant.core.model.ExclusionSource
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

fun RecurrenceMaster.toEntity(): RecurrenceMasterEntity {
    val (endType, endValue) = rule.end.persistedValue()
    return RecurrenceMasterEntity(
        id = id,
        inputId = inputId,
        transactionId = transactionId,
        title = title,
        startDateEpochDay = startDate.toEpochDay(),
        startMinuteOfDay = startTime.toSecondOfDay() / 60,
        durationMinutes = durationMinutes,
        zoneId = zoneId.id,
        frequency = rule.frequency.name,
        interval = rule.interval,
        weekdaysCsv = rule.weekdays.sortedBy(DayOfWeek::getValue).joinToString(",") { it.value.toString() },
        dayOfMonth = rule.dayOfMonth,
        ordinal = rule.ordinal,
        ordinalWeekdayIso = rule.ordinalWeekday?.value,
        endType = endType,
        endValue = endValue,
        exclusionKindsCsv = exclusionKinds.sortedBy(Enum<*>::name).joinToString(",") { it.name },
        exclusionPolicy = exclusionPolicy.name,
        googleCalendarId = googleCalendarId,
        remoteSeriesId = remoteSeriesId,
        syncState = syncState.name,
        deletedAtEpochMs = deletedAt?.toEpochMilli(),
        updatedAtEpochMs = updatedAt.toEpochMilli(),
    )
}

fun RecurrenceMasterEntity.toDomain(): RecurrenceMaster = RecurrenceMaster(
    id = id,
    inputId = inputId,
    transactionId = transactionId,
    title = title,
    startDate = LocalDate.ofEpochDay(startDateEpochDay),
    startTime = LocalTime.ofSecondOfDay(startMinuteOfDay * 60L),
    durationMinutes = durationMinutes,
    zoneId = ZoneId.of(zoneId),
    rule = RecurrenceRule(
        frequency = RecurrenceFrequency.valueOf(frequency),
        interval = interval,
        weekdays = weekdaysCsv.csvValues().map { DayOfWeek.of(it.toInt()) }.toSet(),
        dayOfMonth = dayOfMonth,
        ordinal = ordinal,
        ordinalWeekday = ordinalWeekdayIso?.let(DayOfWeek::of),
        end = recurrenceEnd(endType, endValue),
    ),
    exclusionKinds = exclusionKindsCsv.csvValues().map(ExclusionKind::valueOf).toSet(),
    exclusionPolicy = ExclusionPolicy.valueOf(exclusionPolicy),
    googleCalendarId = googleCalendarId,
    remoteSeriesId = remoteSeriesId,
    syncState = SyncState.valueOf(syncState),
    deletedAt = deletedAtEpochMs?.let(Instant::ofEpochMilli),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)

fun RecurrenceException.toEntity() = RecurrenceExceptionEntity(
    id = id,
    masterId = key.masterId,
    originalStartEpochMs = key.originalStartAt.toEpochMilli(),
    kind = kind.name,
    effectiveStartEpochMs = effectiveStartAt?.toEpochMilli(),
    effectiveEndEpochMs = effectiveEndAt?.toEpochMilli(),
    titleOverride = titleOverride,
    remoteEventId = remoteEventId,
    syncState = syncState.name,
)

fun RecurrenceExceptionEntity.toDomain() = RecurrenceException(
    id = id,
    key = OccurrenceKey(masterId, Instant.ofEpochMilli(originalStartEpochMs)),
    kind = RecurrenceExceptionKind.valueOf(kind),
    effectiveStartAt = effectiveStartEpochMs?.let(Instant::ofEpochMilli),
    effectiveEndAt = effectiveEndEpochMs?.let(Instant::ofEpochMilli),
    titleOverride = titleOverride,
    remoteEventId = remoteEventId,
    syncState = SyncState.valueOf(syncState),
)

fun ExclusionSource.toEntity(lastRefreshedAt: Instant? = null) = ExclusionSourceEntity(
    id = id,
    calendarId = calendarId,
    displayName = displayName,
    kind = kind.name,
    enabled = enabled,
    lastRefreshedAtEpochMs = lastRefreshedAt?.toEpochMilli(),
)

fun ExclusionSourceEntity.toDomain() = ExclusionSource(
    id = id,
    calendarId = calendarId,
    displayName = displayName,
    kind = ExclusionKind.valueOf(kind),
    enabled = enabled,
)

fun ExclusionDate.toEntity() = ExclusionDateEntity(
    sourceId = sourceId,
    remoteEventId = remoteEventId,
    dateEpochDay = date.toEpochDay(),
    year = date.year,
    title = title,
    approved = approved,
)

fun ExclusionDateEntity.toDomain() = ExclusionDate(
    sourceId = sourceId,
    remoteEventId = remoteEventId,
    date = LocalDate.ofEpochDay(dateEpochDay),
    title = title,
    approved = approved,
)

fun RecurrenceMaster.toUndoEntity(
    operationId: String,
    phase: UndoSnapshotPhase,
): RecurrenceUndoMasterSnapshotEntity {
    val entity = toEntity()
    return RecurrenceUndoMasterSnapshotEntity(
        operationId, phase, entity.id, entity.inputId, entity.transactionId, entity.title,
        entity.startDateEpochDay, entity.startMinuteOfDay, entity.durationMinutes, entity.zoneId,
        entity.frequency, entity.interval, entity.weekdaysCsv, entity.dayOfMonth, entity.ordinal,
        entity.ordinalWeekdayIso, entity.endType, entity.endValue, entity.exclusionKindsCsv,
        entity.exclusionPolicy, entity.googleCalendarId, entity.remoteSeriesId, entity.syncState,
        entity.deletedAtEpochMs, entity.updatedAtEpochMs,
    )
}

fun RecurrenceUndoMasterSnapshotEntity.toDomain(): RecurrenceMaster = RecurrenceMasterEntity(
    id, inputId, transactionId, title, startDateEpochDay, startMinuteOfDay, durationMinutes, zoneId,
    frequency, interval, weekdaysCsv, dayOfMonth, ordinal, ordinalWeekdayIso, endType, endValue,
    exclusionKindsCsv, exclusionPolicy, googleCalendarId, remoteSeriesId, syncState,
    deletedAtEpochMs, updatedAtEpochMs,
).toDomain()

fun RecurrenceException.toUndoEntity(
    operationId: String,
    phase: UndoSnapshotPhase,
): RecurrenceUndoExceptionSnapshotEntity {
    val entity = toEntity()
    return RecurrenceUndoExceptionSnapshotEntity(
        operationId, phase, entity.id, entity.masterId, entity.originalStartEpochMs, entity.kind,
        entity.effectiveStartEpochMs, entity.effectiveEndEpochMs, entity.titleOverride,
        entity.remoteEventId, entity.syncState,
    )
}

fun RecurrenceUndoExceptionSnapshotEntity.toDomain(): RecurrenceException = RecurrenceExceptionEntity(
    id, masterId, originalStartEpochMs, kind, effectiveStartEpochMs, effectiveEndEpochMs,
    titleOverride, remoteEventId, syncState,
).toDomain()

private fun RecurrenceEnd.persistedValue(): Pair<String, Long?> = when (this) {
    is RecurrenceEnd.Until -> "UNTIL" to date.toEpochDay()
    is RecurrenceEnd.Count -> "COUNT" to occurrences.toLong()
    RecurrenceEnd.Never -> "NEVER" to null
}

private fun recurrenceEnd(type: String, value: Long?): RecurrenceEnd = when (type) {
    "UNTIL" -> RecurrenceEnd.Until(LocalDate.ofEpochDay(requireNotNull(value)))
    "COUNT" -> RecurrenceEnd.Count(requireNotNull(value).toInt())
    "NEVER" -> RecurrenceEnd.Never
    else -> error("Unknown recurrence end type: $type")
}

private fun String.csvValues(): List<String> = split(',').map(String::trim).filter(String::isNotEmpty)
