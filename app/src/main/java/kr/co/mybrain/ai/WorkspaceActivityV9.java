package kr.co.mybrain.ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MyBrain AI 1.9.1 기본 작업 화면입니다.
 * 화면이 열린 직후 최신 입력 동작을 연결하며, 이전 버전의 지연 패치가 실행돼도
 * 실제 터치는 1.9.1 통합 입력과 음성 화면을 우선 실행합니다.
 */
public class WorkspaceActivityV9 extends WorkspaceActivityV8 {

    private static final int REQUEST_HOME_VOICE_UI = 11801;
    private static final Pattern KOREAN_DATE = Pattern.compile(
            "(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일");

    private View rootView;
    private boolean applying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootView = findViewById(android.R.id.content);
        installImmediateWatcher();
        applyLatestActions();

        // 이전 버전의 예약 작업 이후 접근성 클릭 동작도 최신 상태로 다시 확정합니다.
        if (rootView != null) {
            rootView.postDelayed(this::applyLatestActions, 560L);
            rootView.postDelayed(this::applyLatestActions, 820L);
            rootView.postDelayed(this::applyLatestActions, 1_080L);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyLatestActions();
    }

    private void installImmediateWatcher() {
        if (rootView == null) return;
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        applyLatestActions();
                    }
                });
    }

    private void applyLatestActions() {
        if (rootView == null || applying) return;
        applying = true;
        try {
            List<View> views = new ArrayList<>();
            collect(rootView, views);
            for (View view : views) {
                if (view instanceof Button) patchButton((Button) view, views);
                if (view instanceof TextView) patchHomeEntry((TextView) view, views);
            }
        } finally {
            applying = false;
        }
    }

    private void patchButton(Button button, List<View> views) {
        String value = textOf(button);
        if ("＋".equals(value)) {
            Runnable clickAction = () -> openUnifiedInput(views, null, null);
            Runnable longAction = () -> startActivity(new Intent(this, WorkItemManagerActivity.class));
            connectTouch(button, clickAction, longAction);
            button.setContentDescription("빠른 입력, 길게 누르면 여러 항목 관리");
        } else if ("🎤".equals(value)) {
            Runnable action = () -> startActivityForResult(
                    new Intent(this, VoiceCaptureActivityV3.class), REQUEST_HOME_VOICE_UI);
            connectTouch(button, action, null);
            button.setContentDescription("여러 문장을 이어서 음성 입력");
        }
    }

    private void patchHomeEntry(TextView text, List<View> views) {
        if (!"무엇을 기록할까요?".equals(textOf(text))) return;
        Runnable action = () -> openUnifiedInput(views, null, null);
        text.setOnClickListener(v -> action.run());
        text.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                action.run();
                view.setPressed(false);
            } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
                view.setPressed(true);
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                view.setPressed(false);
            }
            return true;
        });
        text.setContentDescription("통합 빠른 입력 열기");
    }

    /**
     * 짧게 누르면 기본 기능을 실행하고, 길게 누르면 별도 기능을 실행합니다.
     * 이전 버전이 클릭 리스너를 늦게 교체해도 이 터치 처리가 우선됩니다.
     */
    private void connectTouch(Button button, Runnable clickAction, Runnable longAction) {
        button.setOnClickListener(v -> clickAction.run());
        if (longAction != null) {
            button.setOnLongClickListener(v -> {
                longAction.run();
                return true;
            });
        } else {
            button.setOnLongClickListener(null);
        }

        final boolean[] longTriggered = {false};
        final Runnable longPressRunnable = () -> {
            if (longAction == null) return;
            longTriggered[0] = true;
            button.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            longAction.run();
        };

        button.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    longTriggered[0] = false;
                    view.setPressed(true);
                    if (longAction != null) {
                        view.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    view.removeCallbacks(longPressRunnable);
                    view.setPressed(false);
                    if (!longTriggered[0]) clickAction.run();
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    view.removeCallbacks(longPressRunnable);
                    view.setPressed(false);
                    longTriggered[0] = false;
                    return true;

                default:
                    return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_HOME_VOICE_UI) {
            if (resultCode == RESULT_OK && data != null) {
                String value = safe(data.getStringExtra(VoiceCaptureActivityV2.EXTRA_RESULT_TEXT)).trim();
                if (!value.isEmpty()) {
                    List<View> views = new ArrayList<>();
                    collect(rootView, views);
                    openUnifiedInput(views, value, "음성 입력");
                }
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void openUnifiedInput(List<View> views, String prefill, String source) {
        Intent intent = new Intent(this, UnifiedQuickInputActivityV4.class);
        String defaultType = inferDefaultType(views);
        String defaultDate = inferDefaultDate(views);
        if (!defaultType.isEmpty()) {
            intent.putExtra(UnifiedQuickInputActivity.EXTRA_DEFAULT_TYPE, defaultType);
        }
        if (!defaultDate.isEmpty()) {
            intent.putExtra(UnifiedQuickInputActivity.EXTRA_DEFAULT_DATE, defaultDate);
        }
        if (prefill != null && !prefill.trim().isEmpty()) {
            intent.putExtra(UnifiedQuickInputActivityV2.EXTRA_PREFILL_TEXT, prefill);
            intent.putExtra(UnifiedQuickInputActivityV2.EXTRA_PREFILL_SOURCE,
                    source == null ? "" : source);
        }
        startActivity(intent);
    }

    private String inferDefaultType(List<View> views) {
        String header = findHeaderTitle(views);
        if ("할 일".equals(header)) return "할 일";
        if ("일정".equals(header) || "달력".equals(header)) return "일정";
        if ("메모".equals(header)) return "메모";
        return "";
    }

    private String findHeaderTitle(List<View> views) {
        for (View view : views) {
            if (!(view instanceof TextView)) continue;
            TextView text = (TextView) view;
            String value = textOf(text);
            if (!("MyBrain AI".equals(value) || "할 일".equals(value) || "일정".equals(value)
                    || "메모".equals(value) || "달력".equals(value))) continue;
            if (text.getParent() instanceof LinearLayout
                    && ((LinearLayout) text.getParent()).getChildCount() == 2) return value;
        }
        return "";
    }

    private String inferDefaultDate(List<View> views) {
        for (View view : views) {
            if (!(view instanceof TextView)) continue;
            Matcher matcher = KOREAN_DATE.matcher(textOf((TextView) view));
            if (!matcher.find()) continue;
            try {
                return String.format(Locale.KOREA, "%04d-%02d-%02d",
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3)));
            } catch (Exception ignored) { }
        }
        return "";
    }

    private void collect(View view, List<View> output) {
        if (view == null) return;
        output.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), output);
    }

    private String textOf(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
