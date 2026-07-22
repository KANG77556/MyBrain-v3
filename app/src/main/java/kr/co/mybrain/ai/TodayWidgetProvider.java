package kr.co.mybrain.ai;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
 */
public class TodayWidgetProvider extends AppWidgetProvider {
    private static final String PREFS = "mybrain_data";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 5;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, manager, appWidgetId);
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
        views.setTextViewText(R.id.widget_items, makeItemText(items));

        Intent openIntent = new Intent(context, NotificationPermissionActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                31001,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent);

        manager.updateAppWidget(appWidgetId, views);
    }

    /** 저장된 자료 중 오늘 발생하는 일정과 미완료 할 일을 읽습니다. */
    private static List<WidgetItem> loadTodayItems(Context context) {
        List<WidgetItem> result = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_ITEMS, "");
        if (stored == null || stored.trim().isEmpty()) return result;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(new Date());
        for (String line : stored.split("\n")) {
            String[] values = line.split("\t", -1);
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

            result.add(new WidgetItem(type, title, time));
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

    private static String makeItemText(List<WidgetItem> items) {
        if (items.isEmpty()) return "오늘 예정된 일정이나 할 일이 없습니다.";

        StringBuilder text = new StringBuilder();
        for (WidgetItem item : items) {
            if (text.length() > 0) text.append("\n");
            String mark = "할 일".equals(item.type) ? "□" : "●";
            text.append(mark).append(" ");
            if (!item.time.isEmpty()) text.append(item.time).append("  ");
            text.append(item.title.isEmpty() ? "제목 없음" : item.title);
        }
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

        WidgetItem(String type, String title, String time) {
            this.type = type;
            this.title = title;
            this.time = time;
        }
    }
}
