package kr.co.mybrain.v2.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import kr.co.mybrain.v2.data.WorkItemRepository;

/** 기기 재부팅 또는 앱 교체 후 저장된 미래 알림을 다시 등록합니다. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        WorkItemRepository.getInstance(context).getAll(items -> {
            long now = System.currentTimeMillis();
            for (kr.co.mybrain.v2.data.WorkItemEntity item : items) {
                if (item.reminderAt != null && item.reminderAt > now && !item.completed) {
                    ReminderScheduler.schedule(context, item);
                }
            }
        });
    }
}