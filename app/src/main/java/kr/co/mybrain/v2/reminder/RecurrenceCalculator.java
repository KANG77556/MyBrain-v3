package kr.co.mybrain.v2.reminder;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;

import kr.co.mybrain.v2.data.WorkItemEntity;

/** 반복 규칙에 따라 다음 시작·종료·알림 시각을 계산합니다. */
public final class RecurrenceCalculator {
    private RecurrenceCalculator() { }

    public static boolean isRepeating(WorkItemEntity item) {
        return item != null && item.repeatRule != null && !"NONE".equals(item.repeatRule);
    }

    public static boolean moveToNext(WorkItemEntity item, ZoneId zoneId) {
        if (!isRepeating(item) || item.startAt == null) return false;

        long oldStart = item.startAt;
        long duration = item.endAt == null ? -1L : Math.max(0L, item.endAt - oldStart);
        long reminderOffset = item.reminderAt == null ? Long.MIN_VALUE : oldStart - item.reminderAt;

        ZonedDateTime next = Instant.ofEpochMilli(oldStart).atZone(zoneId);
        switch (item.repeatRule) {
            case "DAILY":
                next = next.plusDays(1);
                break;
            case "WEEKDAYS":
                do {
                    next = next.plusDays(1);
                } while (next.getDayOfWeek() == DayOfWeek.SATURDAY
                        || next.getDayOfWeek() == DayOfWeek.SUNDAY);
                break;
            case "WEEKLY":
                next = next.plusWeeks(1);
                break;
            case "MONTHLY":
                next = next.plusMonths(1);
                break;
            default:
                return false;
        }

        item.startAt = next.toInstant().toEpochMilli();
        item.endAt = duration < 0L ? null : item.startAt + duration;
        item.reminderAt = reminderOffset == Long.MIN_VALUE ? null : item.startAt - reminderOffset;
        item.completed = false;
        return true;
    }
}
