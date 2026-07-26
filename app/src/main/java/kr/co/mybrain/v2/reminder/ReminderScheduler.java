package kr.co.mybrain.v2.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import kr.co.mybrain.v2.data.WorkItemEntity;

/** 일정·할 일 알림을 AlarmManager에 등록하거나 취소합니다. */
public final class ReminderScheduler {
    private ReminderScheduler() {}

    public static void schedule(Context context, WorkItemEntity item) {
        if (item == null || item.reminderAt == null || item.deletedAt != null || item.completed) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class)
                .putExtra("item_id", item.id)
                .putExtra("title", item.title)
                .putExtra("type", item.type);
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode(item.id), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.reminderAt, pending);
    }

    public static void cancel(Context context, long itemId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode(itemId), intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) manager.cancel(pending);
    }

    private static int requestCode(long id) {
        return (int) (id ^ (id >>> 32));
    }
}