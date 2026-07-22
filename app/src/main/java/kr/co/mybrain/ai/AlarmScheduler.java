package kr.co.mybrain.ai;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 저장된 일정과 할 일을 읽어 다음 알림 한 건씩 예약합니다.
 * 반복 일정은 현재 시각 이후의 가장 가까운 발생일을 계산합니다.
 */
public final class AlarmScheduler {
    private static final String PREFS = "mybrain_data";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_ALARM_CODES = "alarm_request_codes";

    private AlarmScheduler() {
    }

    /** 현재 저장 데이터 전체를 기준으로 알림을 다시 예약합니다. */
    public static void rescheduleAll(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        cancelPrevious(context, prefs);

        String stored = prefs.getString(KEY_ITEMS, "");
        if (stored == null || stored.trim().isEmpty()) return;

        Set<String> newCodes = new HashSet<>();
        for (String line : stored.split("\n")) {
            String[] values = line.split("\t", -1);
            if (values.length < 5) continue;

            String type = unescape(values[0]);
            String title = unescape(values[1]);
            String date = unescape(values[2]);
            String time = unescape(values[3]);
            boolean completed = values.length >= 6 && "1".equals(values[5]);
            int reminderMinutes = parseReminderMinutes(values.length >= 7 ? values[6] : "0");
            String repeatType = normalizeRepeat(values.length >= 8 ? values[7] : "NONE");
            String repeatEndDate = values.length >= 9 ? unescape(values[8]) : "";

            if (completed || reminderMinutes < 0 || date.isEmpty() || time.isEmpty()) continue;
            if (!("일정".equals(type) || "할 일".equals(type))) continue;

            long occurrence = findNextOccurrence(date, time, repeatType, repeatEndDate,
                    reminderMinutes, System.currentTimeMillis());
            if (occurrence <= 0L) continue;

            long triggerAt = occurrence - reminderMinutes * 60_000L;
            int requestCode = (type + "|" + title + "|" + date + "|" + time + "|"
                    + reminderMinutes + "|" + repeatType).hashCode();
            schedule(context, requestCode, triggerAt, type, title, reminderMinutes);
            newCodes.add(String.valueOf(requestCode));
        }

        prefs.edit().putStringSet(KEY_ALARM_CODES, newCodes).apply();
    }

    /** 반복 규칙에 맞는 다음 발생 시각을 계산합니다. */
    private static long findNextOccurrence(String date, String time, String repeatType,
                                           String repeatEndDate, int reminderMinutes,
                                           long now) {
        long first = parseDateTime(date, time);
        if (first <= 0L) return -1L;

        long end = repeatEndDate.isEmpty() ? Long.MAX_VALUE : endOfDay(repeatEndDate);
        if (end <= 0L) return -1L;

        Calendar candidate = Calendar.getInstance();
        candidate.setTimeInMillis(first);

        for (int guard = 0; guard < 3700; guard++) {
            long itemTime = candidate.getTimeInMillis();
            long triggerAt = itemTime - reminderMinutes * 60_000L;
            if (itemTime <= end && triggerAt > now) return itemTime;
            if ("NONE".equals(repeatType)) return -1L;
            moveNext(candidate, repeatType);
            if (candidate.getTimeInMillis() > end) return -1L;
        }
        return -1L;
    }

    /** 반복 유형에 따라 다음 날짜로 이동합니다. */
    private static void moveNext(Calendar calendar, String repeatType) {
        switch (repeatType) {
            case "DAILY":
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                break;
            case "WEEKLY":
                calendar.add(Calendar.WEEK_OF_YEAR, 1);
                break;
            case "MONTHLY":
                calendar.add(Calendar.MONTH, 1);
                break;
            case "WEEKDAYS":
                do {
                    calendar.add(Calendar.DAY_OF_MONTH, 1);
                } while (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                        || calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY);
                break;
            default:
                calendar.add(Calendar.YEAR, 100);
                break;
        }
    }

    private static void schedule(Context context, int requestCode, long triggerAt,
                                 String type, String title, int reminderMinutes) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("type", type);
        intent.putExtra("title", title);
        intent.putExtra("reminderMinutes", reminderMinutes);
        intent.putExtra("notificationId", requestCode);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
    }

    /** 이전 데이터에서 생성했던 예약을 모두 취소합니다. */
    private static void cancelPrevious(Context context, SharedPreferences prefs) {
        Set<String> codes = prefs.getStringSet(KEY_ALARM_CODES, new HashSet<>());
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;

        for (String value : codes) {
            try {
                int requestCode = Integer.parseInt(value);
                Intent intent = new Intent(context, AlarmReceiver.class);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                );
                if (pendingIntent != null) {
                    manager.cancel(pendingIntent);
                    pendingIntent.cancel();
                }
            } catch (NumberFormatException ignored) {
                // 손상된 예약 번호는 건너뜁니다.
            }
        }
        prefs.edit().remove(KEY_ALARM_CODES).apply();
    }

    private static int parseReminderMinutes(String value) {
        try {
            int minutes = Integer.parseInt(value);
            if (minutes == -1 || minutes == 0 || minutes == 5 || minutes == 10
                    || minutes == 30 || minutes == 60) return minutes;
        } catch (NumberFormatException ignored) {
            // 기존 데이터는 정각 알림으로 복구합니다.
        }
        return 0;
    }

    private static String normalizeRepeat(String value) {
        if ("DAILY".equals(value) || "WEEKLY".equals(value)
                || "MONTHLY".equals(value) || "WEEKDAYS".equals(value)) return value;
        return "NONE";
    }

    private static long parseDateTime(String date, String time) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
            format.setLenient(false);
            Date parsed = format.parse(date + " " + time);
            return parsed == null ? -1L : parsed.getTime();
        } catch (ParseException e) {
            return -1L;
        }
    }

    private static long endOfDay(String date) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
            format.setLenient(false);
            Date parsed = format.parse(date + " 23:59");
            return parsed == null ? -1L : parsed.getTime();
        } catch (ParseException e) {
            return -1L;
        }
    }

    private static String unescape(String value) {
        return value.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }
}
