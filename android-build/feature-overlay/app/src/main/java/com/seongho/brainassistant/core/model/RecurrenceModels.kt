package com.seongho.brainassistant.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }
enum class ExclusionKind { KOREAN_PUBLIC_HOLIDAY, SCHOOL_CALENDAR }
enum class ExclusionPolicy { SKIP, MOVE_TO_NEXT_WEEKDAY }
enum class RecurrenceExceptionKind { MODIFIED, CANCELLED, MOVED }
enum class RecurrenceScope { THIS_OCCURRENCE, THIS_AND_FOLLOWING, ALL_OCCURRENCES }

sealed interface RecurrenceEnd {
    data class Until(val date: LocalDate) : RecurrenceEnd

    data class Count(val occurrences: Int) : RecurrenceEnd {
        init {
            require(occurrences > 0) { "반복 횟수는 양수여야 합니다." }
        }
    }

    data object Never : RecurrenceEnd
}

data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val dayOfMonth: Int? = null,
    val ordinal: Int? = null,
    val ordinalWeekday: DayOfWeek? = null,
    val end: RecurrenceEnd = RecurrenceEnd.Never,
) {
    init {
        require(interval > 0) { "반복 간격은 양수여야 합니다." }
        require(dayOfMonth == null || dayOfMonth > 0) { "월간 일자는 양수여야 합니다." }
        require(ordinal == null || ordinal > 0) { "월간 순서는 양수여야 합니다." }
        require(frequency != RecurrenceFrequency.MONTHLY || ordinal == null || ordinalWeekday != null) {
            "월간 순서 반복에는 요일이 필요합니다."
        }
    }
}

data class RecurrenceDraft(
    val localId: String = UUID.randomUUID().toString(),
    val title: String,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val durationMinutes: Int,
    val zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
    val rule: RecurrenceRule,
    val exclusionKinds: Set<ExclusionKind> = emptySet(),
    val exclusionPolicy: ExclusionPolicy = ExclusionPolicy.SKIP,
    val confidence: Double,
) {
    init {
        require(durationMinutes > 0) { "일정 기간은 양수여야 합니다." }
    }
}

data class RecurrenceMaster(
    val id: String,
    val inputId: String,
    val transactionId: String,
    val title: String,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val durationMinutes: Int,
    val zoneId: ZoneId,
    val rule: RecurrenceRule,
    val exclusionKinds: Set<ExclusionKind>,
    val exclusionPolicy: ExclusionPolicy,
    val googleCalendarId: String = "primary",
    val remoteSeriesId: String? = null,
    val syncState: SyncState = SyncState.PENDING,
    val deletedAt: Instant? = null,
    val updatedAt: Instant,
) {
    init {
        require(durationMinutes > 0) { "일정 기간은 양수여야 합니다." }
    }
}

data class OccurrenceKey(val masterId: String, val originalStartAt: Instant)

data class RecurrenceException(
    val id: String,
    val key: OccurrenceKey,
    val kind: RecurrenceExceptionKind,
    val effectiveStartAt: Instant? = null,
    val effectiveEndAt: Instant? = null,
    val titleOverride: String? = null,
    val remoteEventId: String? = null,
    val syncState: SyncState = SyncState.PENDING,
)

data class RecurrenceOccurrence(
    val key: OccurrenceKey,
    val title: String,
    val startAt: Instant,
    val endAt: Instant,
    val kind: RecurrenceExceptionKind? = null,
    val exclusionReason: String? = null,
    val conflictReason: String? = null,
)

data class ExclusionSource(
    val id: String,
    val calendarId: String,
    val displayName: String,
    val kind: ExclusionKind,
    val enabled: Boolean,
)

data class ExclusionDate(
    val sourceId: String,
    val remoteEventId: String,
    val date: LocalDate,
    val title: String,
    val approved: Boolean,
)

enum class RecurrenceMutationKind { UPDATE, DELETE }

data class RecurrenceMutation(
    val key: OccurrenceKey,
    val scope: RecurrenceScope,
    val kind: RecurrenceMutationKind,
    val replacement: RecurrenceDraft? = null,
)

data class RecurrenceCommit(
    val operationId: String,
    val affectedMasterIds: List<String>,
    val remoteSyncPending: Boolean,
    val orphanedExceptionIds: List<String> = emptyList(),
)

data class PersistedBatch(
    val transactionId: String,
    val ordinaryItems: PersistedItems,
    val recurrenceCommit: RecurrenceCommit?,
)
