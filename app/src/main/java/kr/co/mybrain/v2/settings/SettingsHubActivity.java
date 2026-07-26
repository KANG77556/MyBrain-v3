package kr.co.mybrain.v2.settings;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.NumberFormat;
import java.util.Locale;

import kr.co.mybrain.v2.transfer.BackupRestoreActivity;
import kr.co.mybrain.v2.transfer.ReleaseRecoveryDiagnosticsActivity;
import kr.co.mybrain.v2.ui.AppUi;

/** 홈에 흩어진 설정 메뉴를 한곳에 모은 단순한 설정 허브입니다. */
public class SettingsHubActivity extends AppCompatActivity {
    private TextView summaryText;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildScreen());
    }

    @Override protected void onResume() {
        super.onResume();
        refreshSummary();
    }

    private View buildScreen() {
        AppUi.Screen screen = AppUi.screen(this);
        LinearLayout root = screen.root;

        Button back = AppUi.secondaryButton(this, "←  설정");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, AppUi.dp(this, 48)));

        root.addView(AppUi.title(this, "설정"));
        root.addView(AppUi.subtitle(this, "AI 연결, 비용, 백업과 앱 상태를 한곳에서 관리합니다."));

        LinearLayout summary = AppUi.card(this);
        root.addView(summary, AppUi.cardParams(this));
        summary.addView(AppUi.sectionTitle(this, "현재 상태"));
        summaryText = AppUi.body(this, "설정을 불러오는 중입니다.");
        summary.addView(summaryText);

        LinearLayout aiCard = AppUi.card(this);
        root.addView(aiCard, AppUi.cardParams(this));
        aiCard.addView(AppUi.sectionTitle(this, "AI"));
        Button connection = AppUi.menuButton(this, "AI 연결", "GPT·Gemini, API 키, 모델 선택");
        connection.setOnClickListener(v -> startActivity(new Intent(this, AiSettingsActivity.class)));
        AppUi.addMenu(aiCard, this, connection);

        Button cost = AppUi.menuButton(this, "비용·데이터", "월간 한도, Wi-Fi 전용, 비용 알림");
        cost.setOnClickListener(v -> startActivity(new Intent(this, AiBudgetActivity.class)));
        AppUi.addMenu(aiCard, this, cost);

        Button compare = AppUi.menuButton(this, "모델 비교", "실제 속도, 성공률, 예상 비용 비교");
        compare.setOnClickListener(v -> startActivity(new Intent(this, AiModelComparisonActivity.class)));
        AppUi.addMenu(aiCard, this, compare);

        LinearLayout dataCard = AppUi.card(this);
        root.addView(dataCard, AppUi.cardParams(this));
        dataCard.addView(AppUi.sectionTitle(this, "데이터와 앱"));
        Button backup = AppUi.menuButton(this, "백업·복원·업데이트", "암호화 백업, 복원, APK 업데이트");
        backup.setOnClickListener(v -> startActivity(new Intent(this, BackupRestoreActivity.class)));
        AppUi.addMenu(dataCard, this, backup);

        Button diagnostics = AppUi.menuButton(this, "배포·복구 진단", "앱 서명, 백업 리허설, 업데이트 준비 상태");
        diagnostics.setOnClickListener(v -> startActivity(new Intent(this, ReleaseRecoveryDiagnosticsActivity.class)));
        AppUi.addMenu(dataCard, this, diagnostics);

        TextView version = AppUi.body(this, "MyBrain AI " + currentVersion());
        version.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        version.setPadding(0, AppUi.dp(this, 18), 0, 0);
        root.addView(version);
        return screen.scroll;
    }

    private void refreshSummary() {
        if (summaryText == null) return;
        AiSettings ai = AiSettings.load(this);
        AiBudgetSettings budget = AiBudgetSettings.load(this);
        AiUsageStore.CombinedSummary usage = AiUsageStore.loadCombined(this);
        String budgetText = budget.budgetEnabled
                ? formatWon(usage.monthlyEstimatedCostWon) + " / " + formatWon(budget.monthlyLimitWon)
                : formatWon(usage.monthlyEstimatedCostWon) + " · 한도 사용 안 함";
        summaryText.setText("AI  " + ai.providerLabel() + " · " + ai.selectedModel()
                + "\n이번 달  요청 " + usage.monthlyRequests + "회 · " + usage.monthlyTotalTokens + "토큰"
                + "\n예상 비용  " + budgetText
                + "\n데이터 사용  " + (budget.wifiOnly ? "Wi-Fi에서만 AI 사용" : "모든 네트워크 허용"));
    }

    private String currentVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode() : info.versionCode;
            return (info.versionName == null ? "" : info.versionName) + " (" + code + ")";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String formatWon(long value) {
        return NumberFormat.getIntegerInstance(Locale.KOREA).format(Math.max(0L, value)) + "원";
    }
}