package com.seongho.brainassistant.core.sync

import androidx.room.withTransaction
import com.seongho.brainassistant.core.database.AppDatabase
import com.seongho.brainassistant.core.database.ExclusionDateEntity
import com.seongho.brainassistant.core.database.ExclusionSourceEntity
import com.seongho.brainassistant.core.database.RecurrenceExceptionEntity
import com.seongho.brainassistant.core.database.RecurrenceOutboxEntity
import com.seongho.brainassistant.core.database.RecurrenceOutboxOperation
import com.seongho.brainassistant.core.database.toDomain
import com.seongho.brainassistant.core.database.toEntity
import com.seongho.brainassistant.core.model.ExclusionDate
import com.seongho.brainassistant.core.model.ExclusionSource
import com.seongho.brainassistant.core.model.RecurrenceException
import com.seongho.brainassistant.core.model.RecurrenceExceptionKind
import com.seongho.brainassistant.core.model.RecurrenceOccurrence
import com.seongho.brainassistant.core.model.SyncState
import com.seongho.brainassistant.core.recurrence.RecurrenceEngine
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ExclusionCandidateKey(
    val sourceId: String,
    val remoteEventId: String,
    val date: LocalDate,
)

class RoomExclusionSettingsStore(
    private val database: AppDatabase,
) {
    suspend fun loadSources(): List<ExclusionSource> = database.exclusionSourceDao()
        .listAll().map(ExclusionSourceEntity::toDomain)

    suspend fun loadSchoolCandidates(startYear: Int, endYear: Int): List<ExclusionDate> {
        val schoolSourceIds = loadSources().filter { it.kind == com.seongho.brainassistant.core.model.ExclusionKind.SCHOOL_CALENDAR }
            .mapTo(mutableSetOf(), ExclusionSource::id)
        return database.exclusionDateDao().listYears(startYear, endYear)
            .filter { it.sourceId in schoolSourceIds }
            .map(ExclusionDateEntity::toDomain)
    }

    suspend fun saveSelections(
        enabledSourceIds: Set<String>,
        approvedCandidates: Set<ExclusionCandidateKey>,
        startYear: Int,
        endYear: Int,
    ) {
        require(startYear <= endYear)
        database.withTransaction {
            database.exclusionSourceDao().listAll().forEach { row ->
                val source = row.toDomain()
                val enabled = source.kind == com.seongho.brainassistant.core.model.ExclusionKind.KOREAN_PUBLIC_HOLIDAY ||
                    source.id in enabledSourceIds
                database.exclusionSourceDao().upsert(source.copy(enabled = enabled).toEntity())
            }
            database.exclusionDateDao().listYears(startYear, endYear).forEach { row ->
                val source = database.exclusionSourceDao().get(row.sourceId)?.toDomain()
                if (source?.kind == com.seongho.brainassistant.core.model.ExclusionKind.SCHOOL_CALENDAR) {
                    val key = ExclusionCandidateKey(row.sourceId, row.remoteEventId, LocalDate.ofEpochDay(row.dateEpochDay))
                    database.exclusionDateDao().upsertAll(listOf(row.copy(approved = key in approvedCandidates)))
                }
            }
        }
    }
}

class RoomExclusionCacheStore(
    private val database: AppDatabase,
) : ExclusionCacheStore {
    private val sources = database.exclusionSourceDao()
    private val dates = database.exclusionDateDao()

    override suspend fun mergeDiscoveredSources(discovered: List<ExclusionSource>) {
        database.withTransaction {
            discovered.forEach { source ->
                val existing = sources.get(source.id)
                sources.upsert(
                    source.copy(enabled = existing?.enabled ?: source.enabled)
                        .toEntity(existing?.lastRefreshedAtEpochMs?.let(Instant::ofEpochMilli)),
                )
            }
        }
    }

    override suspend fun enabledSources(): List<ExclusionSource> =
        sources.listEnabled().map(ExclusionSourceEntity::toDomain)

    override suspend fun replaceSourceYear(
        sourceId: String,
        year: Int,
        dates: List<ExclusionDate>,
    ) {
        require(dates.all { it.sourceId == sourceId && it.date.year == year })
        database.withTransaction {
            val approvedKeys = this@RoomExclusionCacheStore.dates.listForSourceYear(sourceId, year)
                .filter(ExclusionDateEntity::approved)
                .mapTo(mutableSetOf()) { it.remoteEventId to it.dateEpochDay }
            val rows = dates.map { date ->
                date.copy(approved = date.approved || (date.remoteEventId to date.date.toEpochDay()) in approvedKeys)
                    .toEntity()
            }
            this@RoomExclusionCacheStore.dates.replaceForSourceYear(sourceId, year, rows)
        }
    }
}

class RoomRecurrenceExclusionRecalculator(
    private val database: AppDatabase,
    private val engine: RecurrenceEngine = RecurrenceEngine(),
) : RecurrenceExclusionRecalculator {
    override suspend fun recalculate(year: Int) {
        val first = LocalDate.of(year, 1, 1)
        val last = LocalDate.of(year, 12, 31)
        database.withTransaction {
            val enabledSources = database.exclusionSourceDao().listEnabled()
                .map(ExclusionSourceEntity::toDomain)
                .associateBy(ExclusionSource::id)
            val approvedDates = database.exclusionDateDao()
                .listApprovedRange(first.toEpochDay(), last.toEpochDay())
                .map(ExclusionDateEntity::toDomain)
            val masters = database.recurrenceMasterDao()
                .listOverlapping(first.toEpochDay(), last.toEpochDay())
                .map { it.toDomain() }

            masters.forEach { master ->
                val exclusionDays = approvedDates.asSequence()
                    .filter { date -> enabledSources[date.sourceId]?.kind in master.exclusionKinds }
                    .map(ExclusionDate::date)
                    .toSet()
                val existing = database.recurrenceExceptionDao().listForMaster(master.id)
                    .map(RecurrenceExceptionEntity::toDomain)
                val existingMoved = existing.filter { it.kind == RecurrenceExceptionKind.MOVED }
                    .associateBy { it.key }
                val desired = engine.generate(master, existing.filter { it.kind != RecurrenceExceptionKind.MOVED }, exclusionDays, first..last)
                    .filter { it.kind == RecurrenceExceptionKind.MOVED }
                    .associate { occurrence ->
                        val previous = existingMoved[occurrence.key]
                        occurrence.key to occurrence.toMovedException(previous)
                    }
                val obsolete = existingMoved.filterKeys { it !in desired.keys }.values
                val changed = desired.values.filter { candidate ->
                    val previous = existingMoved[candidate.key]
                    previous == null || previous.effectiveStartAt != candidate.effectiveStartAt ||
                        previous.effectiveEndAt != candidate.effectiveEndAt
                }
                if (obsolete.isNotEmpty() || changed.isNotEmpty()) {
                    obsolete.forEach { database.recurrenceExceptionDao().delete(it.id) }
                    database.recurrenceExceptionDao().upsertAll(changed.map(RecurrenceException::toEntity))
                    database.recurrenceOutboxDao().upsert(
                        RecurrenceOutboxEntity(
                            id = UUID.randomUUID().toString(),
                            masterId = master.id,
                            exceptionId = null,
                            operation = RecurrenceOutboxOperation.UPSERT_SERIES,
                            createdAtEpochMs = Instant.now().toEpochMilli(),
                            attemptCount = 0,
                            lastError = null,
                        ),
                    )
                }
            }
        }
    }

    private fun RecurrenceOccurrence.toMovedException(previous: RecurrenceException?) = RecurrenceException(
        id = previous?.id ?: "policy:${key.masterId}:${key.originalStartAt.toEpochMilli()}",
        key = key,
        kind = RecurrenceExceptionKind.MOVED,
        effectiveStartAt = startAt,
        effectiveEndAt = endAt,
        remoteEventId = previous?.remoteEventId,
        syncState = previous?.syncState ?: SyncState.PENDING,
    )
}
