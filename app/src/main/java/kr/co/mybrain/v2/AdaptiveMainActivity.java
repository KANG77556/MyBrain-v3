package kr.co.mybrain.v2;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.text.NumberFormat;
import java.util.Locale;

import kr.co.mybrain.v2.settings.AiBudgetSettings;
import kr.co.mybrain.v2.settings.AiSettings;
import kr.co.mybrain.v2.settings.AiUsageStore;
import kr.co.mybrain.v2.settings.SettingsHubActivity;
import kr.co.mybrain.v2.ui.AppUi;

/** 스마트폰 한 열·태블릿 두 열 UI와 단순한 홈 탐색을 구성합니다. */
public class AdaptiveMainActivity extends MainActivity {
    private static final int TABLET_MIN_WIDTH_DP = 700;
    private TextView homeUsageText;
    private ProgressBar homeBudgetProgress;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().post(() -> {
            simplifyBaseHome();
            applyAdaptiveLayout();
            addSettingsToTopNavigation();
            addHomeUsageCard();
            refreshHomeUsage();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        getWindow().getDecorView().post(this::refreshHomeUsage);
    }

    private void simplifyBaseHome() {
        LinearLayout root = findRoot();
        if (root == null || root.getChildCount() < 8) return;
        if (root.getChildAt(0) instanceof TextView) ((TextView) root.getChildAt(0)).setText("MyBrain");
        if (root.getChildAt(1) instanceof TextView) {
            ((TextView) root.getChildAt(1)).setText("말하거나 입력하면 일정·할 일·메모로 정리합니다.");
        }
        renameButtons(root);
    }

    private void renameButtons(View view) {
        if (view instanceof Button) {
            Button button = (Button) view;
            String value = String.valueOf(button.getText());
            if (value.contains("일정·오늘")) button.setText("오늘 일정");
            else if (value.contains("저장 목록")) button.setText("저장 목록");
            else if (value.contains("음성으로 입력")) button.setText("말해서 입력");
            else if (value.contains("AI로 정밀 분석")) button.setText("AI 정밀 분석");
            else if (value.contains("결과 확인·수정")) button.setText("결과 확인·수정");
            else if (value.contains("저장하기")) button.setText("저장");
            else if (value.contains("빠른 추가")) button.setText("빠른 추가");
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) renameButtons(group.getChildAt(i));
        }
    }

    private void applyAdaptiveLayout() {
        int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        int smallestWidth = getResources().getConfiguration().smallestScreenWidthDp;
        boolean tablet = smallestWidth >= 600 || widthDp >= TABLET_MIN_WIDTH_DP;
        LinearLayout root = findRoot();
        if (root == null) return;
        int side = tablet ? (widthDp >= 1100 ? dp(48) : dp(30)) : dp(18);
        root.setPadding(side, root.getPaddingTop(), side, root.getPaddingBottom());
        if (tablet) applyTabletLayout(root);
    }

    private void addSettingsToTopNavigation() {
        LinearLayout root = findRoot();
        if (root == null || root.findViewWithTag("alpha29-settings-hub") != null) return;
        View candidate = root.getChildCount() > 2 ? root.getChildAt(2) : null;
        if (!(candidate instanceof LinearLayout)) return;
        LinearLayout nav = (LinearLayout) candidate;
        if (nav.getChildCount() < 2) return;

        for (int i = 0; i < 2; i++) {
            View item = nav.getChildAt(i);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1f);
            if (i == 0) params.setMargins(0, 0, dp(4), 0);
            else params.setMargins(dp(4), 0, dp(4), 0);
            item.setLayoutParams(params);
        }

        Button settings = compactNavigationButton("설정");
        settings.setTag("alpha29-settings-hub");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsHubActivity.class)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1f);
        params.setMargins(dp(4), 0, 0, 0);
        nav.addView(settings, params);
    }

    private void addHomeUsageCard() {
        LinearLayout root = findRoot();
        if (root == null || root.findViewWithTag("alpha29-home-usage") != null) return;
        LinearLayout card = new LinearLayout(this);
        card.setTag("alpha29-home-usage");
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        card.setBackground(AppUi.round(this, AppUi.SURFACE, 16, AppUi.BORDER));
        card.setOnClickListener(v -> startActivity(new Intent(this, SettingsHubActivity.class)));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("이번 달 AI", 16, AppUi.TEXT, true);
        TextView more = text("설정  ›", 14, AppUi.PRIMARY, true);
        more.setGravity(Gravity.END);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(more, new LinearLayout.LayoutParams(-2, -2));
        card.addView(header);

        homeUsageText = text("사용 현황을 불러오는 중입니다.", 14, AppUi.SUBTEXT, false);
        homeUsageText.setLineSpacing(dp(2), 1f);
        homeUsageText.setPadding(0, dp(7), 0, 0);
        card.addView(homeUsageText);

        homeBudgetProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        homeBudgetProgress.setMax(100);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(10));
        progressParams.setMargins(0, dp(9), 0, 0);
        card.addView(homeBudgetProgress, progressParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(10), 0, dp(2));
        root.addView(card, Math.min(3, root.getChildCount()), params);
    }

    private void refreshHomeUsage() {
        if (homeUsageText == null || homeBudgetProgress == null) return;
        AiUsageStore.CombinedSummary summary = AiUsageStore.loadCombined(this);
        AiBudgetSettings budget = AiBudgetSettings.load(this);
        AiSettings settings = AiSettings.load(this);
        long spent = summary.monthlyEstimatedCostWon;
        String cost = budget.budgetEnabled
                ? formatWon(spent) + " / " + formatWon(budget.monthlyLimitWon)
                : formatWon(spent);
        homeUsageText.setText(settings.providerLabel() + " · " + settings.selectedModel()
                + "\n요청 " + summary.monthlyRequests + "회 · " + summary.monthlyTotalTokens
                + "토큰 · 예상 " + cost);

        if (!budget.budgetEnabled) {
            homeBudgetProgress.setVisibility(View.GONE);
            homeUsageText.setTextColor(AppUi.SUBTEXT);
        } else {
            homeBudgetProgress.setVisibility(View.VISIBLE);
            homeBudgetProgress.setProgress(Math.min(100, budget.progressPercent(spent)));
            if (spent >= budget.monthlyLimitWon) homeUsageText.setTextColor(AppUi.DANGER);
            else if (spent >= budget.warningAmountWon()) homeUsageText.setTextColor(AppUi.WARNING);
            else homeUsageText.setTextColor(AppUi.SUCCESS);
        }
    }

    private void applyTabletLayout(LinearLayout root) {
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

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setGravity(Gravity.TOP);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(inputCard, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.addView(resultCard, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(56));
        saveParams.setMargins(0, dp(12), 0, 0);
        right.addView(saveButton, saveParams);
        LinearLayout.LayoutParams quickParams = new LinearLayout.LayoutParams(-1, dp(52));
        quickParams.setMargins(0, dp(10), 0, 0);
        right.addView(quickAddButton, quickParams);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.setMargins(0, dp(4), 0, 0);
        right.addView(statusText, statusParams);

        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, -2, 1.08f);
        leftParams.setMargins(0, 0, dp(10), 0);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -2, .92f);
        rightParams.setMargins(dp(10), 0, 0, 0);
        columns.addView(left, leftParams);
        columns.addView(right, rightParams);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(-1, -2);
        columnParams.setMargins(0, dp(16), 0, 0);
        root.addView(columns, 3, columnParams);
        setMaximumTextWidth(root, dp(760));
    }

    private LinearLayout findRoot() {
        View content = findViewById(android.R.id.content);
        ScrollView scroll = findScrollView(content);
        if (scroll == null || scroll.getChildCount() == 0) return null;
        View child = scroll.getChildAt(0);
        return child instanceof LinearLayout ? (LinearLayout) child : null;
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

    private void setMaximumTextWidth(View view, int maxWidth) {
        if (view instanceof TextView) ((TextView) view).setMaxWidth(maxWidth);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) setMaximumTextWidth(group.getChildAt(i), maxWidth);
        }
    }

    private Button compactNavigationButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(AppUi.TEXT);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(AppUi.round(this, Color.WHITE, 13, AppUi.BORDER));
        button.setStateListAnimator(null);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private String formatWon(long value) {
        return NumberFormat.getIntegerInstance(Locale.KOREA).format(Math.max(0L, value)) + "원";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}