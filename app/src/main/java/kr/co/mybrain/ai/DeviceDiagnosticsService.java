package kr.co.mybrain.ai;

import android.Manifest;
import android.app.AlarmManager;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;
import java.util.ArrayList;
import java.util.List;

/** Android 시스템 상태를 읽되 사용자 일정·메모·API 키는 읽지 않습니다. */
public final class DeviceDiagnosticsService {
    private final Context context;
    public DeviceDiagnosticsService(Context context) { this.context = context.getApplicationContext(); }
    public List<DiagnosticItem> inspectAll() {
        List<DiagnosticItem> out = new ArrayList<>();
        out.add(safe(DiagnosticItem.Type.APP_INFO, "앱 정보", this::appInfo));
        out.add(safe(DiagnosticItem.Type.DEVICE_INFO, "기기 정보", this::deviceInfo));
        out.add(safe(DiagnosticItem.Type.NOTIFICATION_PERMISSION, "알림 권한", this::notification));
        out.add(safe(DiagnosticItem.Type.EXACT_ALARM_PERMISSION, "정확한 알람", this::exactAlarm));
        out.add(safe(DiagnosticItem.Type.MICROPHONE_PERMISSION, "마이크 권한", this::microphone));
        out.add(safe(DiagnosticItem.Type.BATTERY_OPTIMIZATION, "배터리 최적화", this::battery));
        out.add(safe(DiagnosticItem.Type.WIDGET_SUPPORT, "홈 화면 위젯", this::widget));
        out.add(safe(DiagnosticItem.Type.DATABASE, "앱 데이터 저장소", this::database));
        return out;
    }
    private DiagnosticItem safe(DiagnosticItem.Type type, String title, Check check) {
        try { return check.run(); } catch (Exception ignored) { return DiagnosticItem.error(type, title); }
    }
    private DiagnosticItem appInfo() throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        String version = info.versionName == null ? "알 수 없음" : info.versionName;
        return new DiagnosticItem(DiagnosticItem.Type.APP_INFO, "앱 정보", "MyBrain AI " + version + " (빌드 " + PackageInfoCompat.getLongVersionCode(info) + ")", DiagnosticItem.Status.NORMAL, DiagnosticItem.Action.COPY_APP_INFO);
    }
    private DiagnosticItem deviceInfo() {
        return new DiagnosticItem(DiagnosticItem.Type.DEVICE_INFO, "기기 정보", Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT, DiagnosticItem.Status.NORMAL, DiagnosticItem.Action.COPY_APP_INFO);
    }
    private DiagnosticItem notification() {
        boolean granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ? NotificationManagerCompat.from(context).areNotificationsEnabled() : ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        return new DiagnosticItem(DiagnosticItem.Type.NOTIFICATION_PERMISSION, "알림 권한", granted ? "알림을 정상적으로 받을 수 있습니다." : "알림 권한이 차단되어 있습니다.", DiagnosticStatusResolver.permission(granted), granted ? null : DiagnosticItem.Action.OPEN_NOTIFICATION_SETTINGS);
    }
    private DiagnosticItem exactAlarm() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return new DiagnosticItem(DiagnosticItem.Type.EXACT_ALARM_PERMISSION, "정확한 알람", "현재 Android 버전에서는 별도 허용이 필요하지 않습니다.", DiagnosticItem.Status.UNSUPPORTED, null);
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        boolean allowed = manager != null && manager.canScheduleExactAlarms();
        return new DiagnosticItem(DiagnosticItem.Type.EXACT_ALARM_PERMISSION, "정확한 알람", allowed ? "정확한 시간에 알림을 예약할 수 있습니다." : "정확한 알람 허용이 필요합니다.", DiagnosticStatusResolver.exactAlarm(true, allowed), allowed ? null : DiagnosticItem.Action.OPEN_EXACT_ALARM_SETTINGS);
    }
    private DiagnosticItem microphone() {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) return new DiagnosticItem(DiagnosticItem.Type.MICROPHONE_PERMISSION, "마이크 권한", "이 기기에는 마이크가 없습니다.", DiagnosticItem.Status.UNSUPPORTED, null);
        boolean granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        return new DiagnosticItem(DiagnosticItem.Type.MICROPHONE_PERMISSION, "마이크 권한", granted ? "음성 입력을 사용할 수 있습니다." : "음성 입력을 사용하려면 권한이 필요합니다.", DiagnosticStatusResolver.permission(granted), granted ? null : DiagnosticItem.Action.REQUEST_MICROPHONE_PERMISSION);
    }
    private DiagnosticItem battery() {
        PowerManager manager = context.getSystemService(PowerManager.class);
        boolean ignored = manager != null && manager.isIgnoringBatteryOptimizations(context.getPackageName());
        return new DiagnosticItem(DiagnosticItem.Type.BATTERY_OPTIMIZATION, "배터리 최적화", ignored ? "백그라운드 제한 대상에서 제외되어 있습니다." : "알림이 지연될 수 있어 설정 확인을 권장합니다.", DiagnosticStatusResolver.permission(ignored), ignored ? null : DiagnosticItem.Action.OPEN_BATTERY_SETTINGS);
    }
    private DiagnosticItem widget() {
        boolean supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported();
        return new DiagnosticItem(DiagnosticItem.Type.WIDGET_SUPPORT, "홈 화면 위젯", supported ? "앱에서 위젯 추가를 요청할 수 있습니다." : "홈 화면을 길게 눌러 위젯을 추가하세요.", DiagnosticStatusResolver.widget(supported), DiagnosticItem.Action.REQUEST_WIDGET_PIN);
    }
    private DiagnosticItem database() {
        context.getSharedPreferences(WorkItemStore.PREFS, Context.MODE_PRIVATE).contains(WorkItemStore.KEY_ITEMS);
        return new DiagnosticItem(DiagnosticItem.Type.DATABASE, "앱 데이터 저장소", "데이터 저장소 읽기가 정상입니다.", DiagnosticItem.Status.NORMAL, null);
    }
    private interface Check { DiagnosticItem run() throws Exception; }
}
