package kr.co.mybrain.v2.reminder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import kr.co.mybrain.v2.MainActivity;
import kr.co.mybrain.v2.data.WorkItemEntity;

/** 예약 시각에 알림을 표시합니다. */
public final class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "mybrain_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        long itemId = intent.getLongExtra("item_id", -1L);
        if (itemId < 0) return;
        String title = intent.getStringExtra("title");
        String type = intent.getStringExtra("type");
        ensureChannel(context);

        PendingIntent open = PendingIntent.getActivity(context, requestCode(itemId, 0),
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent complete = action(context, itemId, ReminderActionReceiver.ACTION_COMPLETE, 10000);
        PendingIntent snooze = action(context, itemId, ReminderActionReceiver.ACTION_SNOOZE, 20000);

        String label = WorkItemEntity.TYPE_TASK.equals(type) ? "할 일 알림" : "일정 알림";
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title == null || title.isEmpty() ? label : title)
                .setContentText(label)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(0, "완료", complete)
                .addAction(0, "10분 미루기", snooze);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(requestCode(itemId, 0), builder.build());
    }

    private static PendingIntent action(Context context, long itemId, String action, int offset) {
        Intent actionIntent = new Intent(context, ReminderActionReceiver.class)
                .setAction(action)
                .putExtra("item_id", itemId);
        return PendingIntent.getBroadcast(context, requestCode(itemId, offset), actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int requestCode(long itemId, int offset) {
        return (int) (itemId ^ (itemId >>> 32)) + offset;
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "일정 및 할 일 알림", NotificationManager.IMPORTANCE_HIGH));
        }
    }
}
