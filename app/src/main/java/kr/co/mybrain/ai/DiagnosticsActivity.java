package kr.co.mybrain.ai;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** 사용자가 직접 권한·알림·위젯 상태를 확인하고 조치하는 실행형 진단 화면입니다. */
public class DiagnosticsActivity extends Activity {
    private final List<DiagnosticItem> items = new ArrayList<>();
    private DeviceDiagnosticsService service;
    private DiagnosticsActionHandler actions;
    private LinearLayout list;
    private boolean firstResume = true;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        service = new DeviceDiagnosticsService(this);
        actions = new DiagnosticsActionHandler(this);
        buildScreen();
        refresh();
    }
    @Override protected void onResume() {
        super.onResume();
        if (firstResume) firstResume = false; else refresh();
    }
    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(28));
        root.setBackgroundColor(Color.rgb(247, 249, 253));
        scroll.addView(root);
        TextView title = text("기기 진단·테스트", 25, Color.rgb(24, 54, 96));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, full());
        TextView guide = text("권한과 시스템 설정을 확인합니다. 일정·메모·API 키 내용은 읽거나 공유하지 않습니다.", 14, Color.DKGRAY);
        guide.setPadding(0, dp(8), 0, dp(14));
        root.addView(guide, full());
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, full());
        addButton(root, "전체 다시 점검", v -> refresh());
        addButton(root, "테스트 알림 보내기", v -> Toast.makeText(this, actions.sendTestNotification() ? "테스트 알림을 보냈습니다." : "알림 권한을 먼저 허용하세요.", Toast.LENGTH_LONG).show());
        addButton(root, "진단 결과 복사", v -> { actions.copy(DiagnosticsReportFormatter.format(items)); Toast.makeText(this, "진단 결과를 복사했습니다.", Toast.LENGTH_SHORT).show(); });
        addButton(root, "진단 결과 공유", v -> { if (!actions.share(DiagnosticsReportFormatter.format(items))) Toast.makeText(this, "공유 앱이 없어 결과를 복사했습니다.", Toast.LENGTH_LONG).show(); });
        addButton(root, "닫기", v -> finish());
        setContentView(scroll);
    }
    private void refresh() {
        items.clear(); items.addAll(service.inspectAll()); list.removeAllViews();
        for (DiagnosticItem item : items) list.addView(card(item));
    }
    private LinearLayout card(DiagnosticItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(Color.WHITE);
        TextView title = text(icon(item.status) + " " + item.title, 17, statusColor(item.status));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title, full());
        TextView description = text(item.description, 14, Color.rgb(68, 80, 98));
        description.setPadding(0, dp(5), 0, item.action == null ? 0 : dp(7));
        card.addView(description, full());
        if (item.action != null && item.action != DiagnosticItem.Action.COPY_APP_INFO) {
            Button action = button(actionLabel(item.action));
            action.setOnClickListener(v -> actions.perform(item.action));
            card.addView(action, new LinearLayout.LayoutParams(-1, dp(44)));
        }
        LinearLayout.LayoutParams params = full(); params.setMargins(0, 0, 0, dp(10)); card.setLayoutParams(params);
        return card;
    }
    private void addButton(LinearLayout root, String label, android.view.View.OnClickListener listener) {
        Button button = button(label); button.setOnClickListener(listener); root.addView(button, buttonParams());
    }
    private String actionLabel(DiagnosticItem.Action action) {
        switch (action) {
            case OPEN_NOTIFICATION_SETTINGS: return "알림 설정 열기";
            case OPEN_EXACT_ALARM_SETTINGS: return "정확한 알람 설정 열기";
            case REQUEST_MICROPHONE_PERMISSION: return "마이크 권한 요청";
            case OPEN_BATTERY_SETTINGS: return "배터리 설정 열기";
            case REQUEST_WIDGET_PIN: return "위젯 추가";
            case RETRY_DATABASE_CHECK: return "다시 점검";
            default: return "실행";
        }
    }
    private String icon(DiagnosticItem.Status status) {
        switch (status) { case NORMAL: return "✓"; case ACTION_REQUIRED: return "!"; case UNSUPPORTED: return "−"; case ERROR: return "×"; default: return "…"; }
    }
    private int statusColor(DiagnosticItem.Status status) {
        if (status == DiagnosticItem.Status.NORMAL) return Color.rgb(20,125,72);
        if (status == DiagnosticItem.Status.ERROR) return Color.rgb(190,52,52);
        if (status == DiagnosticItem.Status.ACTION_REQUIRED) return Color.rgb(174,101,18);
        return Color.rgb(84,96,112);
    }
    private TextView text(String value, int size, int color) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v; }
    private Button button(String value) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setGravity(Gravity.CENTER); return b; }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams buttonParams() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(50)); p.setMargins(0,0,0,dp(8)); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
