package kr.co.mybrain.v2.settings;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.text.NumberFormat;
import java.time.YearMonth;
import java.util.Locale;

/** 월간 예상 비용이 경고 기준이나 한도에 처음 도달했을 때 알림을 표시합니다. */
public final class AiBudgetNotifier {
    public static final String CHANNEL_ID = "ai_budget_alerts";
    private static final String PREFS = "mybrain_v2_ai_budget_notifications";
    private static final int WARNING_ID = 2601;
    private static final int LIMIT_ID = 2602;

    private AiBudgetNotifier() {}

    public static void maybeNotify(
            Context context,
            long previousCombinedCostWon,
            long currentCombinedCostWon,
            AiBudgetSettings settings) {
        if (!settings.notificationsEnabled || !settings.budgetEnabled) return;
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;

        long warning = settings.warningAmountWon();
        long limit = settings.monthlyLimitWon;
        boolean crossedLimit = previousCombinedCostWon < limit && currentCombinedCostWon >= limit;
        boolean crossedWarning = previousCombinedCostWon < warning && currentCombinedCostWon >= warning;
        if (!crossedLimit && !crossedWarning) return;

        String level = crossedLimit ? "limit" : "warning";
        String onceKey = YearMonth.now() + "_" + level;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(onceKey, false)) return;
        prefs.edit().putBoolean(onceKey, true).apply();

        createChannel(context);
        String title = crossedLimit ? "AI 비용 한도에 도달했습니다" : "AI 비용 경고 기준에 도달했습니다";
        String detail = "이번 달 예상 비용 " + formatWon(currentCombinedCostWon)
                + " / 한도 " + formatWon(limit)
                + (crossedLimit && settings.blockAtLimit ? " · 이후 클라우드 AI가 차단됩니다." : "");

        Intent intent = new Intent(context, AiBudgetActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, crossedLimit ? LIMIT_ID : WARNING_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(detail)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(detail))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_STATUS);
        NotificationManagerCompat.from(context).notify(crossedLimit ? LIMIT_ID : WARNING_ID, builder.build());
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "AI 비용 알림", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("월간 AI 예상 비용이 경고 기준이나 한도에 도달하면 알립니다.");
        manager.createNotificationChannel(channel);
    }

    private static String formatWon(long value) {
        return NumberFormat.getIntegerInstance(Locale.KOREA).format(Math.max(0L, value)) + "원";
    }
}
