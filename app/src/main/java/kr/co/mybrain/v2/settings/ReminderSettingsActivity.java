package kr.co.mybrain.v2.settings;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import kr.co.mybrain.v2.data.WorkItemRepository;
import kr.co.mybrain.v2.reminder.ReminderAuditStore;
import kr.co.mybrain.v2.reminder.ReminderNotifications;
import kr.co.mybrain.v2.reminder.ReminderPermissionState;
import kr.co.mybrain.v2.reminder.ReminderRescheduler;
import kr.co.mybrain.v2.reminder.ReminderScheduleResult;
import kr.co.mybrain.v2.ui.AppUi;

/** 알림 표시 권한과 정확한 알람, 저장된 예약 상태를 사용자가 직접 확인합니다. */
public final class ReminderSettingsActivity extends AppCompatActivity {
    private TextView permissionSummary;
    private TextView futureSummary;
    private TextView auditSummary;
    private WorkItemRepository repository;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                Toast.makeText(this,
                        granted ? "알림 표시를 허용했습니다." : "알림 표시가 허용되지 않았습니다.",
                        Toast.LENGTH_SHORT).show();
                refresh();
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = WorkItemRepository.getInstance(this);
        ReminderNotifications.ensureChannel(this);
        setContentView(buildScreen());
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private View buildScreen() {
        AppUi.Screen screen = AppUi.screen(this);
        LinearLayout root = screen.root;

        Button back = AppUi.secondaryButton(this, "←  설정으로");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, AppUi.dp(this, AppUi.minimumTouchDp(this))));
        root.addView(AppUi.title(this, "일정 알림"));
        root.addView(AppUi.subtitle(this,
                "저장된 일정이 알림 시각에 표시될 수 있는지 확인하고 필요한 권한을 설정합니다."));

        LinearLayout stateCard = AppUi.card(this);
        root.addView(stateCard, AppUi.cardParams(this));
        stateCard.addView(AppUi.sectionTitle(this, "현재 상태"));
        permissionSummary = AppUi.body(this, "알림 상태를 확인하는 중입니다.");
        stateCard.addView(permissionSummary);
        futureSummary = AppUi.body(this, "저장된 미래 알림을 확인하는 중입니다.");
        futureSummary.setPadding(0, AppUi.dp(this, 8), 0, 0);
        stateCard.addView(futureSummary);

        Button notification = AppUi.menuButton(this, "알림 표시 허용", "Android 알림 권한과 앱 알림 설정 열기");
        notification.setOnClickListener(v -> requestNotificationPermission());
        AppUi.addMenu(stateCard, this, notification);

        Button exact = AppUi.menuButton(this, "정확한 알람 허용", "허용하면 절전 중에도 지정 시각에 더 가깝게 알림");
        exact.setOnClickListener(v -> openExactAlarmSettings());
        AppUi.addMenu(stateCard, this, exact);

        LinearLayout actionCard = AppUi.card(this);
        root.addView(actionCard, AppUi.cardParams(this));
        actionCard.addView(AppUi.sectionTitle(this, "예약 확인"));
        auditSummary = AppUi.body(this, "마지막 예약 결과를 확인하는 중입니다.");
        actionCard.addView(auditSummary);

        Button reschedule = AppUi.primaryButton(this, "저장된 알림 다시 등록");
        reschedule.setOnClickListener(v -> {
            reschedule.setEnabled(false);
            reschedule.setText("다시 등록하는 중…");
            ReminderRescheduler.rescheduleAll(this, "MANUAL_SETTINGS", count -> runOnUiThread(() -> {
                reschedule.setEnabled(true);
                reschedule.setText("저장된 알림 다시 등록");
                Toast.makeText(this, count + "개의 미래 알림을 다시 등록했습니다.", Toast.LENGTH_LONG).show();
                refresh();
            }));
        });
        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(-1, AppUi.dp(this, 56));
        primaryParams.setMargins(0, AppUi.dp(this, 14), 0, 0);
        actionCard.addView(reschedule, primaryParams);

        Button test = AppUi.secondaryButton(this, "테스트 알림 표시");
        test.setOnClickListener(v -> {
            boolean shown = ReminderNotifications.showTest(this);
            if (shown) Toast.makeText(this, "테스트 알림을 표시했습니다.", Toast.LENGTH_SHORT).show();
            else {
                Toast.makeText(this, "알림 표시 권한을 먼저 허용하세요.", Toast.LENGTH_LONG).show();
                requestNotificationPermission();
            }
        });
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(-1, AppUi.dp(this, 54));
        testParams.setMargins(0, AppUi.dp(this, 10), 0, 0);
        actionCard.addView(test, testParams);

        LinearLayout noteCard = AppUi.card(this);
        root.addView(noteCard, AppUi.cardParams(this));
        noteCard.addView(AppUi.sectionTitle(this, "알아두세요"));
        noteCard.addView(AppUi.body(this,
                "정확한 알람이 꺼져 있어도 일정은 저장되고 일반 알람으로 예약됩니다. "
                        + "다만 배터리 절전 상태에서는 표시 시각이 조금 늦어질 수 있습니다. "
                        + "삼성 기기의 절전 앱 목록에 MyBrain이 포함되면 알림이 지연될 수 있습니다."));

        return screen.scroll;
    }

    private void refresh() {
        if (permissionSummary == null) return;
        permissionSummary.setText(ReminderPermissionState.summary(this));

        repository.getAll(items -> {
            int future = 0;
            int pending = 0;
            long now = System.currentTimeMillis();
            if (items != null) {
                for (kr.co.mybrain.v2.data.WorkItemEntity item : items) {
                    if (item == null || item.reminderAt == null || item.reminderAt <= now
                            || item.completed || item.deletedAt != null) continue;
                    future++;
                    if (kr.co.mybrain.v2.reminder.ReminderScheduler.hasPendingIntent(this, item.id)) pending++;
                }
            }
            int finalFuture = future;
            int finalPending = pending;
            runOnUiThread(() -> {
                if (futureSummary != null) {
                    futureSummary.setText("미래 알림 " + finalFuture + "개 · 예약 요청 확인 " + finalPending + "개");
                }
            });
        });

        ReminderAuditStore.Snapshot audit = ReminderAuditStore.load(this);
        String lastStatus = statusLabel(audit.status);
        String lastTime = audit.recordedAt <= 0L ? "기록 없음" : formatDateTime(audit.recordedAt);
        String rescheduled = audit.rescheduleAt <= 0L
                ? "자동 재등록 기록 없음"
                : formatDateTime(audit.rescheduleAt) + " · " + audit.rescheduleCount + "개";
        auditSummary.setText("마지막 예약  " + lastStatus + " · " + lastTime
                + "\n마지막 재등록  " + rescheduled
                + (audit.detail.isEmpty() ? "" : "\n상세  " + audit.detail));
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !ReminderPermissionState.notificationsAllowed(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        } catch (Exception ignored) {
            openAppDetails();
        }
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(this, "이 Android 버전에서는 별도 허용이 필요하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
            openAppDetails();
        }
    }

    private void openAppDetails() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private String statusLabel(String status) {
        if (ReminderScheduleResult.EXACT.equals(status)) return "정확한 알람 예약";
        if (ReminderScheduleResult.INEXACT.equals(status)) return "일반 알람 예약";
        if (ReminderScheduleResult.PAST.equals(status)) return "지난 시각";
        if (ReminderScheduleResult.FAILED.equals(status)) return "예약 실패";
        return "알림 없음";
    }

    private String formatDateTime(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.KOREA));
    }
}
