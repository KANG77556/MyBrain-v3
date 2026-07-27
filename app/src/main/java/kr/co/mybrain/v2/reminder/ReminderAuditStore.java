package kr.co.mybrain.v2.reminder;

import android.content.Context;
import android.content.SharedPreferences;

/** 알림 예약 요청의 마지막 결과를 기기 안에 보관해 진단 화면에서 확인합니다. */
public final class ReminderAuditStore {
    private static final String FILE = "reminder_audit_v1";
    private static final String LAST_STATUS = "last_status";
    private static final String LAST_ITEM = "last_item";
    private static final String LAST_REMINDER = "last_reminder";
    private static final String LAST_RECORDED = "last_recorded";
    private static final String LAST_DETAIL = "last_detail";
    private static final String LAST_REASON = "last_reason";
    private static final String RESCHEDULE_COUNT = "reschedule_count";
    private static final String RESCHEDULE_AT = "reschedule_at";

    private ReminderAuditStore() {}

    public static void record(Context context, long itemId, ReminderScheduleResult result, String reason) {
        if (context == null || result == null) return;
        prefs(context).edit()
                .putString(LAST_STATUS, result.status)
                .putLong(LAST_ITEM, itemId)
                .putLong(LAST_REMINDER, result.reminderAt == null ? -1L : result.reminderAt)
                .putLong(LAST_RECORDED, System.currentTimeMillis())
                .putString(LAST_DETAIL, result.detail)
                .putString(LAST_REASON, reason == null ? "" : reason)
                .apply();
    }

    public static void recordReschedule(Context context, int count) {
        if (context == null) return;
        prefs(context).edit()
                .putInt(RESCHEDULE_COUNT, Math.max(0, count))
                .putLong(RESCHEDULE_AT, System.currentTimeMillis())
                .apply();
    }

    public static Snapshot load(Context context) {
        SharedPreferences prefs = prefs(context);
        return new Snapshot(
                prefs.getString(LAST_STATUS, ReminderScheduleResult.NONE),
                prefs.getLong(LAST_ITEM, -1L),
                prefs.getLong(LAST_REMINDER, -1L),
                prefs.getLong(LAST_RECORDED, 0L),
                prefs.getString(LAST_DETAIL, ""),
                prefs.getString(LAST_REASON, ""),
                prefs.getInt(RESCHEDULE_COUNT, 0),
                prefs.getLong(RESCHEDULE_AT, 0L));
    }

    public static void clearItem(Context context, long itemId) {
        Snapshot snapshot = load(context);
        if (snapshot.itemId != itemId) return;
        prefs(context).edit()
                .remove(LAST_STATUS)
                .remove(LAST_ITEM)
                .remove(LAST_REMINDER)
                .remove(LAST_RECORDED)
                .remove(LAST_DETAIL)
                .remove(LAST_REASON)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static final class Snapshot {
        public final String status;
        public final long itemId;
        public final long reminderAt;
        public final long recordedAt;
        public final String detail;
        public final String reason;
        public final int rescheduleCount;
        public final long rescheduleAt;

        Snapshot(String status, long itemId, long reminderAt, long recordedAt, String detail,
                 String reason, int rescheduleCount, long rescheduleAt) {
            this.status = status;
            this.itemId = itemId;
            this.reminderAt = reminderAt;
            this.recordedAt = recordedAt;
            this.detail = detail == null ? "" : detail;
            this.reason = reason == null ? "" : reason;
            this.rescheduleCount = rescheduleCount;
            this.rescheduleAt = rescheduleAt;
        }
    }
}
