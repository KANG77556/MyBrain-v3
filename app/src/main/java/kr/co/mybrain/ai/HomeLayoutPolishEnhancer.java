package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Galaxy S24 Ultra 실기기 검증에서 확인된 홈 화면 하단 겹침을 보정합니다.
 * 화면 구조는 유지하고 중앙 추가 버튼, 스크롤 여백, 타일 높이만 조정합니다.
 */
final class HomeLayoutPolishEnhancer {
    private HomeLayoutPolishEnhancer() { }

    static void install(Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }
            @Override public void onActivityStarted(Activity activity) { }

            @Override
            public void onActivityResumed(Activity activity) {
                if (!(activity instanceof RedesignedMainActivity)) return;
                View root = activity.findViewById(android.R.id.content);
                if (root == null) return;
                root.post(() -> tune(activity, root));
            }

            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
    }

    private static void tune(Activity activity, View view) {
        if (view instanceof Button) tuneButton(activity, (Button) view);
        if (view instanceof ScrollView && containsText(view, "오늘 요약")) {
            ScrollView scroll = (ScrollView) view;
            scroll.setClipToPadding(false);
            if (scroll.getChildCount() > 0) {
                View child = scroll.getChildAt(0);
                child.setPadding(child.getPaddingLeft(), child.getPaddingTop(),
                        child.getPaddingRight(), dp(activity, 176));
            }
        }
        if (view instanceof TextView) tuneTile(activity, (TextView) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) tune(activity, group.getChildAt(i));
        }
    }

    private static void tuneButton(Activity activity, Button button) {
        String description = String.valueOf(button.getContentDescription());
        ViewGroup.LayoutParams raw = button.getLayoutParams();
        if ("새 기록 작성".equals(description) && raw instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) raw;
            params.width = dp(activity, 60);
            params.height = dp(activity, 60);
            params.bottomMargin = dp(activity, 80);
            button.setLayoutParams(params);
        } else if ("설정 열기".equals(description) && raw != null) {
            raw.width = dp(activity, 44);
            raw.height = dp(activity, 44);
            button.setLayoutParams(raw);
        }
    }

    private static void tuneTile(Activity activity, TextView tile) {
        String name = String.valueOf(tile.getContentDescription());
        ViewGroup.LayoutParams params = tile.getLayoutParams();
        if (params == null) return;
        if (isQuickTile(name)) {
            params.height = dp(activity, 74);
            tile.setLayoutParams(params);
        } else if (isRecordTile(name)) {
            params.height = dp(activity, 82);
            tile.setLayoutParams(params);
        }
    }

    private static boolean isQuickTile(String value) {
        return "메모 작성".equals(value) || "할 일 추가".equals(value)
                || "일정 추가".equals(value) || "D-Day 추가".equals(value);
    }

    private static boolean isRecordTile(String value) {
        return "전체 기록".equals(value) || "메모".equals(value) || "할 일".equals(value)
                || "일정(캘린더)".equals(value) || "D-Day".equals(value) || "제출".equals(value);
    }

    private static boolean containsText(View view, String text) {
        if (view instanceof TextView && String.valueOf(((TextView) view).getText()).contains(text)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsText(group.getChildAt(i), text)) return true;
            }
        }
        return false;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
