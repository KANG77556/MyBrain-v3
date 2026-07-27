package kr.co.mybrain.v2.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import kr.co.mybrain.v2.data.WorkItemEntity;

public class TodayDashboardPolicyTest {
    @Test public void summarize_countsSchedulesTasksAndNextItem() {
        long now = 1_000_000L;
        WorkItemEntity schedule = item(WorkItemEntity.TYPE_SCHEDULE, "회의", now + 3_600_000L, false);
        WorkItemEntity task = item(WorkItemEntity.TYPE_TASK, "교실 정리", now + 7_200_000L, false);
        WorkItemEntity completed = item(WorkItemEntity.TYPE_TASK, "완료", now + 10_800_000L, true);

        TodayDashboardPolicy.Summary summary = TodayDashboardPolicy.summarize(
                Arrays.asList(schedule, task, completed), Arrays.asList(task), now);

        assertEquals(1, summary.scheduleCount);
        assertEquals(1, summary.todayTaskCount);
        assertEquals(1, summary.unfinishedTaskCount);
        assertEquals(Long.valueOf(now + 3_600_000L), summary.nextStart);
        assertEquals("회의", summary.nextTitle);
    }

    @Test public void summarize_handlesEmptyLists() {
        TodayDashboardPolicy.Summary summary = TodayDashboardPolicy.summarize(
                Collections.emptyList(), Collections.emptyList(), 10L);
        assertEquals(0, summary.scheduleCount);
        assertEquals(0, summary.todayTaskCount);
        assertEquals(0, summary.unfinishedTaskCount);
        assertNull(summary.nextStart);
        assertEquals("다음 일정 없음", TodayDashboardPolicy.nextTimeLabel(null, 10L));
    }

    @Test public void nextTimeLabel_roundsUpMinutesAndFormatsHours() {
        assertEquals("1분 후", TodayDashboardPolicy.nextTimeLabel(60_001L, 1L));
        assertEquals("1시간 1분 후", TodayDashboardPolicy.nextTimeLabel(3_660_001L, 1L));
        assertEquals("2시간 후", TodayDashboardPolicy.nextTimeLabel(7_200_001L, 1L));
    }

    private static WorkItemEntity item(String type, String title, long startAt, boolean completed) {
        WorkItemEntity item = new WorkItemEntity();
        item.type = type;
        item.title = title;
        item.startAt = startAt;
        item.completed = completed;
        return item;
    }
}
