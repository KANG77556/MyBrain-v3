package kr.co.mybrain.v2.reminder;

/** 일정 저장과 알림 예약 결과를 분리해 사용자에게 정확한 상태를 안내합니다. */
public final class ReminderScheduleResult {
    public static final String NONE = "NONE";
    public static final String PAST = "PAST";
    public static final String EXACT = "EXACT";
    public static final String INEXACT = "INEXACT";
    public static final String FAILED = "FAILED";

    public final String status;
    public final Long reminderAt;
    public final boolean pendingIntentCreated;
    public final boolean notificationsAllowed;
    public final String detail;

    private ReminderScheduleResult(String status, Long reminderAt, boolean pendingIntentCreated,
                                   boolean notificationsAllowed, String detail) {
        this.status = status;
        this.reminderAt = reminderAt;
        this.pendingIntentCreated = pendingIntentCreated;
        this.notificationsAllowed = notificationsAllowed;
        this.detail = detail == null ? "" : detail;
    }

    public static ReminderScheduleResult none(boolean notificationsAllowed) {
        return new ReminderScheduleResult(NONE, null, false, notificationsAllowed, "알림 없음");
    }

    public static ReminderScheduleResult past(Long reminderAt, boolean notificationsAllowed) {
        return new ReminderScheduleResult(PAST, reminderAt, false, notificationsAllowed,
                "알림 시각이 이미 지났습니다.");
    }

    public static ReminderScheduleResult exact(long reminderAt, boolean pendingIntentCreated,
                                               boolean notificationsAllowed) {
        return new ReminderScheduleResult(EXACT, reminderAt, pendingIntentCreated,
                notificationsAllowed, "정확한 시각으로 예약했습니다.");
    }

    public static ReminderScheduleResult inexact(long reminderAt, boolean pendingIntentCreated,
                                                 boolean notificationsAllowed) {
        return new ReminderScheduleResult(INEXACT, reminderAt, pendingIntentCreated,
                notificationsAllowed, "절전 정책에 따라 알림 시각이 조금 늦어질 수 있습니다.");
    }

    public static ReminderScheduleResult failed(Long reminderAt, boolean notificationsAllowed,
                                                String detail) {
        return new ReminderScheduleResult(FAILED, reminderAt, false, notificationsAllowed, detail);
    }

    public boolean alarmScheduled() {
        return (EXACT.equals(status) || INEXACT.equals(status)) && pendingIntentCreated;
    }

    public boolean needsAttention() {
        if (NONE.equals(status)) return false;
        return !alarmScheduled() || !notificationsAllowed || INEXACT.equals(status);
    }

    public String userSummary() {
        if (NONE.equals(status)) return "알림을 사용하지 않는 항목입니다.";
        if (PAST.equals(status)) return "알림 시각이 이미 지나 예약하지 않았습니다.";
        if (FAILED.equals(status)) return "일정은 저장됐지만 알림 예약에 실패했습니다.";
        if (!notificationsAllowed) return "알람은 예약했지만 알림 표시 권한이 꺼져 있습니다.";
        if (INEXACT.equals(status)) return "알림을 예약했지만 절전 정책으로 조금 늦을 수 있습니다.";
        return "알림 예약까지 확인했습니다.";
    }
}
