package kr.co.mybrain.v2.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SaveIntegrityPolicyTest {
    @Test public void scheduleWithoutDate_requiresCorrection() {
        assertTrue(SaveIntegrityPolicy.requiresDate(WorkItemEntity.TYPE_SCHEDULE, null));
        assertFalse(SaveIntegrityPolicy.requiresDate(WorkItemEntity.TYPE_SCHEDULE, 1L));
    }

    @Test public void undatedTask_canStillBeSaved() {
        assertFalse(SaveIntegrityPolicy.requiresDate(WorkItemEntity.TYPE_TASK, null));
        assertFalse(SaveIntegrityPolicy.opensCalendar(WorkItemEntity.TYPE_TASK, null));
    }

    @Test public void datedScheduleAndTask_openCalendar() {
        assertTrue(SaveIntegrityPolicy.opensCalendar(WorkItemEntity.TYPE_SCHEDULE, 100L));
        assertTrue(SaveIntegrityPolicy.opensCalendar(WorkItemEntity.TYPE_TASK, 100L));
        assertFalse(SaveIntegrityPolicy.opensCalendar(WorkItemEntity.TYPE_MEMO, 100L));
    }

    @Test public void titleAndSource_areNormalized() {
        assertEquals("교무 회의", SaveIntegrityPolicy.normalizedTitle("  교무   회의  "));
        assertEquals("제목 없음", SaveIntegrityPolicy.normalizedTitle("  "));
        assertEquals("내일 9시 회의", SaveIntegrityPolicy.normalizedSource(" 내일   9시 회의 "));
    }
}
