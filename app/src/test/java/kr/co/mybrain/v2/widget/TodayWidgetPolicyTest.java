package kr.co.mybrain.v2.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;

import kr.co.mybrain.v2.data.WorkItemEntity;

public class TodayWidgetPolicyTest {
    private final ZoneId zone = ZoneId.of("Asia/Seoul");

    @Test public void summaryCountsSchedulesAndTasks() {
        WorkItemEntity schedule = item(WorkItemEntity.TYPE_SCHEDULE, "회의", 9, 0);
        WorkItemEntity memo = item(WorkItemEntity.TYPE_MEMO, "메모", 10, 0);
        WorkItemEntity task = item(WorkItemEntity.TYPE_TASK, "제출", 11, 0);
        assertEquals("오늘 일정 1개  ·  미완료 1개",
                TodayWidgetPolicy.summary(Arrays.asList(schedule, memo), Collections.singletonList(task)));
    }

    @Test public void timelineShowsAtMostThreeSchedules() {
        String text = TodayWidgetPolicy.timeline(Arrays.asList(
                item(WorkItemEntity.TYPE_SCHEDULE, "첫 일정", 9, 0),
                item(WorkItemEntity.TYPE_SCHEDULE, "둘 일정", 10, 0),
                item(WorkItemEntity.TYPE_SCHEDULE, "셋 일정", 11, 0),
                item(WorkItemEntity.TYPE_SCHEDULE, "넷 일정", 12, 0)), zone);
        assertTrue(text.contains("09:00  첫 일정"));
        assertTrue(text.contains("11:00  셋 일정"));
        assertTrue(!text.contains("넷 일정"));
    }

    @Test public void emptyTimelineHasFriendlyMessage() {
        assertEquals("오늘 등록된 일정이 없습니다.", TodayWidgetPolicy.timeline(Collections.emptyList(), zone));
    }

    private WorkItemEntity item(String type, String title, int hour, int minute) {
        WorkItemEntity item = new WorkItemEntity();
        item.type = type;
        item.title = title;
        item.startAt = LocalDateTime.of(2026, 7, 27, hour, minute).atZone(zone).toInstant().toEpochMilli();
        return item;
    }
}
