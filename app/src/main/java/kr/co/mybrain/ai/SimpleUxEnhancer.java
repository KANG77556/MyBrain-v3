package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 기본 화면에는 꼭 필요한 기능만 보여주는 간단 모드 보정 모듈입니다.
 * 세부 날짜·시간·알림·반복 기능은 삭제하지 않고 '분석 결과 수정' 아래에 접어 둡니다.
 */
public final class SimpleUxEnhancer {

    private static final String TAG_TOGGLE = "mybrain_simple_analysis_toggle";
    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int PRIMARY_LIGHT = Color.rgb(235, 242, 255);
    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BORDER = Color.rgb(220, 228, 240);

    private SimpleUxEnhancer() { }

    /** 앱의 최신 화면이 열릴 때 간단 모드를 자동으로 적용합니다. */
    public static void install(Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                schedule(activity);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                schedule(activity);
            }

            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
    }

    /** 이전 UI 보정 작업이 끝난 뒤에도 간단 모드가 유지되도록 여러 시점에 확인합니다. */
    private static void schedule(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        root.post(() -> apply(activity));
        root.postDelayed(() -> apply(activity), 450L);
        root.postDelayed(() -> apply(activity), 1_250L);
    }

    private static void apply(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;

        String name = activity.getClass().getSimpleName();
        if (name.startsWith("UnifiedQuickInputActivity")) simplifyUnifiedInput(activity, root);
        else if (name.startsWith("WorkspaceActivity")) simplifyHome(root);
        else if (name.startsWith("DocumentCaptureActivity")) simplifyDocument(root);
        else if (name.startsWith("VoiceCaptureActivity")) simplifyVoice(root);
    }

    /** 빠른 입력 화면을 입력 → 자동 요약 → 저장 순서로 단순화합니다. */
    private static void simplifyUnifiedInput(Activity activity, View root) {
        replaceText(root,
                "자연스럽게 입력하면 종류·날짜·시간을 자동으로 정리합니다.",
                "내용을 입력하면 자동으로 정리합니다.");
        replaceText(root,
                "날짜가 없는 메모는 바로 저장할 수 있습니다. 날짜·시간 칩을 길게 누르면 값을 지울 수 있습니다.",
                "필요할 때만 분석 결과를 수정하세요.");

        Button save = findButton(root, "분석 결과로 저장");
        if (save == null) save = findButton(root, "저장");
        if (save != null) {
            save.setText("저장");
            save.setContentDescription("자동 분석 결과 저장");
            save.setTextSize(17);
            save.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        if (findTagged(root, TAG_TOGGLE) != null) return;

        Button detail = findButton(root, "상세 설정");
        if (detail == null || !(detail.getParent() instanceof ViewGroup)) return;
        ViewGroup detailRow = (ViewGroup) detail.getParent();
        if (!(detailRow.getParent() instanceof LinearLayout)) return;
        LinearLayout preview = (LinearLayout) detailRow.getParent();

        Set<View> rows = new LinkedHashSet<>();
        for (Button button : findButtons(preview)) {
            String text = textOf(button);
            if (isAnalysisButton(text) && button.getParent() instanceof View) {
                rows.add((View) button.getParent());
            }
        }
        if (rows.isEmpty()) return;

        int insertIndex = preview.getChildCount();
        for (View row : rows) {
            int index = preview.indexOfChild(row);
            if (index >= 0) insertIndex = Math.min(insertIndex, index);
            row.setVisibility(View.GONE);
        }

        Button toggle = new Button(activity);
        toggle.setTag(TAG_TOGGLE);
        toggle.setText("분석 결과 수정");
        toggle.setTextColor(PRIMARY);
        toggle.setTextSize(14);
        toggle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        toggle.setAllCaps(false);
        toggle.setGravity(Gravity.CENTER);
        toggle.setMinimumHeight(0);
        toggle.setMinimumWidth(0);
        toggle.setContentDescription("종류, 날짜, 시간, 알림과 반복 설정 열기");
        toggle.setBackground(rounded(activity, PRIMARY_LIGHT, 14, BORDER, 1));

        final boolean[] expanded = {false};
        final List<View> controlledRows = new ArrayList<>(rows);
        toggle.setOnClickListener(v -> {
            expanded[0] = !expanded[0];
            for (View row : controlledRows) {
                row.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
            }
            toggle.setText(expanded[0] ? "수정 닫기" : "분석 결과 수정");
            toggle.setContentDescription(expanded[0]
                    ? "세부 분석 결과 닫기"
                    : "종류, 날짜, 시간, 알림과 반복 설정 열기");
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50));
        params.setMargins(dp(activity, 3), dp(activity, 4), dp(activity, 3), dp(activity, 5));
        preview.addView(toggle, Math.max(0, insertIndex), params);
    }

    /** 홈 화면의 안내 문장을 짧고 행동 중심으로 바꿉니다. */
    private static void simplifyHome(View root) {
        replaceText(root,
                "입력 후 종류·날짜·시간을 확인하고 한 번에 저장합니다.",
                "입력하거나 말하면 자동으로 정리됩니다.");
        replaceText(root, "빠른 입력", "바로 기록");
    }

    /** 문서 촬영 화면은 재촬영과 OCR 실행 판단에 필요한 안내만 남깁니다. */
    private static void simplifyDocument(View root) {
        replaceText(root, "OCR 전에 사진 상태를 확인합니다.", "사진 확인");
        replaceText(root,
                "방향을 자동으로 바로잡고, 문서 경계가 확실할 때만 안전하게 자릅니다.",
                "글자가 선명한지 확인한 뒤 OCR을 실행하세요.");
        TextView help = findTextStarting(root, "점수가 낮아도 OCR을 실행할 수 있지만");
        if (help != null) help.setVisibility(View.GONE);
    }

    /** 음성 화면의 긴 설명을 짧게 줄여 바로 말할 수 있게 합니다. */
    private static void simplifyVoice(View root) {
        replaceText(root,
                "한 문장씩 말한 뒤 ‘한 문장 더 말하기’를 누르세요. 마지막에 입력창에 한 번에 추가합니다.",
                "한 문장씩 말하고 필요하면 계속 추가하세요.");
    }

    private static boolean isAnalysisButton(String text) {
        return "메모".equals(text) || "할 일".equals(text) || "일정".equals(text)
                || "날짜 없음".equals(text) || text.matches("\\d{1,2}월\\s*\\d{1,2}일")
                || "시간 없음".equals(text) || text.matches("\\d{2}:\\d{2}")
                || text.contains("알림") || text.matches("\\d+(분|시간|일) 전")
                || "반복 없음".equals(text) || "매일".equals(text) || "매주".equals(text)
                || "매월".equals(text) || "평일".equals(text) || "상세 설정".equals(text);
    }

    private static void replaceText(View root, String from, String to) {
        for (TextView view : findTextViews(root)) {
            if (from.equals(textOf(view))) view.setText(to);
        }
    }

    private static Button findButton(View root, String text) {
        for (Button button : findButtons(root)) {
            if (text.equals(textOf(button))) return button;
        }
        return null;
    }

    private static TextView findTextStarting(View root, String prefix) {
        for (TextView view : findTextViews(root)) {
            if (textOf(view).startsWith(prefix)) return view;
        }
        return null;
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

    private static List<Button> findButtons(View root) {
        List<Button> result = new ArrayList<>();
        collect(root, result, null);
        return result;
    }

    private static List<TextView> findTextViews(View root) {
        List<TextView> result = new ArrayList<>();
        collect(root, null, result);
        return result;
    }

    private static void collect(View view, List<Button> buttons, List<TextView> texts) {
        if (view == null) return;
        if (buttons != null && view instanceof Button) buttons.add((Button) view);
        if (texts != null && view instanceof TextView) texts.add((TextView) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collect(group.getChildAt(i), buttons, texts);
        }
    }

    private static String textOf(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private static GradientDrawable rounded(
            Activity activity, int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(activity, radius));
        if (strokeWidth > 0) drawable.setStroke(dp(activity, strokeWidth), strokeColor);
        return drawable;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}