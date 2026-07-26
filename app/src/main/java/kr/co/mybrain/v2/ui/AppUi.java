package kr.co.mybrain.v2.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** MyBrain 전 화면에서 사용하는 색상·여백·카드·버튼 규칙입니다. */
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
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 18), dp(activity, 12), dp(activity, 18), dp(activity, 30));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int side = isTablet(activity) ? dp(activity, 28) : dp(activity, 18);
            root.setPadding(side, bars.top + dp(activity, 12), side, bars.bottom + dp(activity, 30));
            return insets;
        });
        return new Screen(scroll, root);
    }

    public static TextView title(Context context, String value) {
        TextView view = text(context, value, 28, TEXT, true);
        view.setPadding(0, dp(context, 18), 0, dp(context, 4));
        return view;
    }

    public static TextView subtitle(Context context, String value) {
        TextView view = text(context, value, 15, SUBTEXT, false);
        view.setLineSpacing(dp(context, 2), 1f);
        view.setPadding(0, 0, 0, dp(context, 14));
        return view;
    }

    public static TextView sectionTitle(Context context, String value) {
        TextView view = text(context, value, 17, TEXT, true);
        view.setPadding(0, 0, 0, dp(context, 9));
        return view;
    }

    public static TextView body(Context context, String value) {
        TextView view = text(context, value, 14, SUBTEXT, false);
        view.setLineSpacing(dp(context, 3), 1f);
        return view;
    }

    public static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 16), dp(context, 15), dp(context, 16), dp(context, 15));
        card.setBackground(round(context, SURFACE, 18, BORDER));
        return card;
    }

    public static LinearLayout.LayoutParams cardParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(context, 10), 0, 0);
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
        button.setBackground(round(context, SURFACE, 14, BORDER));
        return button;
    }

    public static Button menuButton(Context context, String title, String description) {
        Button button = baseButton(context, title + "\n" + description);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setTextColor(TEXT);
        button.setTextSize(15);
        button.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        button.setBackground(round(context, SURFACE, 14, BORDER));
        button.setMinHeight(dp(context, 68));
        return button;
    }

    public static Button compactButton(Context context, String value) {
        Button button = baseButton(context, value);
        button.setTextColor(TEXT);
        button.setTextSize(14);
        button.setBackground(round(context, SURFACE, 12, BORDER));
        return button;
    }

    public static TextView text(Context context, String value, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
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

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static boolean isTablet(Context context) {
        return context.getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    private static Button baseButton(Context context, String value) {
        Button button = new Button(context);
        button.setText(value);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        button.setMinHeight(dp(context, 50));
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