package kr.co.mybrain.ai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MyBrain AI 1.8.8 기본 화면입니다.
 * 기존 입력 방식 선택 화면 대신 홈과 플러스 버튼에서 통합 입력창을 바로 엽니다.
 */
public class WorkspaceActivityV6 extends WorkspaceActivityV5 {
    private static final String HOME_QUICK_TAG = "mybrain_home_quick_actions";
    private static final String HOME_UNIFIED_MARKER = "mybrain_unified_home_entry";
    private static final String UNIFIED_DRAFT_PREFS = "mybrain_unified_input_draft";
    private static final Pattern KOREAN_DATE = Pattern.compile("(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일");

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

    /** 이전 버전의 패치가 끝난 뒤 마지막으로 통합 입력 동작을 적용합니다. */
    private void schedulePatch() {
        if (rootView == null || patchScheduled) return;
        patchScheduled = true;
        rootView.postDelayed(() -> {
            patchScheduled = false;
            applyUnifiedInputPatch();
        }, 520L);
    }

    private void applyUnifiedInputPatch() {
        List<View> views = new ArrayList<>();
        collect(rootView, views);
        patchFloatingButtons(views);
        patchHomeQuickCard(views);
    }

    /** 모든 플러스 버튼이 입력 방식 선택 화면이 아니라 통합 입력창을 바로 열도록 합니다. */
    private void patchFloatingButtons(List<View> views) {
        for (View view : views) {
            if (!(view instanceof Button)) continue;
            Button button = (Button) view;
            if (!"＋".equals(textOf(button))) continue;
            button.setOnClickListener(v -> openUnifiedInput(null));
            button.setOnLongClickListener(v -> {
                startActivity(new Intent(this, WorkItemManagerActivity.class));
                return true;
            });
            button.setContentDescription("통합 빠른 입력, 길게 누르면 여러 항목 관리");
        }
    }

    /** 1.8.7의 네 개 버튼 카드를 하나의 입력 시작 영역으로 간소화합니다. */
    private void patchHomeQuickCard(List<View> views) {
        LinearLayout card = null;
        for (View view : views) {
            if (view instanceof LinearLayout && HOME_QUICK_TAG.equals(view.getTag())) {
                card = (LinearLayout) view;
                break;
            }
        }
        if (card == null || HOME_UNIFIED_MARKER.equals(card.getContentDescription())) return;

        card.removeAllViews();
        card.setContentDescription(HOME_UNIFIED_MARKER);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(rounded(Color.WHITE, 18, Color.rgb(220, 228, 240), 1));

        TextView title = new TextView(this);
        title.setText("빠른 입력");
        title.setTextSize(16);
        title.setTextColor(Color.rgb(28, 38, 52));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(0, dp(9), 0, 0);

        TextView field = new TextView(this);
        field.setText("무엇을 기록할까요?");
        field.setTextSize(15);
        field.setTextColor(Color.rgb(118, 132, 153));
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(14), 0, dp(10), 0);
        field.setBackground(rounded(Color.rgb(247, 249, 253), 15, Color.rgb(215, 224, 238), 1));
        field.setOnClickListener(v -> openUnifiedInput(null));
        inputRow.addView(field, new LinearLayout.LayoutParams(0, dp(52), 1f));

        Button voice = new Button(this);
        voice.setText("🎤");
        voice.setTextSize(19);
        voice.setAllCaps(false);
        voice.setTextColor(Color.WHITE);
        voice.setGravity(Gravity.CENTER);
        voice.setMinimumWidth(0);
        voice.setMinimumHeight(0);
        voice.setContentDescription("음성으로 빠른 입력");
        voice.setBackground(rounded(Color.rgb(34, 96, 214), 15, Color.rgb(34, 96, 214), 0));
        voice.setOnClickListener(v -> openUnifiedInput(QuickInputActivity.MODE_VOICE));
        LinearLayout.LayoutParams voiceParams = new LinearLayout.LayoutParams(dp(54), dp(52));
        voiceParams.setMargins(dp(8), 0, 0, 0);
        inputRow.addView(voice, voiceParams);
        card.addView(inputRow);

        TextView guide = new TextView(this);
        guide.setText("입력 후 종류·날짜·시간을 확인하고 한 번에 저장합니다.");
        guide.setTextSize(12);
        guide.setTextColor(Color.rgb(102, 116, 138));
        guide.setPadding(dp(2), dp(8), dp(2), 0);
        card.addView(guide);
    }

    private void openUnifiedInput(String startMode) {
        sanitizeUnifiedDraft();
        List<View> views = new ArrayList<>();
        collect(rootView, views);

        Intent intent = new Intent(this, UnifiedQuickInputActivity.class);
        String defaultType = inferDefaultType(views);
        String defaultDate = inferDefaultDate(views);
        if (!defaultType.isEmpty()) intent.putExtra(UnifiedQuickInputActivity.EXTRA_DEFAULT_TYPE, defaultType);
        if (!defaultDate.isEmpty()) intent.putExtra(UnifiedQuickInputActivity.EXTRA_DEFAULT_DATE, defaultDate);
        if (startMode != null) intent.putExtra(UnifiedQuickInputActivity.EXTRA_START_MODE, startMode);
        startActivity(intent);
    }

    /** 수동 알림을 선택하지 않은 초안에 내부 센티널 값이 남지 않도록 정리합니다. */
    private void sanitizeUnifiedDraft() {
        SharedPreferences draft = getSharedPreferences(UNIFIED_DRAFT_PREFS, MODE_PRIVATE);
        if (draft.contains("reminder")
                && draft.getInt("reminder", Integer.MIN_VALUE) == Integer.MIN_VALUE) {
            draft.edit().remove("reminder").apply();
        }
    }

    /** 현재 메뉴의 성격을 기본 종류로 전달합니다. */
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

    /** 달력·일간 화면에 명확한 선택 날짜가 보이면 새 일정의 기본 날짜로 사용합니다. */
    private String inferDefaultDate(List<View> views) {
        for (View view : views) {
            if (!(view instanceof TextView)) continue;
            Matcher matcher = KOREAN_DATE.matcher(textOf((TextView) view));
            if (!matcher.find()) continue;
            try {
                return String.format(java.util.Locale.KOREA, "%04d-%02d-%02d",
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3)));
            } catch (Exception ignored) { }
        }
        return "";
    }

    private void collect(View view, List<View> output) {
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

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
