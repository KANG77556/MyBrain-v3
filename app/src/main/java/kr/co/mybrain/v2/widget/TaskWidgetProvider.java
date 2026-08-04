package kr.co.mybrain.v2.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.List;

import kr.co.mybrain.v2.AdaptiveMainActivity;
import kr.co.mybrain.v2.R;
import kr.co.mybrain.v2.WorkItemListActivity;
import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.data.WorkItemRepository;
import kr.co.mybrain.v2.ui.TodayProductivityController;

/** 미완료 할 일과 빠른 추가 버튼을 제공하는 홈 위젯입니다. */
public final class TaskWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "kr.co.mybrain.v2.widget.TASK_REFRESH";

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
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, TaskWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        WorkItemRepository.getInstance(app).getOpenTasks(tasks -> update(app, manager, ids, tasks));
    }

    private static void update(Context context, AppWidgetManager manager, int[] ids, List<WorkItemEntity> tasks) {
        RemoteViews views = baseViews(context);
        views.setTextViewText(R.id.widget_task_summary, WidgetTextPolicy.taskSummary(tasks));
        views.setTextViewText(R.id.widget_task_lines, WidgetTextPolicy.taskLines(tasks));
        manager.updateAppWidget(ids, views);
    }

    private static RemoteViews baseViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_task);
        views.setOnClickPendingIntent(R.id.widget_task_root, activityIntent(context, WorkItemListActivity.class, 210));
        views.setOnClickPendingIntent(R.id.widget_task_add, quickIntent(context, 211));
        views.setOnClickPendingIntent(R.id.widget_task_refresh, refreshIntent(context, 212));
        return views;
    }

    private static PendingIntent activityIntent(Context context, Class<?> target, int code) {
        Intent intent = new Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent quickIntent(Context context, int code) {
        Intent intent = new Intent(context, AdaptiveMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(TodayProductivityController.EXTRA_QUICK_TYPE, WorkItemEntity.TYPE_TASK);
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent refreshIntent(Context context, int code) {
        Intent intent = new Intent(context, TaskWidgetProvider.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
