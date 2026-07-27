package kr.co.mybrain.v2.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import kr.co.mybrain.v2.AdaptiveMainActivity;
import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.data.WorkItemRepository;

/** 일정 화면에 오늘 요약과 일정·할 일·메모 빠른 입력을 추가합니다. */
public final class TodayProductivityController {
    public static final String EXTRA_QUICK_TYPE = "kr.co.mybrain.v2.extra.QUICK_TYPE";
    private static final String TARGET_ACTIVITY = "kr.co.mybrain.v2.CalendarActivity";
    private static final String DASHBOARD_TAG = "mybrain_today_dashboard";
    private static final String QUICK_ROW_TAG = "mybrain_quick_entry_row";
    private static final String COMPACT_TOGGLE_TAG = "mybrain_calendar_compact_toggle";
    private static volatile boolean installed;

    private TodayProductivityController() {}

    public static void install(Application application) {
        if (installed) return;
        synchronized (TodayProductivityController.class) {
            if (installed) return;
            installed = true;
            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) {
                    activity.getWindow().getDecorView().post(() -> attach(activity));
                }
                @Override public void onActivityStarted(@NonNull Activity activity) {}
                @Override public void onActivityResumed(@NonNull Activity activity) {
                    activity.getWindow().getDecorView().post(() -> attach(activity));
                }
                @Override public void onActivityPaused(@NonNull Activity activity) {}
                @Override public void onActivityStopped(@NonNull Activity activity) {}
                @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle state) {}
                @Override public void onActivityDestroyed(@NonNull Activity activity) {}
            });
        }
    }

    private static void attach(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (!TARGET_ACTIVITY.equals(activity.getClass().getName())) return;
        View content = activity.findViewById(android.R.id.content);
        CalendarView calendar = findCalendar(content);
        if (calendar == null || !(calendar.getParent() instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) calendar.getParent();

        LinearLayout dashboard = (LinearLayout) findTaggedView(root, DASHBOARD_TAG);
        if (dashboard == null) {
            dashboard = buildDashboard(activity);
            View toggle = findTaggedView(root, COMPACT_TOGGLE_TAG);
            int index = toggle == null ? Math.max(0, root.indexOfChild(calendar)) : root.indexOfChild(toggle);
            root.addView(dashboard, Math.max(0, index), AppUi.cardParams(activity));
        }
        refreshDashboard(activity, dashboard);

        if (findTaggedView(root, QUICK_ROW_TAG) == null) {
            Button addButton = findButtonContaining(root, "새 항목 추가");
            if (addButton != null && addButton.getParent() == root) {
                LinearLayout quickRow = buildQuickRow(activity);
                quickRow.setTag(QUICK_ROW_TAG);
                int index = root.indexOfChild(addButton);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, AppUi.dp(activity, 50));
                params.setMargins(0, AppUi.dp(activity, 8), 0, 0);
                root.addView(quickRow, Math.max(0, index), params);
                addButton.setVisibility(View.GONE);
            }
        }
    }

    private static LinearLayout buildDashboard(Activity activity) {
        LinearLayout card = AppUi.card(activity);
        card.setTag(DASHBOARD_TAG);
        TextView title = AppUi.text(activity, "오늘 요약", 18, AppUi.TEXT, true);
        card.addView(title);

        LinearLayout stats = new LinearLayout(activity);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        stats.setWeightSum(3f);
        stats.setTag("stats");
        stats.addView(stat(activity, "일정", "0개"), new LinearLayout.LayoutParams(0, AppUi.dp(activity, 62), 1f));
        stats.addView(stat(activity, "오늘 할 일", "0개"), new LinearLayout.LayoutParams(0, AppUi.dp(activity, 62), 1f));
        stats.addView(stat(activity, "미완료", "0개"), new LinearLayout.LayoutParams(0, AppUi.dp(activity, 62), 1f));
        card.addView(stats);

        TextView next = AppUi.body(activity, "다음 일정 확인 중…");
        next.setTag("next");
        next.setPadding(0, AppUi.dp(activity, 4), 0, 0);
        card.addView(next);
        return card;
    }

    private static LinearLayout stat(Activity activity, String label, String value) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView valueView = AppUi.text(activity, value, 19, AppUi.PRIMARY, true);
        valueView.setTag("value");
        box.addView(valueView);
        box.addView(AppUi.text(activity, label, 12, AppUi.SUBTEXT, false));
        return box;
    }

    private static void refreshDashboard(Activity activity, LinearLayout dashboard) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        long from = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        WorkItemRepository repository = WorkItemRepository.getInstance(activity);
        repository.getBetween(from, to, todayItems -> repository.getOpenTasks(openTasks ->
                activity.runOnUiThread(() -> applySummary(dashboard, todayItems, openTasks))));
    }

    private static void applySummary(LinearLayout dashboard, List<WorkItemEntity> todayItems,
                                     List<WorkItemEntity> openTasks) {
        long now = System.currentTimeMillis();
        TodayDashboardPolicy.Summary summary = TodayDashboardPolicy.summarize(todayItems, openTasks, now);
        View statsView = findTaggedView(dashboard, "stats");
        if (statsView instanceof LinearLayout) {
            LinearLayout stats = (LinearLayout) statsView;
            setStat(stats, 0, summary.scheduleCount + "개");
            setStat(stats, 1, summary.todayTaskCount + "개");
            setStat(stats, 2, summary.unfinishedTaskCount + "개");
        }
        View nextView = findTaggedView(dashboard, "next");
        if (nextView instanceof TextView) {
            String time = TodayDashboardPolicy.nextTimeLabel(summary.nextStart, now);
            String title = summary.nextTitle == null || summary.nextTitle.trim().isEmpty()
                    ? "" : " · " + summary.nextTitle.trim();
            ((TextView) nextView).setText("다음 일정  " + time + title);
        }
    }

    private static void setStat(LinearLayout stats, int index, String value) {
        if (index < 0 || index >= stats.getChildCount()) return;
        View box = stats.getChildAt(index);
        View valueView = findTaggedView(box, "value");
        if (valueView instanceof TextView) ((TextView) valueView).setText(value);
    }

    private static LinearLayout buildQuickRow(Activity activity) {
        LinearLayout row = new LinearLayout(activity);
        row.setWeightSum(3f);
        addQuickButton(activity, row, "＋ 일정", WorkItemEntity.TYPE_SCHEDULE, true, false);
        addQuickButton(activity, row, "✓ 할 일", WorkItemEntity.TYPE_TASK, false, false);
        addQuickButton(activity, row, "메모", WorkItemEntity.TYPE_MEMO, false, true);
        return row;
    }

    private static void addQuickButton(Activity activity, LinearLayout row, String label,
                                       String type, boolean first, boolean last) {
        Button button = AppUi.secondaryButton(activity, label);
        button.setOnClickListener(v -> openQuickEntry(activity, type));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1f);
        params.setMargins(first ? 0 : AppUi.dp(activity, 4), 0,
                last ? 0 : AppUi.dp(activity, 4), 0);
        row.addView(button, params);
    }

    private static void openQuickEntry(Activity activity, String type) {
        Intent intent = new Intent(activity, AdaptiveMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_QUICK_TYPE, type);
        activity.startActivity(intent);
    }

    private static CalendarView findCalendar(View view) {
        if (view instanceof CalendarView) return (CalendarView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            CalendarView found = findCalendar(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static Button findButtonContaining(View view, String text) {
        if (view instanceof Button && ((Button) view).getText().toString().contains(text)) return (Button) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            Button found = findButtonContaining(group.getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private static View findTaggedView(View view, String tag) {
        if (tag.equals(view.getTag())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findTaggedView(group.getChildAt(i), tag);
            if (found != null) return found;
        }
        return null;
    }
}
