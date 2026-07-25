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
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 홈 화면에서 오늘 필요한 정보만 우선 보여주는 간단 홈 보정 모듈입니다.
 * 기존 일정·할 일·메모 화면은 변경하지 않고 홈의 표시 순서만 정리합니다.
 */
public final class HomeSimpleEnhancer {

    private static final String QUICK_TAG = "mybrain_home_quick_actions";
    private static final String SUMMARY_TAG = "mybrain_simple_home_summary";
    private static final String SPACER_TAG = "mybrain_simple_home_spacer";

    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int PRIMARY_LIGHT = Color.rgb(235, 242, 255);
    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BORDER = Color.rgb(220, 228, 240);
    private static final int BACKGROUND = Color.rgb(247, 249, 253);

    private static final Map<Activity, Runnable> pendingTasks = new WeakHashMap<>();
    private static final Map<Activity, Boolean> watcherInstalled = new WeakHashMap<>();

    private HomeSimpleEnhancer() { }

    /** 최신 작업 화면이 열리거나 다시 그려질 때 간단 홈을 자동 적용합니다. */
    public static void install(Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                installWatcher(activity);
                schedule(activity);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                installWatcher(activity);
                schedule(activity);
            }

            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

            @Override
            public void onActivityDestroyed(Activity activity) {
                synchronized (pendingTasks) {
                    pendingTasks.remove(activity);
                    watcherInstalled.remove(activity);
                }
            }
        });
    }

    /** 하단 메뉴를 전환해 홈 화면이 새로 만들어지는 상황을 감지합니다. */
    private static void installWatcher(Activity activity) {
        if (!isWorkspace(activity)) return;
        synchronized (pendingTasks) {
            if (Boolean.TRUE.equals(watcherInstalled.get(activity))) return;
            watcherInstalled.put(activity, true);
        }

        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            // 홈 요약이 이미 있으면 추가 레이아웃 작업을 만들지 않아 반복 실행을 방지합니다.
            if (findTagged(root, SUMMARY_TAG) == null) schedule(activity);
        });
    }

    /** 짧은 시간에 여러 번 발생하는 화면 변경 신호를 한 번으로 합칩니다. */
    private static void schedule(Activity activity) {
        if (!isWorkspace(activity)) return;
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;

        synchronized (pendingTasks) {
            Runnable previous = pendingTasks.get(activity);
            if (previous != null) root.removeCallbacks(previous);
            Runnable task = () -> apply(activity);
            pendingTasks.put(activity, task);
            root.postDelayed(task, 90L);
        }
    }

    private static boolean isWorkspace(Activity activity) {
        return activity.getClass().getSimpleName().startsWith("WorkspaceActivity");
    }

    private static void apply(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (root == null || !isHomeScreen(root)) return;

        LinearLayout page = findHomePage(root);
        View quickCard = findTagged(root, QUICK_TAG);
        if (page == null || quickCard == null || quickCard.getParent() != page) return;

        moveToTop(page, quickCard);
        simplifyQuickCard(quickCard);

        TextView dateLabel = findDateLabel(page, quickCard);
        if (dateLabel != null) moveAfter(page, dateLabel, quickCard);

        LinearLayout summary = findOrCreateSummary(activity, page, quickCard, dateLabel);
        rebuildSummary(activity, root, summary);
        hideLegacyHomeContent(page, quickCard, dateLabel, summary);
        ensureBottomSpacer(activity, page);
        simplifyHeaderActions(root);
    }

    private static boolean isHomeScreen(View root) {
        boolean header = false;
        boolean today = false;
        boolean tasks = false;
        for (TextView value : findTextViews(root)) {
            String text = textOf(value);
            if ("MyBrain AI".equals(text)) header = true;
            else if ("오늘 일정".equals(text)) today = true;
            else if ("우선 처리할 일".equals(text)) tasks = true;
        }
        return header && today && tasks;
    }

    private static LinearLayout findHomePage(View view) {
        if (view instanceof ScrollView) {
            ScrollView scroll = (ScrollView) view;
            if (scroll.getChildCount() > 0 && scroll.getChildAt(0) instanceof LinearLayout) {
                LinearLayout candidate = (LinearLayout) scroll.getChildAt(0);
                if (containsDirectText(candidate, "오늘 일정")
                        && containsDirectText(candidate, "우선 처리할 일")) {
                    return candidate;
                }
            }
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            LinearLayout found = findHomePage(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static boolean containsDirectText(LinearLayout page, String target) {
        for (int i = 0; i < page.getChildCount(); i++) {
            View child = page.getChildAt(i);
            if (child instanceof TextView && target.equals(textOf((TextView) child))) return true;
        }
        return false;
    }

    private static void moveToTop(LinearLayout page, View view) {
        if (page.indexOfChild(view) == 0) return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        page.removeView(view);
        page.addView(view, 0, params);
    }

    private static void moveAfter(LinearLayout page, View view, View anchor) {
        if (view == anchor) return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        page.removeView(view);
        int target = Math.min(page.indexOfChild(anchor) + 1, page.getChildCount());
        page.addView(view, target, params);
    }

    /** 빠른 입력 카드는 제목과 입력 시작 영역만 남깁니다. */
    private static void simplifyQuickCard(View quickCard) {
        for (TextView value : findTextViews(quickCard)) {
            String text = textOf(value);
            if ("빠른 입력".equals(text)) value.setText("바로 기록");
            if (text.startsWith("입력 후") || text.startsWith("입력하거나 말하면")) {
                value.setVisibility(View.GONE);
            }
        }
    }

    private static TextView findDateLabel(LinearLayout page, View quickCard) {
        for (int i = 0; i < page.getChildCount(); i++) {
            View child = page.getChildAt(i);
            if (child == quickCard || !(child instanceof TextView)) continue;
            TextView text = (TextView) child;
            if (textOf(text).matches("\\d{4}년\\s*\\d{1,2}월\\s*\\d{1,2}일.*")) {
                text.setTextSize(13);
                text.setTextColor(MUTED);
                text.setPadding(dp(text, 2), dp(text, 10), dp(text, 2), dp(text, 4));
                return text;
            }
        }
        return null;
    }

    private static LinearLayout findOrCreateSummary(
            Activity activity, LinearLayout page, View quickCard, TextView dateLabel) {
        View existing = findTagged(page, SUMMARY_TAG);
        if (existing instanceof LinearLayout) return (LinearLayout) existing;

        LinearLayout summary = new LinearLayout(activity);
        summary.setTag(SUMMARY_TAG);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setPadding(dp(activity, 16), dp(activity, 15), dp(activity, 16), dp(activity, 14));
        summary.setBackground(rounded(activity, Color.WHITE, 20, BORDER, 1));
        summary.setElevation(dp(activity, 2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(activity, 8), 0, dp(activity, 8));
        int anchor = dateLabel == null ? page.indexOfChild(quickCard) : page.indexOfChild(dateLabel);
        page.addView(summary, Math.min(anchor + 1, page.getChildCount()), params);
        return summary;
    }

    /** 저장 자료에서 가장 가까운 오늘 일정과 미완료 할 일만 골라 표시합니다. */
    private static void rebuildSummary(Activity activity, View root, LinearLayout summary) {
        summary.removeAllViews();
        List<WorkItemRecord> items = WorkItemStore.load(activity);
        LocalDate today = LocalDate.now();

        List<WorkItemRecord> schedules = new ArrayList<>();
        List<WorkItemRecord> tasks = new ArrayList<>();
        for (WorkItemRecord item : items) {
            if (item.completed) continue;
            if ("일정".equals(item.type) && occurs(item, today)) schedules.add(item);
            else if ("할 일".equals(item.type)) tasks.add(item);
        }

        schedules.sort(Comparator.comparing(HomeSimpleEnhancer::timeSortKey)
                .thenComparing(item -> safe(item.title)));
        tasks.sort(Comparator.comparing(HomeSimpleEnhancer::dateSortKey)
                .thenComparing(HomeSimpleEnhancer::timeSortKey)
                .thenComparing(item -> safe(item.title)));

        LinearLayout heading = new LinearLayout(activity);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text(activity, "오늘", 20, TEXT, true),
                new LinearLayout.LayoutParams(0, dp(activity, 36), 1f));
        TextView count = text(activity,
                "일정 " + schedules.size() + " · 할 일 " + tasks.size(), 13, MUTED, false);
        count.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        heading.addView(count, new LinearLayout.LayoutParams(-2, dp(activity, 36)));
        summary.addView(heading);

        WorkItemRecord nearest = findNearestSchedule(schedules);
        if (nearest == null) {
            TextView empty = text(activity, "오늘 일정이 없습니다.", 15, MUTED, false);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            empty.setPadding(dp(activity, 12), dp(activity, 13), dp(activity, 12), dp(activity, 13));
            empty.setBackground(rounded(activity, BACKGROUND, 15, BORDER, 1));
            summary.addView(empty, matchMargins(activity, 0, 7, 0, 8));
        } else {
            summary.addView(scheduleCard(activity, root, nearest),
                    matchMargins(activity, 0, 7, 0, 8));
        }

        TextView taskTitle = text(activity, "남은 할 일", 16, TEXT, true);
        taskTitle.setPadding(dp(activity, 2), dp(activity, 4), 0, dp(activity, 5));
        summary.addView(taskTitle);

        if (tasks.isEmpty()) {
            TextView empty = text(activity, "남은 할 일이 없습니다.", 14, MUTED, false);
            empty.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
            summary.addView(empty);
        } else {
            for (int i = 0; i < Math.min(2, tasks.size()); i++) {
                summary.addView(taskRow(activity, root, tasks.get(i)));
            }
            if (tasks.size() > 2) {
                TextView more = text(activity, "+ " + (tasks.size() - 2) + "개 더 있음", 13, MUTED, false);
                more.setPadding(dp(activity, 10), dp(activity, 5), dp(activity, 10), dp(activity, 5));
                more.setOnClickListener(v -> openTab(root, "할 일"));
                summary.addView(more);
            }
        }

        Button all = new Button(activity);
        all.setText("오늘 전체 보기");
        all.setTextSize(15);
        all.setTextColor(PRIMARY);
        all.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        all.setAllCaps(false);
        all.setGravity(Gravity.CENTER);
        all.setMinimumHeight(0);
        all.setMinimumWidth(0);
        all.setContentDescription("달력에서 오늘 일정 전체 보기");
        all.setBackground(rounded(activity, PRIMARY_LIGHT, 15, Color.rgb(204, 218, 246), 1));
        all.setOnClickListener(v -> openTab(root, "달력"));
        LinearLayout.LayoutParams allParams = new LinearLayout.LayoutParams(-1, dp(activity, 50));
        allParams.setMargins(0, dp(activity, 10), 0, 0);
        summary.addView(all, allParams);
    }

    private static View scheduleCard(Activity activity, View root, WorkItemRecord item) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        card.setBackground(rounded(activity, PRIMARY_LIGHT, 16, Color.rgb(204, 218, 246), 1));
        card.setOnClickListener(v -> openTab(root, "달력"));
        card.setContentDescription("다음 일정 " + safe(item.title));
        card.addView(text(activity, "다음 일정", 12, PRIMARY, true));

        String prefix = safe(item.time).isEmpty() ? "" : displayTime(item.time) + "  ";
        TextView title = text(activity,
                prefix + emptyDefault(item.title, "제목 없는 일정"), 17, TEXT, true);
        title.setPadding(0, dp(activity, 4), 0, 0);
        title.setMaxLines(2);
        card.addView(title);
        return card;
    }

    private static View taskRow(Activity activity, View root, WorkItemRecord item) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 8), dp(activity, 7), dp(activity, 8), dp(activity, 7));
        row.setOnClickListener(v -> openTab(root, "할 일"));
        row.setContentDescription("할 일 " + safe(item.title));

        TextView mark = text(activity, "○", 20, Color.rgb(234, 120, 35), false);
        mark.setGravity(Gravity.CENTER);
        row.addView(mark, new LinearLayout.LayoutParams(dp(activity, 30), dp(activity, 40)));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(activity, emptyDefault(item.title, "제목 없는 할 일"), 15, TEXT, false);
        title.setMaxLines(1);
        texts.addView(title);
        String due = dueLabel(item);
        if (!due.isEmpty()) {
            TextView dueText = text(activity, due, 12, MUTED, false);
            dueText.setPadding(0, dp(activity, 2), 0, 0);
            texts.addView(dueText);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private static WorkItemRecord findNearestSchedule(List<WorkItemRecord> schedules) {
        if (schedules.isEmpty()) return null;
        LocalTime now = LocalTime.now();
        for (WorkItemRecord item : schedules) {
            LocalTime time = parseTime(item.time);
            if (time != null && !time.isBefore(now)) return item;
        }
        return schedules.get(0);
    }

    private static boolean occurs(WorkItemRecord item, LocalDate target) {
        LocalDate start = parseDate(item.date);
        if (start == null) return false;
        if (start.equals(target)) return true;
        if (target.isBefore(start) || "NONE".equals(safe(item.repeatType))) return false;

        LocalDate end = parseDate(item.repeatEndDate);
        if (end != null && target.isAfter(end)) return false;

        switch (safe(item.repeatType)) {
            case "DAILY": return true;
            case "WEEKLY": return start.getDayOfWeek() == target.getDayOfWeek();
            case "MONTHLY": return start.getDayOfMonth() == target.getDayOfMonth();
            case "WEEKDAYS":
                DayOfWeek day = target.getDayOfWeek();
                return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
            default: return false;
        }
    }

    private static String dateSortKey(WorkItemRecord item) {
        return safe(item.date).isEmpty() ? "9999-12-31" : safe(item.date);
    }

    private static String timeSortKey(WorkItemRecord item) {
        return safe(item.time).isEmpty() ? "99:99" : safe(item.time);
    }

    private static String dueLabel(WorkItemRecord item) {
        LocalDate date = parseDate(item.date);
        if (date == null) return "";
        LocalDate today = LocalDate.now();
        String label;
        if (date.isBefore(today)) label = "기한 지남";
        else if (date.equals(today)) label = "오늘까지";
        else if (date.equals(today.plusDays(1))) label = "내일까지";
        else label = date.getMonthValue() + "월 " + date.getDayOfMonth() + "일까지";
        if (!safe(item.time).isEmpty()) label += " · " + displayTime(item.time);
        return label;
    }

    private static String displayTime(String value) {
        LocalTime time = parseTime(value);
        if (time == null) return value;
        String period = time.getHour() < 12 ? "오전" : "오후";
        int hour = time.getHour() % 12;
        if (hour == 0) hour = 12;
        return String.format(java.util.Locale.KOREA,
                "%s %d:%02d", period, hour, time.getMinute());
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(safe(value), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(safe(value), DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /** 기존 통계·최근 메모·중복 미리보기는 홈에서만 숨깁니다. */
    private static void hideLegacyHomeContent(
            LinearLayout page, View quickCard, TextView dateLabel, LinearLayout summary) {
        for (int i = 0; i < page.getChildCount(); i++) {
            View child = page.getChildAt(i);
            boolean keep = child == quickCard || child == dateLabel || child == summary
                    || SPACER_TAG.equals(child.getTag());
            child.setVisibility(keep ? View.VISIBLE : View.GONE);
        }
    }

    private static void ensureBottomSpacer(Activity activity, LinearLayout page) {
        if (findTagged(page, SPACER_TAG) != null) return;
        View spacer = new View(activity);
        spacer.setTag(SPACER_TAG);
        page.addView(spacer, new LinearLayout.LayoutParams(-1, dp(activity, 94)));
    }

    /** 설정과 동기화 버튼은 기능은 유지하고 시각적 비중만 낮춥니다. */
    private static void simplifyHeaderActions(View root) {
        for (Button button : findButtons(root)) {
            String text = textOf(button);
            if (!("⚙".equals(text) || "↻".equals(text) || "⟳".equals(text))) continue;
            button.setTextSize(17);
            button.setAlpha(0.82f);
            ViewGroup.LayoutParams params = button.getLayoutParams();
            if (params != null) {
                params.width = Math.max(params.width, dp(button, 44));
                params.height = Math.max(params.height, dp(button, 44));
                button.setLayoutParams(params);
            }
        }
    }

    private static void openTab(View root, String tabName) {
        for (TextView view : findTextViews(root)) {
            String text = textOf(view);
            if (tabName.equals(String.valueOf(view.getTag())) || text.endsWith("\n" + tabName)) {
                view.performClick();
                return;
            }
        }
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

    private static List<TextView> findTextViews(View root) {
        List<TextView> result = new ArrayList<>();
        collect(root, result, null);
        return result;
    }

    private static List<Button> findButtons(View root) {
        List<Button> result = new ArrayList<>();
        collect(root, null, result);
        return result;
    }

    private static void collect(View view, List<TextView> texts, List<Button> buttons) {
        if (view == null) return;
        if (texts != null && view instanceof TextView) texts.add((TextView) view);
        if (buttons != null && view instanceof Button) buttons.add((Button) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collect(group.getChildAt(i), texts, buttons);
        }
    }

    private static TextView text(Activity activity, String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private static LinearLayout.LayoutParams matchMargins(
            Activity activity, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(activity, left), dp(activity, top),
                dp(activity, right), dp(activity, bottom));
        return params;
    }

    private static GradientDrawable rounded(
            Activity activity, int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(activity, radius));
        if (strokeWidth > 0) drawable.setStroke(dp(activity, strokeWidth), strokeColor);
        return drawable;
    }

    private static String textOf(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String emptyDefault(String value, String fallback) {
        return safe(value).trim().isEmpty() ? fallback : value.trim();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}