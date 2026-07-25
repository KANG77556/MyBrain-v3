package kr.co.mybrain.ai;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * MyBrain AI 1.8.7 기본 화면입니다.
 * 기존 1.8.6 UI 위에 홈 빠른 실행과 통합 입력 메뉴를 연결합니다.
 */
public class WorkspaceActivityV5 extends WorkspaceActivityV4 {
    private static final String HOME_QUICK_TAG = "mybrain_home_quick_actions";
    private View rootView;
    private boolean patchScheduled;
    private int lastSignature = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootView = findViewById(android.R.id.content);
        installWatcher();
        schedulePatch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        schedulePatch();
    }

    private void installWatcher() {
        if (rootView == null) return;
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        int signature = signature(rootView);
                        if (signature != lastSignature) {
                            lastSignature = signature;
                            schedulePatch();
                        }
                    }
                });
    }

    private void schedulePatch() {
        if (rootView == null || patchScheduled) return;
        patchScheduled = true;
        rootView.postDelayed(() -> {
            patchScheduled = false;
            applyQuickInputPatch();
        }, 260L);
    }

    private void applyQuickInputPatch() {
        List<View> views = new ArrayList<>();
        collect(rootView, views);
        patchFloatingButton(views);
        if (isHomeScreen(views)) injectHomeQuickActions(views);
    }

    /** 모든 화면의 플러스 버튼을 네 가지 입력 수단을 고르는 통합 화면으로 연결합니다. */
    private void patchFloatingButton(List<View> views) {
        for (View view : views) {
            if (!(view instanceof Button)) continue;
            Button button = (Button) view;
            if (!"＋".equals(textOf(button))) continue;
            button.setOnClickListener(v -> openQuickInput(null));
            button.setOnLongClickListener(v -> {
                startActivity(new Intent(this, WorkItemManagerActivity.class));
                return true;
            });
            button.setContentDescription("새 기록 방법 선택, 길게 누르면 여러 항목 관리");
        }
    }

    private boolean isHomeScreen(List<View> views) {
        for (View view : views) {
            if (view instanceof TextView && "MyBrain AI".equals(textOf((TextView) view))) return true;
        }
        return false;
    }

    /** 홈 화면 상단에 직접·음성·문서·사진 빠른 실행 카드를 추가합니다. */
    private void injectHomeQuickActions(List<View> views) {
        for (View view : views) {
            if (HOME_QUICK_TAG.equals(view.getTag())) return;
        }

        LinearLayout page = findHomePage(rootView);
        if (page == null) return;

        LinearLayout card = new LinearLayout(this);
        card.setTag(HOME_QUICK_TAG);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(rounded(Color.WHITE, 18, Color.rgb(220, 228, 240), 1));

        TextView title = new TextView(this);
        title.setText("빠른 입력");
        title.setTextSize(16);
        title.setTextColor(Color.rgb(28, 38, 52));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(dp(2), 0, dp(2), dp(8));
        card.addView(title);

        LinearLayout firstRow = quickRow();
        firstRow.addView(quickButton("✍ 직접 입력", QuickInputActivity.MODE_DIRECT), weighted());
        firstRow.addView(quickButton("🎤 음성 메모", QuickInputActivity.MODE_VOICE), weighted());
        card.addView(firstRow);

        LinearLayout secondRow = quickRow();
        secondRow.addView(quickButton("📷 문서 촬영", QuickInputActivity.MODE_CAMERA), weighted());
        secondRow.addView(quickButton("🖼 사진 OCR", QuickInputActivity.MODE_GALLERY), weighted());
        card.addView(secondRow);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(4), 0, dp(14));
        int index = Math.min(1, page.getChildCount());
        page.addView(card, index, params);
    }

    private LinearLayout quickRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private Button quickButton(String label, String mode) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(Color.rgb(34, 96, 214));
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinimumHeight(0);
        button.setMinimumWidth(0);
        button.setBackground(rounded(Color.rgb(238, 244, 255), 14, Color.rgb(207, 221, 247), 1));
        button.setOnClickListener(v -> openQuickInput(mode));
        return button;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private void openQuickInput(String mode) {
        Intent intent = new Intent(this, QuickInputActivity.class);
        if (mode != null) intent.putExtra(QuickInputActivity.EXTRA_MODE, mode);
        startActivity(intent);
    }

    private LinearLayout findHomePage(View view) {
        if (view instanceof ScrollView) {
            ScrollView scroll = (ScrollView) view;
            if (scroll.getChildCount() > 0 && scroll.getChildAt(0) instanceof LinearLayout) {
                LinearLayout candidate = (LinearLayout) scroll.getChildAt(0);
                if (candidate.getChildCount() >= 4) return candidate;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                LinearLayout found = findHomePage(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private void collect(View view, List<View> output) {
        output.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), output);
    }

    private int signature(View view) {
        int value = view.getClass().getName().hashCode();
        if (view instanceof TextView) value = 31 * value + textOf((TextView) view).hashCode();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            value = 31 * value + group.getChildCount();
            for (int i = 0; i < group.getChildCount(); i++) value = 31 * value + signature(group.getChildAt(i));
        }
        return value;
    }

    private String textOf(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
