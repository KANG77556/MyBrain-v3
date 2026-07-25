package kr.co.mybrain.ai;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MyBrain AI 1.8.5 입력·목록 조작 UX 화면입니다.
 * 1.8.4 화면 위에 전체 화면 입력, 상세 보기, 스와이프, 다중 선택을 연결합니다.
 */
public class WorkspaceActivityV4 extends WorkspaceActivityV3 {
    private static final String ENHANCED = "mybrain_ux_enhanced";
    private static final Pattern KOREAN_DATE = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일");

    private View rootView;
    private boolean patchScheduled;
    private int lastSignature = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootView = findViewById(android.R.id.content);
        installWatcher();
        showGuideOnce();
        scheduleUxPatch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduleUxPatch();
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
                            scheduleUxPatch();
                        }
                    }
                });
    }

    private void scheduleUxPatch() {
        if (rootView == null || patchScheduled) return;
        patchScheduled = true;
        rootView.postDelayed(() -> {
            patchScheduled = false;
            applyUxPatch();
        }, 140L);
    }

    private void applyUxPatch() {
        List<View> views = new ArrayList<>();
        collect(rootView, views);
        patchAddButton(views);
        patchBottomMenus(views);
        patchCards(views);
    }

    /** 플로팅 추가 버튼을 새 전체 화면 입력으로 연결합니다. */
    private void patchAddButton(List<View> views) {
        for (View view : views) {
            if (!(view instanceof Button)) continue;
            Button button = (Button) view;
            if (!"＋".equals(textOf(button))) continue;
            button.setOnClickListener(v -> openEditor(-1, detectDefaultType(), detectSelectedDate()));
            button.setOnLongClickListener(v -> {
                startActivity(new Intent(this, WorkItemManagerActivity.class));
                return true;
            });
            button.setContentDescription("새 항목 추가, 길게 누르면 여러 항목 관리");
        }
    }

    /** 하단 메뉴를 길게 누르면 현재 자료의 다중 선택 화면을 엽니다. */
    private void patchBottomMenus(List<View> views) {
        for (View view : views) {
            if (!(view instanceof TextView)) continue;
            TextView text = (TextView) view;
            String value = textOf(text);
            if (!(value.endsWith("홈") || value.endsWith("할 일") || value.endsWith("일정")
                    || value.endsWith("메모") || value.endsWith("달력"))) continue;
            if (!value.contains("\n")) continue;
            text.setOnLongClickListener(v -> {
                startActivity(new Intent(this, WorkItemManagerActivity.class));
                return true;
            });
        }
    }

    /** 화면에 표시된 일정·할 일·메모 카드를 찾아 터치 동작을 교체합니다. */
    private void patchCards(List<View> views) {
        List<WorkItemRecord> items = WorkItemStore.load(this);
        Set<Integer> assigned = new HashSet<>();

        for (View view : views) {
            if (!(view instanceof LinearLayout)) continue;
            LinearLayout card = (LinearLayout) view;
            if (!looksLikeItemCard(card)) continue;
            if (ENHANCED.equals(card.getContentDescription())) continue;

            String title = extractCardTitle(card);
            String allText = collectText(card);
            int index = findUnassigned(items, title, allText, assigned);
            if (index < 0) continue;
            assigned.add(index);
            card.setContentDescription(ENHANCED);
            installCardGesture(card, index);
        }
    }

    private void installCardGesture(LinearLayout card, int itemIndex) {
        final float[] downX = {0f};
        final long[] downAt = {0L};
        final boolean[] moving = {false};

        card.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = event.getRawX();
                    downAt[0] = System.currentTimeMillis();
                    moving[0] = false;
                    view.animate().cancel();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float delta = event.getRawX() - downX[0];
                    if (Math.abs(delta) > dp(8)) moving[0] = true;
                    view.setTranslationX(delta * 0.55f);
                    view.setAlpha(Math.max(0.62f, 1f - Math.abs(delta) / dp(520)));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float movement = event.getRawX() - downX[0];
                    long duration = System.currentTimeMillis() - downAt[0];
                    view.animate().translationX(0f).alpha(1f).setDuration(160L).start();

                    if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) return true;
                    if (Math.abs(movement) >= dp(88)) {
                        if (movement > 0) completeBySwipe(itemIndex);
                        else confirmDeleteBySwipe(itemIndex);
                    } else if (!moving[0] && duration >= 560L) {
                        Intent intent = new Intent(this, WorkItemManagerActivity.class);
                        intent.putExtra(WorkItemManagerActivity.EXTRA_PRESELECT_INDEX, itemIndex);
                        startActivity(intent);
                    } else if (!moving[0]) {
                        Intent intent = new Intent(this, WorkItemDetailActivity.class);
                        intent.putExtra(WorkItemDetailActivity.EXTRA_INDEX, itemIndex);
                        startActivity(intent);
                    }
                    return true;
                default:
                    return true;
            }
        });
    }

    private void completeBySwipe(int index) {
        List<WorkItemRecord> items = WorkItemStore.load(this);
        if (index < 0 || index >= items.size()) return;
        WorkItemRecord item = items.get(index);
        if (!"할 일".equals(item.type)) {
            Toast.makeText(this, "오른쪽 밀기는 할 일에서만 완료 처리됩니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        item.completed = !item.completed;
        WorkItemStore.save(this, items);
        Toast.makeText(this, item.completed ? "완료했습니다." : "완료를 취소했습니다.", Toast.LENGTH_SHORT).show();
        recreate();
    }

    private void confirmDeleteBySwipe(int index) {
        List<WorkItemRecord> items = WorkItemStore.load(this);
        if (index < 0 || index >= items.size()) return;
        WorkItemRecord item = items.get(index);
        new AlertDialog.Builder(this)
                .setTitle("왼쪽 밀기 삭제")
                .setMessage("‘" + item.title + "’ 항목을 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> {
                    List<WorkItemRecord> current = WorkItemStore.load(this);
                    if (index >= 0 && index < current.size()) current.remove(index);
                    WorkItemStore.save(this, current);
                    recreate();
                })
                .show();
    }

    private void openEditor(int index, String defaultType, String date) {
        Intent intent = new Intent(this, WorkItemEditorActivity.class);
        intent.putExtra(WorkItemEditorActivity.EXTRA_INDEX, index);
        intent.putExtra(WorkItemEditorActivity.EXTRA_TYPE, defaultType);
        intent.putExtra(WorkItemEditorActivity.EXTRA_DATE, date);
        startActivity(intent);
    }

    private String detectDefaultType() {
        String header = findHeaderText(rootView);
        if ("할 일".equals(header)) return "할 일";
        if ("일정".equals(header) || "달력".equals(header)) return "일정";
        return "메모";
    }

    private String findHeaderText(View view) {
        if (view instanceof TextView) {
            String value = textOf((TextView) view);
            if ("MyBrain AI".equals(value) || "홈".equals(value) || "할 일".equals(value)
                    || "일정".equals(value) || "메모".equals(value) || "달력".equals(value)) {
                return value;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                String found = findHeaderText(group.getChildAt(i));
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }

    /** 달력의 선택 날짜 제목을 읽어 새 일정의 기본 날짜로 사용합니다. */
    private String detectSelectedDate() {
        List<View> views = new ArrayList<>();
        collect(rootView, views);
        int year = Calendar.getInstance().get(Calendar.YEAR);
        for (View view : views) {
            if (!(view instanceof TextView)) continue;
            String value = textOf((TextView) view);
            if (!value.contains("요일") || value.contains("~")) continue;
            Matcher matcher = KOREAN_DATE.matcher(value);
            if (matcher.find()) {
                try {
                    int month = Integer.parseInt(matcher.group(1));
                    int day = Integer.parseInt(matcher.group(2));
                    return String.format(Locale.KOREA, "%04d-%02d-%02d", year, month, day);
                } catch (Exception ignored) { }
            }
        }
        return new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Calendar.getInstance().getTime());
    }

    private int findUnassigned(List<WorkItemRecord> items, String title, String allText,
                               Set<Integer> assigned) {
        int best = WorkItemStore.findBestIndex(items, title, allText);
        if (best >= 0 && !assigned.contains(best)) return best;
        for (int i = 0; i < items.size(); i++) {
            if (assigned.contains(i)) continue;
            WorkItemRecord item = items.get(i);
            if (safe(item.title).equals(title)) return i;
        }
        return -1;
    }

    private boolean looksLikeItemCard(LinearLayout card) {
        if (card.getOrientation() != LinearLayout.HORIZONTAL || card.getChildCount() < 2) return false;
        View first = card.getChildAt(0);
        View second = card.getChildAt(1);
        return !(first instanceof TextView) && second instanceof LinearLayout
                && first.getLayoutParams() != null && first.getLayoutParams().width <= dp(8);
    }

    private String extractCardTitle(LinearLayout card) {
        if (card.getChildCount() < 2 || !(card.getChildAt(1) instanceof LinearLayout)) return "";
        LinearLayout area = (LinearLayout) card.getChildAt(1);
        for (int i = 0; i < area.getChildCount(); i++) {
            if (area.getChildAt(i) instanceof TextView) {
                String value = textOf((TextView) area.getChildAt(i));
                if (value.startsWith("✓ ")) value = value.substring(2);
                if (!value.isEmpty()) return value;
            }
        }
        return "";
    }

    private String collectText(View view) {
        StringBuilder output = new StringBuilder();
        appendText(view, output);
        return output.toString();
    }

    private void appendText(View view, StringBuilder output) {
        if (view instanceof TextView) output.append(textOf((TextView) view)).append(' ');
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) appendText(group.getChildAt(i), output);
        }
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void showGuideOnce() {
        if (getSharedPreferences("mybrain_ui_settings", MODE_PRIVATE)
                .getBoolean("swipe_guide_shown", false)) return;
        getSharedPreferences("mybrain_ui_settings", MODE_PRIVATE).edit()
                .putBoolean("swipe_guide_shown", true).apply();
        Toast.makeText(this, "항목을 오른쪽으로 밀면 완료, 왼쪽으로 밀면 삭제할 수 있습니다.",
                Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
