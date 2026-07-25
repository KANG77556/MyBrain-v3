package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 통합 입력 화면에 사용자가 직접 누르는 '분석하기' 버튼을 추가합니다.
 *
 * 기존 저장 형식과 분석 로직은 변경하지 않고, 입력값을 다시 바인딩하여
 * 부모 화면의 검증된 분석 로직을 즉시 실행합니다.
 */
public final class AnalysisButtonEnhancer {

    private static final int REQUEST_VOICE = 11601;
    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int PRIMARY_LIGHT = Color.rgb(235, 242, 255);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final String TAG_ANALYZE = "mybrain_explicit_analyze_button";

    private AnalysisButtonEnhancer() { }

    /** 앱 전체에 한 번만 설치합니다. */
    public static void install(Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                if (!(activity instanceof UnifiedQuickInputActivityV4)) return;
                View content = activity.findViewById(android.R.id.content);
                if (content == null) return;
                content.post(() -> apply(activity, content));
                content.postDelayed(() -> apply(activity, content), 450L);
            }

            @Override public void onActivityCreated(Activity activity, Bundle state) { }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
    }

    private static void apply(Activity activity, View root) {
        reconnectVoiceButton(activity, root);
        if (findTagged(root, TAG_ANALYZE) != null) return;

        EditText input = findInput(root);
        LinearLayout mediaRow = findMediaRow(root);
        if (input == null || mediaRow == null || !(mediaRow.getParent() instanceof LinearLayout)) return;

        LinearLayout parent = (LinearLayout) mediaRow.getParent();
        Button analyze = new Button(activity);
        analyze.setTag(TAG_ANALYZE);
        analyze.setText("✨ 분석하기");
        analyze.setTextSize(16);
        analyze.setTextColor(Color.WHITE);
        analyze.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        analyze.setAllCaps(false);
        analyze.setMinimumHeight(0);
        analyze.setMinimumWidth(0);
        analyze.setBackground(rounded(activity, PRIMARY, 16));
        analyze.setContentDescription("입력한 내용을 할 일, 일정 또는 메모로 분석");
        analyze.setOnClickListener(v -> analyze(activity, input, analyze));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 54));
        params.setMargins(0, 0, 0, dp(activity, 12));
        parent.addView(analyze, parent.indexOfChild(mediaRow) + 1, params);
    }

    /** 개선된 앱 내부 음성 화면을 사용하도록 기존 음성 버튼을 다시 연결합니다. */
    private static void reconnectVoiceButton(Activity activity, View root) {
        for (Button button : findButtons(root)) {
            String label = textOf(button);
            if (!label.startsWith("🎤")) continue;
            button.setText("🎤 음성");
            button.setContentDescription("개선된 한국어 음성 입력 열기");
            button.setOnTouchListener(null);
            button.setOnClickListener(v -> activity.startActivityForResult(
                    new Intent(activity, VoiceCaptureActivityV3.class), REQUEST_VOICE));
        }
    }

    private static void analyze(Activity activity, EditText input, Button analyze) {
        String value = input.getText() == null ? "" : input.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(activity, "분석할 내용을 먼저 입력해 주세요.", Toast.LENGTH_SHORT).show();
            input.requestFocus();
            return;
        }

        analyze.setEnabled(false);
        analyze.setText("분석 중...");

        // 같은 값을 다시 설정하면 기존 TextWatcher와 분석 미리보기 로직이 실행됩니다.
        input.setText(value);
        input.setSelection(input.length());
        input.clearFocus();

        analyze.postDelayed(() -> {
            analyze.setEnabled(true);
            analyze.setText("✨ 다시 분석");
            Toast.makeText(activity, "분석 결과를 확인한 뒤 저장하세요.", Toast.LENGTH_SHORT).show();
        }, 220L);
    }

    private static LinearLayout findMediaRow(View root) {
        for (View view : findViews(root)) {
            if (!(view instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) view;
            boolean voice = false;
            boolean camera = false;
            boolean gallery = false;
            for (int i = 0; i < row.getChildCount(); i++) {
                View child = row.getChildAt(i);
                if (!(child instanceof TextView)) continue;
                String text = textOf((TextView) child);
                if (text.startsWith("🎤")) voice = true;
                else if (text.startsWith("📷")) camera = true;
                else if (text.startsWith("🖼")) gallery = true;
            }
            if (voice && camera && gallery) return row;
        }
        return null;
    }

    private static EditText findInput(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            EditText found = findInput(group.getChildAt(i));
            if (found != null) return found;
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
        for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), result);
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

    private static String textOf(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private static GradientDrawable rounded(Activity activity, int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(activity, radius));
        return drawable;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}