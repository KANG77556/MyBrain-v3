package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * MyBrain AI 1.9.2 공통 UI 안전 보정 모듈입니다.
 *
 * 휴대전화의 제스처 내비게이션, 시스템 글자 확대, 화면 회전, 키보드 표시 상태에 따라
 * 하단 고정 버튼과 입력 화면이 잘리지 않도록 공통 보정을 적용합니다.
 * 기존 저장·OCR·음성·알림 기능에는 관여하지 않습니다.
 */
public final class UiSafeAreaEnhancer {

    private static final String TAG_FIXED_SAVE = "mybrain_fixed_save_action";
    private static final String TAG_FIXED_DOCUMENT = "mybrain_document_fixed_action";

    /** Activity가 사라지면 자동으로 정리되도록 약한 참조를 사용합니다. */
    private static final WeakHashMap<View, PaddingState> INSET_TARGETS = new WeakHashMap<>();
    private static final WeakHashMap<Activity, Boolean> INSTALLED_ACTIVITIES = new WeakHashMap<>();

    private UiSafeAreaEnhancer() {
        // 공통 기능만 제공하므로 객체를 만들지 않습니다.
    }

    /** 앱 전체 Activity에 안전영역과 접근성 보정을 자동 적용합니다. */
    public static void install(Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                configureWindow(activity);
                scheduleApply(activity, 0L);
                scheduleApply(activity, 120L);

                // 이전 버전 화면의 지연 배치 작업이 끝난 뒤 한 번 더 최종 보정합니다.
                scheduleApply(activity, 1_250L);
            }

            @Override public void onActivityStarted(Activity activity) { }

            @Override
            public void onActivityResumed(Activity activity) {
                configureWindow(activity);
                scheduleApply(activity, 0L);
            }

            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

            @Override
            public void onActivityDestroyed(Activity activity) {
                INSTALLED_ACTIVITIES.remove(activity);
            }
        });
    }

    /** 키보드가 화면을 덮지 않도록 하고 밝은 시스템 바와 앱 화면을 자연스럽게 연결합니다. */
    private static void configureWindow(Activity activity) {
        Window window = activity.getWindow();
        if (window == null) return;

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.WHITE);

        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
    }

    /** 화면이 완전히 만들어진 다음 안전 보정을 실행합니다. */
    private static void scheduleApply(Activity activity, long delayMillis) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        content.postDelayed(() -> apply(activity), delayMillis);
    }

    private static void apply(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        INSTALLED_ACTIVITIES.put(activity, true);
        improveGeneralTouchTargets(activity, content);

        if (activity instanceof UnifiedQuickInputActivityV4) {
            improveUnifiedInput(activity, content);
        }
        if (activity instanceof DocumentCaptureActivityV2) {
            improveDocumentCapture(activity, content);
        }
        if (activity instanceof VoiceCaptureActivityV3) {
            improveVoiceInput(activity, content);
        }
        if (activity instanceof PrivacyReviewActivity) {
            improvePrivacyReview(activity, content);
        }
        if (activity instanceof WorkspaceActivityV9) {
            improveWorkspace(activity, content);
        }
    }

    /** 통합 입력 화면의 키보드, 큰 글씨, 하단 저장 버튼을 보정합니다. */
    private static void improveUnifiedInput(Activity activity, View content) {
        EditText input = findFirstEditText(content);
        if (input != null) {
            input.setHorizontallyScrolling(false);
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setMaxLines(12);
            input.setContentDescription("일정, 할 일 또는 메모 내용을 자연스럽게 입력");
            input.setOnEditorActionListener((view, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE
                        || actionId == EditorInfo.IME_ACTION_SEND
                        || actionId == EditorInfo.IME_ACTION_GO) {
                    hideKeyboard(activity, view);
                    return true;
                }
                return false;
            });
        }

        float fontScale = activity.getResources().getConfiguration().fontScale;
        boolean largeText = fontScale > 1.15f;

        for (Button button : findButtons(content)) {
            String value = textOf(button);
            if (value.startsWith("🎤") || value.startsWith("📷") || value.startsWith("🖼")) {
                button.setSingleLine(true);
                button.setMinHeight(dp(activity, largeText ? 52 : 48));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    button.setAutoSizeTextTypeUniformWithConfiguration(
                            11, 14, 1, TypedValue.COMPLEX_UNIT_SP);
                }
            } else if ("분석 결과로 저장".equals(value)) {
                button.setMinHeight(dp(activity, 58));
                button.setContentDescription("분석한 일정, 할 일 또는 메모 저장");
            } else if (isAnalysisChip(value)) {
                button.setSingleLine(false);
                button.setMaxLines(2);
                button.setEllipsize(TextUtils.TruncateAt.END);
                button.setMinHeight(dp(activity, largeText ? 54 : 50));
                button.setIncludeFontPadding(false);
            }
        }

        View fixedSave = findTagged(content, TAG_FIXED_SAVE);
        if (fixedSave != null) applyBottomNavigationInset(fixedSave);

        for (View view : findViews(content)) {
            if (view instanceof ScrollView) {
                ScrollView scroll = (ScrollView) view;
                scroll.setClipToPadding(false);
                break;
            }
        }
    }

    /** 문서 화면에서 미리보기와 하단 버튼이 큰 글씨에서도 함께 보이도록 조정합니다. */
    private static void improveDocumentCapture(Activity activity, View content) {
        View fixedAction = findTagged(content, TAG_FIXED_DOCUMENT);
        if (fixedAction != null) applyBottomNavigationInset(fixedAction);

        ImageView preview = findFirstImageView(content);
        if (preview != null && preview.getLayoutParams() != null) {
            float density = activity.getResources().getDisplayMetrics().density;
            float heightDp = content.getHeight() > 0
                    ? content.getHeight() / density
                    : activity.getResources().getDisplayMetrics().heightPixels / density;
            float fontScale = activity.getResources().getConfiguration().fontScale;
            boolean landscape = activity.getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_LANDSCAPE;

            int target;
            if (landscape) {
                target = Math.round(Math.max(140f, Math.min(190f, heightDp * 0.36f)));
            } else {
                float maximum = fontScale > 1.18f ? 270f : 320f;
                target = Math.round(Math.max(190f, Math.min(maximum, heightDp * 0.34f)));
            }
            ViewGroup.LayoutParams params = preview.getLayoutParams();
            params.height = dp(activity, target);
            preview.setLayoutParams(params);
        }
    }

    /** 음성 화면의 하단 조작 영역이 내비게이션 바와 겹치지 않도록 합니다. */
    private static void improveVoiceInput(Activity activity, View content) {
        LinearLayout root = findFirstLinearLayout(content);
        if (root != null) applyBottomNavigationInset(root);

        for (Button button : findButtons(content)) {
            String value = textOf(button);
            if (value.contains("입력창에 추가")) {
                button.setMinHeight(dp(activity, 56));
                button.setContentDescription("인식한 모든 음성 문장을 입력창에 추가");
            } else if (value.contains("문장") || value.contains("듣기")) {
                button.setMinHeight(dp(activity, 50));
            }
        }
    }

    /** 개인정보 비교 화면의 적용·취소 버튼에 하단 안전 여백을 제공합니다. */
    private static void improvePrivacyReview(Activity activity, View content) {
        LinearLayout root = findFirstLinearLayout(content);
        if (root != null) applyBottomNavigationInset(root);

        for (Button button : findButtons(content)) {
            String value = textOf(button);
            if ("마스킹 결과 적용".equals(value)) {
                button.setMinHeight(dp(activity, 56));
                button.setContentDescription("개인정보를 가린 결과를 입력창에 적용");
            }
        }
    }

    /** 홈 하단 메뉴와 플러스 버튼의 안전영역·터치 크기를 보정합니다. */
    private static void improveWorkspace(Activity activity, View content) {
        for (View view : findViews(content)) {
            if (view instanceof LinearLayout && isBottomNavigation((LinearLayout) view)) {
                applyBottomNavigationInset(view);
                break;
            }
        }

        for (Button button : findButtons(content)) {
            if ("＋".equals(textOf(button))) {
                button.setMinWidth(dp(activity, 56));
                button.setMinHeight(dp(activity, 56));
                button.setContentDescription("빠른 입력, 길게 누르면 여러 항목 관리");
            }
        }
    }

    /** 모든 버튼이 최소 48dp 터치 영역을 갖도록 합니다. */
    private static void improveGeneralTouchTargets(Activity activity, View content) {
        int minimum = dp(activity, 48);
        for (Button button : findButtons(content)) {
            button.setMinWidth(Math.max(button.getMinWidth(), minimum));
            button.setMinHeight(Math.max(button.getMinHeight(), minimum));
        }
    }

    /** 하단 내비게이션 바 높이를 실제 시스템 값만큼 안전하게 추가합니다. */
    private static void applyBottomNavigationInset(View target) {
        if (INSET_TARGETS.containsKey(target)) {
            target.requestApplyInsets();
            return;
        }

        PaddingState base = new PaddingState(
                target.getPaddingLeft(), target.getPaddingTop(),
                target.getPaddingRight(), target.getPaddingBottom());
        INSET_TARGETS.put(target, base);

        target.setOnApplyWindowInsetsListener((view, insets) -> {
            int bottomInset;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bottomInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            } else {
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            PaddingState padding = INSET_TARGETS.get(view);
            if (padding != null) {
                view.setPadding(padding.left, padding.top, padding.right,
                        padding.bottom + Math.max(0, bottomInset));
            }
            return insets;
        });
        target.requestApplyInsets();
    }

    private static boolean isAnalysisChip(String value) {
        return "메모".equals(value) || "할 일".equals(value) || "일정".equals(value)
                || "날짜 없음".equals(value) || value.matches("\\d{1,2}월\\s*\\d{1,2}일")
                || "시간 없음".equals(value) || value.matches("\\d{2}:\\d{2}")
                || value.contains("알림") || value.matches("\\d+(분|시간|일) 전")
                || "반복 없음".equals(value) || "매일".equals(value) || "매주".equals(value)
                || "매월".equals(value) || "평일".equals(value) || "상세 설정".equals(value);
    }

    private static boolean isBottomNavigation(LinearLayout layout) {
        if (layout.getOrientation() != LinearLayout.HORIZONTAL || layout.getChildCount() != 5) {
            return false;
        }
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);
            if (!(child instanceof TextView)) return false;
            values.append(textOf((TextView) child)).append('|');
        }
        String text = values.toString();
        return text.contains("홈") && text.contains("할 일") && text.contains("일정")
                && text.contains("메모") && text.contains("달력");
    }

    private static void hideKeyboard(Activity activity, View view) {
        InputMethodManager manager = (InputMethodManager)
                activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        view.clearFocus();
    }

    private static View findTagged(View view, String tag) {
        if (tag.equals(view.getTag())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findTagged(group.getChildAt(i), tag);
            if (found != null) return found;
        }
        return null;
    }

    private static EditText findFirstEditText(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            EditText result = findFirstEditText(group.getChildAt(i));
            if (result != null) return result;
        }
        return null;
    }

    private static ImageView findFirstImageView(View view) {
        if (view instanceof ImageView) return (ImageView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            ImageView result = findFirstImageView(group.getChildAt(i));
            if (result != null) return result;
        }
        return null;
    }

    private static LinearLayout findFirstLinearLayout(View view) {
        if (view instanceof LinearLayout) return (LinearLayout) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            LinearLayout result = findFirstLinearLayout(group.getChildAt(i));
            if (result != null) return result;
        }
        return null;
    }

    private static List<Button> findButtons(View root) {
        List<Button> result = new ArrayList<>();
        for (View view : findViews(root)) {
            if (view instanceof Button) result.add((Button) view);
        }
        return result;
    }

    private static List<View> findViews(View root) {
        List<View> result = new ArrayList<>();
        collect(root, result);
        return result;
    }

    private static void collect(View view, List<View> result) {
        if (view == null) return;
        result.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collect(group.getChildAt(i), result);
        }
    }

    private static String textOf(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    /** 안전영역 적용 전의 원래 여백을 기억합니다. */
    private static final class PaddingState {
        final int left;
        final int top;
        final int right;
        final int bottom;

        PaddingState(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
