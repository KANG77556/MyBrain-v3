package kr.co.mybrain.v2.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import kr.co.mybrain.v2.AdaptiveMainActivity;
import kr.co.mybrain.v2.R;
import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.ui.QuickEntryController;
import kr.co.mybrain.v2.ui.TodayProductivityController;

/** 앱을 열자마자 메모 또는 음성 입력을 시작하는 빠른 메모 위젯입니다. */
public final class QuickMemoWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_memo);
        views.setOnClickPendingIntent(R.id.widget_quick_memo_root, quickIntent(context, false, 201));
        views.setOnClickPendingIntent(R.id.widget_quick_memo_voice, quickIntent(context, true, 202));
        manager.updateAppWidget(appWidgetIds, views);
    }

    private static PendingIntent quickIntent(Context context, boolean voice, int requestCode) {
        Intent intent = new Intent(context, AdaptiveMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(TodayProductivityController.EXTRA_QUICK_TYPE, WorkItemEntity.TYPE_MEMO);
        if (voice) intent.putExtra(QuickEntryController.EXTRA_START_VOICE, true);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
