package kr.co.mybrain.ai;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 예약된 시각에 일정·할 일 알림을 표시합니다. */
public class AlarmReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "mybrain_schedule_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        String type = intent.getStringExtra("type");
        int reminderMinutes = intent.getIntExtra("reminderMinutes", 0);
        int notificationId = intent.getIntExtra("notificationId", 1);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        createChannel(manager);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(context, CHANNEL_ID)
                : new android.app.Notification.Builder(context);

        String content = title == null || title.isEmpty() ? "확인할 항목이 있습니다." : title;
        String timing = reminderText(reminderMinutes);
        if (!timing.isEmpty()) content = timing + " · " + content;

        builder.setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle((type == null ? "일정" : type) + " 알림")
                .setContentText(content)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        manager.notify(notificationId, builder.build());
    }

    /** 알림이 일정 시각보다 얼마나 먼저 울렸는지 표시합니다. */
    private String reminderText(int minutes) {
        if (minutes == 5) return "5분 전";
        if (minutes == 10) return "10분 전";
        if (minutes == 30) return "30분 전";
        if (minutes == 60) return "1시간 전";
        return "";
    }

    /** Android 8 이상에서 필요한 알림 채널을 만듭니다. */
    private void createChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "일정 및 할 일 알림",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("MyBrain AI에 저장된 일정과 할 일을 알려줍니다.");
            manager.createNotificationChannel(channel);
        }
    }
}
