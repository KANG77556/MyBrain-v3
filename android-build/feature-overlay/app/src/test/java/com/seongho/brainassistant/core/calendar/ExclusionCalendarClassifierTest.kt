package com.seongho.brainassistant.core.calendar

import com.seongho.brainassistant.core.model.ExclusionKind
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExclusionCalendarClassifierTest {
    private val classifier = ExclusionCalendarClassifier()

    @Test
    fun detectsKoreanHolidayCalendarNames() {
        assertTrue(classifier.isKoreanHoliday("대한민국의 공휴일"))
        assertTrue(classifier.isKoreanHoliday("Holidays in South Korea"))
        assertFalse(classifier.isKoreanHoliday("교직원 회의"))
    }

    @Test
    fun schoolCandidatesRequireKnownCategory() {
        assertEquals(SchoolExclusionCategory.VACATION, classifier.classifySchoolEvent("여름방학"))
        assertEquals(SchoolExclusionCategory.DISCRETIONARY_CLOSURE, classifier.classifySchoolEvent("재량휴업일"))
        assertEquals(SchoolExclusionCategory.SCHOOL_EVENT, classifier.classifySchoolEvent("학교 운동회"))
        assertNull(classifier.classifySchoolEvent("수학과 협의회"))
    }

    @Test
    fun multiDayProviderEntryExpandsEndExclusiveInSeoul() {
        val rows = ExclusionDateNormalizer().normalize(
            sourceId = "school",
            sourceKind = ExclusionKind.SCHOOL_CALENDAR,
            event = CalendarExclusionEvent(
                remoteEventId = "vacation-1",
                title = "여름방학",
                begin = Instant.parse("2026-08-02T15:00:00Z"),
                endExclusive = Instant.parse("2026-08-05T15:00:00Z"),
            ),
        )

        assertEquals(
            listOf(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 5)),
            rows.map { it.date },
        )
        assertTrue(rows.none { it.approved })
    }

    @Test
    fun publicHolidayRowsAreApprovedAutomatically() {
        val rows = ExclusionDateNormalizer().normalize(
            sourceId = "holiday",
            sourceKind = ExclusionKind.KOREAN_PUBLIC_HOLIDAY,
            event = CalendarExclusionEvent(
                remoteEventId = "holiday-1",
                title = "광복절",
                begin = Instant.parse("2026-08-14T15:00:00Z"),
                endExclusive = Instant.parse("2026-08-15T15:00:00Z"),
            ),
        )

        assertEquals(listOf(LocalDate.of(2026, 8, 15)), rows.map { it.date })
        assertTrue(rows.single().approved)
    }
}
