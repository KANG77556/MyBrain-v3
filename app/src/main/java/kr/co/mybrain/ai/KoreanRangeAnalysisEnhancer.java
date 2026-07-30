package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.WeakHashMap;

/**
 * 범위 입력을 첫 날짜·첫 시간으로 축약하지 않고 미리보기 칩과 설명에 표시합니다.
 * 수동으로 날짜나 시간을 고친 경우에는 사용자의 선택을 덮어쓰지 않습니다.
 */
public final class KoreanRangeAnalysisEnhancer {
    private static final WeakHashMap<EditText, TextWatcher> WATCHERS = new WeakHashMap<>();

    private KoreanRangeAnalysisEnhancer() { }

    public static void install(Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) { attach(activity); }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) { attach(activity); }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
    }

    private static void attach(Activity activity) {
        if (!(activity instanceof UnifiedQuickInputActivity)) return;
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        root.postDelayed(() -> bind(activity), 300L);
        root.postDelayed(() -> apply(activity), 760L);
    }

    private static void bind(Activity activity) {
        EditText input = findInput(activity.findViewById(android.R.id.content));
        if (input == null) return;
        synchronized (WATCHERS) {
            if (WATCHERS.containsKey(input)) {
                apply(activity);
                return;
            }
            TextWatcher watcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
                @Override public void afterTextChanged(Editable s) {
                    input.postDelayed(() -> apply(activity), 260L);
                }
            };
            input.addTextChangedListener(watcher);
            WATCHERS.put(input, watcher);
        }
        apply(activity);
    }

    private static void apply(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        EditText input = findInput(root);
        if (input == null) return;
        List<AiAnalysisResult> results = KoreanScheduleRangeParser.parse(input.getText().toString(), new Date());
        if (results.size() <= 1) return;

        AiAnalysisResult first = results.get(0);
        AiAnalysisResult last = results.get(results.size() - 1);
        List<Button> buttons = new ArrayList<>();
        collectButtons(root, buttons);

        Button type = null;
        Button date = null;
        Button time = null;
        Button repeat = null;
        Button reminder = null;
        String firstDate = displayDate(first.date);
        for (Button button : buttons) {
            String value = text(button);
            if ("일정".equals(value) || "메모".equals(value) || "할 일".equals(value)) type = button;
            else if (value.equals(firstDate) || value.equals("날짜 없음") || value.matches("\\d{1,2}월\\s*\\d{1,2}일.*")) date = button;
            else if (value.equals(first.time) || value.equals("시간 없음") || value.matches("\\d{2}:\\d{2}(?:\\s*~.*)?")) time = button;
            else if (value.contains("알림")) reminder = button;
            else if (value.contains("반복") || value.equals("매일") || value.equals("기간 내 매일")) repeat = button;
        }

        boolean automaticDate = date != null && (text(date).equals(firstDate) || text(date).contains("~") || text(date).equals("날짜 없음"));
        boolean automaticTime = time != null && (text(time).equals(first.time) || text(time).contains("~") || text(time).equals("시간 없음"));
        if (!automaticDate || !automaticTime) return;

        String dateRange = firstDate + "~" + displayDate(last.date);
        String timeRange = first.time + "~" + first.endTime;
        if (type != null) type.setText("일정");
        date.setText(dateRange);
        time.setText(timeRange);
        if (repeat != null && (text(repeat).contains("반복") || text(repeat).equals("기간 내 매일"))) {
            repeat.setText("기간 내 매일");
        }

        ViewGroup preview = type != null && type.getParent() instanceof ViewGroup
                && type.getParent().getParent() instanceof ViewGroup
                ? (ViewGroup) type.getParent().getParent() : null;
        if (preview == null) return;
        List<TextView> directTexts = new ArrayList<>();
        for (int i = 0; i < preview.getChildCount(); i++) {
            View child = preview.getChildAt(i);
            if (child instanceof TextView && !(child instanceof Button)) directTexts.add((TextView) child);
        }
        if (!directTexts.isEmpty()) directTexts.get(0).setText("일정 " + results.size() + "건으로 정리");
        if (directTexts.size() > 1) {
            String reminderText = reminder == null ? "" : text(reminder);
            String detail = dateRange + " · " + timeRange;
            if (!reminderText.isEmpty() && !reminderText.equals("알림 없음")) detail += " · " + reminderText;
            directTexts.get(1).setText(detail);
        }
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

    private static void collectButtons(View view, List<Button> output) {
        if (view instanceof Button) output.add((Button) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collectButtons(group.getChildAt(i), output);
    }

    private static String displayDate(String value) {
        if (value == null || value.length() < 10) return value == null ? "" : value;
        try {
            return Integer.parseInt(value.substring(5, 7)) + "월 "
                    + Integer.parseInt(value.substring(8, 10)) + "일";
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String text(TextView view) {
        return view == null || view.getText() == null ? "" : view.getText().toString().trim();
    }
}
