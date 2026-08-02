package com.seongho.brainassistant.data

import androidx.room.withTransaction
import com.seongho.brainassistant.core.calendar.RemoteCalendarEvent
import com.seongho.brainassistant.core.database.*
import com.seongho.brainassistant.core.model.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomBrainRepository(private val database: AppDatabase) : BrainRepository {
    private val inputs = database.inputDao()
    private val notes = database.noteDao()
    private val tasks = database.taskDao()
    private val calendars = database.calendarDao()
    private val dDays = database.ddayDao()
    private val analyses = database.analysisDao()
    private val outbox = database.syncOutboxDao()

    override suspend fun saveInput(input: InputRecord) = inputs.upsert(input.toEntity())
    override suspend fun saveNote(note: NoteItem) = notes.upsert(note.toEntity())
    override suspend fun saveTask(task: TaskItem) = tasks.upsert(task.toEntity())
    override suspend fun saveDDay(item: DDayItem) = dDays.upsert(item.toEntity())

    override suspend fun saveCalendar(item: CalendarItem, enqueue: Boolean) {
        database.withTransaction {
            calendars.upsert(item.toEntity())
            if (enqueue) {
                val operation = if (item.googleEventId == null) OutboxOperation.CREATE else OutboxOperation.UPDATE
                outbox.upsert(SyncOutboxItem(localCalendarId = item.id, operation = operation, createdAt = Instant.now()).toEntity())
            }
        }
    }

    override suspend fun saveAnalysis(record: AnalysisRecord) = analyses.upsert(record.toEntity())

    override suspend fun saveParsedItems(inputId: String, items: List<ParsedItem>, transactionId: String): PersistedItems {
        val now = Instant.now()
        val noteItems = items.filter { it.type == ItemType.NOTE }.map {
            NoteItem(id = it.localId, inputId = inputId, transactionId = transactionId, title = it.title, body = it.body.ifBlank { it.title }, updatedAt = now)
        }
        val taskItems = items.filter { it.type == ItemType.TASK }.map {
            TaskItem(id = it.localId, inputId = inputId, transactionId = transactionId, kind = it.type, title = it.title, dueAt = it.dueAt, priority = it.priority.coerceIn(1, 3), estimatedMinutes = it.estimatedMinutes, updatedAt = now)
        }
        val eventItems = items.filter { it.type == ItemType.EVENT && it.startAt != null }.map {
            val start = requireNotNull(it.startAt)
            CalendarItem(id = it.localId, inputId = inputId, transactionId = transactionId, title = it.title, startAt = start, endAt = it.endAt ?: start.plus(Duration.ofHours(1)), syncState = SyncState.PENDING, updatedAt = now)
        }
        val zone = ZoneId.of("Asia/Seoul")
        val taskIds = taskItems.mapTo(mutableSetOf()) { it.id }
        val eventIds = eventItems.mapTo(mutableSetOf()) { it.id }
        val dDayItems = items.filter { it.type == ItemType.D_DAY }.map { parsed ->
            val targetDate = parsed.targetDate
                ?: parsed.dueAt?.atZone(zone)?.toLocalDate()
                ?: parsed.startAt?.atZone(zone)?.toLocalDate()
                ?: error("D-Day에는 날짜가 필요합니다.")
            DDayItem(
                id = parsed.localId,
                inputId = inputId,
                transactionId = transactionId,
                title = parsed.title,
                targetDate = targetDate,
                category = DDayCategory.DEADLINE,
                importance = parsed.priority.coerceIn(1, 3),
                linkedTaskId = parsed.linkedLocalIds.firstOrNull(taskIds::contains),
                linkedCalendarId = parsed.linkedLocalIds.firstOrNull(eventIds::contains),
                reminderOffsets = parsed.reminderOffsets.ifEmpty { setOf(7, 3, 1, 0) },
                updatedAt = now,
            )
        }
        database.withTransaction {
            noteItems.forEach { notes.upsert(it.toEntity()) }
            taskItems.forEach { tasks.upsert(it.toEntity()) }
            dDayItems.forEach { dDays.upsert(it.toEntity()) }
            eventItems.forEach {
                calendars.upsert(it.toEntity())
                outbox.upsert(SyncOutboxItem(localCalendarId = it.id, operation = OutboxOperation.CREATE, createdAt = now).toEntity())
            }
        }
        return PersistedItems(transactionId, noteItems, taskItems, eventItems, dDayItems)
    }

    override suspend fun confirmReviewedItems(inputId: String, items: List<ParsedItem>): PersistedItems =
        saveParsedItems(inputId, items, UUID.randomUUID().toString())

    override suspend fun softDeleteTask(id: String, deletedAt: Instant) = tasks.softDelete(id, deletedAt.toEpochMilli())

    override suspend fun softDeleteCalendar(id: String, deletedAt: Instant) {
        database.withTransaction {
            calendars.softDelete(id, deletedAt.toEpochMilli())
            outbox.upsert(SyncOutboxItem(localCalendarId = id, operation = OutboxOperation.DELETE, createdAt = deletedAt).toEntity())
        }
    }

    override suspend fun softDeleteByTransaction(transactionId: String, deletedAt: Instant) {
        database.withTransaction {
            val calendarItems = calendars.listByTransaction(transactionId).map(CalendarEntity::toDomain)
            notes.softDeleteByTransaction(transactionId, deletedAt.toEpochMilli())
            tasks.softDeleteByTransaction(transactionId, deletedAt.toEpochMilli())
            dDays.softDeleteByTransaction(transactionId, deletedAt.toEpochMilli())
            calendars.softDeleteByTransaction(transactionId, deletedAt.toEpochMilli())
            calendarItems.forEach { item ->
                outbox.deleteByLocalCalendarId(item.id)
                if (item.googleEventId != null) {
                    outbox.upsert(
                        SyncOutboxItem(
                            localCalendarId = item.id,
                            operation = OutboxOperation.DELETE,
                            createdAt = deletedAt,
                        ).toEntity()
                    )
                }
            }
        }
    }

    override suspend fun restoreItem(item: TrashItem) {
        val now = Instant.now().toEpochMilli()
        when (item.type) {
            ItemType.NOTE -> notes.restore(item.id, now)
            ItemType.TASK -> tasks.restore(item.id, now)
            ItemType.D_DAY -> dDays.restore(item.id, now)
            ItemType.EVENT -> database.withTransaction {
                calendars.restore(item.id, now)
                val calendar = calendars.get(item.id)?.toDomain() ?: return@withTransaction
                outbox.deleteByLocalCalendarId(item.id)
                outbox.upsert(
                    SyncOutboxItem(
                        localCalendarId = item.id,
                        operation = if (calendar.googleEventId == null) OutboxOperation.CREATE else OutboxOperation.UPDATE,
                        createdAt = Instant.ofEpochMilli(now),
                    ).toEntity()
                )
            }
        }
    }

    override suspend fun deletePermanently(item: TrashItem) {
        when (item.type) {
            ItemType.NOTE -> notes.deletePermanently(item.id)
            ItemType.TASK -> tasks.deletePermanently(item.id)
            ItemType.D_DAY -> dDays.deletePermanently(item.id)
            ItemType.EVENT -> database.withTransaction {
                outbox.deleteByLocalCalendarId(item.id)
                calendars.deletePermanently(item.id)
            }
        }
    }

    override fun observeToday(now: Instant): Flow<TodaySnapshot> {
        val zone = ZoneId.of("Asia/Seoul")
        val day = now.atZone(zone).toLocalDate()
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        return combine(
            tasks.observeActiveBefore(end.toEpochMilli()).map { list -> list.map(TaskEntity::toDomain) },
            calendars.observeRange(start.toEpochMilli(), end.toEpochMilli()).map { list -> list.map(CalendarEntity::toDomain) },
            notes.observeRecent().map { list -> list.map(NoteEntity::toDomain) },
        ) { taskItems, eventItems, noteItems -> TodaySnapshot(taskItems, eventItems, noteItems) }
    }

    override fun observeDDays(today: LocalDate): Flow<List<DDayItem>> =
        dDays.observeActive().map { rows ->
            rows.map(DDayEntity::toDomain).filter { item ->
                item.targetDate >= today ||
                    (item.showElapsedDays && !item.targetDate.plusDays(item.archiveAfterDays.toLong()).isBefore(today))
            }
        }

    override suspend fun getRepresentativeDDay(today: LocalDate): DDayItem? =
        dDays.getRepresentative(today.toEpochDay())?.toDomain()

    override fun observeTrash(): Flow<List<TrashItem>> = combine(
        notes.observeTrash(), tasks.observeTrash(), calendars.observeTrash(), dDays.observeTrash(),
    ) { noteRows, taskRows, calendarRows, dDayRows ->
        buildList {
            addAll(noteRows.map { TrashItem(it.id, ItemType.NOTE, it.title, Instant.ofEpochMilli(requireNotNull(it.deletedAtEpochMs))) })
            addAll(taskRows.map { TrashItem(it.id, ItemType.valueOf(it.itemType), it.title, Instant.ofEpochMilli(requireNotNull(it.deletedAtEpochMs))) })
            addAll(calendarRows.map { TrashItem(it.id, ItemType.EVENT, it.title, Instant.ofEpochMilli(requireNotNull(it.deletedAtEpochMs))) })
            addAll(dDayRows.map { TrashItem(it.id, ItemType.D_DAY, it.title, Instant.ofEpochMilli(requireNotNull(it.deletedAtEpochMs))) })
        }.sortedByDescending { it.deletedAt }
    }

    override suspend fun listCalendars(start: Instant, end: Instant): List<CalendarItem> =
        calendars.listRange(start.toEpochMilli(), end.toEpochMilli()).map(CalendarEntity::toDomain)

    override suspend fun getCalendar(id: String): CalendarItem? = calendars.get(id)?.toDomain()
    override suspend fun getCalendarByGoogleEventId(id: String): CalendarItem? = calendars.getByGoogleEventId(id)?.toDomain()
    override suspend fun pendingOutbox(limit: Int): List<SyncOutboxItem> = outbox.pending(limit).map(SyncOutboxEntity::toDomain)
    override suspend fun markOutboxDone(id: String) = outbox.delete(id)
    override suspend fun markOutboxFailed(id: String, error: String) = outbox.markFailed(id, error.take(180))

    override suspend fun markCalendarSynced(localId: String, remote: RemoteCalendarEvent) {
        val local = calendars.get(localId)?.toDomain() ?: return
        calendars.upsert(local.copy(googleEventId = remote.id, externalEtag = remote.etag, externalUpdatedAt = remote.updatedAt, syncState = SyncState.SYNCED, updatedAt = Instant.now()).toEntity())
    }

    override suspend fun applyRemoteCalendarUpdate(localId: String, remote: RemoteCalendarEvent) {
        val local = calendars.get(localId)?.toDomain() ?: return
        calendars.upsert(local.copy(title = remote.title, startAt = remote.startAt, endAt = remote.endAt, externalEtag = remote.etag, externalUpdatedAt = remote.updatedAt, syncState = SyncState.SYNCED, updatedAt = Instant.now()).toEntity())
    }

    override suspend fun applyRemoteCalendarDeletion(localId: String, deletedAt: Instant) {
        database.withTransaction {
            outbox.deleteByLocalCalendarId(localId)
            calendars.applyRemoteDeletion(localId, deletedAt.toEpochMilli())
        }
    }

    override suspend fun markCalendarConflict(localId: String, remote: RemoteCalendarEvent) {
        val local = calendars.get(localId)?.toDomain() ?: return
        calendars.upsert(local.copy(externalEtag = remote.etag, externalUpdatedAt = remote.updatedAt, syncState = SyncState.CONFLICT, updatedAt = Instant.now()).toEntity())
    }

    override suspend fun purgeDeletedBefore(cutoff: Instant): Int = database.withTransaction {
        notes.purgeBefore(cutoff.toEpochMilli()) +
            tasks.purgeBefore(cutoff.toEpochMilli()) +
            calendars.purgeBefore(cutoff.toEpochMilli()) +
            dDays.purgeBefore(cutoff.toEpochMilli())
    }
}
