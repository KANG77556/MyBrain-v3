package com.seongho.brainassistant.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

enum class ItemType { NOTE, TASK, EVENT, D_DAY }
enum class ItemStatus { ACTIVE, COMPLETED, DELETED }
enum class SyncState { LOCAL_ONLY, PENDING, SYNCED, FAILED, CONFLICT }
enum class OutboxOperation { CREATE, UPDATE, DELETE }
enum class ClarificationField { DATE, TIME, TITLE, RECURRENCE }

data class InputRecord(
    val id: String = UUID.randomUUID().toString(),
    val rawText: String,
    val createdAt: Instant,
    val source: String,
)

data class NoteItem(
    val id: String = UUID.randomUUID().toString(),
    val inputId: String,
    val transactionId: String,
    val title: String,
    val body: String,
    val status: ItemStatus = ItemStatus.ACTIVE,
    val deletedAt: Instant? = null,
    val updatedAt: Instant = Instant.now(),
)

data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val inputId: String,
    val transactionId: String,
    val kind: ItemType = ItemType.TASK,
    val title: String,
    val dueAt: Instant?,
    val priority: Int,
    val estimatedMinutes: Int?,
    val status: ItemStatus = ItemStatus.ACTIVE,
    val deletedAt: Instant? = null,
    val updatedAt: Instant = Instant.now(),
)

data class CalendarItem(
    val id: String = UUID.randomUUID().toString(),
    val inputId: String,
    val transactionId: String,
    val title: String,
    val startAt: Instant,
    val endAt: Instant,
    val googleCalendarId: String = "primary",
    val googleEventId: String? = null,
    val externalEtag: String? = null,
    val externalUpdatedAt: Instant? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val deletedAt: Instant? = null,
    val updatedAt: Instant = Instant.now(),
)

data class AnalysisRecord(
    val id: String = UUID.randomUUID().toString(),
    val inputId: String,
    val analyzer: String,
    val confidence: Double,
    val clarificationFields: Set<ClarificationField>,
    val createdAt: Instant,
)

data class SyncOutboxItem(
    val id: String = UUID.randomUUID().toString(),
    val localCalendarId: String,
    val operation: OutboxOperation,
    val createdAt: Instant,
    val attemptCount: Int = 0,
    val lastError: String? = null,
)

data class ParsedItem(
    val localId: String = UUID.randomUUID().toString(),
    val batchId: String? = null,
    val batchIndex: Int? = null,
    val type: ItemType,
    val title: String,
    val body: String = "",
    val startAt: Instant? = null,
    val endAt: Instant? = null,
    val dueAt: Instant? = null,
    val targetDate: LocalDate? = null,
    val priority: Int = 2,
    val estimatedMinutes: Int? = null,
    val reminderOffsets: Set<Int> = emptySet(),
    val linkedLocalIds: Set<String> = emptySet(),
    val sourceStart: Int? = null,
    val sourceEnd: Int? = null,
)

data class ParsedBatch(
    val id: String = UUID.randomUUID().toString(),
    val originalText: String,
    val items: List<ParsedItem>,
    val requiresReview: Boolean,
)

data class AnalysisRequest(
    val rawText: String,
    val referenceTime: ZonedDateTime,
    val zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
)

data class AnalysisResult(
    val items: List<ParsedItem>,
    val confidence: Double,
    val clarificationFields: Set<ClarificationField>,
    val analyzer: String,
)

data class TodaySnapshot(
    val tasks: List<TaskItem> = emptyList(),
    val events: List<CalendarItem> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
) {
    val overdueCount: Int get() = tasks.count { it.dueAt != null && it.dueAt.isBefore(Instant.now()) && it.status == ItemStatus.ACTIVE }
    val conflictCount: Int get() = events.count { it.syncState == SyncState.CONFLICT }
}

data class TrashItem(
    val id: String,
    val type: ItemType,
    val title: String,
    val deletedAt: Instant,
)

data class PersistedItems(
    val transactionId: String,
    val notes: List<NoteItem>,
    val tasks: List<TaskItem>,
    val events: List<CalendarItem>,
    val dDays: List<DDayItem> = emptyList(),
)
