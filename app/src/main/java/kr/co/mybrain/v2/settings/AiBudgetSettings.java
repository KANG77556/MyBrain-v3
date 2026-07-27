package kr.co.mybrain.v2.settings;

import android.content.Context;
import android.content.SharedPreferences;

/** 클라우드 AI 비용 한도, 알림과 네트워크 사용 정책을 저장합니다. */
public final class AiBudgetSettings {
    private static final String PREFS = "mybrain_v2_ai_budget";
    public static final long DEFAULT_MONTHLY_LIMIT_WON = 5_000L;
    public static final int DEFAULT_WARNING_PERCENT = 80;
    public static final int DEFAULT_WON_PER_USD = 1_400;

    public boolean wifiOnly;
    public boolean budgetEnabled;
    public boolean blockAtLimit = true;
    public boolean notificationsEnabled = true;
    public long monthlyLimitWon = DEFAULT_MONTHLY_LIMIT_WON;
    public int warningPercent = DEFAULT_WARNING_PERCENT;
    public int wonPerUsd = DEFAULT_WON_PER_USD;

    private AiBudgetSettings() {}

    public static AiBudgetSettings load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AiBudgetSettings value = new AiBudgetSettings();
        value.wifiOnly = prefs.getBoolean("wifi_only", false);
        value.budgetEnabled = prefs.getBoolean("budget_enabled", false);
        value.blockAtLimit = prefs.getBoolean("block_at_limit", true);
        value.notificationsEnabled = prefs.getBoolean("notifications_enabled", true);
        value.monthlyLimitWon = clampLimit(
                prefs.getLong("monthly_limit_won", DEFAULT_MONTHLY_LIMIT_WON));
        value.warningPercent = clampWarning(
                prefs.getInt("warning_percent", DEFAULT_WARNING_PERCENT));
        value.wonPerUsd = clampExchangeRate(
                prefs.getInt("won_per_usd", DEFAULT_WON_PER_USD));
        return value;
    }

    public void save(Context context) {
        monthlyLimitWon = clampLimit(monthlyLimitWon);
        warningPercent = clampWarning(warningPercent);
        wonPerUsd = clampExchangeRate(wonPerUsd);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("wifi_only", wifiOnly)
                .putBoolean("budget_enabled", budgetEnabled)
                .putBoolean("block_at_limit", blockAtLimit)
                .putBoolean("notifications_enabled", notificationsEnabled)
                .putLong("monthly_limit_won", monthlyLimitWon)
                .putInt("warning_percent", warningPercent)
                .putInt("won_per_usd", wonPerUsd)
                .apply();
    }

    public long warningAmountWon() {
        return Math.max(1L, Math.round(monthlyLimitWon * warningPercent / 100.0));
    }

    public int progressPercent(long spentWon) {
        if (!budgetEnabled || monthlyLimitWon <= 0L) return 0;
        return (int) Math.min(999L, Math.round(Math.max(0L, spentWon) * 100.0 / monthlyLimitWon));
    }

    private static long clampLimit(long value) {
        return Math.max(100L, Math.min(100_000_000L, value));
    }

    private static int clampWarning(int value) {
        return Math.max(10, Math.min(100, value));
    }

    private static int clampExchangeRate(int value) {
        return Math.max(100, Math.min(10_000, value));
    }
}
