package kr.co.mybrain.v2;

import android.app.Application;
import android.content.Context;

import kr.co.mybrain.v2.settings.AiBudgetNotifier;

/** 앱 전체 컨텍스트를 보관하고 알림 채널을 초기화합니다. */
public class MyBrainApplication extends Application {
    private static Context appContext;

    @Override public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
        AiBudgetNotifier.createChannel(this);
    }

    public static Context appContext() {
        if (appContext == null) throw new IllegalStateException("앱 초기화가 완료되지 않았습니다.");
        return appContext;
    }
}
