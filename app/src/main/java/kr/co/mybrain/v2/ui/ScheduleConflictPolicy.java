package kr.co.mybrain.v2.ui;

import java.util.ArrayList;
import java.util.List;

import kr.co.mybrain.v2.data.WorkItemEntity;

/** 새 일정과 기존 일정의 시간 겹침을 계산합니다. */
public final class ScheduleConflictPolicy {
    private static final long DEFAULT_DURATION_MS = 60L * 60L * 1000L;

    private ScheduleConflictPolicy() {}

    public static long endAt(WorkItemEntity item) {
        if (item == null || item.startAt == null) return -1L;
        if (item.endAt == null || item.endAt <= item.startAt) return item.startAt + DEFAULT_DURATION_MS;
        return item.endAt;
    }

    public static boolean overlaps(WorkItemEntity candidate, WorkItemEntity existing) {
        if (candidate == null || existing == null || candidate.startAt == null || existing.startAt == null) return false;
        if (!WorkItemEntity.TYPE_SCHEDULE.equals(candidate.type)
                || !WorkItemEntity.TYPE_SCHEDULE.equals(existing.type)) return false;
        long candidateEnd = endAt(candidate);
        long existingEnd = endAt(existing);
        return candidate.startAt < existingEnd && existing.startAt < candidateEnd;
    }

    public static List<WorkItemEntity> conflicts(WorkItemEntity candidate, List<WorkItemEntity> existingItems) {
        List<WorkItemEntity> result = new ArrayList<>();
        if (existingItems == null) return result;
        for (WorkItemEntity existing : existingItems) {
            if (existing == null || existing.deletedAt != null) continue;
            if (candidate != null && candidate.id > 0L && candidate.id == existing.id) continue;
            if (overlaps(candidate, existing)) result.add(existing);
        }
        return result;
    }

    public static long queryFrom(WorkItemEntity item) {
        if (item == null || item.startAt == null) return -1L;
        return item.startAt - 24L * 60L * 60L * 1000L;
    }

    public static long queryTo(WorkItemEntity item) {
        long end = endAt(item);
        return end < 0L ? -1L : end + 24L * 60L * 60L * 1000L;
    }
}
