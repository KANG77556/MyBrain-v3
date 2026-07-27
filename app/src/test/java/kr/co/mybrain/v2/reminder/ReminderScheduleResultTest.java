package kr.co.mybrain.v2.reminder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReminderScheduleResultTest {
    @Test public void exactWithNotificationNeedsNoAttention() {
        ReminderScheduleResult result = ReminderScheduleResult.exact(1000L, true, true);
        assertTrue(result.alarmScheduled());
        assertFalse(result.needsAttention());
        assertTrue(result.userSummary().contains("확인"));
    }

    @Test public void exactWithoutNotificationNeedsAttention() {
        ReminderScheduleResult result = ReminderScheduleResult.exact(1000L, true, false);
        assertTrue(result.alarmScheduled());
        assertTrue(result.needsAttention());
        assertTrue(result.userSummary().contains("권한"));
    }

    @Test public void inexactAlwaysExplainsPossibleDelay() {
        ReminderScheduleResult result = ReminderScheduleResult.inexact(1000L, true, true);
        assertTrue(result.alarmScheduled());
        assertTrue(result.needsAttention());
        assertTrue(result.userSummary().contains("늦"));
    }

    @Test public void failedAlarmDoesNotCancelSavedDataMeaning() {
        ReminderScheduleResult result = ReminderScheduleResult.failed(1000L, true, "failure");
        assertFalse(result.alarmScheduled());
        assertTrue(result.needsAttention());
        assertTrue(result.userSummary().contains("일정은 저장"));
    }

    @Test public void itemWithoutReminderNeedsNoAttention() {
        ReminderScheduleResult result = ReminderScheduleResult.none(false);
        assertFalse(result.alarmScheduled());
        assertFalse(result.needsAttention());
    }
}
