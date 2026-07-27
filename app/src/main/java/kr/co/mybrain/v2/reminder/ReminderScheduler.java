package kr.co.mybrain.v2.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import kr.co.mybrain.v2.data.WorkItemEntity;

/** 일정·할 일 알림을 등록하고 실제 예약 요청 결과를 반환합니다. */
public final class ReminderScheduler {
    private ReminderScheduler() {}

    public static ReminderScheduleResult schedule(Context context, WorkItemEntity item) {
        return schedule(context, item, "SAVE_OR_UPDATE");
    }

    public static ReminderScheduleResult schedule(Context context, WorkItemEntity item, String reason) {
        boolean notificationsAllowed = ReminderPermissionState.notificationsAllowed(context);
        if (item == null || item.reminderAt == null || item.deletedAt != null || item.completed) {
            ReminderScheduleResult result = ReminderScheduleResult.none(notificationsAllowed);
            if (item != null) ReminderAuditStore.record(context, item.id, result, reason);
            return result;
        }
        if (item.reminderAt <= System.currentTimeMillis()) {
            ReminderScheduleResult result = ReminderScheduleResult.past(item.reminderAt, notificationsAllowed);
            ReminderAuditStore.record(context, item.id, result, reason);
            return result;
        }

        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            ReminderScheduleResult result = ReminderScheduleResult.failed(
                    item.reminderAt, notificationsAllowed, "알람 서비스를 사용할 수 없습니다.");
            ReminderAuditStore.record(context, item.id, result, reason);
            return result;
        }

        Intent intent = reminderIntent(context, item);
        PendingIntent pending = PendingIntent.getBroadcast(
                context,
                requestCode(item.id),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            boolean exactAllowed = ReminderPermissionState.exactAlarmsAllowed(context);
            if (exactAllowed) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.reminderAt, pending);
                } else {
                    manager.setExact(AlarmManager.RTC_WAKEUP, item.reminderAt, pending);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.reminderAt, pending);
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, item.reminderAt, pending);
            }

            boolean pendingExists = PendingIntent.getBroadcast(
                    context,
                    requestCode(item.id),
                    new Intent(context, ReminderReceiver.class),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE) != null;
            ReminderScheduleResult result = exactAllowed
                    ? ReminderScheduleResult.exact(item.reminderAt, pendingExists, notificationsAllowed)
                    : ReminderScheduleResult.inexact(item.reminderAt, pendingExists, notificationsAllowed);
            ReminderAuditStore.record(context, item.id, result, reason);
            return result;
        } catch (Exception error) {
            ReminderScheduleResult result = ReminderScheduleResult.failed(
                    item.reminderAt,
                    notificationsAllowed,
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            ReminderAuditStore.record(context, item.id, result, reason);
            return result;
        }
    }

    public static void cancel(Context context, long itemId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(
                context,
                requestCode(itemId),
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) {
            if (manager != null) manager.cancel(pending);
            pending.cancel();
        }
        ReminderAuditStore.clearItem(context, itemId);
    }

    public static boolean hasPendingIntent(Context context, long itemId) {
        PendingIntent pending = PendingIntent.getBroadcast(
                context,
                requestCode(itemId),
                new Intent(context, ReminderReceiver.class),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        return pending != null;
    }

    private static Intent reminderIntent(Context context, WorkItemEntity item) {
        return new Intent(context, ReminderReceiver.class)
                .putExtra("item_id", item.id)
                .putExtra("title", item.title)
                .putExtra("type", item.type);
    }

    private static int requestCode(long id) {
        return (int) (id ^ (id >>> 32));
    }
}
