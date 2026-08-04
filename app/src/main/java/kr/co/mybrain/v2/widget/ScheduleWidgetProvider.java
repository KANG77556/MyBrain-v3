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
import kr.co.mybrain.v2.ui.TodayProductivityController;

/** 오늘 일정과 일정 빠른 추가를 제공하는 홈 위젯입니다. */
public final class ScheduleWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "kr.co.mybrain.v2.widget.SCHEDULE_REFRESH";

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        refreshAll(context);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) refreshAll(context);
    }

    public static void refreshAll(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, ScheduleWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        long from = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        WorkItemRepository.getInstance(app).getBetween(from, to, items -> update(app, manager, ids, items, zone));
    }

    private static void update(Context context, AppWidgetManager manager, int[] ids,
                               List<WorkItemEntity> items, ZoneId zone) {
        RemoteViews views = baseViews(context);
        views.setTextViewText(R.id.widget_schedule_summary, WidgetTextPolicy.scheduleSummary(items));
        views.setTextViewText(R.id.widget_schedule_lines, WidgetTextPolicy.scheduleLines(items, zone));
        manager.updateAppWidget(ids, views);
    }

    private static RemoteViews baseViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_schedule);
        views.setOnClickPendingIntent(R.id.widget_schedule_root, activityIntent(context, CalendarActivity.class, 220));
        views.setOnClickPendingIntent(R.id.widget_schedule_add, quickIntent(context, 221));
        views.setOnClickPendingIntent(R.id.widget_schedule_refresh, refreshIntent(context, 222));
        return views;
    }

    private static PendingIntent activityIntent(Context context, Class<?> target, int code) {
        Intent intent = new Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent quickIntent(Context context, int code) {
        Intent intent = new Intent(context, AdaptiveMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(TodayProductivityController.EXTRA_QUICK_TYPE, WorkItemEntity.TYPE_SCHEDULE);
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent refreshIntent(Context context, int code) {
        Intent intent = new Intent(context, ScheduleWidgetProvider.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
