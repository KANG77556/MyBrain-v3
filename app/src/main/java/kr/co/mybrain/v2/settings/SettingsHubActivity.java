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
import kr.co.mybrain.v2.ui.UiPreferences;

/** 자주 쓰는 설정과 고급 관리 기능을 분리한 설정 허브입니다. */
public class SettingsHubActivity extends AppCompatActivity {
    private TextView summaryText;
    private TextView displaySummary;

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

        Button back = AppUi.secondaryButton(this, "←  홈으로");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, AppUi.dp(this, AppUi.minimumTouchDp(this))));

        root.addView(AppUi.title(this, "설정"));
        root.addView(AppUi.subtitle(this, "자주 쓰는 설정부터 순서대로 배치했습니다."));

        LinearLayout summary = AppUi.card(this);
        root.addView(summary, AppUi.cardParams(this));
        summary.addView(AppUi.sectionTitle(this, "현재 사용 상태"));
        summaryText = AppUi.body(this, "설정을 불러오는 중입니다.");
        summary.addView(summaryText);

        LinearLayout displayCard = AppUi.card(this);
        root.addView(displayCard, AppUi.cardParams(this));
        displayCard.addView(AppUi.sectionTitle(this, "화면과 조작"));
        displaySummary = AppUi.body(this, "화면 설정을 불러오는 중입니다.");
        displayCard.addView(displaySummary);
        Button accessibility = AppUi.menuButton(this, "화면·접근성", "글자 크기, 큰 버튼, 고대비, 한 손 조작");
        accessibility.setOnClickListener(v -> startActivity(new Intent(this, AccessibilitySettingsActivity.class)));
        AppUi.addMenu(displayCard, this, accessibility);

        LinearLayout aiCard = AppUi.card(this);
        root.addView(aiCard, AppUi.cardParams(this));
        aiCard.addView(AppUi.sectionTitle(this, "AI 사용 설정"));
        Button connection = AppUi.menuButton(this, "1. AI 연결", "GPT·Gemini 선택, API 키, 사용 모델");
        connection.setOnClickListener(v -> startActivity(new Intent(this, AiSettingsActivity.class)));
        AppUi.addMenu(aiCard, this, connection);

        Button cost = AppUi.menuButton(this, "2. 비용과 데이터", "월간 한도, Wi-Fi 전용, 비용 알림");
        cost.setOnClickListener(v -> startActivity(new Intent(this, AiBudgetActivity.class)));
        AppUi.addMenu(aiCard, this, cost);

        Button compare = AppUi.menuButton(this, "3. 모델 비교", "내 사용 기록 기준 속도·성공률·예상 비용");
        compare.setOnClickListener(v -> startActivity(new Intent(this, AiModelComparisonActivity.class)));
        AppUi.addMenu(aiCard, this, compare);

        LinearLayout dataCard = AppUi.card(this);
        root.addView(dataCard, AppUi.cardParams(this));
        dataCard.addView(AppUi.sectionTitle(this, "데이터 보호"));
        Button backup = AppUi.menuButton(this, "백업·복원·업데이트", "암호화 백업을 만들고 새 APK를 안전하게 설치");
        backup.setOnClickListener(v -> startActivity(new Intent(this, BackupRestoreActivity.class)));
        AppUi.addMenu(dataCard, this, backup);

        LinearLayout advancedCard = AppUi.card(this);
        root.addView(advancedCard, AppUi.cardParams(this));
        advancedCard.addView(AppUi.sectionTitle(this, "고급 관리"));
        TextView advancedNote = AppUi.body(this, "일반 사용 중에는 변경할 필요가 없습니다.");
        advancedCard.addView(advancedNote);
        Button diagnostics = AppUi.menuButton(this, "배포·복구 진단", "앱 서명과 백업 복원 가능 상태 확인");
        diagnostics.setOnClickListener(v -> startActivity(new Intent(this, ReleaseRecoveryDiagnosticsActivity.class)));
        AppUi.addMenu(advancedCard, this, diagnostics);

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
                : formatWon(usage.monthlyEstimatedCostWon) + " · 한도 꺼짐";
        summaryText.setText("AI  " + ai.providerLabel() + " · " + ai.selectedModel()
                + "\n이번 달  요청 " + usage.monthlyRequests + "회 · " + usage.monthlyTotalTokens + "토큰"
                + "\n예상 비용  " + budgetText
                + "\n네트워크  " + (budget.wifiOnly ? "Wi-Fi에서만 AI 사용" : "모바일 데이터 사용 가능"));
        if (displaySummary != null) displaySummary.setText(UiPreferences.load(this).summary());
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