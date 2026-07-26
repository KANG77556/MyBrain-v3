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

import kr.co.mybrain.v2.settings.AiBudgetActivity;
import kr.co.mybrain.v2.settings.AiBudgetSettings;
import kr.co.mybrain.v2.settings.AiModelComparisonActivity;
import kr.co.mybrain.v2.settings.AiSettings;
import kr.co.mybrain.v2.settings.AiSettingsActivity;
import kr.co.mybrain.v2.settings.AiUsageStore;

/** 스마트폰 한 열·태블릿 두 열 UI와 AI 사용 현황 카드를 구성합니다. */
public class AdaptiveMainActivity extends MainActivity {
    private static final int TABLET_MIN_WIDTH_DP = 700;
    private static final int TEXT = Color.rgb(24, 34, 48);
    private static final int SUBTEXT = Color.rgb(91, 106, 128);
    private static final int BORDER = Color.rgb(218, 224, 234);
    private static final int DANGER = Color.rgb(218, 53, 69);
    private static final int WARNING = Color.rgb(185, 108, 0);
    private static final int SUCCESS = Color.rgb(29, 128, 75);

    private TextView homeUsageText;
    private ProgressBar homeBudgetProgress;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().post(() -> {
            applyAdaptiveLayout();
            addAiSettingsEntry();
            addBudgetSettingsEntry();
            addHomeUsageCard();
            refreshHomeUsage();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        getWindow().getDecorView().post(this::refreshHomeUsage);
    }

    private void applyAdaptiveLayout() {
        int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        int smallestWidth = getResources().getConfiguration().smallestScreenWidthDp;
        boolean tablet = smallestWidth >= 600 || widthDp >= TABLET_MIN_WIDTH_DP;
        LinearLayout root = findRoot();
        if (root == null) return;
        if (!tablet) {
            root.setPadding(dp(18), root.getPaddingTop(), dp(18), root.getPaddingBottom());
            return;
        }
        applyTabletLayout(root, widthDp);
    }

    private void addAiSettingsEntry() {
        LinearLayout root = findRoot();
        if (root == null || root.findViewWithTag("alpha22-ai-settings") != null) return;
        Button button = navigationButton("⚙  AI 설정");
        button.setTag("alpha22-ai-settings");
        button.setOnClickListener(v -> startActivity(new Intent(this, AiSettingsActivity.class)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.setMargins(0, dp(8), 0, dp(2));
        root.addView(button, Math.min(2, root.getChildCount()), params);
    }

    private void addBudgetSettingsEntry() {
        LinearLayout root = findRoot();
        if (root == null || root.findViewWithTag("alpha25-budget-settings") != null) return;
        Button button = navigationButton("₩  AI 비용·데이터 설정");
        button.setTag("alpha25-budget-settings");
        button.setOnClickListener(v -> startActivity(new Intent(this, AiBudgetActivity.class)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.setMargins(0, dp(4), 0, dp(2));
        root.addView(button, Math.min(3, root.getChildCount()), params);
    }

    private void addHomeUsageCard() {
        LinearLayout root = findRoot();
        if (root == null || root.findViewWithTag("alpha26-home-usage") != null) return;
        LinearLayout card = new LinearLayout(this);
        card.setTag("alpha26-home-usage");
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(Color.WHITE, 18, BORDER));

        TextView title = text("이번 달 AI 사용", 17, TEXT, true);
        card.addView(title);
        homeUsageText = text("사용 기록을 불러오는 중입니다.", 14, SUBTEXT, false);
        homeUsageText.setLineSpacing(dp(3), 1f);
        homeUsageText.setPadding(0, dp(8), 0, 0);
        card.addView(homeUsageText);

        homeBudgetProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        homeBudgetProgress.setMax(100);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(12));
        progressParams.setMargins(0, dp(10), 0, 0);
        card.addView(homeBudgetProgress, progressParams);

        Button compare = navigationButton("모델별 비용·속도 비교");
        compare.setOnClickListener(v -> startActivity(new Intent(this, AiModelComparisonActivity.class)));
        LinearLayout.LayoutParams compareParams = new LinearLayout.LayoutParams(-1, dp(46));
        compareParams.setMargins(0, dp(10), 0, 0);
        card.addView(compare, compareParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(10), 0, dp(4));
        root.addView(card, Math.min(4, root.getChildCount()), params);
    }

    private void refreshHomeUsage() {
        if (homeUsageText == null || homeBudgetProgress == null) return;
        AiUsageStore.CombinedSummary summary = AiUsageStore.loadCombined(this);
        AiBudgetSettings budget = AiBudgetSettings.load(this);
        AiSettings settings = AiSettings.load(this);
        long spent = summary.monthlyEstimatedCostWon;
        String cost = budget.budgetEnabled
                ? formatWon(spent) + " / " + formatWon(budget.monthlyLimitWon)
                        + " · " + budget.progressPercent(spent) + "%"
                : formatWon(spent) + " · 한도 사용 안 함";
        homeUsageText.setText("요청 " + summary.monthlyRequests + "회 · 성공 "
                + summary.monthlySuccesses + "회 · 전체 " + summary.monthlyTotalTokens + "토큰"
                + "\n예상 비용 " + cost
                + "\n현재 모델 " + settings.providerLabel() + " · " + settings.selectedModel()
                + (summary.monthlyUnknownPricingRequests > 0
                ? "\n단가 미등록 요청 " + summary.monthlyUnknownPricingRequests + "회 비용 제외" : ""));

        if (!budget.budgetEnabled) {
            homeBudgetProgress.setVisibility(View.GONE);
            homeUsageText.setTextColor(SUBTEXT);
        } else {
            homeBudgetProgress.setVisibility(View.VISIBLE);
            homeBudgetProgress.setProgress(Math.min(100, budget.progressPercent(spent)));
            if (spent >= budget.monthlyLimitWon) homeUsageText.setTextColor(DANGER);
            else if (spent >= budget.warningAmountWon()) homeUsageText.setTextColor(WARNING);
            else homeUsageText.setTextColor(SUCCESS);
        }
    }

    private void applyTabletLayout(LinearLayout root, int widthDp) {
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
        columnParams.setMargins(0, dp(18), 0, 0);
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
            for (int i = 0; i < group.getChildCount(); i++) {
                setMaximumTextWidth(group.getChildAt(i), maxWidth);
            }
        }
    }

    private Button navigationButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
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

    private GradientDrawable rounded(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private String formatWon(long value) {
        return NumberFormat.getIntegerInstance(Locale.KOREA).format(Math.max(0L, value)) + "원";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
