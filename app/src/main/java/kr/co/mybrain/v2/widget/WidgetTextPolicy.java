package kr.co.mybrain.v2.widget;

import java.time.ZoneId;
import java.util.List;

import kr.co.mybrain.v2.data.WorkItemEntity;

/** 기능별 홈 위젯에 표시할 텍스트를 안전하게 구성합니다. */
public final class WidgetTextPolicy {
    private WidgetTextPolicy() {}

    public static String taskSummary(List<WorkItemEntity> tasks) {
        int count = tasks == null ? 0 : tasks.size();
        return "미완료 할 일 " + count + "개";
    }

    public static String taskLines(List<WorkItemEntity> tasks) {
        if (tasks == null || tasks.isEmpty()) return "남은 할 일이 없습니다.";
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (WorkItemEntity item : tasks) {
            if (item == null || !WorkItemEntity.TYPE_TASK.equals(item.type)) continue;
            if (count > 0) out.append("\n");
            out.append("□ ").append(title(item.title, "제목 없는 할 일"));
            if (++count == 3) break;
        }
        return count == 0 ? "남은 할 일이 없습니다." : out.toString();
    }

    public static String scheduleSummary(List<WorkItemEntity> items) {
        int count = 0;
        if (items != null) {
            for (WorkItemEntity item : items) {
                if (item != null && WorkItemEntity.TYPE_SCHEDULE.equals(item.type)) count++;
            }
        }
        return "오늘 일정 " + count + "개";
    }

    public static String scheduleLines(List<WorkItemEntity> items, ZoneId zoneId) {
        return TodayWidgetPolicy.timeline(items, zoneId);
    }

    private static String title(String value, String fallback) {
        String result = value == null || value.trim().isEmpty() ? fallback : value.trim();
        return result.length() <= 24 ? result : result.substring(0, 24) + "…";
    }
}
