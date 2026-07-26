package kr.co.mybrain.v2.reminder;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import kr.co.mybrain.v2.data.WorkItemRepository;

/** 알림의 완료·미루기 동작을 처리합니다. */
public final class ReminderActionReceiver extends BroadcastReceiver {
    public static final String ACTION_COMPLETE = "kr.co.mybrain.v2.COMPLETE_REMINDER";
    public static final String ACTION_SNOOZE = "kr.co.mybrain.v2.SNOOZE_REMINDER";

    @Override
    public void onReceive(Context context, Intent intent) {
        long itemId = intent.getLongExtra("item_id", -1L);
        if (itemId < 0) return;

        WorkItemRepository repository = WorkItemRepository.getInstance(context);
        if (ACTION_COMPLETE.equals(intent.getAction())) {
            repository.advanceRecurrence(itemId, advanced -> {
                if (!advanced) repository.setCompleted(itemId, true, ignored -> { });
                cancelNotification(context, itemId);
            });
        } else if (ACTION_SNOOZE.equals(intent.getAction())) {
            repository.getById(itemId, item -> {
                if (item == null) return;
                item.reminderAt = System.currentTimeMillis() + 10 * 60 * 1000L;
                repository.update(item, ignored -> cancelNotification(context, itemId));
            });
        }
    }

    private void cancelNotification(Context context, long itemId) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel((int) itemId);
    }
}
