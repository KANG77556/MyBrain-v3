package kr.co.mybrain.v2.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import kr.co.mybrain.v2.AdaptiveMainActivity;
import kr.co.mybrain.v2.CalendarActivity;
import kr.co.mybrain.v2.R;
import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.data.WorkItemRepository;
import kr.co.mybrain.v2.ui.QuickEntryController;
import kr.co.mybrain.v2.ui.TodayProductivityController;

/** 홈 화면에서 오늘 일정과 미완료 할 일을 보여주는 위젯입니다. */
public final class TodayWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "kr.co.mybrain.v2.widget.REFRESH";

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        refreshAll(context);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) refreshAll(context);
    }

    public static void refreshAll(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, TodayWidgetProvider.class));
        if (ids == null || ids.length == 0) return;

        RemoteViews loading = baseViews(app);
        loading.setTextViewText(R.id.widget_summary, "오늘 정보를 불러오는 중…");
        loading.setTextViewText(R.id.widget_timeline, "잠시만 기다려 주세요.");
        manager.updateAppWidget(ids, loading);

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        long from = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        WorkItemRepository repository = WorkItemRepository.getInstance(app);
        repository.getBetween(from, to, todayItems ->
                repository.getOpenTasks(openTasks -> updateViews(app, manager, ids, todayItems, openTasks, zone)));
    }

    private static void updateViews(Context context, AppWidgetManager manager, int[] ids,
                                    List<WorkItemEntity> today, List<WorkItemEntity> tasks, ZoneId zone) {
        RemoteViews views = baseViews(context);
        views.setTextViewText(R.id.widget_summary, TodayWidgetPolicy.summary(today, tasks));
        views.setTextViewText(R.id.widget_timeline, TodayWidgetPolicy.timeline(today, zone));
        manager.updateAppWidget(ids, views);
    }

    private static RemoteViews baseViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today);
        views.setOnClickPendingIntent(R.id.widget_root, activityIntent(context, CalendarActivity.class, 100));
        views.setOnClickPendingIntent(R.id.widget_add_schedule,
                quickIntent(context, WorkItemEntity.TYPE_SCHEDULE, false, 101));
        views.setOnClickPendingIntent(R.id.widget_add_task,
                quickIntent(context, WorkItemEntity.TYPE_TASK, false, 102));
        views.setOnClickPendingIntent(R.id.widget_voice,
                quickIntent(context, null, true, 103));
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context));
        return views;
    }

    private static PendingIntent activityIntent(Context context, Class<?> target, int requestCode) {
        Intent intent = new Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent quickIntent(Context context, String type, boolean voice, int requestCode) {
        Intent intent = new Intent(context, AdaptiveMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (type != null) intent.putExtra(TodayProductivityController.EXTRA_QUICK_TYPE, type);
        if (voice) intent.putExtra(QuickEntryController.EXTRA_START_VOICE, true);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent refreshIntent(Context context) {
        Intent intent = new Intent(context, TodayWidgetProvider.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(context, 104, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
