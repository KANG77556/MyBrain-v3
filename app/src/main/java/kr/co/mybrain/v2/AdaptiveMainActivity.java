package kr.co.mybrain.v2;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import kr.co.mybrain.v2.settings.AiBudgetActivity;
import kr.co.mybrain.v2.settings.AiSettingsActivity;

/**
 * 스마트폰에서는 기존 한 열 UI를 유지하고, 태블릿에서는 입력과 분석 결과를
 * 두 열로 배치하는 반응형 시작 화면입니다.
 */
public class AdaptiveMainActivity extends MainActivity {

    private static final int TABLET_MIN_WIDTH_DP = 700;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().post(() -> {
            applyAdaptiveLayout();
            addAiSettingsEntry();
            addBudgetSettingsEntry();
        });
    }

    private void applyAdaptiveLayout() {
        int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        int smallestWidth = getResources().getConfiguration().smallestScreenWidthDp;
        boolean tablet = smallestWidth >= 600 || widthDp >= TABLET_MIN_WIDTH_DP;

        View content = findViewById(android.R.id.content);
        ScrollView scroll = findScrollView(content);
        if (scroll == null || scroll.getChildCount() == 0) return;
        View child = scroll.getChildAt(0);
        if (!(child instanceof LinearLayout)) return;

        LinearLayout root = (LinearLayout) child;
        if (!tablet) {
            applyPhoneLayout(root);
            return;
        }
        applyTabletLayout(root, widthDp);
    }

    private void addAiSettingsEntry() {
        View content = findViewById(android.R.id.content);
        ScrollView scroll = findScrollView(content);
        if (scroll == null || scroll.getChildCount() == 0) return;
        View child = scroll.getChildAt(0);
        if (!(child instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) child;
        if (root.findViewWithTag("alpha22-ai-settings") != null) return;

        Button settingsButton = new Button(this);
        settingsButton.setTag("alpha22-ai-settings");
        settingsButton.setText("⚙  AI 설정");
        settingsButton.setTextSize(15);
        settingsButton.setAllCaps(false);
        settingsButton.setGravity(Gravity.CENTER);
        settingsButton.setOnClickListener(v ->
                startActivity(new Intent(this, AiSettingsActivity.class)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(8), 0, dp(2));
        int position = Math.min(2, root.getChildCount());
        root.addView(settingsButton, position, params);
    }

    private void addBudgetSettingsEntry() {
        View content = findViewById(android.R.id.content);
        ScrollView scroll = findScrollView(content);
        if (scroll == null || scroll.getChildCount() == 0) return;
        View child = scroll.getChildAt(0);
        if (!(child instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) child;
        if (root.findViewWithTag("alpha25-budget-settings") != null) return;

        Button budgetButton = new Button(this);
        budgetButton.setTag("alpha25-budget-settings");
        budgetButton.setText("₩  AI 비용·데이터 설정");
        budgetButton.setTextSize(15);
        budgetButton.setAllCaps(false);
        budgetButton.setGravity(Gravity.CENTER);
        budgetButton.setOnClickListener(v ->
                startActivity(new Intent(this, AiBudgetActivity.class)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(4), 0, dp(2));
        int position = Math.min(3, root.getChildCount());
        root.addView(budgetButton, position, params);
    }

    private void applyPhoneLayout(LinearLayout root) {
        int side = dp(18);
        root.setPadding(side, root.getPaddingTop(), side, root.getPaddingBottom());
        setMaximumTextWidth(root, Integer.MAX_VALUE);
    }

    private void applyTabletLayout(LinearLayout root, int widthDp) {
        // MainActivity의 홈 구성: 제목, 설명, 내비게이션, 입력 카드, 결과 카드,
        // 저장, 빠른 추가, 상태 문구 순서입니다.
        if (root.getChildCount() < 8 || root.getTag() != null) return;
        root.setTag("adaptive-tablet-applied");

        View inputCard = root.getChildAt(3);
        View resultCard = root.getChildAt(4);
        View saveButton = root.getChildAt(5);
        View quickAddButton = root.getChildAt(6);
        View statusText = root.getChildAt(7);

        root.removeView(statusText);
        root.removeView(quickAddButton);
        root.removeView(saveButton);
        root.removeView(resultCard);
        root.removeView(inputCard);

        int sidePadding = widthDp >= 1100 ? dp(48) : dp(30);
        root.setPadding(sidePadding, root.getPaddingTop(), sidePadding, root.getPaddingBottom());

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setGravity(Gravity.TOP);

        LinearLayout leftColumn = new LinearLayout(this);
        leftColumn.setOrientation(LinearLayout.VERTICAL);
        leftColumn.addView(inputCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout rightColumn = new LinearLayout(this);
        rightColumn.setOrientation(LinearLayout.VERTICAL);
        rightColumn.addView(resultCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams fullButton = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        fullButton.setMargins(0, dp(12), 0, 0);
        rightColumn.addView(saveButton, fullButton);

        LinearLayout.LayoutParams quickButton = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        quickButton.setMargins(0, dp(10), 0, 0);
        rightColumn.addView(quickAddButton, quickButton);

        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(4), 0, 0);
        rightColumn.addView(statusText, statusParams);

        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.08f);
        leftParams.setMargins(0, 0, dp(10), 0);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.92f);
        rightParams.setMargins(dp(10), 0, 0, 0);
        columns.addView(leftColumn, leftParams);
        columns.addView(rightColumn, rightParams);

        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        columnParams.setMargins(0, dp(18), 0, 0);
        root.addView(columns, 3, columnParams);

        // 태블릿에서는 긴 설명이 과도하게 넓어지지 않도록 읽기 폭을 제한합니다.
        setMaximumTextWidth(root, dp(760));
    }

    private void setMaximumTextWidth(View view, int maxWidth) {
        if (view instanceof TextView && maxWidth != Integer.MAX_VALUE) {
            ((TextView) view).setMaxWidth(maxWidth);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setMaximumTextWidth(group.getChildAt(i), maxWidth);
            }
        }
    }

    private ScrollView findScrollView(View view) {
        if (view instanceof ScrollView) return (ScrollView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            ScrollView found = findScrollView(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
