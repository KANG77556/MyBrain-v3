package kr.co.mybrain.v2.ui;

import java.util.List;

import kr.co.mybrain.v2.data.WorkItemEntity;

/** 오늘 일정·할 일·다음 일정을 화면 표시용 값으로 계산합니다. */
public final class TodayDashboardPolicy {
    private TodayDashboardPolicy() {}

    public static Summary summarize(List<WorkItemEntity> todayItems,
                                    List<WorkItemEntity> openTasks,
                                    long now) {
        int schedules = 0;
        int todayTasks = 0;
        long nextStart = Long.MAX_VALUE;
        String nextTitle = null;

        if (todayItems != null) {
            for (WorkItemEntity item : todayItems) {
                if (item == null) continue;
                if (WorkItemEntity.TYPE_SCHEDULE.equals(item.type)) schedules++;
                if (WorkItemEntity.TYPE_TASK.equals(item.type) && !item.completed) todayTasks++;
                if (item.startAt != null && item.startAt >= now && item.startAt < nextStart) {
                    nextStart = item.startAt;
                    nextTitle = item.title;
                }
            }
        }

        int unfinished = openTasks == null ? 0 : openTasks.size();
        return new Summary(schedules, todayTasks, unfinished,
                nextStart == Long.MAX_VALUE ? null : nextStart, nextTitle);
    }

    public static String nextTimeLabel(Long nextStart, long now) {
        if (nextStart == null) return "다음 일정 없음";
        long remaining = nextStart - now;
        if (remaining <= 0L) return "곧 시작";
        long minutes = (remaining + 59_999L) / 60_000L;
        if (minutes < 60L) return minutes + "분 후";
        long hours = minutes / 60L;
        long rest = minutes % 60L;
        return rest == 0L ? hours + "시간 후" : hours + "시간 " + rest + "분 후";
    }

    public static final class Summary {
        public final int scheduleCount;
        public final int todayTaskCount;
        public final int unfinishedTaskCount;
        public final Long nextStart;
        public final String nextTitle;

        Summary(int scheduleCount, int todayTaskCount, int unfinishedTaskCount,
                Long nextStart, String nextTitle) {
            this.scheduleCount = scheduleCount;
            this.todayTaskCount = todayTaskCount;
            this.unfinishedTaskCount = unfinishedTaskCount;
            this.nextStart = nextStart;
            this.nextTitle = nextTitle;
        }
    }
}
