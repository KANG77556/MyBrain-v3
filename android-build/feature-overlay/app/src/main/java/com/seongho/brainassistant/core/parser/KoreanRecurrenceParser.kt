package com.seongho.brainassistant.core.parser

import com.seongho.brainassistant.core.model.ExclusionKind
import com.seongho.brainassistant.core.model.ExclusionPolicy
import com.seongho.brainassistant.core.model.RecurrenceDraft
import com.seongho.brainassistant.core.model.RecurrenceEnd
import com.seongho.brainassistant.core.model.RecurrenceFrequency
import com.seongho.brainassistant.core.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

class KoreanRecurrenceParser {
    fun parse(text: String, reference: ZonedDateTime): RecurrenceDraft? {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        val times = parseTimeRange(normalized) ?: return null
        val schedule = parseSchedule(normalized, reference, times.first) ?: return null

        return RecurrenceDraft(
            title = cleanRecurringTitle(normalized),
            startDate = schedule.start,
            startTime = times.first,
            durationMinutes = Duration.between(times.first, times.second).toMinutes().toInt(),
            rule = schedule.rule.copy(end = parseEnd(normalized, reference.toLocalDate()) ?: schedule.rule.end),
            exclusionKinds = parseExclusions(normalized),
            exclusionPolicy = if (NEXT_WEEKDAY_POLICY_PATTERN.containsMatchIn(normalized)) {
                ExclusionPolicy.MOVE_TO_NEXT_WEEKDAY
            } else {
                ExclusionPolicy.SKIP
            },
            confidence = 0.90,
        )
    }

    private fun parseSchedule(
        text: String,
        reference: ZonedDateTime,
        startTime: LocalTime,
    ): Schedule? {
        val referenceDate = reference.toLocalDate()
        WEEKDAY_RANGE_PATTERN.find(text)?.let { match ->
            val startDay = weekday(match.groupValues[1])
            val endDay = weekday(match.groupValues[2])
            if (endDay.value < startDay.value) return null
            val nextMonday = referenceDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            val start = nextMonday.plusDays((startDay.value - DayOfWeek.MONDAY.value).toLong())
            val end = nextMonday.plusDays((endDay.value - DayOfWeek.MONDAY.value).toLong())
            return Schedule(
                start,
                RecurrenceRule(
                    frequency = RecurrenceFrequency.WEEKLY,
                    weekdays = (startDay.value..endDay.value).map(DayOfWeek::of).toSet(),
                    end = RecurrenceEnd.Until(end),
                ),
            )
        }

        MONTHLY_LAST_WEEKDAY_PATTERN.find(text)?.let { match ->
            val targetDay = weekday(match.groupValues[1])
            var date = YearMonth.from(referenceDate).atEndOfMonth().with(TemporalAdjusters.lastInMonth(targetDay))
            if (!isFuture(date, startTime, reference)) {
                date = YearMonth.from(referenceDate).plusMonths(1).atEndOfMonth()
                    .with(TemporalAdjusters.lastInMonth(targetDay))
            }
            return Schedule(
                date,
                RecurrenceRule(
                    frequency = RecurrenceFrequency.MONTHLY,
                    ordinal = LAST_ORDINAL,
                    ordinalWeekday = targetDay,
                ),
            )
        }

        MONTHLY_DAY_PATTERN.find(text)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return null
            if (day !in 1..31) return null
            val date = generateSequence(YearMonth.from(referenceDate)) { it.plusMonths(1) }
                .take(MONTH_SEARCH_LIMIT)
                .mapNotNull { validDay(it, day) }
                .firstOrNull { isFuture(it, startTime, reference) }
                ?: return null
            return Schedule(
                date,
                RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, dayOfMonth = day),
            )
        }

        YEARLY_PATTERN.find(text)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return null
            val day = match.groupValues[2].toIntOrNull() ?: return null
            if (month !in 1..12 || day !in 1..31) return null
            val date = generateSequence(referenceDate.year) { it + 1 }
                .take(YEAR_SEARCH_LIMIT)
                .mapNotNull { year -> runCatching { LocalDate.of(year, month, day) }.getOrNull() }
                .firstOrNull { isFuture(it, startTime, reference) }
                ?: return null
            return Schedule(date, RecurrenceRule(frequency = RecurrenceFrequency.YEARLY))
        }

        WEEKLY_PATTERN.find(text)?.let { match ->
            val days = match.groupValues[2].map(::weekday).toSet()
            if (days.isEmpty()) return null
            val start = days.minOf { day ->
                val candidate = referenceDate.with(TemporalAdjusters.nextOrSame(day))
                if (isFuture(candidate, startTime, reference)) candidate else candidate.plusWeeks(1)
            }
            return Schedule(
                start,
                RecurrenceRule(
                    frequency = RecurrenceFrequency.WEEKLY,
                    interval = if (match.groupValues[1] == "격주") 2 else 1,
                    weekdays = days,
                ),
            )
        }

        return null
    }

    private fun parseTimeRange(text: String): Pair<LocalTime, LocalTime>? {
        val match = TIME_RANGE_PATTERN.find(text) ?: return null
        val startMarker = match.groupValues[1]
        val start = parseTime(startMarker, match.groupValues[2], match.groupValues[3]) ?: return null
        val rawEndMarker = match.groupValues[4]
        val endMarker = when {
            rawEndMarker.isNotBlank() -> rawEndMarker
            match.groupValues[5] == "12" -> ""
            else -> startMarker
        }
        val end = parseTime(
            endMarker,
            match.groupValues[5],
            match.groupValues[6],
        ) ?: return null
        return if (end.isAfter(start)) start to end else null
    }

    private fun parseTime(marker: String, hourText: String, minuteText: String): LocalTime? {
        var hour = hourText.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = minuteText.ifBlank { "0" }.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        if (marker == "오후" && hour in 1..11) hour += 12
        if (marker == "오전" && hour == 12) hour = 0
        return LocalTime.of(hour, minute)
    }

    private fun parseEnd(text: String, reference: LocalDate): RecurrenceEnd? {
        COUNT_PATTERN.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { count ->
            if (count > 0) return RecurrenceEnd.Count(count)
        }
        if (NEXT_MONTH_END_PATTERN.containsMatchIn(text)) {
            return RecurrenceEnd.Until(YearMonth.from(reference).plusMonths(1).atEndOfMonth())
        }
        return null
    }

    private fun parseExclusions(text: String): Set<ExclusionKind> = buildSet {
        if (text.contains("공휴일")) add(ExclusionKind.KOREAN_PUBLIC_HOLIDAY)
        if (text.contains("방학")) add(ExclusionKind.SCHOOL_CALENDAR)
    }

    private fun cleanRecurringTitle(text: String): String {
        var title = text
        CLEANUP_PATTERNS.forEach { pattern -> title = title.replace(pattern, " ") }
        return title.replace(Regex("\\s+"), " ").trim(' ', ',', '.')
    }

    private fun weekday(koreanInitial: Char): DayOfWeek = when (koreanInitial) {
        '월' -> DayOfWeek.MONDAY
        '화' -> DayOfWeek.TUESDAY
        '수' -> DayOfWeek.WEDNESDAY
        '목' -> DayOfWeek.THURSDAY
        '금' -> DayOfWeek.FRIDAY
        '토' -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    private fun weekday(koreanInitial: String): DayOfWeek = weekday(koreanInitial.single())

    private fun validDay(month: YearMonth, day: Int): LocalDate? =
        if (day in 1..month.lengthOfMonth()) month.atDay(day) else null

    private fun isFuture(
        date: LocalDate,
        startTime: LocalTime,
        reference: ZonedDateTime,
    ): Boolean = date.atTime(startTime).atZone(reference.zone).isAfter(reference)

    private data class Schedule(val start: LocalDate, val rule: RecurrenceRule)

    private companion object {
        const val LAST_ORDINAL = 5
        const val MONTH_SEARCH_LIMIT = 4_800
        const val YEAR_SEARCH_LIMIT = 400
        val WEEKDAY_RANGE_PATTERN = Regex("다음\\s*주\\s*([월화수목금토일])요일\\s*부터\\s*([월화수목금토일])요일\\s*까지")
        val MONTHLY_LAST_WEEKDAY_PATTERN = Regex("매월\\s*마지막\\s*([월화수목금토일])요일")
        val MONTHLY_DAY_PATTERN = Regex("매월\\s*(\\d{1,2})일")
        val YEARLY_PATTERN = Regex("매년\\s*(\\d{1,2})월\\s*(\\d{1,2})일")
        val WEEKLY_PATTERN = Regex("(매주|격주)\\s*([월화수목금토일]+)(?:요일)?")
        val TIME_RANGE_PATTERN = Regex("(오전|오후)?\\s*(\\d{1,2})시(?:\\s*(\\d{1,2})분)?\\s*부터\\s*(오전|오후)?\\s*(\\d{1,2})시(?:\\s*(\\d{1,2})분)?\\s*까지")
        val COUNT_PATTERN = Regex("(\\d+)회")
        val NEXT_MONTH_END_PATTERN = Regex("다음\\s*달\\s*말까지")
        val NEXT_WEEKDAY_POLICY_PATTERN = Regex("다음\\s*평일")
        val CLEANUP_PATTERNS = listOf(
            WEEKDAY_RANGE_PATTERN,
            MONTHLY_LAST_WEEKDAY_PATTERN,
            MONTHLY_DAY_PATTERN,
            YEARLY_PATTERN,
            WEEKLY_PATTERN,
            TIME_RANGE_PATTERN,
            COUNT_PATTERN,
            NEXT_MONTH_END_PATTERN,
            Regex("공휴일(?:과|와|은)?\\s*방학\\s*제외"),
            Regex("공휴일(?:은|과|와)?\\s*다음\\s*평일로\\s*이동"),
            Regex("공휴일\\s*제외"),
            Regex("방학\\s*제외"),
        )
    }
}
