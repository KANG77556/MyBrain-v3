package com.seongho.brainassistant.core.database

import com.seongho.brainassistant.core.model.AnalysisRecord
import com.seongho.brainassistant.core.model.CalendarItem
import com.seongho.brainassistant.core.model.DDayCategory
import com.seongho.brainassistant.core.model.DDayItem
import com.seongho.brainassistant.core.model.InputRecord
import com.seongho.brainassistant.core.model.ItemStatus
import com.seongho.brainassistant.core.model.ItemType
import com.seongho.brainassistant.core.model.NoteItem
import com.seongho.brainassistant.core.model.OutboxOperation
import com.seongho.brainassistant.core.model.SyncOutboxItem
import com.seongho.brainassistant.core.model.SyncState
import com.seongho.brainassistant.core.model.TaskItem
import com.seongho.brainassistant.core.model.WidgetConfig
import com.seongho.brainassistant.core.model.WidgetSizeClass
import com.seongho.brainassistant.core.model.WidgetSnapshot
import com.seongho.brainassistant.core.model.WidgetThemeMode
import com.seongho.brainassistant.core.model.WidgetType
import java.time.Instant
import java.time.LocalDate

fun InputRecord.toEntity() = InputEntity(id, rawText, source, createdAt.toEpochMilli())

fun NoteItem.toEntity() = NoteEntity(
    id,
    inputId,
    transactionId,
    title,
    body,
    status.name,
    deletedAt?.toEpochMilli(),
    updatedAt.toEpochMilli(),
)

fun TaskItem.toEntity() = TaskEntity(
    id,
    inputId,
    transactionId,
    kind.name,
    title,
    dueAt?.toEpochMilli(),
    priority,
    estimatedMinutes,
    status.name,
    deletedAt?.toEpochMilli(),
    updatedAt.toEpochMilli(),
)

fun CalendarItem.toEntity() = CalendarEntity(
    id,
    inputId,
    transactionId,
    title,
    startAt.toEpochMilli(),
    endAt.toEpochMilli(),
    googleCalendarId,
    googleEventId,
    externalEtag,
    externalUpdatedAt?.toEpochMilli(),
    syncState.name,
    deletedAt?.toEpochMilli(),
    updatedAt.toEpochMilli(),
)

fun AnalysisRecord.toEntity() = AnalysisEntity(
    id,
    inputId,
    analyzer,
    confidence,
    clarificationFields.joinToString(",") { it.name },
    createdAt.toEpochMilli(),
)

fun SyncOutboxItem.toEntity() = SyncOutboxEntity(
    id,
    localCalendarId,
    operation.name,
    createdAt.toEpochMilli(),
    attemptCount,
    lastError,
)

fun DDayItem.toEntity() = DDayEntity(
    id = id,
    inputId = inputId,
    transactionId = transactionId,
    title = title,
    targetDateEpochDay = targetDate.toEpochDay(),
    category = category.name,
    importance = importance,
    isPinned = isPinned,
    showElapsedDays = showElapsedDays,
    archiveAfterDays = archiveAfterDays,
    recurrenceRule = recurrenceRule,
    linkedTaskId = linkedTaskId,
    linkedCalendarId = linkedCalendarId,
    reminderOffsetsCsv = reminderOffsets.sortedDescending().joinToString(","),
    status = status.name,
    deletedAtEpochMs = deletedAt?.toEpochMilli(),
    updatedAtEpochMs = updatedAt.toEpochMilli(),
)

fun WidgetConfig.toEntity(updatedAt: Instant) = WidgetConfigEntity(
    widgetId = widgetId,
    widgetType = widgetType.name,
    sizeClass = sizeClass.name,
    calendarId = calendarId,
    filtersCsv = filters.sorted().joinToString(","),
    themeMode = themeMode.name,
    maskSensitivePreview = maskSensitivePreview,
    updatedAtEpochMs = updatedAt.toEpochMilli(),
)

fun WidgetSnapshot.toEntity() = WidgetSnapshotEntity(
    widgetId = widgetId,
    widgetType = type.name,
    payloadJson = payloadJson,
    generatedAtEpochMs = generatedAt.toEpochMilli(),
)

fun NoteEntity.toDomain() = NoteItem(
    id,
    inputId,
    transactionId,
    title,
    body,
    ItemStatus.valueOf(status),
    deletedAtEpochMs?.let(Instant::ofEpochMilli),
    Instant.ofEpochMilli(updatedAtEpochMs),
)

fun TaskEntity.toDomain() = TaskItem(
    id,
    inputId,
    transactionId,
    ItemType.valueOf(itemType),
    title,
    dueAtEpochMs?.let(Instant::ofEpochMilli),
    priority,
    estimatedMinutes,
    ItemStatus.valueOf(status),
    deletedAtEpochMs?.let(Instant::ofEpochMilli),
    Instant.ofEpochMilli(updatedAtEpochMs),
)

fun CalendarEntity.toDomain() = CalendarItem(
    id,
    inputId,
    transactionId,
    title,
    Instant.ofEpochMilli(startAtEpochMs),
    Instant.ofEpochMilli(endAtEpochMs),
    googleCalendarId,
    googleEventId,
    externalEtag,
    externalUpdatedAtEpochMs?.let(Instant::ofEpochMilli),
    SyncState.valueOf(syncState),
    deletedAtEpochMs?.let(Instant::ofEpochMilli),
    Instant.ofEpochMilli(updatedAtEpochMs),
)

fun SyncOutboxEntity.toDomain() = SyncOutboxItem(
    id,
    localCalendarId,
    OutboxOperation.valueOf(operation),
    Instant.ofEpochMilli(createdAtEpochMs),
    attemptCount,
    lastError,
)

fun DDayEntity.toDomain() = DDayItem(
    id = id,
    inputId = inputId,
    transactionId = transactionId,
    title = title,
    targetDate = LocalDate.ofEpochDay(targetDateEpochDay),
    category = DDayCategory.valueOf(category),
    importance = importance,
    isPinned = isPinned,
    showElapsedDays = showElapsedDays,
    archiveAfterDays = archiveAfterDays,
    recurrenceRule = recurrenceRule,
    linkedTaskId = linkedTaskId,
    linkedCalendarId = linkedCalendarId,
    reminderOffsets = reminderOffsetsCsv.csvValues().map(String::toInt).toSet(),
    status = ItemStatus.valueOf(status),
    deletedAt = deletedAtEpochMs?.let(Instant::ofEpochMilli),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)

fun WidgetConfigEntity.toDomain() = WidgetConfig(
    widgetId = widgetId,
    widgetType = WidgetType.valueOf(widgetType),
    sizeClass = WidgetSizeClass.valueOf(sizeClass),
    calendarId = calendarId,
    filters = filtersCsv.csvValues().toSet(),
    themeMode = WidgetThemeMode.valueOf(themeMode),
    maskSensitivePreview = maskSensitivePreview,
)

fun WidgetSnapshotEntity.toDomain() = WidgetSnapshot(
    widgetId = widgetId,
    type = WidgetType.valueOf(widgetType),
    payloadJson = payloadJson,
    generatedAt = Instant.ofEpochMilli(generatedAtEpochMs),
)

private fun String.csvValues(): List<String> =
    split(',').map(String::trim).filter(String::isNotEmpty)
