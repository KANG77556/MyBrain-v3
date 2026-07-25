package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * 음성으로 입력 화면을 연 경우 입력된 문장을 자동으로 분석하도록 연결합니다.
 *
 * 기존 저장 형식과 분석 엔진은 그대로 유지하며, 분석 버튼을 한 번 자동 실행합니다.
 * 사용자가 직접 입력하거나 저장된 초안을 연 경우에는 자동 실행하지 않습니다.
 */
public final class VoiceAnalysisFlowEnhancer {

    private static final String VOICE_SOURCE = "음성 입력";
    private static final WeakHashMap<Activity, Boolean> COMPLETED = new WeakHashMap<>();

    private VoiceAnalysisFlowEnhancer() { }

    /** 앱 전체 생명주기에 음성 후 자동 분석 흐름을 설치합니다. */
    public static void install(Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                if (!(activity instanceof UnifiedQuickInputActivityV4)) return;
                if (Boolean.TRUE.equals(COMPLETED.get(activity))) return;

                String source = safe(activity.getIntent().getStringExtra(
                        UnifiedQuickInputActivityV2.EXTRA_PREFILL_SOURCE));
                if (!VOICE_SOURCE.equals(source)) return;

                View root = activity.findViewById(android.R.id.content);
                if (root == null) return;
                root.postDelayed(() -> runAutomaticAnalysis(activity, root), 700L);
            }

            @Override public void onActivityCreated(Activity activity, Bundle state) { }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) { COMPLETED.remove(activity); }
        });
    }

    /** 입력값과 분석 버튼이 준비된 뒤 한 번만 분석을 실행합니다. */
    private static void runAutomaticAnalysis(Activity activity, View root) {
        if (activity.isFinishing() || Boolean.TRUE.equals(COMPLETED.get(activity))) return;

        EditText input = findInput(root);
        Button analyze = findAnalyzeButton(root);
        if (input == null || analyze == null) {
            root.postDelayed(() -> runAutomaticAnalysis(activity, root), 350L);
            return;
        }

        String value = input.getText() == null ? "" : input.getText().toString().trim();
        if (value.isEmpty()) {
            root.postDelayed(() -> runAutomaticAnalysis(activity, root), 350L);
            return;
        }

        COMPLETED.put(activity, true);
        analyze.performClick();
        Toast.makeText(activity, "음성 내용을 분석했습니다. 결과를 확인해 주세요.",
                Toast.LENGTH_SHORT).show();
    }

    private static Button findAnalyzeButton(View root) {
        for (View view : findViews(root)) {
            if (!(view instanceof Button)) continue;
            String text = textOf((TextView) view);
            if (text.contains("분석하기") || text.contains("다시 분석")) return (Button) view;
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

    private static String textOf(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
