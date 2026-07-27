package kr.co.mybrain.v2.reminder;

import android.content.Context;

import kr.co.mybrain.v2.data.WorkItemRepository;

/** 저장된 미래 알림을 앱 시작·업데이트·재부팅 후 다시 등록합니다. */
public final class ReminderRescheduler {
    public interface Callback { void onComplete(int scheduledCount); }

    private ReminderRescheduler() {}

    public static void rescheduleAll(Context context, String reason, Callback callback) {
        Context appContext = context.getApplicationContext();
        ReminderNotifications.ensureChannel(appContext);
        WorkItemRepository.getInstance(appContext).getAll(items -> {
            int count = 0;
            long now = System.currentTimeMillis();
            if (items != null) {
                for (kr.co.mybrain.v2.data.WorkItemEntity item : items) {
                    if (item == null || item.reminderAt == null || item.reminderAt <= now
                            || item.completed || item.deletedAt != null) continue;
                    ReminderScheduleResult result = ReminderScheduler.schedule(appContext, item, reason);
                    if (result.alarmScheduled()) count++;
                }
            }
            ReminderAuditStore.recordReschedule(appContext, count);
            if (callback != null) callback.onComplete(count);
        });
    }
}
