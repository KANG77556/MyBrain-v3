package kr.co.mybrain.ai;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 홈 화면에 오늘의 일정과 미완료 할 일을 표시하는 위젯입니다.
 * 할 일 줄을 누르면 앱을 열지 않고 바로 완료 처리합니다.
 */
public class TodayWidgetProvider extends AppWidgetProvider {
    private static final String PREFS = "mybrain_data";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 5;

    private static final String ACTION_COMPLETE_TASK =
            "kr.co.mybrain.ai.action.COMPLETE_WIDGET_TASK";
    private static final String EXTRA_SOURCE_INDEX = "sourceIndex";

    private static final int[] ITEM_VIEW_IDS = {
            R.id.widget_item_1,
            R.id.widget_item_2,
            R.id.widget_item_3,
            R.id.widget_item_4,
            R.id.widget_item_5
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, manager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_COMPLETE_TASK.equals(intent.getAction())) {
            int sourceIndex = intent.getIntExtra(EXTRA_SOURCE_INDEX, -1);
            completeTask(context, sourceIndex);
        }
    }

    /** 앱 내부 데이터가 바뀌었을 때 설치된 모든 위젯을 새로 그립니다. */
    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, TodayWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(provider);
        for (int id : ids) {
            updateWidget(context, manager, id);
        }
    }

    /** 한 개 위젯 화면을 구성합니다. */
    private static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today);
        List<WidgetItem> items = loadTodayItems(context);

        String today = new SimpleDateFormat("M월 d일 E요일", Locale.KOREA).format(new Date());
        views.setTextViewText(R.id.widget_date, today);
        views.setTextViewText(R.id.widget_count, "오늘 " + items.size() + "건");

        PendingIntent openPendingIntent = createOpenPendingIntent(context);
        views.setOnClickPendingIntent(R.id.widget_date, openPendingIntent);
        views.setOnClickPendingIntent(R.id.widget_count, openPendingIntent);
        views.setOnClickPendingIntent(R.id.widget_open, openPendingIntent);
        views.setOnClickPendingIntent(R.id.widget_empty, openPendingIntent);

        views.setViewVisibility(R.id.widget_empty, items.isEmpty() ? View.VISIBLE : View.GONE);

        for (int i = 0; i < ITEM_VIEW_IDS.length; i++) {
            int viewId = ITEM_VIEW_IDS[i];
            if (i >= items.size()) {
                views.setViewVisibility(viewId, View.GONE);
                views.setOnClickPendingIntent(viewId, null);
                continue;
            }

            WidgetItem item = items.get(i);
            views.setViewVisibility(viewId, View.VISIBLE);
            views.setTextViewText(viewId, makeItemText(item));

            if ("할 일".equals(item.type)) {
                views.setOnClickPendingIntent(
                        viewId,
                        createCompletePendingIntent(context, item.sourceIndex)
                );
            } else {
                views.setOnClickPendingIntent(viewId, openPendingIntent);
            }
        }

        manager.updateAppWidget(appWidgetId, views);
    }

    /** 위젯에서 앱을 여는 동작을 만듭니다. */
    private static PendingIntent createOpenPendingIntent(Context context) {
        Intent openIntent = new Intent(context, NotificationPermissionActivity.class);
        return PendingIntent.getActivity(
                context,
                31001,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    /** 특정 할 일을 완료 처리하는 방송 동작을 만듭니다. */
    private static PendingIntent createCompletePendingIntent(Context context, int sourceIndex) {
        Intent completeIntent = new Intent(context, TodayWidgetProvider.class);
        completeIntent.setAction(ACTION_COMPLETE_TASK);
        completeIntent.putExtra(EXTRA_SOURCE_INDEX, sourceIndex);

        return PendingIntent.getBroadcast(
                context,
                32000 + sourceIndex,
                completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    /** 저장된 원본 줄 번호를 기준으로 해당 할 일을 완료 상태로 바꿉니다. */
    private static void completeTask(Context context, int sourceIndex) {
        if (sourceIndex < 0) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_ITEMS, "");
        if (stored == null || stored.trim().isEmpty()) return;

        String[] lines = stored.split("\n", -1);
        if (sourceIndex >= lines.length) return;

        String[] values = lines[sourceIndex].split("\t", -1);
        if (values.length < 5 || !"할 일".equals(unescape(values[0]))) return;

        // 구버전 데이터에도 완료 필드를 안전하게 추가합니다.
        List<String> fields = new ArrayList<>();
        for (String value : values) fields.add(value);
        while (fields.size() < 6) fields.add("");
        if ("1".equals(fields.get(5))) return;
        fields.set(5, "1");
        lines[sourceIndex] = joinFields(fields);

        StringBuilder output = new StringBuilder();
        for (String line : lines) {
            if (output.length() > 0) output.append("\n");
            output.append(line);
        }

        prefs.edit().putString(KEY_ITEMS, output.toString()).apply();

        // 저장 감지기와 별개로 즉시 화면과 알림을 갱신합니다.
        AlarmScheduler.rescheduleAll(context.getApplicationContext());
        updateAll(context.getApplicationContext());
    }

    private static String joinFields(List<String> fields) {
        StringBuilder output = new StringBuilder();
        for (String field : fields) {
            if (output.length() > 0) output.append("\t");
            output.append(field);
        }
        return output.toString();
    }

    /** 저장된 자료 중 오늘 발생하는 일정과 미완료 할 일을 읽습니다. */
    private static List<WidgetItem> loadTodayItems(Context context) {
        List<WidgetItem> result = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_ITEMS, "");
        if (stored == null || stored.trim().isEmpty()) return result;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(new Date());
        String[] lines = stored.split("\n");
        for (int sourceIndex = 0; sourceIndex < lines.length; sourceIndex++) {
            String[] values = lines[sourceIndex].split("\t", -1);
            if (values.length < 5) continue;

            String type = unescape(values[0]);
            String title = unescape(values[1]);
            String date = unescape(values[2]);
            String time = unescape(values[3]);
            boolean completed = values.length >= 6 && "1".equals(values[5]);
            String repeatType = values.length >= 8 ? unescape(values[7]) : "반복 없음";
            String repeatEnd = values.length >= 9 ? unescape(values[8]) : "";

            if (!("일정".equals(type) || "할 일".equals(type))) continue;
            if ("할 일".equals(type) && completed) continue;
            if (!occursOn(date, repeatType, repeatEnd, today)) continue;

            result.add(new WidgetItem(type, title, time, sourceIndex));
            if (result.size() >= MAX_ITEMS) break;
        }
        return result;
    }

    /** 반복 조건을 반영해 특정 날짜에 항목이 발생하는지 확인합니다. */
    private static boolean occursOn(String startDate, String repeatType, String repeatEnd, String targetDate) {
        Date start = parseDate(startDate);
        Date target = parseDate(targetDate);
        if (start == null || target == null || target.before(start)) return false;

        Date end = parseDate(repeatEnd);
        if (end != null && target.after(end)) return false;
        if (startDate.equals(targetDate)) return true;

        Calendar startCal = Calendar.getInstance();
        Calendar targetCal = Calendar.getInstance();
        startCal.setTime(start);
        targetCal.setTime(target);

        if ("매일".equals(repeatType)) return true;
        if ("평일".equals(repeatType)) {
            int day = targetCal.get(Calendar.DAY_OF_WEEK);
            return day != Calendar.SATURDAY && day != Calendar.SUNDAY;
        }
        if ("매주".equals(repeatType)) {
            return startCal.get(Calendar.DAY_OF_WEEK) == targetCal.get(Calendar.DAY_OF_WEEK);
        }
        if ("매월".equals(repeatType)) {
            return startCal.get(Calendar.DAY_OF_MONTH) == targetCal.get(Calendar.DAY_OF_MONTH);
        }
        return false;
    }

    private static String makeItemText(WidgetItem item) {
        StringBuilder text = new StringBuilder();
        text.append("할 일".equals(item.type) ? "□ " : "● ");
        if (!item.time.isEmpty()) text.append(item.time).append("  ");
        text.append(item.title.isEmpty() ? "제목 없음" : item.title);
        if ("할 일".equals(item.type)) text.append("  (완료)");
        return text.toString();
    }

    private static Date parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
            format.setLenient(false);
            return format.parse(value);
        } catch (ParseException ignored) {
            return null;
        }
    }

    private static String unescape(String value) {
        return value.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    /** 위젯 표시 전용 간단한 자료 구조입니다. */
    private static class WidgetItem {
        final String type;
        final String title;
        final String time;
        final int sourceIndex;

        WidgetItem(String type, String title, String time, int sourceIndex) {
            this.type = type;
            this.title = title;
            this.time = time;
            this.sourceIndex = sourceIndex;
        }
    }
}
