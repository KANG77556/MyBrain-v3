package kr.co.mybrain.v2.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** MyBrain 전 화면에서 사용하는 색상·여백·카드·버튼·접근성 규칙입니다. */
public final class AppUi {
    public static final int BG = Color.rgb(247, 249, 252);
    public static final int SURFACE = Color.WHITE;
    public static final int TEXT = Color.rgb(31, 41, 55);
    public static final int SUBTEXT = Color.rgb(100, 116, 139);
    public static final int PRIMARY = Color.rgb(53, 89, 199);
    public static final int PRIMARY_SOFT = Color.rgb(238, 242, 255);
    public static final int BORDER = Color.rgb(226, 232, 240);
    public static final int SUCCESS = Color.rgb(22, 128, 78);
    public static final int WARNING = Color.rgb(180, 83, 9);
    public static final int DANGER = Color.rgb(220, 38, 38);

    private AppUi() {}

    public static Screen screen(Activity activity) {
        UiPreferences preferences = UiPreferences.load(activity);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(preferences.highContrast ? Color.WHITE : BG);
        scroll.setSmoothScrollingEnabled(!preferences.reduceMotion);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int side = initialSidePadding(activity, preferences);
        int bottom = preferences.oneHandMode ? 112 : 30;
        root.setPadding(dp(activity, side), dp(activity, 12), dp(activity, side), dp(activity, bottom));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int resolvedSide = initialSidePadding(activity, preferences);
            int resolvedBottom = preferences.oneHandMode ? 112 : 30;
            root.setPadding(dp(activity, resolvedSide), bars.top + dp(activity, 12),
                    dp(activity, resolvedSide), bars.bottom + dp(activity, resolvedBottom));
            return insets;
        });
        return new Screen(scroll, root);
    }

    public static TextView title(Context context, String value) {
        TextView view = text(context, value, 28, TEXT, true);
        view.setPadding(0, dp(context, 18), 0, dp(context, 4));
        view.setContentDescription("화면 제목, " + value);
        ViewCompat.setAccessibilityHeading(view, true);
        return view;
    }

    public static TextView subtitle(Context context, String value) {
        TextView view = text(context, value, 15, UiPreferences.load(context).highContrast
                ? Color.rgb(55, 65, 81) : SUBTEXT, false);
        view.setLineSpacing(dp(context, 2), 1.05f);
        view.setPadding(0, 0, 0, dp(context, 14));
        return view;
    }

    public static TextView sectionTitle(Context context, String value) {
        TextView view = text(context, value, 17, TEXT, true);
        view.setPadding(0, 0, 0, dp(context, 9));
        ViewCompat.setAccessibilityHeading(view, true);
        return view;
    }

    public static TextView body(Context context, String value) {
        TextView view = text(context, value, 14, UiPreferences.load(context).highContrast
                ? Color.rgb(55, 65, 81) : SUBTEXT, false);
        view.setLineSpacing(dp(context, 3), 1.05f);
        return view;
    }

    public static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 16), dp(context, 15), dp(context, 16), dp(context, 15));
        card.setBackground(round(context, SURFACE, 18,
                UiPreferences.load(context).highContrast ? Color.rgb(148, 163, 184) : BORDER));
        return card;
    }

    public static LinearLayout emptyState(Context context, String title, String description) {
        LinearLayout empty = card(context);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        empty.setPadding(dp(context, 20), dp(context, 28), dp(context, 20), dp(context, 28));
        TextView titleView = text(context, title, 17, TEXT, true);
        titleView.setGravity(Gravity.CENTER);
        ViewCompat.setAccessibilityHeading(titleView, true);
        empty.addView(titleView);
        TextView descriptionView = body(context, description);
        descriptionView.setGravity(Gravity.CENTER);
        descriptionView.setPadding(0, dp(context, 7), 0, 0);
        empty.addView(descriptionView);
        return empty;
    }

    public static LinearLayout.LayoutParams cardParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(context, 10), 0, 0);
        return params;
    }

    public static LinearLayout.LayoutParams actionParams(Context context) {
        UiPreferences preferences = UiPreferences.load(context);
        int height = preferences.largeTouchTargets || preferences.oneHandMode ? 58 : 52;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(context, height));
        params.setMargins(0, dp(context, 12), 0, 0);
        return params;
    }

    public static Button primaryButton(Context context, String value) {
        Button button = baseButton(context, value);
        button.setTextColor(Color.WHITE);
        button.setTypeface(null, Typeface.BOLD);
        button.setBackground(round(context, PRIMARY, 14, 0));
        return button;
    }

    public static Button secondaryButton(Context context, String value) {
        Button button = baseButton(context, value);
        button.setTextColor(TEXT);
        button.setBackground(round(context, SURFACE, 14,
                UiPreferences.load(context).highContrast ? Color.rgb(100, 116, 139) : BORDER));
        return button;
    }

    public static Button menuButton(Context context, String title, String description) {
        Button button = baseButton(context, title + "\n" + description);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setTextColor(TEXT);
        button.setTextSize(15);
        button.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        button.setBackground(round(context, SURFACE, 14,
                UiPreferences.load(context).highContrast ? Color.rgb(100, 116, 139) : BORDER));
        button.setMinHeight(dp(context, 72));
        button.setContentDescription(title + ", " + description);
        return button;
    }

    public static Button compactButton(Context context, String value) {
        Button button = baseButton(context, value);
        button.setTextColor(TEXT);
        button.setTextSize(14);
        button.setBackground(round(context, SURFACE, 12,
                UiPreferences.load(context).highContrast ? Color.rgb(100, 116, 139) : BORDER));
        return button;
    }

    public static EditText input(Context context, String hint) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setTextSize(16);
        input.setTextColor(TEXT);
        input.setHintTextColor(UiPreferences.load(context).highContrast
                ? Color.rgb(75, 85, 99) : SUBTEXT);
        input.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        input.setBackground(round(context, SURFACE, 14,
                UiPreferences.load(context).highContrast ? Color.rgb(100, 116, 139) : BORDER));
        input.setMinHeight(dp(context, minimumTouchDp(context)));
        input.setContentDescription(hint);
        return input;
    }

    public static TextView text(Context context, String value, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    public static GradientDrawable round(Context context, int fill, int radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (stroke != 0) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    public static void addMenu(LinearLayout root, Context context, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(context, 8), 0, 0);
        root.addView(button, params);
    }

    public static void setEnabled(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : .45f);
        button.setContentDescription(button.getText() + (enabled ? "" : ", 현재 사용할 수 없음"));
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static boolean isTablet(Context context) {
        return context.getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    public static int minimumTouchDp(Context context) {
        UiPreferences preferences = UiPreferences.load(context);
        return preferences.largeTouchTargets || preferences.oneHandMode ? 56 : 48;
    }

    private static int initialSidePadding(Context context, UiPreferences preferences) {
        if (preferences.oneHandMode && !isTablet(context)) return 16;
        return isTablet(context) ? 28 : 18;
    }

    private static Button baseButton(Context context, String value) {
        Button button = new Button(context);
        button.setText(value);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        button.setMinHeight(dp(context, minimumTouchDp(context)));
        button.setContentDescription(value.replace('\n', ' '));
        return button;
    }

    public static final class Screen {
        public final ScrollView scroll;
        public final LinearLayout root;

        Screen(ScrollView scroll, LinearLayout root) {
            this.scroll = scroll;
            this.root = root;
        }
    }
}