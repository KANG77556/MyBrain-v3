package kr.co.mybrain.v2;

import android.app.Application;
import android.content.Context;

/** 앱 전체에서 네트워크·비용 정책을 안전하게 확인하기 위한 Application 컨텍스트입니다. */
public class MyBrainApplication extends Application {
    private static Context appContext;

    @Override public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
    }

    public static Context appContext() {
        if (appContext == null) throw new IllegalStateException("앱 초기화가 완료되지 않았습니다.");
        return appContext;
    }
}
