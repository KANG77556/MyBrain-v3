package kr.co.mybrain.v2.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

public class KoreanNaturalLanguageParserAlpha45Test {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Test public void nextWeekTuesdayAfternoonTwoIsResolvedExactly() {
        ParsedWorkItem item = KoreanNaturalLanguageParser.parse("다음 주 화요일 오후 2시 교무회의", ZONE);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate expectedDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1).plusDays(1);
        assertEquals(LocalDateTime.of(expectedDate, LocalTime.of(14, 0)), dateTime(item.startAt));
    }

    @Test public void threeDaysLaterMorningUsesNineOclock() {
        ParsedWorkItem item = KoreanNaturalLanguageParser.parse("3일 뒤 오전에 상담", ZONE);
        assertEquals(LocalDate.now(ZONE).plusDays(3), dateTime(item.startAt).toLocalDate());
        assertEquals(LocalTime.of(9, 0), dateTime(item.startAt).toLocalTime());
        assertTrue(item.confidence < 0.9f);
    }

    @Test public void afterWorkUsesSixPm() {
        ParsedWorkItem item = KoreanNaturalLanguageParser.parse("이번 주 금요일 퇴근 후 회의", ZONE);
        assertEquals(LocalTime.of(18, 0), dateTime(item.startAt).toLocalTime());
        assertEquals(DayOfWeek.FRIDAY, dateTime(item.startAt).getDayOfWeek());
    }

    @Test public void monthEndUsesCurrentMonthLastDay() {
        ParsedWorkItem item = KoreanNaturalLanguageParser.parse("월말까지 보고서 제출", ZONE);
        assertNotNull(item.startAt);
        assertEquals(LocalDate.now(ZONE).with(TemporalAdjusters.lastDayOfMonth()), dateTime(item.startAt).toLocalDate());
    }

    @Test public void invalidCalendarDateDoesNotCrashOrInventDate() {
        ParsedWorkItem item = KoreanNaturalLanguageParser.parse("2월 31일 회의", ZONE);
        assertEquals(null, item.startAt);
    }

    @Test public void touchingDateWordsStillCreateSchedule() {
        ParsedWorkItem item = KoreanNaturalLanguageParser.parse("글피 저녁에 약속", ZONE);
        assertNotNull(item.startAt);
        assertEquals(LocalDate.now(ZONE).plusDays(3), dateTime(item.startAt).toLocalDate());
        assertEquals(LocalTime.of(18, 0), dateTime(item.startAt).toLocalTime());
        assertFalse(item.allDay);
    }

    private static LocalDateTime dateTime(Long millis) {
        assertNotNull(millis);
        return Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDateTime();
    }
}
