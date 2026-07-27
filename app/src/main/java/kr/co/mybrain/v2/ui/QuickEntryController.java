package kr.co.mybrain.v2.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import kr.co.mybrain.v2.data.WorkItemEntity;

/** 일정 화면의 빠른 입력 선택값을 홈 입력 화면에 전달합니다. */
public final class QuickEntryController {
    private static final String TARGET_ACTIVITY = "kr.co.mybrain.v2.AdaptiveMainActivity";
    private static volatile boolean installed;

    private QuickEntryController() {}

    public static void install(Application application) {
        if (installed) return;
        synchronized (QuickEntryController.class) {
            if (installed) return;
            installed = true;
            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) {
                    activity.getWindow().getDecorView().postDelayed(() -> apply(activity), 140L);
                }
                @Override public void onActivityStarted(@NonNull Activity activity) {}
                @Override public void onActivityResumed(@NonNull Activity activity) {
                    activity.getWindow().getDecorView().postDelayed(() -> apply(activity), 140L);
                }
                @Override public void onActivityPaused(@NonNull Activity activity) {}
                @Override public void onActivityStopped(@NonNull Activity activity) {}
                @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle state) {}
                @Override public void onActivityDestroyed(@NonNull Activity activity) {}
            });
        }
    }

    private static void apply(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (!TARGET_ACTIVITY.equals(activity.getClass().getName())) return;
        Intent intent = activity.getIntent();
        if (intent == null) return;
        String type = intent.getStringExtra(TodayProductivityController.EXTRA_QUICK_TYPE);
        if (type == null || type.trim().isEmpty()) return;
        String label = labelFor(type);
        Button target = findExactButton(activity.findViewById(android.R.id.content), label);
        if (target != null && target.isEnabled() && target.getVisibility() == View.VISIBLE) {
            intent.removeExtra(TodayProductivityController.EXTRA_QUICK_TYPE);
            target.performClick();
            target.requestFocus();
        }
    }

    static String labelFor(String type) {
        if (WorkItemEntity.TYPE_SCHEDULE.equals(type)) return "일정";
        if (WorkItemEntity.TYPE_TASK.equals(type)) return "할 일";
        return "메모";
    }

    private static Button findExactButton(View view, String text) {
        if (view instanceof Button && text.contentEquals(((Button) view).getText())) return (Button) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            Button found = findExactButton(group.getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }
}
