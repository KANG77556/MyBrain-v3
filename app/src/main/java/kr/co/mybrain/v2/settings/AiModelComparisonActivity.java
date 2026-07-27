package kr.co.mybrain.v2.settings;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 공식 단가와 이 기기에서 누적된 실제 응답시간·비용을 모델별로 비교합니다. */
public class AiModelComparisonActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(246, 248, 252);
    private static final int TEXT = Color.rgb(24, 34, 48);
    private static final int SUBTEXT = Color.rgb(91, 106, 128);
    private static final int PRIMARY = Color.rgb(45, 91, 255);
    private static final int BORDER = Color.rgb(218, 224, 234);
    private static final int SUCCESS = Color.rgb(29, 128, 75);

    private static final String[][] CATALOG_MODELS = {
            {AiSettings.PROVIDER_OPENAI, "gpt-5.4-mini"},
            {AiSettings.PROVIDER_OPENAI, "gpt-5.4-nano"},
            {AiSettings.PROVIDER_OPENAI, "gpt-5-mini"},
            {AiSettings.PROVIDER_OPENAI, "gpt-5-nano"},
            {AiSettings.PROVIDER_OPENAI, "gpt-5.1"},
            {AiSettings.PROVIDER_GEMINI, "gemini-3.6-flash"},
            {AiSettings.PROVIDER_GEMINI, "gemini-3.5-flash-lite"},
            {AiSettings.PROVIDER_GEMINI, "gemini-2.5-flash"}
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildScreen());
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

        Button back = secondaryButton("←  모델 비교");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView title = text("AI 모델 비용·속도 비교", 28, TEXT, true);
        title.setPadding(0, dp(18), 0, dp(4));
        root.addView(title);
        TextView subtitle = text(
                "단가는 앱에 포함된 공급자 가격표이며, 속도와 요청당 비용은 이 기기의 실제 사용 기록입니다.",
                14, SUBTEXT, false);
        subtitle.setLineSpacing(dp(2), 1f);
        subtitle.setPadding(0, 0, 0, dp(12));
        root.addView(subtitle);

        AiSettings selected = AiSettings.load(this);
        AiBudgetSettings budget = AiBudgetSettings.load(this);
        List<AiModelUsageStore.ModelSummary> usage = AiModelUsageStore.loadAll(this);
        AiModelUsageStore.ModelSummary fastest = fastest(usage);
        AiModelUsageStore.ModelSummary cheapest = cheapest(usage);

        LinearLayout guide = card();
        root.addView(guide, cardParams());
        guide.addView(sectionTitle("비교 기준"));
        guide.addView(text("현재 선택: " + selected.providerLabel() + " · " + selected.selectedModel()
                + "\n실측 비교는 요청이 1회 이상 기록된 모델만 대상으로 합니다."
                + "\n환산 기준: 1달러 = "
                + NumberFormat.getIntegerInstance(Locale.KOREA).format(budget.wonPerUsd) + "원",
                14, SUBTEXT, false));

        List<ModelRef> models = mergeModels(usage);
        for (ModelRef ref : models) {
            AiModelUsageStore.ModelSummary stat = find(usage, ref.provider, ref.model);
            root.addView(modelCard(ref, stat, selected, fastest, cheapest, budget), cardParams());
        }

        TextView notice = text("속도는 네트워크 상태, 서버 혼잡, 입력 길이와 모델 추론량에 따라 달라집니다. "
                + "실제 청구액은 공급자 콘솔을 기준으로 확인하세요.", 12, SUBTEXT, false);
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(dp(8), dp(14), dp(8), 0);
        root.addView(notice);
        return scroll;
    }

    private LinearLayout modelCard(
            ModelRef ref,
            AiModelUsageStore.ModelSummary stat,
            AiSettings selected,
            AiModelUsageStore.ModelSummary fastest,
            AiModelUsageStore.ModelSummary cheapest,
            AiBudgetSettings budget) {
        LinearLayout card = card();
        boolean current = AiSettings.normalizeProvider(selected.provider).equals(ref.provider)
                && normalize(selected.selectedModel()).equals(normalize(ref.model));
        boolean isFastest = same(stat, fastest);
        boolean isCheapest = same(stat, cheapest);

        String providerLabel = AiSettings.PROVIDER_GEMINI.equals(ref.provider) ? "Gemini" : "GPT";
        TextView name = text(ref.model + (current ? "  ·  현재 선택" : ""), 17, TEXT, true);
        card.addView(name);
        TextView provider = text(providerLabel + recommendation(isFastest, isCheapest), 13,
                isFastest || isCheapest ? SUCCESS : SUBTEXT, false);
        provider.setPadding(0, dp(3), 0, dp(9));
        card.addView(provider);

        AiPricingCatalog.Price price = AiPricingCatalog.resolve(ref.provider, ref.model);
        String priceLine;
        if (price.known) {
            long inputWon = Math.round(price.inputUsdPerMillion * budget.wonPerUsd);
            long outputWon = Math.round(price.outputUsdPerMillion * budget.wonPerUsd);
            priceLine = "공식 단가 환산 · 입력 100만 토큰 " + formatWon(inputWon)
                    + " · 출력 " + formatWon(outputWon);
        } else {
            priceLine = "앱에 등록된 공식 단가 없음 · 비용 계산 제외";
        }

        String actual;
        if (stat == null || stat.requests <= 0L) {
            actual = "\n실측 기록 없음";
        } else {
            actual = "\n실측 " + stat.requests + "회 · 성공률 "
                    + String.format(Locale.KOREA, "%.0f%%", stat.successRate())
                    + " · 평균 " + formatElapsed(stat.averageElapsedMs())
                    + "\n누적 " + stat.totalTokens + "토큰 · 요청당 예상 비용 "
                    + (stat.averageCostWon() < 0L ? "계산 불가" : formatWon(stat.averageCostWon()));
        }
        TextView detail = text(priceLine + actual, 14, SUBTEXT, false);
        detail.setLineSpacing(dp(3), 1f);
        card.addView(detail);
        return card;
    }

    private List<ModelRef> mergeModels(List<AiModelUsageStore.ModelSummary> usage) {
        List<ModelRef> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (String[] item : CATALOG_MODELS) {
            String key = item[0] + "|" + normalize(item[1]);
            keys.add(key);
            result.add(new ModelRef(item[0], item[1]));
        }
        for (AiModelUsageStore.ModelSummary item : usage) {
            String key = item.provider + "|" + normalize(item.model);
            if (keys.add(key)) result.add(new ModelRef(item.provider, item.model));
        }
        return result;
    }

    private AiModelUsageStore.ModelSummary fastest(List<AiModelUsageStore.ModelSummary> values) {
        AiModelUsageStore.ModelSummary best = null;
        for (AiModelUsageStore.ModelSummary item : values) {
            if (item.successes <= 0L || item.averageElapsedMs() <= 0L) continue;
            if (best == null || item.averageElapsedMs() < best.averageElapsedMs()) best = item;
        }
        return best;
    }

    private AiModelUsageStore.ModelSummary cheapest(List<AiModelUsageStore.ModelSummary> values) {
        AiModelUsageStore.ModelSummary best = null;
        for (AiModelUsageStore.ModelSummary item : values) {
            if (item.successes <= 0L || item.averageCostWon() < 0L) continue;
            if (best == null || item.averageCostWon() < best.averageCostWon()) best = item;
        }
        return best;
    }

    private AiModelUsageStore.ModelSummary find(
            List<AiModelUsageStore.ModelSummary> values, String provider, String model) {
        for (AiModelUsageStore.ModelSummary item : values) {
            if (item.provider.equals(provider) && normalize(item.model).equals(normalize(model))) return item;
        }
        return null;
    }

    private boolean same(AiModelUsageStore.ModelSummary a, AiModelUsageStore.ModelSummary b) {
        return a != null && b != null && a.provider.equals(b.provider)
                && normalize(a.model).equals(normalize(b.model));
    }

    private String recommendation(boolean fastest, boolean cheapest) {
        if (fastest && cheapest) return " · 실측 균형 추천";
        if (fastest) return " · 실측 최고속도";
        if (cheapest) return " · 실측 최저비용";
        return "";
    }

    private String normalize(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.US);
        return text.startsWith("models/") ? text.substring(7) : text;
    }

    private String formatWon(long value) {
        return NumberFormat.getIntegerInstance(Locale.KOREA).format(Math.max(0L, value)) + "원";
    }

    private String formatElapsed(long value) {
        if (value < 1000L) return value + "ms";
        return String.format(Locale.KOREA, "%.1f초", value / 1000.0);
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

    private static final class ModelRef {
        final String provider;
        final String model;
        ModelRef(String provider, String model) {
            this.provider = provider;
            this.model = model;
        }
    }
}
