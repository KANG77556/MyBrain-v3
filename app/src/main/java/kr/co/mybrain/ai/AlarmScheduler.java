package kr.co.mybrain.ai;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * SharedPreferences에 저장된 일정과 할 일을 읽어 알림을 예약합니다.
 * 기존 예약을 정리한 뒤 현재 데이터 기준으로 다시 예약합니다.
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

            if (completed || date.isEmpty() || time.isEmpty()) continue;
            if (!("일정".equals(type) || "할 일".equals(type))) continue;

            long triggerAt = parseDateTime(date, time);
            if (triggerAt <= System.currentTimeMillis()) continue;

            int requestCode = (type + "|" + title + "|" + date + "|" + time).hashCode();
            schedule(context, requestCode, triggerAt, type, title);
            newCodes.add(String.valueOf(requestCode));
        }

        prefs.edit().putStringSet(KEY_ALARM_CODES, newCodes).apply();
    }

    /** 한 개 알림을 절전 상태에서도 가능한 범위에서 예약합니다. */
    private static void schedule(Context context, int requestCode, long triggerAt,
                                 String type, String title) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("type", type);
        intent.putExtra("title", title);
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

    private static String unescape(String value) {
        return value.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }
}
