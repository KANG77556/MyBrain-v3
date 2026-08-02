package com.seongho.brainassistant.data

import com.seongho.brainassistant.core.calendar.RemoteCalendarEvent
import com.seongho.brainassistant.core.model.*
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface BrainRepository {
    suspend fun saveInput(input: InputRecord)
    suspend fun saveNote(note: NoteItem)
    suspend fun saveTask(task: TaskItem)
    suspend fun saveDDay(item: DDayItem)
    suspend fun saveCalendar(item: CalendarItem, enqueue: Boolean = true)
    suspend fun saveAnalysis(record: AnalysisRecord)
    suspend fun saveParsedItems(inputId: String, items: List<ParsedItem>, transactionId: String): PersistedItems
    suspend fun confirmReviewedItems(inputId: String, items: List<ParsedItem>): PersistedItems
    suspend fun confirmReviewedBatch(
        inputId: String,
        items: List<ParsedItem>,
        recurrences: List<RecurrenceDraft>,
    ): PersistedBatch
    suspend fun mutateRecurrence(command: RecurrenceMutation): RecurrenceCommit
    suspend fun undoRecurrence(operationId: String): RecurrenceCommit
    fun observeRecurringOccurrences(start: Instant, end: Instant): Flow<List<RecurrenceOccurrence>>
    suspend fun softDeleteTask(id: String, deletedAt: Instant)
    suspend fun softDeleteCalendar(id: String, deletedAt: Instant)
    suspend fun softDeleteByTransaction(transactionId: String, deletedAt: Instant)
    suspend fun restoreItem(item: TrashItem)
    suspend fun deletePermanently(item: TrashItem)
    fun observeToday(now: Instant): Flow<TodaySnapshot>
    fun observeDDays(today: LocalDate): Flow<List<DDayItem>>
    suspend fun getRepresentativeDDay(today: LocalDate): DDayItem?
    fun observeTrash(): Flow<List<TrashItem>>
    suspend fun listCalendars(start: Instant, end: Instant): List<CalendarItem>
    suspend fun getCalendar(id: String): CalendarItem?
    suspend fun getCalendarByGoogleEventId(id: String): CalendarItem?
    suspend fun pendingOutbox(limit: Int = 50): List<SyncOutboxItem>
    suspend fun markOutboxDone(id: String)
    suspend fun markOutboxFailed(id: String, error: String)
    suspend fun markCalendarSynced(localId: String, remote: RemoteCalendarEvent)
    suspend fun applyRemoteCalendarUpdate(localId: String, remote: RemoteCalendarEvent)
    suspend fun applyRemoteCalendarDeletion(localId: String, deletedAt: Instant)
    suspend fun markCalendarConflict(localId: String, remote: RemoteCalendarEvent)
    suspend fun purgeDeletedBefore(cutoff: Instant): Int
}
