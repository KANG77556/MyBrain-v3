package kr.co.mybrain.v2.ui;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 모든 화면에 같은 글자 크기, 최소 터치 영역, TalkBack 라벨과 한 손 조작 여백을 적용합니다.
 * 개별 화면의 저장·분석 로직은 건드리지 않고 View 계층만 안전하게 정리합니다.
 */
public final class UiConsistencyController {
    private static final Map<TextView, Float> ORIGINAL_TEXT_SP =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean installed;

    private UiConsistencyController() {}

    public static void install(Application application) {
        if (installed) return;
        synchronized (UiConsistencyController.class) {
            if (installed) return;
            installed = true;
            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) {
                    activity.getWindow().getDecorView().post(() -> apply(activity));
                }
                @Override public void onActivityStarted(@NonNull Activity activity) {}
                @Override public void onActivityResumed(@NonNull Activity activity) {
                    activity.getWindow().getDecorView().post(() -> apply(activity));
                }
                @Override public void onActivityPaused(@NonNull Activity activity) {}
                @Override public void onActivityStopped(@NonNull Activity activity) {}
                @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle state) {}
                @Override public void onActivityDestroyed(@NonNull Activity activity) {}
            });
        }
    }

    public static void apply(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        UiPreferences preferences = UiPreferences.load(activity);
        Window window = activity.getWindow();
        window.setStatusBarColor(preferences.highContrast ? Color.WHITE : AppUi.BG);
        window.setNavigationBarColor(preferences.highContrast ? Color.WHITE : AppUi.BG);
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        content.setBackgroundColor(preferences.highContrast ? Color.WHITE : AppUi.BG);
        styleTree(activity, content, preferences);
    }

    private static void styleTree(Activity activity, View view, UiPreferences preferences) {
        if (view == null) return;

        if (preferences.reduceMotion) {
            view.animate().cancel();
            view.clearAnimation();
        }

        int touchDp = preferences.largeTouchTargets || preferences.oneHandMode ? 56 : 48;
        int touchPx = AppUi.dp(activity, touchDp);
        if (view.isClickable() || view instanceof Button || view instanceof CompoundButton
                || view instanceof EditText || view instanceof Spinner) {
            view.setMinimumHeight(Math.max(view.getMinimumHeight(), touchPx));
            view.setMinimumWidth(Math.max(view.getMinimumWidth(), AppUi.dp(activity, 48)));
        }

        if (view instanceof TextView) {
            styleTextView(activity, (TextView) view, preferences);
        }
        if (view instanceof Button) {
            Button button = (Button) view;
            button.setAllCaps(false);
            if (preferences.reduceMotion) button.setStateListAnimator(null);
            setDescriptionFromText(button);
        } else if (view instanceof CompoundButton) {
            setDescriptionFromText((CompoundButton) view);
        } else if (view instanceof EditText) {
            EditText input = (EditText) view;
            if ((input.getContentDescription() == null || input.getContentDescription().length() == 0)
                    && input.getHint() != null) {
                input.setContentDescription(input.getHint());
            }
        } else if (view instanceof Spinner) {
            Spinner spinner = (Spinner) view;
            if (spinner.getContentDescription() == null) spinner.setContentDescription("선택 목록");
        }

        if (view instanceof ScrollView) {
            ScrollView scroll = (ScrollView) view;
            if (preferences.reduceMotion) scroll.setSmoothScrollingEnabled(false);
            if (scroll.getChildCount() > 0 && preferences.oneHandMode) {
                View child = scroll.getChildAt(0);
                int bottom = Math.max(child.getPaddingBottom(), AppUi.dp(activity, 112));
                child.setPadding(child.getPaddingLeft(), child.getPaddingTop(),
                        child.getPaddingRight(), bottom);
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleTree(activity, group.getChildAt(i), preferences);
            }
        }
    }

    private static void styleTextView(Activity activity, TextView textView, UiPreferences preferences) {
        float originalSp;
        synchronized (ORIGINAL_TEXT_SP) {
            Float stored = ORIGINAL_TEXT_SP.get(textView);
            if (stored == null) {
                float scaledDensity = textView.getResources().getDisplayMetrics().scaledDensity;
                stored = scaledDensity <= 0f ? 14f : textView.getTextSize() / scaledDensity;
                ORIGINAL_TEXT_SP.put(textView, stored);
            }
            originalSp = stored;
        }
        float adjusted = Math.min(38f, Math.max(12f, originalSp * preferences.textScale()));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, adjusted);
        textView.setIncludeFontPadding(true);
        textView.setLineSpacing(AppUi.dp(activity, adjusted >= 18f ? 2 : 1), 1.05f);

        boolean heading = originalSp >= 22f
                || (originalSp >= 17f && textView.getTypeface() != null
                && textView.getTypeface().getStyle() == Typeface.BOLD
                && textView.length() <= 40);
        if (heading) ViewCompat.setAccessibilityHeading(textView, true);

        CharSequence content = textView.getText();
        if (content != null) {
            String value = content.toString();
            if (value.contains("성공") || value.contains("실패") || value.contains("완료")
                    || value.contains("진행 중") || value.contains("연결됨")) {
                ViewCompat.setAccessibilityLiveRegion(textView, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
            }
        }

        if (preferences.highContrast && !(textView instanceof Button)) {
            int color = textView.getCurrentTextColor();
            if (isStandardTextColor(color)) textView.setTextColor(Color.BLACK);
            else if (isStandardSubtextColor(color)) textView.setTextColor(Color.rgb(55, 65, 81));
        }
    }

    private static void setDescriptionFromText(TextView view) {
        if (view.getContentDescription() != null && view.getContentDescription().length() > 0) return;
        CharSequence text = view.getText();
        if (text == null) return;
        String value = text.toString().replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (!value.isEmpty()) view.setContentDescription(value);
    }

    private static boolean isStandardTextColor(int color) {
        return color == AppUi.TEXT
                || color == Color.rgb(24, 34, 48)
                || color == Color.rgb(28, 38, 52)
                || color == Color.rgb(31, 41, 55);
    }

    private static boolean isStandardSubtextColor(int color) {
        return color == AppUi.SUBTEXT
                || color == Color.rgb(91, 106, 128)
                || color == Color.rgb(100, 116, 139)
                || color == Color.rgb(102, 116, 138)
                || color == Color.rgb(125, 136, 153);
    }
}