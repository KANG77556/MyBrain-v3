package kr.co.mybrain.v2.settings;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.NumberFormat;
import java.util.Locale;

/** AI 비용 한도와 네트워크 정책을 설정하는 화면입니다. */
public class AiBudgetActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(246, 248, 252);
    private static final int TEXT = Color.rgb(24, 34, 48);
    private static final int SUBTEXT = Color.rgb(91, 106, 128);
    private static final int PRIMARY = Color.rgb(45, 91, 255);
    private static final int BORDER = Color.rgb(218, 224, 234);
    private static final int DANGER = Color.rgb(218, 53, 69);

    private AiSettings aiSettings;
    private AiBudgetSettings budget;
    private Switch wifiOnlySwitch;
    private Switch budgetEnabledSwitch;
    private Switch blockAtLimitSwitch;
    private EditText monthlyLimitInput;
    private EditText warningPercentInput;
    private EditText exchangeRateInput;
    private TextView monthlySummary;
    private TextView priceSummary;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        aiSettings = AiSettings.load(this);
        budget = AiBudgetSettings.load(this);
        setContentView(buildScreen());
        bindValues();
        refreshSummary();
    }

    @Override protected void onResume() {
        super.onResume();
        if (monthlySummary != null) refreshSummary();
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            root.setPadding(dp(18), bars.top + dp(12), dp(18), bars.bottom + dp(30));
            return insets;
        });

        Button back = secondaryButton("←  비용·네트워크 설정");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView title = text("AI 사용 보호", 28, TEXT, true);
        title.setPadding(0, dp(18), 0, dp(4));
        root.addView(title);
        TextView subtitle = text("데이터 사용과 월간 예상 비용을 제어합니다.", 15, SUBTEXT, false);
        subtitle.setPadding(0, 0, 0, dp(12));
        root.addView(subtitle);

        LinearLayout networkCard = card();
        root.addView(networkCard, cardParams());
        networkCard.addView(sectionTitle("1. 네트워크 사용"));
        wifiOnlySwitch = optionSwitch("Wi-Fi에서만 클라우드 AI 사용");
        networkCard.addView(wifiOnlySwitch);
        TextView networkNote = text("모바일 데이터에서는 자동으로 기기 분석 결과를 사용합니다.", 13, SUBTEXT, false);
        networkNote.setPadding(0, dp(6), 0, 0);
        networkCard.addView(networkNote);

        LinearLayout budgetCard = card();
        root.addView(budgetCard, cardParams());
        budgetCard.addView(sectionTitle("2. 월간 예상 비용 한도"));
        budgetEnabledSwitch = optionSwitch("월간 비용 한도 사용");
        budgetCard.addView(budgetEnabledSwitch);
        monthlyLimitInput = numericInput("예: 5000");
        budgetCard.addView(labeledInput("월간 한도(원)", monthlyLimitInput));
        warningPercentInput = numericInput("예: 80");
        budgetCard.addView(labeledInput("경고 기준(%)", warningPercentInput));
        blockAtLimitSwitch = optionSwitch("한도 도달 시 클라우드 AI 차단");
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(-1, -2);
        switchParams.setMargins(0, dp(8), 0, 0);
        budgetCard.addView(blockAtLimitSwitch, switchParams);
        TextView budgetNote = text("한도가 꺼져 있으면 비용을 계산만 하고 AI 호출은 차단하지 않습니다.", 13, SUBTEXT, false);
        budgetNote.setPadding(0, dp(6), 0, 0);
        budgetCard.addView(budgetNote);

        LinearLayout rateCard = card();
        root.addView(rateCard, cardParams());
        rateCard.addView(sectionTitle("3. 원화 환산 기준"));
        exchangeRateInput = numericInput("예: 1400");
        rateCard.addView(labeledInput("1달러당 원화", exchangeRateInput));
        TextView rateNote = text("자동 환율 조회 없이 입력한 환율로만 추정합니다. 실제 카드 청구 금액과 다를 수 있습니다.", 13, SUBTEXT, false);
        rateNote.setPadding(0, dp(6), 0, 0);
        rateCard.addView(rateNote);

        LinearLayout summaryCard = card();
        root.addView(summaryCard, cardParams());
        summaryCard.addView(sectionTitle("4. 이번 달 사용 상태"));
        monthlySummary = text("", 14, TEXT, false);
        monthlySummary.setLineSpacing(dp(3), 1f);
        summaryCard.addView(monthlySummary);
        priceSummary = text("", 13, SUBTEXT, false);
        priceSummary.setLineSpacing(dp(2), 1f);
        priceSummary.setPadding(0, dp(10), 0, 0);
        summaryCard.addView(priceSummary);

        Button save = primaryButton("✓  사용 보호 설정 저장");
        save.setOnClickListener(v -> saveSettings());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(56));
        saveParams.setMargins(0, dp(14), 0, 0);
        root.addView(save, saveParams);

        TextView notice = text("단가표 기준일: " + AiPricingCatalog.PRICE_EFFECTIVE_DATE
                + " · 공급자 가격 변경, 무료 등급, 세금은 반영되지 않을 수 있습니다.", 12, SUBTEXT, false);
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(dp(8), dp(12), dp(8), 0);
        root.addView(notice);
        return scroll;
    }

    private void bindValues() {
        wifiOnlySwitch.setChecked(budget.wifiOnly);
        budgetEnabledSwitch.setChecked(budget.budgetEnabled);
        blockAtLimitSwitch.setChecked(budget.blockAtLimit);
        monthlyLimitInput.setText(String.valueOf(budget.monthlyLimitWon));
        warningPercentInput.setText(String.valueOf(budget.warningPercent));
        exchangeRateInput.setText(String.valueOf(budget.wonPerUsd));
    }

    private void saveSettings() {
        try {
            budget.wifiOnly = wifiOnlySwitch.isChecked();
            budget.budgetEnabled = budgetEnabledSwitch.isChecked();
            budget.blockAtLimit = blockAtLimitSwitch.isChecked();
            budget.monthlyLimitWon = Long.parseLong(monthlyLimitInput.getText().toString().trim());
            budget.warningPercent = Integer.parseInt(warningPercentInput.getText().toString().trim());
            budget.wonPerUsd = Integer.parseInt(exchangeRateInput.getText().toString().trim());
            budget.save(this);
            budget = AiBudgetSettings.load(this);
            bindValues();
            refreshSummary();
            Toast.makeText(this, "AI 사용 보호 설정을 저장했습니다.", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "숫자 입력값을 확인하세요.", Toast.LENGTH_LONG).show();
        }
    }

    private void refreshSummary() {
        aiSettings = AiSettings.load(this);
        budget = AiBudgetSettings.load(this);
        AiUsageStore.Summary summary = AiUsageStore.load(this, aiSettings.provider);
        long spent = summary.monthlyEstimatedCostWon;
        String progress;
        if (budget.budgetEnabled) {
            int percent = budget.monthlyLimitWon <= 0L ? 0
                    : (int) Math.min(999L, Math.round(spent * 100.0 / budget.monthlyLimitWon));
            progress = formatWon(spent) + " / " + formatWon(budget.monthlyLimitWon) + " · " + percent + "%";
        } else {
            progress = formatWon(spent) + " · 한도 사용 안 함";
        }
        monthlySummary.setText(aiSettings.providerLabel() + " · " + summary.monthKey.substring(0, 4)
                + "년 " + Integer.parseInt(summary.monthKey.substring(4)) + "월"
                + "\n예상 비용: " + progress
                + "\n요청 " + summary.monthlyRequests + "회 · 성공 " + summary.monthlySuccesses + "회"
                + "\n토큰: 입력 " + summary.monthlyInputTokens + " / 출력 " + summary.monthlyOutputTokens
                + " / 전체 " + summary.monthlyTotalTokens
                + (summary.monthlyUnknownPricingRequests > 0
                ? "\n단가를 알 수 없는 요청 " + summary.monthlyUnknownPricingRequests + "회는 비용에서 제외" : ""));
        monthlySummary.setTextColor(budget.budgetEnabled && spent >= budget.monthlyLimitWon ? DANGER : TEXT);

        AiPricingCatalog.Price price = AiPricingCatalog.resolve(aiSettings.provider, aiSettings.selectedModel());
        if (!price.known) {
            priceSummary.setText("선택 모델: " + aiSettings.selectedModel()
                    + "\n등록된 단가가 없어 비용은 계산되지 않습니다.");
            return;
        }
        long inputWon = Math.round(price.inputUsdPerMillion * budget.wonPerUsd);
        long outputWon = Math.round(price.outputUsdPerMillion * budget.wonPerUsd);
        priceSummary.setText("선택 모델: " + aiSettings.selectedModel()
                + "\n1백만 입력 토큰 약 " + formatWon(inputWon)
                + " · 출력 토큰 약 " + formatWon(outputWon)
                + "\n환산 기준: 1달러 = " + NumberFormat.getIntegerInstance(Locale.KOREA).format(budget.wonPerUsd) + "원");
    }

    private LinearLayout labeledInput(String label, EditText input) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(label, 13, SUBTEXT, false);
        title.setPadding(0, dp(10), 0, dp(5));
        wrap.addView(title);
        wrap.addView(input, new LinearLayout.LayoutParams(-1, dp(52)));
        return wrap;
    }

    private Switch optionSwitch(String label) {
        Switch value = new Switch(this);
        value.setText(label);
        value.setTextColor(TEXT);
        value.setTextSize(15);
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setPadding(0, dp(4), 0, dp(4));
        return value;
    }

    private EditText numericInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setHintTextColor(Color.rgb(125, 136, 153));
        input.setTextSize(16);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(rounded(Color.WHITE, 12, BORDER));
        return input;
    }

    private String formatWon(long value) {
        return NumberFormat.getIntegerInstance(Locale.KOREA).format(Math.max(0L, value)) + "원";
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(14), dp(14), dp(14), dp(14));
        view.setBackground(rounded(Color.WHITE, 18, BORDER));
        return view;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(12), 0, 0);
        return params;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 17, TEXT, true);
        view.setPadding(0, 0, 0, dp(10));
        return view;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setTypeface(null, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(PRIMARY, 14, 0));
        button.setStateListAnimator(null);
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(15);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(Color.WHITE, 14, BORDER));
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
