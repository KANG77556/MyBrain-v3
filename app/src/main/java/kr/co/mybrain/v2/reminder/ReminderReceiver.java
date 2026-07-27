package kr.co.mybrain.v2.reminder;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import kr.co.mybrain.v2.AdaptiveMainActivity;
import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.data.WorkItemRepository;

/** 예약 시각에 알림을 표시하고 반복 일정의 다음 회차를 준비합니다. */
public final class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = ReminderNotifications.CHANNEL_ID;

    @Override
    public void onReceive(Context context, Intent intent) {
        long itemId = intent.getLongExtra("item_id", -1L);
        if (itemId < 0L) return;

        String title = intent.getStringExtra("title");
        String type = intent.getStringExtra("type");
        ReminderNotifications.ensureChannel(context);

        PendingIntent open = PendingIntent.getActivity(
                context,
                requestCode(itemId, 0),
                new Intent(context, AdaptiveMainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        PendingIntent complete = action(context, itemId, ReminderActionReceiver.ACTION_COMPLETE, 1);
        PendingIntent snooze = action(context, itemId, ReminderActionReceiver.ACTION_SNOOZE, 2);

        String label = WorkItemEntity.TYPE_TASK.equals(type) ? "할 일 알림" : "일정 알림";
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title == null || title.trim().isEmpty() ? label : title)
                .setContentText(label)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(0, "완료", complete)
                .addAction(0, "10분 미루기", snooze);

        if (ReminderPermissionState.notificationsAllowed(context)) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            try {
                if (manager != null) manager.notify(notificationId(itemId), builder.build());
            } catch (SecurityException ignored) {
                // 권한이 실행 직전에 변경된 경우 알림 표시만 건너뜁니다.
            }
        }

        if (WorkItemEntity.TYPE_SCHEDULE.equals(type)) {
            WorkItemRepository.getInstance(context).advanceRecurrence(itemId, ignored -> { });
        }
    }

    private PendingIntent action(Context context, long itemId, String action, int offset) {
        Intent actionIntent = new Intent(context, ReminderActionReceiver.class)
                .setAction(action)
                .putExtra("item_id", itemId);
        return PendingIntent.getBroadcast(
                context,
                requestCode(itemId, offset),
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private int requestCode(long itemId, int offset) {
        return notificationId(itemId) * 10 + offset;
    }

    private int notificationId(long itemId) {
        return (int) (itemId ^ (itemId >>> 32));
    }
}
