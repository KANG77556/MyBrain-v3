package kr.co.mybrain.v2.reminder;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/** 알림 표시 권한과 정확한 알람 허용 상태를 한 곳에서 확인합니다. */
public final class ReminderPermissionState {
    private ReminderPermissionState() {}

    public static boolean notificationsAllowed(Context context) {
        if (context == null) return false;
        boolean runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    public static boolean exactAlarmsAllowed(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return manager != null && manager.canScheduleExactAlarms();
    }

    public static String summary(Context context) {
        String notifications = notificationsAllowed(context) ? "알림 표시 허용" : "알림 표시 꺼짐";
        String exact = exactAlarmsAllowed(context) ? "정확한 알람 허용" : "일반 알람 사용";
        return notifications + " · " + exact;
    }
}
