package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Window;

/** 앱 전체 생명주기와 공통 UI 보정을 관리합니다. */
public class MyBrainApplication extends Application {
    private static final String PREFS = "mybrain_data";
    private static final String KEY_ITEMS = "items";

    private final SharedPreferences.OnSharedPreferenceChangeListener listener =
            (sharedPreferences, key) -> {
                if (KEY_ITEMS.equals(key)) {
                    AlarmScheduler.rescheduleAll(this);
                    TodayWidgetProvider.updateAll(this);
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        preferences.registerOnSharedPreferenceChangeListener(listener);

        UiUxEnhancer.install(this);
        UiSafeAreaEnhancer.install(this);
        SimpleUxEnhancer.install(this);
        AnalysisButtonEnhancer.install(this);
        VoiceAnalysisFlowEnhancer.install(this);
        HomeLayoutPolishEnhancer.install(this);

        // 1.10.6부터 이번주·다음주 및 시작~종료 시간을 미리보기와 저장에 함께 반영합니다.
        KoreanRangeAnalysisEnhancer.install(this);

        AlarmScheduler.rescheduleAll(this);
        TodayWidgetProvider.updateAll(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }
            @Override public void onActivityStarted(Activity activity) { }

            @Override
            public void onActivityResumed(Activity activity) {
                Window window = activity.getWindow();
                if (window == null) return;
                Window.Callback callback = window.getCallback();
                if (callback == null || KeyboardDismissWindowCallback.isInstalled(callback)) return;
                window.setCallback(new KeyboardDismissWindowCallback(activity, callback));
            }

            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
    }
}
