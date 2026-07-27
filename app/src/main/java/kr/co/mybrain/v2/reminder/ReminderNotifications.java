package kr.co.mybrain.v2.reminder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/** 알림 채널 생성과 권한을 확인한 테스트 알림 표시를 담당합니다. */
public final class ReminderNotifications {
    public static final String CHANNEL_ID = "mybrain_reminders";
    private static final int TEST_NOTIFICATION_ID = 390039;

    private ReminderNotifications() {}

    public static void ensureChannel(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "일정 및 할 일 알림",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("MyBrain에 저장한 일정과 할 일을 알려줍니다.");
        manager.createNotificationChannel(channel);
    }

    public static boolean showTest(Context context) {
        if (context == null || !ReminderPermissionState.notificationsAllowed(context)) return false;
        ensureChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("MyBrain 알림 테스트")
                .setContentText("알림 표시가 정상적으로 작동합니다.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, builder.build());
            return true;
        } catch (SecurityException ignored) {
            return false;
        }
    }
}
