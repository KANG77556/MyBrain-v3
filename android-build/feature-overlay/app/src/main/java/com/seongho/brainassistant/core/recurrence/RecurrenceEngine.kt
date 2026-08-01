package com.seongho.brainassistant.core.recurrence

import com.seongho.brainassistant.core.model.ExclusionPolicy
import com.seongho.brainassistant.core.model.OccurrenceKey
import com.seongho.brainassistant.core.model.RecurrenceEnd
import com.seongho.brainassistant.core.model.RecurrenceException
import com.seongho.brainassistant.core.model.RecurrenceExceptionKind
import com.seongho.brainassistant.core.model.RecurrenceFrequency
import com.seongho.brainassistant.core.model.RecurrenceMaster
import com.seongho.brainassistant.core.model.RecurrenceOccurrence
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

class RecurrenceEngine {
    fun generate(
        master: RecurrenceMaster,
        exceptions: List<RecurrenceException>,
        exclusions: Set<LocalDate>,
        range: ClosedRange<LocalDate>,
    ): List<RecurrenceOccurrence> {
        val exceptionMap = exceptions.associateBy(RecurrenceException::key)
        val lastExceptionDate = exceptions.maxOfOrNull {
            it.key.originalStartAt.atZone(master.zoneId).toLocalDate()
        }
        val processThrough = listOfNotNull(range.endInclusive, lastExceptionDate).maxOrNull()!!
        val accepted = mutableListOf<RecurrenceOccurrence>()

        for (originalDate in candidateDates(master)) {
            if (originalDate > processThrough) break
            if (master.rule.end is RecurrenceEnd.Until && originalDate > master.rule.end.date) break

            val effectiveDate = when {
                originalDate !in exclusions -> originalDate
                master.exclusionPolicy == ExclusionPolicy.SKIP -> continue
                else -> moveToAvailableWeekday(originalDate.plusDays(1), exclusions)
            }
            if (!isWithinEnd(master.rule.end, accepted.size, effectiveDate)) {
                if (master.rule.end is RecurrenceEnd.Count) break
                continue
            }

            val originalStart = originalDate.atTime(master.startTime).atZone(master.zoneId).toInstant()
            val effectiveStart = effectiveDate.atTime(master.startTime).atZone(master.zoneId).toInstant()
            val occurrence = RecurrenceOccurrence(
                key = OccurrenceKey(master.id, originalStart),
                title = master.title,
                startAt = effectiveStart,
                endAt = effectiveStart.plus(Duration.ofMinutes(master.durationMinutes.toLong())),
                kind = if (effectiveDate == originalDate) null else RecurrenceExceptionKind.MOVED,
            )
            applyException(occurrence, exceptionMap)?.let(accepted::add)
        }

        val collisions = accepted.groupingBy(RecurrenceOccurrence::startAt)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        return accepted
            .map { occurrence ->
                if (occurrence.startAt in collisions) {
                    occurrence.copy(conflictReason = COLLISION_REASON)
                } else {
                    occurrence
                }
            }
            .filter { it.startAt.atZone(master.zoneId).toLocalDate() in range }
            .sortedBy(RecurrenceOccurrence::startAt)
    }

    private fun candidateDates(master: RecurrenceMaster): Sequence<LocalDate> = sequence {
        when (master.rule.frequency) {
            RecurrenceFrequency.DAILY -> {
                var date = master.startDate
                while (true) {
                    yield(date)
                    date = date.plusDays(master.rule.interval.toLong())
                }
            }

            RecurrenceFrequency.WEEKLY -> {
                val anchor = master.startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weekdays = master.rule.weekdays.ifEmpty { setOf(master.startDate.dayOfWeek) }.sorted()
                var weekOffset = 0L
                while (true) {
                    for (weekday in weekdays) {
                        val date = anchor.plusWeeks(weekOffset).plusDays((weekday.value - 1).toLong())
                        if (date >= master.startDate) yield(date)
                    }
                    weekOffset += master.rule.interval.toLong()
                }
            }

            RecurrenceFrequency.MONTHLY -> {
                var month = YearMonth.from(master.startDate)
                while (true) {
                    monthlyCandidate(master, month)?.let { date ->
                        if (date >= master.startDate) yield(date)
                    }
                    month = month.plusMonths(master.rule.interval.toLong())
                }
            }

            RecurrenceFrequency.YEARLY -> {
                var year = master.startDate.year
                while (true) {
                    try {
                        yield(LocalDate.of(year, master.startDate.month, master.startDate.dayOfMonth))
                    } catch (_: DateTimeException) {
                        // February 29 has no occurrence in non-leap years.
                    }
                    year += master.rule.interval
                }
            }
        }
    }

    private fun monthlyCandidate(master: RecurrenceMaster, month: YearMonth): LocalDate? {
        val rule = master.rule
        return when {
            rule.ordinal != null && rule.ordinalWeekday != null -> {
                val date = if (rule.ordinal == LAST_ORDINAL) {
                    month.atEndOfMonth().with(TemporalAdjusters.lastInMonth(rule.ordinalWeekday))
                } else {
                    month.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(rule.ordinal, rule.ordinalWeekday))
                }
                date.takeIf { it.month == month.month }
            }

            else -> {
                val day = rule.dayOfMonth ?: master.startDate.dayOfMonth
                if (day <= month.lengthOfMonth()) month.atDay(day) else null
            }
        }
    }

    private fun moveToAvailableWeekday(date: LocalDate, exclusions: Set<LocalDate>): LocalDate {
        var available = date
        while (available in exclusions || available.dayOfWeek in WEEKEND_DAYS) {
            available = available.plusDays(1)
        }
        return available
    }

    private fun isWithinEnd(end: RecurrenceEnd, acceptedCount: Int, date: LocalDate): Boolean = when (end) {
        is RecurrenceEnd.Until -> date <= end.date
        is RecurrenceEnd.Count -> acceptedCount < end.occurrences
        RecurrenceEnd.Never -> true
    }

    private fun applyException(
        occurrence: RecurrenceOccurrence,
        exceptions: Map<OccurrenceKey, RecurrenceException>,
    ): RecurrenceOccurrence? {
        val exception = exceptions[occurrence.key] ?: return occurrence
        if (exception.kind == RecurrenceExceptionKind.CANCELLED) return null

        val startAt = exception.effectiveStartAt ?: occurrence.startAt
        val endAt = exception.effectiveEndAt
            ?: startAt.plus(Duration.between(occurrence.startAt, occurrence.endAt))
        return occurrence.copy(
            title = exception.titleOverride ?: occurrence.title,
            startAt = startAt,
            endAt = endAt,
            kind = exception.kind,
        )
    }

    private companion object {
        const val LAST_ORDINAL = 5
        val WEEKEND_DAYS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        const val COLLISION_REASON = "諛섎났 ?쇱젙 ?대룞 寃곌낵媛 寃뱀묩?덈떎."
    }
}
