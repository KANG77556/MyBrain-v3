package kr.co.mybrain.ai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 휴대전화 재부팅 또는 앱 업데이트가 끝난 뒤 저장된 알림을 다시 예약합니다.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String action = intent.getAction();
        boolean shouldRestore = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);

        if (shouldRestore) {
            // 기존 일정·할 일 데이터를 읽어 다음 알림을 다시 계산합니다.
            AlarmScheduler.rescheduleAll(context.getApplicationContext());
        }
    }
}
