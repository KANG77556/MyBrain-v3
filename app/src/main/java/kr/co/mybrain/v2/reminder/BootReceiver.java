package kr.co.mybrain.v2.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 기기 재부팅·앱 업데이트·시간대 변경 후 미래 알림을 다시 등록합니다. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)) return;

        PendingResult pendingResult = goAsync();
        ReminderRescheduler.rescheduleAll(context, action, ignored -> pendingResult.finish());
    }
}
