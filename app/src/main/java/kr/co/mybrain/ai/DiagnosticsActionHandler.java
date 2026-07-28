package kr.co.mybrain.ai;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/** 진단 화면의 설정 이동·알림·복사·공유 동작을 담당합니다. */
public final class DiagnosticsActionHandler {
    public static final int REQUEST_MICROPHONE = 4701;
    private static final String CHANNEL_ID = "device_diagnostics";
    private final Activity activity;
    public DiagnosticsActionHandler(Activity activity) { this.activity = activity; }
    public void perform(DiagnosticItem.Action action) {
        if (action == null) return;
        switch (action) {
            case OPEN_NOTIFICATION_SETTINGS: openOrAppSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName())); break;
            case OPEN_EXACT_ALARM_SETTINGS: openOrAppSettings(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + activity.getPackageName()))); break;
            case REQUEST_MICROPHONE_PERMISSION: activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE); break;
            case OPEN_BATTERY_SETTINGS: openOrAppSettings(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); break;
            case REQUEST_WIDGET_PIN: requestWidget(); break;
            default: break;
        }
    }
    public boolean sendTestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = activity.getSystemService(NotificationManager.class);
        if (manager == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "기기 진단 테스트", NotificationManager.IMPORTANCE_DEFAULT));
        NotificationCompat.Builder builder = new NotificationCompat.Builder(activity, CHANNEL_ID).setSmallIcon(R.mipmap.ic_launcher).setContentTitle("MyBrain AI 테스트 알림").setContentText("알림 기능이 정상적으로 작동합니다.").setAutoCancel(true);
        NotificationManagerCompat.from(activity).notify(4701, builder.build());
        return true;
    }
    public void copy(String report) {
        ClipboardManager manager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("MyBrain AI 진단", report));
    }
    public boolean share(String report) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, "MyBrain AI 기기 진단 결과");
            intent.putExtra(Intent.EXTRA_TEXT, report);
            activity.startActivity(Intent.createChooser(intent, "진단 결과 공유"));
            return true;
        } catch (Exception ignored) { copy(report); return false; }
    }
    private void requestWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppWidgetManager manager = AppWidgetManager.getInstance(activity);
            ComponentName provider = new ComponentName(activity, TodayWidgetProvider.class);
            if (manager.isRequestPinAppWidgetSupported()) { manager.requestPinAppWidget(provider, null, null); return; }
        }
        openOrAppSettings(new Intent(Settings.ACTION_HOME_SETTINGS));
    }
    private void openOrAppSettings(Intent intent) {
        try { activity.startActivity(intent); }
        catch (Exception ignored) { activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName()))); }
    }
}
