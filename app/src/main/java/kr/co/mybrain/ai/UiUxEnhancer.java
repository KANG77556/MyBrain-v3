package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * 앱의 입력 화면에 공통으로 적용하는 UI·UX 보정 모듈입니다.
 *
 * 기존 분석·저장 로직은 건드리지 않고 화면이 생성된 뒤 다음 항목만 개선합니다.
 * 1. AI 분석 버튼을 입력창 바로 아래로 이동
 * 2. 키보드가 첫 화면을 가리지 않도록 초기 상태를 숨김으로 설정
 * 3. 빈 공간과 버튼을 누르면 키보드를 자동으로 숨김
 * 4. 주요 버튼의 최소 터치 영역과 시각적 우선순위를 통일
 */
public final class UiUxEnhancer implements Application.ActivityLifecycleCallbacks {
    private static UiUxEnhancer instance;
    private WeakReference<Activity> resumedActivity = new WeakReference<>(null);

    private UiUxEnhancer() { }

    /** Application에서 한 번만 설치합니다. */
    public static synchronized void install(Application application) {
        if (instance != null) return;
        instance = new UiUxEnhancer();
        application.registerActivityLifecycleCallbacks(instance);
    }

    @Override
    public void onActivityResumed(Activity activity) {
        resumedActivity = new WeakReference<>(activity);
        activity.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        root.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            applyKeyboardConvenience(activity, root);
            applyCommonTouchTargets(activity, root);
            if (activity instanceof AiInputActivity) {
                enhanceAiInput(activity, root);
            } else if (activity instanceof WorkItemEditorActivity
                    || activity instanceof AiSettingsActivity
                    || activity instanceof IntegratedMainActivity) {
                enhanceFormScreen(activity, root);
            }
        });
    }

    /** AI 분석 화면의 정보 순서와 버튼 배치를 사용 흐름에 맞게 재구성합니다. */
    private void enhanceAiInput(Activity activity, View root) {
        LinearLayout container = findPrimaryVerticalContainer(root);
        if (container == null) return;

        EditText input = null;
        Button analyze = null;
        Button settings = null;
        Button cancel = null;
        TextView notice = null;
        TextView provider = null;

        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof EditText && input == null) {
                input = (EditText) child;
            } else if (child instanceof Button) {
                Button button = (Button) child;
                String label = text(button);
                if (label.contains("분석")) analyze = button;
                else if (label.contains("설정")) settings = button;
                else if ("취소".equals(label)) cancel = button;
            } else if (child instanceof TextView) {
                TextView textView = (TextView) child;
                String label = text(textView);
                if (label.startsWith("현재 방식:")) provider = textView;
                else if (label.contains("날짜·시간") || label.contains("클라우드 API")
                        || label.contains("인터넷이나 AI")) notice = textView;
            }
        }

        if (input == null || analyze == null) return;

        // 입력창을 너무 크게 차지하지 않도록 줄이고, 여전히 긴 문장은 스크롤로 입력합니다.
        ViewGroup.LayoutParams rawInputParams = input.getLayoutParams();
        if (rawInputParams instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams inputParams = (LinearLayout.LayoutParams) rawInputParams;
            inputParams.height = dp(activity, 176);
            inputParams.setMargins(0, dp(activity, 8), 0, dp(activity, 8));
            input.setLayoutParams(inputParams);
        }
        input.setMinLines(5);
        input.setTextSize(16f);
        input.setBackground(rounded(Color.WHITE, 16, Color.rgb(207, 217, 232), 1, activity));
        input.setContentDescription("분석할 메시지 입력");

        // 가장 중요한 실행 버튼을 입력창 바로 다음 위치로 이동합니다.
        int inputIndex = container.indexOfChild(input);
        int analyzeIndex = container.indexOfChild(analyze);
        if (analyzeIndex != inputIndex + 1) {
            container.removeView(analyze);
            container.addView(analyze, Math.min(inputIndex + 1, container.getChildCount()));
        }
        stylePrimaryButton(activity, analyze);
        analyze.setContentDescription("입력한 메시지를 자동 추천 방식으로 분석");

        if (provider != null) {
            provider.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
            provider.setBackground(rounded(Color.rgb(235, 243, 255), 14,
                    Color.rgb(190, 210, 241), 1, activity));
            provider.setTextSize(14f);
        }

        if (notice != null) {
            notice.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
            notice.setBackground(rounded(Color.WHITE, 14,
                    Color.rgb(220, 227, 237), 1, activity));
            notice.setTextSize(13f);
            LinearLayout.LayoutParams params = ensureLinearParams(notice.getLayoutParams());
            params.setMargins(0, dp(activity, 8), 0, dp(activity, 8));
            notice.setLayoutParams(params);
        }

        // 설정과 취소는 보조 기능이므로 한 줄에 배치해 세로 공간을 절약합니다.
        if (settings != null && cancel != null
                && settings.getParent() == container && cancel.getParent() == container) {
            int targetIndex = notice == null ? container.indexOfChild(analyze) + 1
                    : container.indexOfChild(notice) + 1;
            container.removeView(settings);
            container.removeView(cancel);

            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(activity, 2), 0, 0);

            styleSecondaryButton(activity, settings, "AI 설정");
            styleSecondaryButton(activity, cancel, "닫기");

            LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(
                    0, dp(activity, 52), 1f);
            left.setMargins(0, 0, dp(activity, 5), 0);
            LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(
                    0, dp(activity, 52), 1f);
            right.setMargins(dp(activity, 5), 0, 0, 0);
            actions.addView(settings, left);
            actions.addView(cancel, right);

            container.addView(actions, Math.min(targetIndex, container.getChildCount()),
                    new LinearLayout.LayoutParams(-1, -2));
        }
    }

    /** 일반 입력 화면의 버튼 높이와 키보드 동작을 통일합니다. */
    private void enhanceFormScreen(Activity activity, View root) {
        List<View> views = new ArrayList<>();
        collect(root, views);
        for (View view : views) {
            if (!(view instanceof Button)) continue;
            Button button = (Button) view;
            String label = text(button);
            button.setMinimumHeight(dp(activity, 48));
            button.setMinHeight(dp(activity, 48));
            button.setAllCaps(false);

            if (label.contains("저장") || label.contains("분석") || label.contains("완료")) {
                stylePrimaryButton(activity, button);
            } else if (label.contains("날짜") || label.contains("시간") || label.contains("설정")) {
                button.setBackground(rounded(Color.WHITE, 14,
                        Color.rgb(198, 211, 230), 1, activity));
            }
        }
    }

    /** 빈 공간 및 버튼을 누를 때 키보드를 자동으로 닫습니다. */
    private void applyKeyboardConvenience(Activity activity, View root) {
        if (!(activity instanceof AiInputActivity
                || activity instanceof WorkItemEditorActivity
                || activity instanceof AiSettingsActivity
                || activity instanceof IntegratedMainActivity)) return;

        View.OnTouchListener hideListener = (view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) hideKeyboard(activity);
            return false;
        };

        View main = root;
        if (root instanceof ViewGroup && ((ViewGroup) root).getChildCount() > 0) {
            main = ((ViewGroup) root).getChildAt(0);
        }
        main.setOnTouchListener(hideListener);
        if (main instanceof ScrollView && ((ScrollView) main).getChildCount() > 0) {
            ((ScrollView) main).getChildAt(0).setOnTouchListener(hideListener);
        }

        List<View> views = new ArrayList<>();
        collect(root, views);
        for (View view : views) {
            if (view instanceof Button || (view instanceof TextView && !(view instanceof EditText))) {
                // 버튼의 기존 클릭 동작은 유지하고 터치 시작 시 키보드만 먼저 닫습니다.
                view.setOnTouchListener(hideListener);
            }
        }
    }

    /** 주요 버튼이 너무 작아지지 않도록 최소 터치 크기를 보장합니다. */
    private void applyCommonTouchTargets(Activity activity, View root) {
        List<View> views = new ArrayList<>();
        collect(root, views);
        for (View view : views) {
            if (view instanceof Button) {
                view.setMinimumHeight(dp(activity, 48));
                view.setMinimumWidth(dp(activity, 48));
            }
        }
    }

    public static void hideKeyboard(Activity activity) {
        View focused = activity.getCurrentFocus();
        if (focused == null) focused = activity.findViewById(android.R.id.content);
        InputMethodManager manager = (InputMethodManager)
                activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null && focused != null) {
            manager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
        if (focused instanceof EditText) focused.clearFocus();
    }

    private void stylePrimaryButton(Activity activity, Button button) {
        button.setTextColor(Color.WHITE);
        button.setTextSize(16f);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackground(rounded(Color.rgb(35, 92, 190), 15, Color.TRANSPARENT, 0, activity));
        LinearLayout.LayoutParams params = ensureLinearParams(button.getLayoutParams());
        params.height = dp(activity, 56);
        params.setMargins(0, dp(activity, 6), 0, dp(activity, 6));
        button.setLayoutParams(params);
    }

    private void styleSecondaryButton(Activity activity, Button button, String label) {
        button.setText(label);
        button.setTextColor(Color.rgb(40, 53, 70));
        button.setTextSize(14f);
        button.setAllCaps(false);
        button.setBackground(rounded(Color.rgb(244, 246, 249), 14,
                Color.rgb(211, 219, 230), 1, activity));
        button.setMinimumHeight(0);
        button.setMinimumWidth(0);
    }

    private LinearLayout findPrimaryVerticalContainer(View root) {
        if (root instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) root;
            if (layout.getOrientation() == LinearLayout.VERTICAL && layout.getChildCount() >= 4) {
                return layout;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                LinearLayout found = findPrimaryVerticalContainer(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static LinearLayout.LayoutParams ensureLinearParams(ViewGroup.LayoutParams raw) {
        if (raw instanceof LinearLayout.LayoutParams) return (LinearLayout.LayoutParams) raw;
        return new LinearLayout.LayoutParams(
                raw == null ? ViewGroup.LayoutParams.MATCH_PARENT : raw.width,
                raw == null ? ViewGroup.LayoutParams.WRAP_CONTENT : raw.height);
    }

    private static GradientDrawable rounded(int fill, int radiusDp, int stroke,
                                             int strokeDp, Activity activity) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(activity, radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(activity, strokeDp), stroke);
        return drawable;
    }

    private static void collect(View view, List<View> output) {
        output.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), output);
    }

    private static String text(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    @Override public void onActivityCreated(Activity activity, Bundle bundle) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle bundle) { }
    @Override public void onActivityDestroyed(Activity activity) { }
}
