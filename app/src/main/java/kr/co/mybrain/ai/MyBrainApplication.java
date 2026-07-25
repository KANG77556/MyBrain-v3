package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Window;

/**
 * 앱 전체 생명주기를 관리합니다.
 * 일정 데이터가 변경되면 알림과 홈 화면 위젯을 자동으로 갱신하고,
 * 모든 Activity에 입력칸 밖 터치 시 키보드를 닫는 공통 동작을 설치합니다.
 */
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

        // 앱 실행 시 기존 알림과 홈 화면 위젯을 다시 복구합니다.
        AlarmScheduler.rescheduleAll(this);
        TodayWidgetProvider.updateAll(this);

        // 모든 화면에서 입력칸 밖을 누르면 키보드를 닫습니다.
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
