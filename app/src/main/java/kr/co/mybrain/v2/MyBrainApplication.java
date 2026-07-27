package kr.co.mybrain.v2;

import android.app.Application;
import android.content.Context;

import kr.co.mybrain.v2.reminder.ReminderNotifications;
import kr.co.mybrain.v2.reminder.ReminderRescheduler;
import kr.co.mybrain.v2.settings.AiBudgetNotifier;
import kr.co.mybrain.v2.ui.AiRunUiController;
import kr.co.mybrain.v2.ui.BottomSafeAreaController;
import kr.co.mybrain.v2.ui.CalendarCompactController;
import kr.co.mybrain.v2.ui.QuickEntryController;
import kr.co.mybrain.v2.ui.SaveIntegrityController;
import kr.co.mybrain.v2.ui.ScheduleConflictController;
import kr.co.mybrain.v2.ui.TodayProductivityController;
import kr.co.mybrain.v2.ui.UiConsistencyController;

/** 앱 전체 컨텍스트와 화면·AI·저장·알림 안정성 규칙을 초기화합니다. */
public class MyBrainApplication extends Application {
    private static Context appContext;

    @Override public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
        AiBudgetNotifier.createChannel(this);
        ReminderNotifications.ensureChannel(this);
        UiConsistencyController.install(this);
        BottomSafeAreaController.install(this);
        CalendarCompactController.install(this);
        TodayProductivityController.install(this);
        QuickEntryController.install(this);
        AiRunUiController.install(this);
        SaveIntegrityController.install(this);
        ScheduleConflictController.install(this);
        ReminderRescheduler.rescheduleAll(this, "APP_START", null);
    }

    public static Context appContext() {
        if (appContext == null) throw new IllegalStateException("앱 초기화가 완료되지 않았습니다.");
        return appContext;
    }
}
