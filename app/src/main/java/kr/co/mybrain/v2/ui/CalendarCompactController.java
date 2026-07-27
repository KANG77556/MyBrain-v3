package kr.co.mybrain.v2.ui;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 일정 화면에서 월 달력을 접어 상세 목록 공간을 확보합니다. */
public final class CalendarCompactController {
    private static final String TARGET_ACTIVITY = "kr.co.mybrain.v2.CalendarActivity";
    private static final String TOGGLE_TAG = "mybrain_calendar_compact_toggle";
    private static volatile boolean installed;

    private CalendarCompactController() {}

    public static void install(Application application) {
        if (installed) return;
        synchronized (CalendarCompactController.class) {
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
        if (calendar == null || !(calendar.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) calendar.getParent();
        if (findTaggedView(parent, TOGGLE_TAG) != null) return;

        Button toggle = AppUi.secondaryButton(activity, "달력 펼치기");
        toggle.setTag(TOGGLE_TAG);
        toggle.setContentDescription("월 달력 펼치기 또는 접기");
        UiPreferences preferences = UiPreferences.load(activity);
        int height = AppUi.dp(activity, CalendarCompactPolicy.toggleHeightDp(preferences.largeTouchTargets));
        ViewGroup.LayoutParams rawParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        if (parent instanceof LinearLayout) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, height);
            params.setMargins(0, AppUi.dp(activity, 8), 0, AppUi.dp(activity, 4));
            rawParams = params;
        }
        int index = parent.indexOfChild(calendar);
        parent.addView(toggle, Math.max(0, index), rawParams);

        boolean focused = activity.getIntent() != null
                && activity.getIntent().getLongExtra("kr.co.mybrain.v2.extra.HIGHLIGHT_ID", -1L) > 0L;
        boolean[] expanded = { !CalendarCompactPolicy.startCollapsed(AppUi.isTablet(activity), focused) };
        applyState(activity, calendar, toggle, expanded[0]);
        toggle.setOnClickListener(v -> {
            expanded[0] = !expanded[0];
            applyState(activity, calendar, toggle, expanded[0]);
        });
    }

    private static void applyState(Activity activity, CalendarView calendar, Button toggle, boolean expanded) {
        calendar.setVisibility(expanded ? View.VISIBLE : View.GONE);
        String selected = Instant.ofEpochMilli(calendar.getDate()).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREA));
        toggle.setText(CalendarCompactPolicy.toggleLabel(expanded, selected));
        toggle.setContentDescription(expanded ? "월 달력 접기" : selected + " 월 달력 펼치기");
        if (!expanded) toggle.requestFocus();
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