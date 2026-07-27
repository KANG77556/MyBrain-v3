package kr.co.mybrain.v2.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import kr.co.mybrain.v2.data.WorkItemEntity;

public class ScheduleConflictPolicyTest {
    @Test public void overlappingSchedulesAreDetected() {
        WorkItemEntity a = schedule(1_000L, 3_000L);
        WorkItemEntity b = schedule(2_000L, 4_000L);
        assertTrue(ScheduleConflictPolicy.overlaps(a, b));
    }

    @Test public void touchingSchedulesDoNotOverlap() {
        WorkItemEntity a = schedule(1_000L, 2_000L);
        WorkItemEntity b = schedule(2_000L, 3_000L);
        assertFalse(ScheduleConflictPolicy.overlaps(a, b));
    }

    @Test public void missingEndUsesOneHour() {
        WorkItemEntity item = schedule(10_000L, null);
        assertEquals(3_610_000L, ScheduleConflictPolicy.endAt(item));
    }

    @Test public void tasksAreIgnored() {
        WorkItemEntity a = schedule(1_000L, 3_000L);
        WorkItemEntity b = schedule(2_000L, 4_000L);
        b.type = WorkItemEntity.TYPE_TASK;
        assertFalse(ScheduleConflictPolicy.overlaps(a, b));
    }

    @Test public void deletedAndSameItemAreIgnored() {
        WorkItemEntity candidate = schedule(1_000L, 3_000L);
        candidate.id = 7L;
        WorkItemEntity same = schedule(1_500L, 2_500L);
        same.id = 7L;
        WorkItemEntity deleted = schedule(1_500L, 2_500L);
        deleted.deletedAt = 9L;
        List<WorkItemEntity> result = ScheduleConflictPolicy.conflicts(candidate, Arrays.asList(same, deleted));
        assertTrue(result.isEmpty());
    }

    private WorkItemEntity schedule(long start, Long end) {
        WorkItemEntity item = new WorkItemEntity();
        item.type = WorkItemEntity.TYPE_SCHEDULE;
        item.startAt = start;
        item.endAt = end;
        return item;
    }
}
