package com.seongho.brainassistant.testing

import com.seongho.brainassistant.core.calendar.RemoteCalendarEvent
import com.seongho.brainassistant.core.model.*
import com.seongho.brainassistant.data.BrainRepository
import com.seongho.brainassistant.data.RecurrenceMutationPolicy
import com.seongho.brainassistant.core.recurrence.RecurrenceEngine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf

class FakeBrainRepository : BrainRepository {
    val inputs = mutableListOf<InputRecord>()
    val notes = mutableListOf<NoteItem>()
    val tasks = mutableListOf<TaskItem>()
    val calendars = mutableListOf<CalendarItem>()
    val dDays = mutableListOf<DDayItem>()
    val analyses = mutableListOf<AnalysisRecord>()
    val outbox = mutableListOf<SyncOutboxItem>()
    val recurrences = mutableListOf<RecurrenceMaster>()
    val trashFlow = MutableStateFlow<List<TrashItem>>(emptyList())
    val todayFlow = MutableStateFlow(TodaySnapshot())
    private val dDayFlow = MutableStateFlow<List<DDayItem>>(emptyList())

    override suspend fun saveInput(input: InputRecord) { inputs += input }
    override suspend fun saveNote(note: NoteItem) { notes += note }
    override suspend fun saveTask(task: TaskItem) {
        tasks.removeAll { it.id == task.id }
        tasks += task
        refreshToday()
    }
    override suspend fun saveDDay(item: DDayItem) {
        dDays.removeAll { it.id == item.id }
        dDays += item
        dDayFlow.value = dDays.toList()
    }
    override suspend fun saveCalendar(item: CalendarItem, enqueue: Boolean) {
        calendars.removeAll { it.id == item.id }
        calendars += item
    }
    override suspend fun saveAnalysis(record: AnalysisRecord) { analyses += record }
    override suspend fun saveParsedItems(inputId: String, items: List<ParsedItem>, transactionId: String): PersistedItems {
        val zone = ZoneId.of("Asia/Seoul")
        val persistedNotes = items.filter { it.type == ItemType.NOTE }.map {
            NoteItem(it.localId, inputId, transactionId, it.title, it.body.ifBlank { it.title })
        }
        val persistedTasks = items.filter { it.type == ItemType.TASK }.map {
            TaskItem(it.localId, inputId, transactionId, it.type, it.title, it.dueAt, it.priority, it.estimatedMinutes)
        }
        val persistedEvents = items.filter { it.type == ItemType.EVENT && it.startAt != null }.map {
            CalendarItem(it.localId, inputId, transactionId, it.title, requireNotNull(it.startAt), it.endAt ?: requireNotNull(it.startAt).plusSeconds(3600), syncState = SyncState.PENDING)
        }
        val persistedDDays = items.filter { it.type == ItemType.D_DAY }.map { parsed ->
            DDayItem(
                id = parsed.localId,
                inputId = inputId,
                transactionId = transactionId,
                title = parsed.title,
                targetDate = parsed.targetDate
                    ?: parsed.dueAt?.atZone(zone)?.toLocalDate()
                    ?: parsed.startAt?.atZone(zone)?.toLocalDate()
                    ?: error("D-Day에는 날짜가 필요합니다."),
                category = DDayCategory.DEADLINE,
                importance = parsed.priority.coerceIn(1, 3),
                reminderOffsets = parsed.reminderOffsets.ifEmpty { setOf(7, 3, 1, 0) },
            )
        }
        notes += persistedNotes
        tasks += persistedTasks
        calendars += persistedEvents
        dDays += persistedDDays
        dDayFlow.value = dDays.toList()
        refreshToday()
        return PersistedItems(transactionId, persistedNotes, persistedTasks, persistedEvents, persistedDDays)
    }
    override suspend fun confirmReviewedItems(inputId: String, items: List<ParsedItem>): PersistedItems = saveParsedItems(inputId, items, "review-tx")
    override suspend fun confirmReviewedBatch(
        inputId: String,
        items: List<ParsedItem>,
        recurrences: List<RecurrenceDraft>,
    ): PersistedBatch {
        val transactionId = "review-batch-tx"
        val ordinary = saveParsedItems(inputId, items, transactionId)
        val now = Instant.now()
        val masters = recurrences.map { draft ->
            RecurrenceMaster(
                id = draft.localId,
                inputId = inputId,
                transactionId = transactionId,
                title = draft.title,
                startDate = draft.startDate,
                startTime = draft.startTime,
                durationMinutes = draft.durationMinutes,
                zoneId = draft.zoneId,
                rule = draft.rule,
                exclusionKinds = draft.exclusionKinds,
                exclusionPolicy = draft.exclusionPolicy,
                updatedAt = now,
            )
        }
        this.recurrences += masters
        return PersistedBatch(
            transactionId,
            ordinary,
            masters.takeIf { it.isNotEmpty() }?.let {
                RecurrenceCommit("fake-operation", masters.map(RecurrenceMaster::id), true)
            },
        )
    }
    override suspend fun mutateRecurrence(command: RecurrenceMutation): RecurrenceCommit {
        val master = recurrences.first { it.id == command.key.masterId }
        val plan = RecurrenceMutationPolicy().plan(master, emptyList(), command, Instant.now())
        recurrences.removeAll { existing -> plan.upsertMasters.any { it.id == existing.id } }
        recurrences += plan.upsertMasters
        return RecurrenceCommit("fake-mutation", plan.upsertMasters.map(RecurrenceMaster::id), true)
    }
    override suspend fun undoRecurrence(operationId: String): RecurrenceCommit =
        RecurrenceCommit(operationId, emptyList(), false)
    override fun observeRecurringOccurrences(start: Instant, end: Instant): Flow<List<RecurrenceOccurrence>> =
        flowOf(
            recurrences.flatMap { master ->
                RecurrenceEngine().generate(
                    master,
                    emptyList(),
                    emptySet(),
                    start.atZone(master.zoneId).toLocalDate()..end.minusNanos(1).atZone(master.zoneId).toLocalDate(),
                )
            }.filter { it.startAt >= start && it.startAt < end },
        )
    override suspend fun softDeleteTask(id: String, deletedAt: Instant) { tasks.removeAll { it.id == id } }
    override suspend fun softDeleteCalendar(id: String, deletedAt: Instant) {
        val current = getCalendar(id) ?: return
        saveCalendar(current.copy(deletedAt = deletedAt, syncState = SyncState.PENDING), false)
        outbox += SyncOutboxItem(localCalendarId = id, operation = OutboxOperation.DELETE, createdAt = deletedAt)
    }
    override suspend fun softDeleteByTransaction(transactionId: String, deletedAt: Instant) {
        notes.removeAll { it.transactionId == transactionId }
        tasks.removeAll { it.transactionId == transactionId }
        calendars.removeAll { it.transactionId == transactionId }
        dDays.removeAll { it.transactionId == transactionId }
        dDayFlow.value = dDays.toList()
        refreshToday()
    }
    override suspend fun restoreItem(item: TrashItem) = Unit
    override suspend fun deletePermanently(item: TrashItem) = Unit
    override fun observeToday(now: Instant): Flow<TodaySnapshot> = todayFlow
    override fun observeDDays(today: LocalDate): Flow<List<DDayItem>> =
        dDayFlow.map { visibleDDays(today, it) }
    override suspend fun getRepresentativeDDay(today: LocalDate): DDayItem? =
        visibleDDays(today, dDays).sortedWith(representativeComparator(today)).firstOrNull()
    override fun observeTrash(): Flow<List<TrashItem>> = trashFlow
    override suspend fun listCalendars(start: Instant, end: Instant): List<CalendarItem> = calendars.filter { it.startAt < end && it.endAt > start }
    override suspend fun getCalendar(id: String): CalendarItem? = calendars.firstOrNull { it.id == id }
    override suspend fun getCalendarByGoogleEventId(id: String): CalendarItem? = calendars.firstOrNull { it.googleEventId == id }
    override suspend fun pendingOutbox(limit: Int): List<SyncOutboxItem> = outbox.take(limit)
    override suspend fun markOutboxDone(id: String) { outbox.removeAll { it.id == id } }
    override suspend fun markOutboxFailed(id: String, error: String) = Unit
    override suspend fun markCalendarSynced(localId: String, remote: RemoteCalendarEvent) {
        val current = getCalendar(localId) ?: return
        saveCalendar(current.copy(googleEventId = remote.id, externalEtag = remote.etag, syncState = SyncState.SYNCED), false)
    }
    override suspend fun applyRemoteCalendarUpdate(localId: String, remote: RemoteCalendarEvent) {
        val current = getCalendar(localId) ?: return
        saveCalendar(current.copy(title = remote.title, startAt = remote.startAt, endAt = remote.endAt, externalEtag = remote.etag, syncState = SyncState.SYNCED), false)
    }
    override suspend fun applyRemoteCalendarDeletion(localId: String, deletedAt: Instant) {
        val current = getCalendar(localId) ?: return
        outbox.removeAll { it.localCalendarId == localId }
        saveCalendar(current.copy(deletedAt = deletedAt, syncState = SyncState.SYNCED), false)
    }
    override suspend fun markCalendarConflict(localId: String, remote: RemoteCalendarEvent) {
        val current = getCalendar(localId) ?: return
        saveCalendar(current.copy(syncState = SyncState.CONFLICT), false)
    }
    override suspend fun purgeDeletedBefore(cutoff: Instant): Int {
        val before = trashFlow.value.size
        trashFlow.value = trashFlow.value.filterNot { it.deletedAt.isBefore(cutoff) }
        return before - trashFlow.value.size
    }

    private fun visibleDDays(today: LocalDate, source: List<DDayItem>): List<DDayItem> =
        source.filter { item ->
            item.status == ItemStatus.ACTIVE &&
                item.deletedAt == null &&
                (
                    item.targetDate >= today ||
                        (item.showElapsedDays && !item.targetDate.plusDays(item.archiveAfterDays.toLong()).isBefore(today))
                    )
        }

    private fun representativeComparator(today: LocalDate): Comparator<DDayItem> =
        compareByDescending<DDayItem> { it.isPinned }
            .thenBy { if (it.targetDate >= today) 0 else 1 }
            .thenBy { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(today, it.targetDate)) }
            .thenByDescending { it.importance }

    private fun refreshToday() {
        todayFlow.value = TodaySnapshot(tasks, calendars, notes)
    }
}
