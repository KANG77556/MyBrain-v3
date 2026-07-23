package kr.co.mybrain.ai;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 기존 일정·할 일·메모 기능에 백업과 AI 공급자 설정을 통합한 실제 메인 화면입니다.
 * 로컬 모드는 MainActivity의 기존 분석기를 그대로 사용하고, GPT·Gemini 모드만 클라우드 화면으로 연결합니다.
 */
public class IntegratedMainActivity extends MainActivity {
    private static final int COLOR_PRIMARY = Color.rgb(35, 92, 190);
    private static final int REQUEST_AI_SETTINGS = 1501;
    private static final int REQUEST_CLOUD_ANALYSIS = 1502;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureIntegratedScreen();
    }

    /** 기존 화면을 유지하면서 버전 문구, 분석 버튼, 관리 버튼만 안전하게 보강합니다. */
    private void configureIntegratedScreen() {
        LinearLayout mainRoot = findMainRoot();
        if (mainRoot == null) return;

        if (mainRoot.getChildCount() > 1 && mainRoot.getChildAt(1) instanceof TextView) {
            TextView versionText = (TextView) mainRoot.getChildAt(1);
            versionText.setText("v1.6.0 · 로컬·GPT·Gemini 선택");
        }

        AiSettings settings = AiSettings.load(this);
        configureAnalyzeButton(mainRoot, settings);
        addManagementButtons(mainRoot, settings);
    }

    /** 로컬 모드에서는 원래 클릭 동작을 유지하고 클라우드 모드에서만 연결 대상을 교체합니다. */
    private void configureAnalyzeButton(LinearLayout mainRoot, AiSettings settings) {
        if (mainRoot.getChildCount() <= 3 || !(mainRoot.getChildAt(3) instanceof Button)) return;
        Button addButton = (Button) mainRoot.getChildAt(3);

        if (AiSettings.PROVIDER_LOCAL.equals(settings.provider)) {
            addButton.setText("＋ 로컬 분석 및 추가");
            // MainActivity가 등록한 기존 로컬 분석 클릭 동작을 그대로 사용합니다.
            return;
        }

        addButton.setText("＋ " + settings.providerLabel() + " AI 분석 및 추가");
        addButton.setOnClickListener(v -> startActivityForResult(
                new Intent(this, AiInputActivity.class), REQUEST_CLOUD_ANALYSIS));
    }

    /** 백업과 AI 설정을 한 줄에 배치하여 홈 화면 아이콘을 늘리지 않습니다. */
    private void addManagementButtons(LinearLayout mainRoot, AiSettings settings) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button backupButton = outlinedButton("백업·복원");
        backupButton.setOnClickListener(v -> startActivity(new Intent(this, BackupActivity.class)));
        row.addView(backupButton, rowButtonParams());

        Button settingsButton = outlinedButton("AI 설정 · " + settings.providerLabel());
        settingsButton.setOnClickListener(v -> startActivityForResult(
                new Intent(this, AiSettingsActivity.class), REQUEST_AI_SETTINGS));
        row.addView(settingsButton, rowButtonParams());

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        rowParams.setMargins(0, 0, 0, dp(8));

        // 제목·요약·분석 버튼 다음, 검색창 바로 앞에 배치합니다.
        int insertIndex = Math.min(4, mainRoot.getChildCount());
        mainRoot.addView(row, insertIndex, rowParams);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == REQUEST_AI_SETTINGS || requestCode == REQUEST_CLOUD_ANALYSIS)
                && resultCode == RESULT_OK) {
            // 설정 변경 또는 새 항목 저장 후 메인 화면과 목록을 최신 상태로 다시 구성합니다.
            recreate();
        }
    }

    private LinearLayout findMainRoot() {
        View contentView = findViewById(android.R.id.content);
        if (!(contentView instanceof ViewGroup)) return null;
        ViewGroup contentRoot = (ViewGroup) contentView;
        if (contentRoot.getChildCount() == 0) return null;
        View mainRootView = contentRoot.getChildAt(0);
        return mainRootView instanceof LinearLayout ? (LinearLayout) mainRootView : null;
    }

    private Button outlinedButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(COLOR_PRIMARY);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setBackground(createButtonBackground());
        return button;
    }

    private LinearLayout.LayoutParams rowButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    /** 관리 버튼의 흰색 배경과 파란 테두리를 만듭니다. */
    private GradientDrawable createButtonBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), COLOR_PRIMARY);
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
