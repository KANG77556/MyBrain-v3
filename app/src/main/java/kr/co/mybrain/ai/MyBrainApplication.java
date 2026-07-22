package kr.co.mybrain.ai;

import android.app.Application;
import android.content.SharedPreferences;

/**
 * 앱 전체 생명주기를 관리합니다.
 * 일정 데이터가 변경되면 알림과 홈 화면 위젯을 자동으로 갱신합니다.
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
    }
}
