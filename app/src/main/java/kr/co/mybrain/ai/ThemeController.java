package kr.co.mybrain.ai;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * 앱 전체에서 사용하는 밝은 모드·어두운 모드·시스템 모드 설정입니다.
 * 기존 화면 코드를 크게 바꾸지 않고도 현재 뷰 계층에 선택한 색상을 적용합니다.
 */
final class ThemeController {
    static final String PREFS = "mybrain_ui_settings";
    static final String KEY_THEME_MODE = "theme_mode";
    static final String MODE_SYSTEM = "SYSTEM";
    static final String MODE_LIGHT = "LIGHT";
    static final String MODE_DARK = "DARK";

    private static final int LIGHT_BACKGROUND = Color.rgb(247, 249, 253);
    private static final int LIGHT_CARD = Color.WHITE;
    private static final int LIGHT_PRIMARY = Color.rgb(34, 96, 214);
    private static final int LIGHT_PRIMARY_SOFT = Color.rgb(235, 242, 255);
    private static final int LIGHT_TEXT = Color.rgb(28, 38, 52);
    private static final int LIGHT_MUTED = Color.rgb(102, 116, 138);
    private static final int LIGHT_BORDER = Color.rgb(220, 228, 240);

    private static final int DARK_BACKGROUND = Color.rgb(10, 18, 32);
    private static final int DARK_CARD = Color.rgb(20, 31, 49);
    private static final int DARK_CARD_ALT = Color.rgb(25, 40, 64);
    private static final int DARK_PRIMARY = Color.rgb(106, 163, 255);
    private static final int DARK_PRIMARY_SOFT = Color.rgb(25, 54, 99);
    private static final int DARK_TEXT = Color.rgb(239, 244, 252);
    private static final int DARK_MUTED = Color.rgb(169, 184, 207);
    private static final int DARK_BORDER = Color.rgb(46, 66, 96);

    private ThemeController() { }

    static String getMode(Context context) {
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_THEME_MODE, MODE_SYSTEM);
        if (MODE_LIGHT.equals(value) || MODE_DARK.equals(value)) return value;
        return MODE_SYSTEM;
    }

    static void setMode(Context context, String mode) {
        String safe = MODE_LIGHT.equals(mode) || MODE_DARK.equals(mode) ? mode : MODE_SYSTEM;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_THEME_MODE, safe).apply();
    }

    static boolean isDark(Context context) {
        String mode = getMode(context);
        if (MODE_DARK.equals(mode)) return true;
        if (MODE_LIGHT.equals(mode)) return false;
        int mask = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mask == Configuration.UI_MODE_NIGHT_YES;
    }

    static int selectedIndex(Context context) {
        String mode = getMode(context);
        if (MODE_LIGHT.equals(mode)) return 1;
        if (MODE_DARK.equals(mode)) return 2;
        return 0;
    }

    static String modeForIndex(int index) {
        if (index == 1) return MODE_LIGHT;
        if (index == 2) return MODE_DARK;
        return MODE_SYSTEM;
    }

    static void applyWindow(Activity activity) {
        boolean dark = isDark(activity);
        Window window = activity.getWindow();
        window.setStatusBarColor(dark ? DARK_BACKGROUND : LIGHT_CARD);
        window.setNavigationBarColor(dark ? DARK_BACKGROUND : LIGHT_CARD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = window.getDecorView().getSystemUiVisibility();
            if (dark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (dark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    static void applyTree(Activity activity, View root) {
        if (root == null) return;
        boolean dark = isDark(activity);
        applyWindow(activity);
        styleView(root, dark, true);
    }

    private static void styleView(View view, boolean dark, boolean root) {
        if (view == null) return;
        if (root) view.setBackgroundColor(dark ? DARK_BACKGROUND : LIGHT_BACKGROUND);
        else recolorBackground(view, dark);

        if (view instanceof EditText) {
            EditText field = (EditText) view;
            field.setTextColor(dark ? DARK_TEXT : LIGHT_TEXT);
            field.setHintTextColor(dark ? DARK_MUTED : LIGHT_MUTED);
            field.setBackground(rounded(dark ? DARK_CARD : LIGHT_CARD,
                    dark ? DARK_BORDER : LIGHT_BORDER, 15));
        } else if (view instanceof Button) {
            styleButton((Button) view, dark);
        } else if (view instanceof TextView) {
            styleText((TextView) view, dark);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleView(group.getChildAt(i), dark, false);
            }
        }
    }

    private static void styleText(TextView view, boolean dark) {
        if (!dark) {
            int current = view.getCurrentTextColor();
            if (isDarkNeutral(current)) view.setTextColor(LIGHT_TEXT);
            return;
        }
        int current = view.getCurrentTextColor();
        if (same(current, LIGHT_PRIMARY)) view.setTextColor(DARK_PRIMARY);
        else if (same(current, LIGHT_MUTED) || same(current, Color.GRAY)) {
            view.setTextColor(DARK_MUTED);
        } else if (!isStatusColor(current)) {
            view.setTextColor(DARK_TEXT);
        }
    }

    private static void styleButton(Button button, boolean dark) {
        String label = String.valueOf(button.getText()).trim();
        boolean primary = "＋".equals(label) || "🎤".equals(label)
                || label.startsWith("+") || label.contains("새 기록")
                || label.contains("추가") || label.contains("저장");
        if (dark) {
            button.setTextColor(primary ? Color.WHITE : DARK_PRIMARY);
            button.setBackground(rounded(primary ? Color.rgb(47, 116, 245) : DARK_CARD_ALT,
                    primary ? Color.rgb(47, 116, 245) : DARK_BORDER, 15));
        } else {
            button.setTextColor(primary ? Color.WHITE : LIGHT_PRIMARY);
            button.setBackground(rounded(primary ? LIGHT_PRIMARY : LIGHT_CARD,
                    primary ? LIGHT_PRIMARY : LIGHT_BORDER, 15));
        }
    }

    private static void recolorBackground(View view, boolean dark) {
        Drawable background = view.getBackground();
        if (background == null) return;
        int current = drawableColor(background);
        if (current == Color.TRANSPARENT) return;
        int replacement = current;
        if (dark) {
            if (same(current, LIGHT_BACKGROUND)) replacement = DARK_BACKGROUND;
            else if (same(current, LIGHT_CARD)) replacement = DARK_CARD;
            else if (same(current, LIGHT_PRIMARY_SOFT)) replacement = DARK_PRIMARY_SOFT;
            else if (same(current, LIGHT_BORDER)) replacement = DARK_BORDER;
        } else {
            if (same(current, DARK_BACKGROUND)) replacement = LIGHT_BACKGROUND;
            else if (same(current, DARK_CARD) || same(current, DARK_CARD_ALT)) replacement = LIGHT_CARD;
            else if (same(current, DARK_PRIMARY_SOFT)) replacement = LIGHT_PRIMARY_SOFT;
            else if (same(current, DARK_BORDER)) replacement = LIGHT_BORDER;
        }
        if (replacement == current) return;
        if (background instanceof GradientDrawable) {
            GradientDrawable copy = (GradientDrawable) background.mutate();
            copy.setColor(replacement);
            view.setBackground(copy);
        } else if (background instanceof ColorDrawable) {
            view.setBackgroundColor(replacement);
        }
    }

    private static int drawableColor(Drawable drawable) {
        if (drawable instanceof ColorDrawable) return ((ColorDrawable) drawable).getColor();
        if (drawable instanceof GradientDrawable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ColorStateList state = ((GradientDrawable) drawable).getColor();
            return state == null ? Color.TRANSPARENT : state.getDefaultColor();
        }
        return Color.TRANSPARENT;
    }

    private static GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(1, stroke);
        drawable.setCornerRadius(radiusDp * 3f);
        return drawable;
    }

    private static boolean same(int first, int second) {
        return (first & 0x00FFFFFF) == (second & 0x00FFFFFF);
    }

    private static boolean isDarkNeutral(int color) {
        return same(color, DARK_TEXT) || same(color, DARK_MUTED) || same(color, Color.WHITE);
    }

    private static boolean isStatusColor(int color) {
        return same(color, Color.rgb(234, 120, 35))
                || same(color, Color.rgb(239, 68, 68))
                || same(color, Color.rgb(34, 197, 94))
                || same(color, Color.rgb(123, 86, 188));
    }
}
