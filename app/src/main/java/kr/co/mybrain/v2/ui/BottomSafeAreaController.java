package kr.co.mybrain.v2.ui;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 동적으로 추가되는 홈 카드와 마지막 버튼이 시스템 내비게이션 바 아래에 가려지지 않도록
 * 화면 구성이 끝난 뒤 스크롤 콘텐츠의 하단 여백을 다시 보정합니다.
 */
public final class BottomSafeAreaController {
    private static volatile boolean installed;

    private BottomSafeAreaController() {}

    public static void install(Application application) {
        if (installed) return;
        synchronized (BottomSafeAreaController.class) {
            if (installed) return;
            installed = true;
            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity activity, Bundle state) {
                    schedule(activity);
                }

                @Override public void onActivityStarted(Activity activity) {}

                @Override public void onActivityResumed(Activity activity) {
                    schedule(activity);
                }

                @Override public void onActivityPaused(Activity activity) {}
                @Override public void onActivityStopped(Activity activity) {}
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
                @Override public void onActivityDestroyed(Activity activity) {}
            });
        }
    }

    private static void schedule(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> apply(activity));
        decor.postDelayed(() -> apply(activity), 220L);
        decor.postDelayed(() -> apply(activity), 760L);
    }

    public static void apply(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(content);
        int navigationBottomPx = 0;
        if (insets != null) {
            Insets navigation = insets.getInsets(
                    WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.systemGestures());
            navigationBottomPx = Math.max(0, navigation.bottom);
        }

        float density = Math.max(1f, activity.getResources().getDisplayMetrics().density);
        int navigationBottomDp = Math.round(navigationBottomPx / density);
        boolean oneHandMode = UiPreferences.load(activity).oneHandMode;
        int requiredBottomPx = AppUi.dp(activity,
                BottomSafeAreaPolicy.requiredBottomDp(navigationBottomDp, oneHandMode));
        applyToTree(content, requiredBottomPx);
    }

    private static void applyToTree(View view, int requiredBottomPx) {
        if (view instanceof ScrollView) {
            ScrollView scroll = (ScrollView) view;
            scroll.setClipToPadding(false);
            if (scroll.getChildCount() > 0) {
                View child = scroll.getChildAt(0);
                int bottom = Math.max(child.getPaddingBottom(), requiredBottomPx);
                if (bottom != child.getPaddingBottom()) {
                    child.setPadding(child.getPaddingLeft(), child.getPaddingTop(),
                            child.getPaddingRight(), bottom);
                    child.requestLayout();
                }
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyToTree(group.getChildAt(i), requiredBottomPx);
            }
        }
    }
}
