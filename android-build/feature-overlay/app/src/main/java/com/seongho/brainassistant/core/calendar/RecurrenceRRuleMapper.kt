package com.seongho.brainassistant.core.calendar

import com.seongho.brainassistant.core.model.RecurrenceEnd
import com.seongho.brainassistant.core.model.RecurrenceFrequency
import com.seongho.brainassistant.core.model.RecurrenceMaster
import com.seongho.brainassistant.core.recurrence.RecurrenceEngine
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class RecurrenceProviderRule(
    val rrule: String,
    val exdates: List<String>,
)

class RecurrenceRRuleMapper(
    private val engine: RecurrenceEngine = RecurrenceEngine(),
) {
    fun map(master: RecurrenceMaster, exdates: List<Instant>): RecurrenceProviderRule {
        val parts = mutableListOf(
            "FREQ=${master.rule.frequency.name}",
            "INTERVAL=${master.rule.interval}",
        )
        when (master.rule.frequency) {
            RecurrenceFrequency.WEEKLY -> {
                val days = master.rule.weekdays.ifEmpty { setOf(master.startDate.dayOfWeek) }
                parts += "BYDAY=${days.sortedBy(DayOfWeek::getValue).joinToString(",", transform = ::weekdayCode)}"
            }
            RecurrenceFrequency.MONTHLY -> when {
                master.rule.ordinal != null && master.rule.ordinalWeekday != null ->
                    parts += "BYDAY=${master.rule.ordinal}${weekdayCode(master.rule.ordinalWeekday)}"
                else -> parts += "BYMONTHDAY=${master.rule.dayOfMonth ?: master.startDate.dayOfMonth}"
            }
            RecurrenceFrequency.YEARLY -> {
                parts += "BYMONTH=${master.startDate.monthValue}"
                parts += "BYMONTHDAY=${master.startDate.dayOfMonth}"
            }
            RecurrenceFrequency.DAILY -> Unit
        }

        val until = when (val end = master.rule.end) {
            is RecurrenceEnd.Until -> end.date
            is RecurrenceEnd.Count -> countUntil(master, exdates, end.occurrences)
            RecurrenceEnd.Never -> null
        }
        if (until != null) parts += "UNTIL=${formatEndOfDay(until, master)}"

        return RecurrenceProviderRule(
            rrule = parts.joinToString(";"),
            exdates = exdates.sorted().map(FORMATTER::format),
        )
    }

    private fun countUntil(master: RecurrenceMaster, exdates: List<Instant>, count: Int): LocalDate {
        val exclusionDates = exdates.mapTo(mutableSetOf()) { it.atZone(master.zoneId).toLocalDate() }
        val last = engine.generate(
            master = master,
            exceptions = emptyList(),
            exclusions = exclusionDates,
            range = master.startDate..master.startDate.plusYears(100),
        ).take(count).lastOrNull() ?: error("반복 일정 종료일을 계산할 수 없습니다.")
        return last.startAt.atZone(master.zoneId).toLocalDate()
    }

    private fun formatEndOfDay(date: LocalDate, master: RecurrenceMaster): String =
        FORMATTER.format(date.atTime(LocalTime.MAX).atZone(master.zoneId).toInstant())

    private fun weekdayCode(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "MO"
        DayOfWeek.TUESDAY -> "TU"
        DayOfWeek.WEDNESDAY -> "WE"
        DayOfWeek.THURSDAY -> "TH"
        DayOfWeek.FRIDAY -> "FR"
        DayOfWeek.SATURDAY -> "SA"
        DayOfWeek.SUNDAY -> "SU"
    }

    private companion object {
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC)
    }
}
