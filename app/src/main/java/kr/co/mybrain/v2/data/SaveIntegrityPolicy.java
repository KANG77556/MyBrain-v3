package kr.co.mybrain.v2.data;

/** 저장 전 필수값과 저장 후 이동 위치를 일관되게 판단합니다. */
public final class SaveIntegrityPolicy {
    private SaveIntegrityPolicy() {}

    public static boolean requiresDate(String type, Long startAt) {
        return WorkItemEntity.TYPE_SCHEDULE.equals(type) && startAt == null;
    }

    public static boolean opensCalendar(String type, Long startAt) {
        return startAt != null && (WorkItemEntity.TYPE_SCHEDULE.equals(type)
                || WorkItemEntity.TYPE_TASK.equals(type));
    }

    public static String normalizedTitle(String value) {
        String title = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        return title.isEmpty() ? "제목 없음" : title;
    }

    public static String normalizedSource(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
