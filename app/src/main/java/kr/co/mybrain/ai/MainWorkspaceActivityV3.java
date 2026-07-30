package kr.co.mybrain.ai;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;

/**
 * 최신 메인 화면에 정돈된 설정 메뉴, 밝은/어두운/시스템 테마 선택,
 * 실기기 진단 바로가기를 추가합니다.
 */
public class MainWorkspaceActivityV3 extends MainWorkspaceActivityV2 {
    private boolean applyingTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeController.applyWindow(this);
        super.onCreate(savedInstanceState);
        installThemeObserver();
        installOrganizedSettingsMenu();
        installDiagnosticsShortcut();
        applyThemeNow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        View root = findViewById(android.R.id.content);
        if (root != null) root.post(() -> {
            installOrganizedSettingsMenu();
            applyThemeNow();
        });
    }

    private void installThemeObserver() {
        View root = findViewById(android.R.id.content);
        if (root == null) return;
        root.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override public void onGlobalLayout() {
                        applyThemeNow();
                    }
                });
    }

    private void applyThemeNow() {
        if (applyingTheme) return;
        applyingTheme = true;
        try {
            ThemeController.applyTree(this, findViewById(android.R.id.content));
        } finally {
            applyingTheme = false;
        }
    }

    /** 기존 톱니바퀴 버튼을 기능별로 정돈된 설정 메뉴에 연결합니다. */
    private void installOrganizedSettingsMenu() {
        View target = findByDescription(findViewById(android.R.id.content), "설정 및 관리");
        if (target != null) target.setOnClickListener(v -> showSettingsMenu());
    }

    private void showSettingsMenu() {
        String[] menus = {
                "화면 테마 · " + themeSummary(),
                "AI 설정",
                "백업·복원",
                "여러 항목 관리",
                "기기 진단 및 테스트",
                "이전 화면(복구)"
        };
        new AlertDialog.Builder(this)
                .setTitle("설정")
                .setItems(menus, (dialog, which) -> {
                    if (which == 0) showThemeDialog();
                    else if (which == 1) startActivity(new Intent(this, AiSettingsActivity.class));
                    else if (which == 2) startActivity(new Intent(this, BackupActivity.class));
                    else if (which == 3) startActivity(new Intent(this, WorkItemManagerActivity.class));
                    else if (which == 4) startActivity(new Intent(this, DiagnosticsActivity.class));
                    else startActivity(new Intent(this, WorkspaceActivityV9.class));
                })
                .show();
    }

    private void showThemeDialog() {
        String[] labels = {"시스템 설정 따르기", "밝은 모드", "어두운 모드"};
        int selected = ThemeController.selectedIndex(this);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("화면 테마")
                .setSingleChoiceItems(labels, selected, null)
                .setNegativeButton("취소", null)
                .setPositiveButton("적용", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    int checked = dialog.getListView().getCheckedItemPosition();
                    ThemeController.setMode(this, ThemeController.modeForIndex(checked));
                    dialog.dismiss();
                    recreate();
                }));
        dialog.show();
    }

    private String themeSummary() {
        String mode = ThemeController.getMode(this);
        if (ThemeController.MODE_LIGHT.equals(mode)) return "밝게";
        if (ThemeController.MODE_DARK.equals(mode)) return "어둡게";
        return "시스템";
    }

    private void installDiagnosticsShortcut() {
        ViewGroup content = findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) return;
        if (findByDescription(content, "기기 진단 및 테스트 열기") != null) return;

        Button button = new Button(this);
        button.setText("진단");
        button.setTextSize(12);
        button.setTextColor(Color.rgb(34, 96, 214));
        button.setAllCaps(false);
        button.setContentDescription("기기 진단 및 테스트 열기");
        button.setOnClickListener(v -> startActivity(new Intent(this, DiagnosticsActivity.class)));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(64), dp(42));
        params.gravity = Gravity.TOP | Gravity.END;
        params.setMargins(0, dp(70), dp(10), 0);
        ((FrameLayout) content).addView(button, params);
    }

    private View findByDescription(View view, String description) {
        if (view == null) return null;
        CharSequence value = view.getContentDescription();
        if (value != null && description.contentEquals(value)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findByDescription(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
