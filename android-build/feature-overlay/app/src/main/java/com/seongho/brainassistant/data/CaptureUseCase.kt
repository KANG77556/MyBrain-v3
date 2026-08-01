package com.seongho.brainassistant.data

import com.seongho.brainassistant.core.calendar.ConflictChecker
import com.seongho.brainassistant.core.calendar.ConflictDecision
import com.seongho.brainassistant.core.model.AnalysisRecord
import com.seongho.brainassistant.core.model.AnalysisRequest
import com.seongho.brainassistant.core.model.CalendarItem
import com.seongho.brainassistant.core.model.ClarificationField
import com.seongho.brainassistant.core.model.InputRecord
import com.seongho.brainassistant.core.model.ItemType
import com.seongho.brainassistant.core.model.ParsedItem
import com.seongho.brainassistant.core.model.PersistedItems
import com.seongho.brainassistant.core.parser.InputAnalyzer
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID

sealed interface CaptureResult {
    data class AutoSaved(
        val inputId: String,
        val transactionId: String,
        val items: List<ParsedItem>,
    ) : CaptureResult

    data class NeedsReview(
        val inputId: String,
        val items: List<ParsedItem>,
        val clarificationFields: Set<ClarificationField>,
        val message: String,
    ) : CaptureResult

    data class Failed(val message: String) : CaptureResult
}

class CaptureUseCase(
    private val repository: BrainRepository,
    private val analyzer: InputAnalyzer,
    private val conflictChecker: ConflictChecker,
    private val clock: Clock,
) {
    suspend fun capture(rawText: String, now: ZonedDateTime): CaptureResult {
        val clean = rawText.trim()
        if (clean.isBlank()) return CaptureResult.Failed("기록할 내용을 입력해 주세요.")

        val input = InputRecord(
            rawText = clean,
            createdAt = clock.instant(),
            source = "TEXT",
        )
        repository.saveInput(input)

        val analysis = runCatching {
            analyzer.analyze(AnalysisRequest(clean, now))
        }.getOrElse {
            return CaptureResult.NeedsReview(
                inputId = input.id,
                items = emptyList(),
                clarificationFields = setOf(ClarificationField.TITLE),
                message = "분석에 실패하여 직접 분류가 필요합니다.",
            )
        }

        val effectiveClarifications = analysis.clarificationFields +
            inferRequiredClarifications(analysis.items)
        repository.saveAnalysis(
            AnalysisRecord(
                inputId = input.id,
                analyzer = analysis.analyzer,
                confidence = analysis.confidence,
                clarificationFields = effectiveClarifications,
                createdAt = clock.instant(),
            ),
        )

        val conflictMessages = findConflictMessages(input.id, analysis.items)
        val autoSave = analysis.confidence >= AUTO_SAVE_CONFIDENCE &&
            effectiveClarifications.isEmpty() &&
            conflictMessages.isEmpty() &&
            analysis.items.isNotEmpty() &&
            analysis.items.all(::hasRequiredFields)
        if (!autoSave) {
            val message = conflictMessages
                .joinToString(" · ")
                .ifBlank { "확인이 필요한 항목이 있습니다." }
            return CaptureResult.NeedsReview(
                inputId = input.id,
                items = analysis.items,
                clarificationFields = effectiveClarifications,
                message = message,
            )
        }

        val transactionId = UUID.randomUUID().toString()
        repository.saveParsedItems(input.id, analysis.items, transactionId)
        return CaptureResult.AutoSaved(input.id, transactionId, analysis.items)
    }

    suspend fun confirm(inputId: String, items: List<ParsedItem>): PersistedItems =
        repository.confirmReviewedItems(inputId, items)

    suspend fun undo(transactionId: String) {
        repository.softDeleteByTransaction(transactionId, clock.instant())
    }

    private suspend fun findConflictMessages(
        inputId: String,
        items: List<ParsedItem>,
    ): List<String> {
        val events = items.filter { it.type == ItemType.EVENT && it.startAt != null }
        if (events.isEmpty()) return emptyList()

        val start = events.mapNotNull { it.startAt }.minOrNull() ?: return emptyList()
        val end = events
            .mapNotNull { it.endAt ?: it.startAt?.plus(Duration.ofHours(1)) }
            .maxOrNull()
            ?: return emptyList()
        val existing = repository.listCalendars(
            start.minus(Duration.ofDays(1)),
            end.plus(Duration.ofDays(1)),
        )
        return events.mapNotNull { parsed ->
            val eventStart = parsed.startAt ?: return@mapNotNull null
            val candidate = CalendarItem(
                id = parsed.localId,
                inputId = inputId,
                transactionId = "preview",
                title = parsed.title,
                startAt = eventStart,
                endAt = parsed.endAt ?: eventStart.plus(Duration.ofHours(1)),
            )
            conflictChecker
                .check(candidate, existing)
                .takeUnless(ConflictDecision::isClear)
                ?.toKoreanMessage()
        }
    }

    private fun inferRequiredClarifications(
        items: List<ParsedItem>,
    ): Set<ClarificationField> = buildSet {
        if (items.isEmpty()) add(ClarificationField.TITLE)
        items.forEach { item ->
            if (item.title.isBlank()) add(ClarificationField.TITLE)
            when (item.type) {
                ItemType.EVENT -> if (item.startAt == null) {
                    add(ClarificationField.DATE)
                    add(ClarificationField.TIME)
                }
                ItemType.D_DAY -> if (!item.hasDDayDate()) {
                    add(ClarificationField.DATE)
                }
                else -> Unit
            }
        }
    }

    private fun hasRequiredFields(item: ParsedItem): Boolean =
        item.title.isNotBlank() && when (item.type) {
            ItemType.EVENT -> item.startAt != null
            ItemType.D_DAY -> item.hasDDayDate()
            else -> true
        }

    private fun ParsedItem.hasDDayDate(): Boolean =
        targetDate != null || dueAt != null || startAt != null

    private companion object {
        const val AUTO_SAVE_CONFIDENCE = 0.85
    }
}
