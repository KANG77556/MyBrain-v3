package com.seongho.brainassistant.core.calendar

import com.seongho.brainassistant.core.model.ExclusionDate
import com.seongho.brainassistant.core.model.ExclusionKind
import com.seongho.brainassistant.core.model.ExclusionSource
import java.time.Instant
import java.time.ZoneId

interface ExclusionCalendarGateway {
    suspend fun discoverSources(): List<ExclusionSource>
    suspend fun loadDates(source: ExclusionSource, year: Int): List<ExclusionDate>
}

enum class SchoolExclusionCategory { VACATION, DISCRETIONARY_CLOSURE, SCHOOL_EVENT }

data class CalendarExclusionEvent(
    val remoteEventId: String,
    val title: String,
    val begin: Instant,
    val endExclusive: Instant,
)

class ExclusionCalendarClassifier {
    fun isKoreanHoliday(calendarName: String): Boolean {
        val normalized = calendarName.lowercase().replace(Regex("\\s+"), " ").trim()
        return ("공휴일" in normalized && ("대한민국" in normalized || "한국" in normalized)) ||
            "holidays in south korea" in normalized ||
            "south korea holidays" in normalized
    }

    fun classifySchoolEvent(title: String): SchoolExclusionCategory? {
        val normalized = title.replace(Regex("\\s+"), "").lowercase()
        return when {
            VACATION_KEYWORDS.any(normalized::contains) -> SchoolExclusionCategory.VACATION
            CLOSURE_KEYWORDS.any(normalized::contains) -> SchoolExclusionCategory.DISCRETIONARY_CLOSURE
            SCHOOL_EVENT_KEYWORDS.any(normalized::contains) -> SchoolExclusionCategory.SCHOOL_EVENT
            else -> null
        }
    }

    fun isSchoolCalendarCandidate(calendarName: String): Boolean {
        val normalized = calendarName.replace(Regex("\\s+"), "").lowercase()
        return SCHOOL_CALENDAR_KEYWORDS.any(normalized::contains)
    }

    private companion object {
        val VACATION_KEYWORDS = listOf("여름방학", "겨울방학", "봄방학", "방학식")
        val CLOSURE_KEYWORDS = listOf("재량휴업", "개교기념일", "휴업일")
        val SCHOOL_EVENT_KEYWORDS = listOf("학교운동회", "체육대회", "학교행사", "현장체험학습")
        val SCHOOL_CALENDAR_KEYWORDS = listOf("학교", "학사일정", "교무", "school", "academiccalendar")
    }
}

class ExclusionDateNormalizer(
    private val zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
) {
    fun normalize(
        sourceId: String,
        sourceKind: ExclusionKind,
        event: CalendarExclusionEvent,
    ): List<ExclusionDate> {
        if (!event.begin.isBefore(event.endExclusive)) return emptyList()
        val first = event.begin.atZone(zoneId).toLocalDate()
        val last = event.endExclusive.minusNanos(1).atZone(zoneId).toLocalDate()
        val approved = sourceKind == ExclusionKind.KOREAN_PUBLIC_HOLIDAY
        return generateSequence(first) { date -> date.plusDays(1) }
            .takeWhile { date -> !date.isAfter(last) }
            .map { date ->
                ExclusionDate(
                    sourceId = sourceId,
                    remoteEventId = event.remoteEventId,
                    date = date,
                    title = event.title,
                    approved = approved,
                )
            }
            .toList()
    }
}
