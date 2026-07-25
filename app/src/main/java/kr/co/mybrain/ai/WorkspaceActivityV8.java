package kr.co.mybrain.ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
 * MyBrain AI 1.9.0 기본 작업 화면입니다.
 * 기존 홈·목록·달력 기능은 유지하고 빠른 입력을 촬영 품질 검토·음성 이어 말하기 화면으로 연결합니다.
 */
public class WorkspaceActivityV8 extends WorkspaceActivityV7 {

    private static final int REQUEST_HOME_VOICE_V2 = 9801;
    private static final Pattern KOREAN_DATE = Pattern.compile(
            "(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일");

    private View rootView;
    private boolean patchScheduled;
    private int lastSignature = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootView = findViewById(android.R.id.content);
        installWatcher();
        schedulePatch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        schedulePatch();
    }

    private void installWatcher() {
        if (rootView == null) return;
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        int signature = signature(rootView);
                        if (signature != lastSignature) {
                            lastSignature = signature;
                            schedulePatch();
                        }
                    }
                });
    }

    /** 이전 버전의 화면 패치가 끝난 뒤 1.9.0 입력 동작을 마지막으로 적용합니다. */
    private void schedulePatch() {
        if (rootView == null || patchScheduled) return;
        patchScheduled = true;
        rootView.postDelayed(() -> {
            patchScheduled = false;
            applyInputPatch();
        }, 980L);
    }

    private void applyInputPatch() {
        List<View> views = new ArrayList<>();
        collect(rootView, views);
        for (View view : views) {
            if (view instanceof Button) patchButton((Button) view, views);
            if (view instanceof TextView) patchHomeEntry((TextView) view, views);
        }
    }

    private void patchButton(Button button, List<View> views) {
        String value = textOf(button);
        if ("＋".equals(value)) {
            button.setOnClickListener(v -> openUnifiedInput(views, null, null));
            button.setOnLongClickListener(v -> {
                startActivity(new Intent(this, WorkItemManagerActivity.class));
                return true;
            });
            button.setContentDescription("촬영 품질 검토가 포함된 빠른 입력, 길게 누르면 여러 항목 관리");
        } else if ("🎤".equals(value)) {
            button.setOnClickListener(v -> startActivityForResult(
                    new Intent(this, VoiceCaptureActivityV2.class), REQUEST_HOME_VOICE_V2));
            button.setContentDescription("여러 문장을 이어서 음성 입력");
        }
    }

    private void patchHomeEntry(TextView text, List<View> views) {
        if (!"무엇을 기록할까요?".equals(textOf(text))) return;
        text.setOnClickListener(v -> openUnifiedInput(views, null, null));
        text.setContentDescription("1.9.0 통합 빠른 입력 열기");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_HOME_VOICE_V2) {
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
        Intent intent = new Intent(this, UnifiedQuickInputActivityV3.class);
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

    private int signature(View view) {
        int value = view.getClass().getName().hashCode();
        if (view instanceof TextView) value = 31 * value + textOf((TextView) view).hashCode();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            value = 31 * value + group.getChildCount();
            for (int i = 0; i < group.getChildCount(); i++) value = 31 * value + signature(group.getChildAt(i));
        }
        return value;
    }

    private String textOf(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
