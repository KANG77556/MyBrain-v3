package kr.co.mybrain.ai;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 기존 업무 저장 문자열을 읽고 쓰는 공통 저장소입니다.
 * 이전 10개 필드 자료와 호환하며 11번째 필드에 종료 시각을 저장합니다.
 */
public final class WorkItemStore {
    public static final String PREFS = "mybrain_data";
    public static final String KEY_ITEMS = "items";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private WorkItemStore() { }

    public static List<WorkItemRecord> load(Context context) {
        List<WorkItemRecord> result = new ArrayList<>();
        String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ITEMS, "");
        if (stored == null || stored.isEmpty()) return result;

        for (String line : stored.split("\n")) {
            String[] values = line.split("\t", -1);
            if (values.length < 5) continue;
            WorkItemRecord item = new WorkItemRecord();
            item.type = unescape(values[0]);
            item.title = unescape(values[1]);
            item.date = unescape(values[2]);
            item.time = unescape(values[3]);
            item.original = unescape(values[4]);
            item.completed = values.length >= 6 && "1".equals(values[5]);
            item.reminderMinutes = parseInt(values.length >= 7 ? values[6] : "0", 0);
            item.repeatType = values.length >= 8 && !values[7].isEmpty() ? values[7] : "NONE";
            item.repeatEndDate = values.length >= 9 ? unescape(values[8]) : "";
            item.colorValue = values.length >= 10 && !values[9].isEmpty() ? values[9] : "DEFAULT";
            item.endTime = values.length >= 11 ? unescape(values[10]) : "";
            result.add(item);
        }
        return result;
    }

    public static void save(Context context, List<WorkItemRecord> items) {
        StringBuilder output = new StringBuilder();
        for (WorkItemRecord item : items) {
            if (output.length() > 0) output.append("\n");
            output.append(escape(item.type)).append("\t")
                    .append(escape(item.title)).append("\t")
                    .append(escape(item.date)).append("\t")
                    .append(escape(item.time)).append("\t")
                    .append(escape(item.original)).append("\t")
                    .append(item.completed ? "1" : "0").append("\t")
                    .append(item.reminderMinutes).append("\t")
                    .append(item.repeatType == null ? "NONE" : item.repeatType).append("\t")
                    .append(escape(item.repeatEndDate)).append("\t")
                    .append(item.colorValue == null ? "DEFAULT" : item.colorValue).append("\t")
                    .append(escape(item.endTime));
        }
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit().putString(KEY_ITEMS, output.toString()).apply();
        AlarmScheduler.rescheduleAll(context);
        TodayWidgetProvider.updateAll(context.getApplicationContext());
    }

    public static int findBestIndex(List<WorkItemRecord> items, String title, String dateText) {
        int titleOnly = -1;
        for (int i = 0; i < items.size(); i++) {
            WorkItemRecord item = items.get(i);
            if (!safe(item.title).equals(safe(title))) continue;
            if (titleOnly < 0) titleOnly = i;
            String dateToken = displayDateToken(item.date);
            if (dateText != null && !dateText.isEmpty()
                    && (!item.date.isEmpty() && dateText.contains(item.date)
                    || !dateToken.isEmpty() && dateText.contains(dateToken))) {
                return i;
            }
        }
        return titleOnly;
    }

    /** 기존 호출과 호환되는 동일 시작 시각 검사입니다. */
    public static boolean hasScheduleConflict(List<WorkItemRecord> items, int ignoreIndex,
                                              String date, String time) {
        return hasScheduleConflict(items, ignoreIndex, date, time, "");
    }

    /**
     * 새 일정 구간이 기존 일정 구간과 겹치는지 검사합니다.
     * 종료 시간이 없는 기존 자료는 시작 시각부터 1분 구간으로 취급합니다.
     */
    public static boolean hasScheduleConflict(List<WorkItemRecord> items, int ignoreIndex,
                                              String date, String startTime, String endTime) {
        if (safe(date).isEmpty() || safe(startTime).isEmpty()) return false;
        int newStart = minuteOfDay(startTime);
        int newEnd = normalizedEnd(newStart, endTime);
        if (newStart < 0 || newEnd <= newStart) return false;

        for (int i = 0; i < items.size(); i++) {
            if (i == ignoreIndex) continue;
            WorkItemRecord item = items.get(i);
            if (!"일정".equals(item.type) || item.completed || !date.equals(item.date)) continue;
            int oldStart = minuteOfDay(item.time);
            int oldEnd = normalizedEnd(oldStart, item.endTime);
            if (oldStart < 0 || oldEnd <= oldStart) continue;
            if (newStart < oldEnd && oldStart < newEnd) return true;
        }
        return false;
    }

    public static boolean isValidTimeRange(String startTime, String endTime) {
        int start = minuteOfDay(startTime);
        int end = minuteOfDay(endTime);
        return start >= 0 && end > start;
    }

    private static int normalizedEnd(int start, String endTime) {
        int parsed = minuteOfDay(endTime);
        return parsed > start ? parsed : start + 1;
    }

    private static int minuteOfDay(String value) {
        try {
            LocalTime parsed = LocalTime.parse(safe(value), TIME_FORMAT);
            return parsed.getHour() * 60 + parsed.getMinute();
        } catch (DateTimeParseException ignored) {
            return -1;
        }
    }

    private static String displayDateToken(String date) {
        if (date == null || date.length() < 10) return "";
        try {
            int month = Integer.parseInt(date.substring(5, 7));
            int day = Integer.parseInt(date.substring(8, 10));
            return month + "월 " + day + "일";
        } catch (Exception ignored) {
            return date;
        }
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (Exception ignored) { return fallback; }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String escape(String value) {
        return safe(value).replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n");
    }

    private static String unescape(String value) {
        return safe(value).replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }
}
