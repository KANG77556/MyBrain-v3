package kr.co.mybrain.v2.widget;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import kr.co.mybrain.v2.data.WorkItemEntity;

/** 홈 위젯에 표시할 오늘 일정과 미완료 할 일을 간결하게 정리합니다. */
public final class TodayWidgetPolicy {
    private TodayWidgetPolicy() {}

    public static String summary(List<WorkItemEntity> today, List<WorkItemEntity> openTasks) {
        int schedules = 0;
        if (today != null) {
            for (WorkItemEntity item : today) {
                if (item != null && WorkItemEntity.TYPE_SCHEDULE.equals(item.type)) schedules++;
            }
        }
        int tasks = openTasks == null ? 0 : openTasks.size();
        return "오늘 일정 " + schedules + "개  ·  미완료 " + tasks + "개";
    }

    public static String timeline(List<WorkItemEntity> today, ZoneId zoneId) {
        if (today == null || today.isEmpty()) return "오늘 등록된 일정이 없습니다.";
        StringBuilder text = new StringBuilder();
        int count = 0;
        for (WorkItemEntity item : today) {
            if (item == null || !WorkItemEntity.TYPE_SCHEDULE.equals(item.type)) continue;
            if (count > 0) text.append("\n");
            text.append(timeLabel(item, zoneId)).append("  ").append(safeTitle(item.title));
            if (++count == 3) break;
        }
        return count == 0 ? "오늘 등록된 일정이 없습니다." : text.toString();
    }

    static String timeLabel(WorkItemEntity item, ZoneId zoneId) {
        if (item.allDay || item.startAt == null) return "종일";
        ZoneId safeZone = zoneId == null ? ZoneId.systemDefault() : zoneId;
        return Instant.ofEpochMilli(item.startAt).atZone(safeZone)
                .format(DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA));
    }

    static String safeTitle(String value) {
        String title = value == null || value.trim().isEmpty() ? "제목 없는 일정" : value.trim();
        return title.length() <= 22 ? title : title.substring(0, 22) + "…";
    }
}
