package kr.co.mybrain.v2.settings;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** 클라우드 AI 호출 전 네트워크와 월간 비용 한도를 검사합니다. */
public final class AiCloudPolicy {
    private AiCloudPolicy() {}

    public static Decision evaluate(Context context, String provider) {
        AiBudgetSettings budget = AiBudgetSettings.load(context);
        NetworkState network = currentNetwork(context);
        if (!network.internetAvailable) {
            return Decision.blocked("인터넷 연결이 없어 기기 분석으로 전환했습니다.", budget, 0L, 0L);
        }
        if (budget.wifiOnly && !network.wifi) {
            return Decision.blocked("Wi-Fi에서만 AI 사용 옵션이 켜져 있어 기기 분석으로 전환했습니다.", budget, 0L, 0L);
        }

        AiUsageStore.Summary summary = AiUsageStore.load(context, provider);
        long spent = summary.monthlyEstimatedCostWon;
        long average = summary.monthlyPricedRequests <= 0L
                ? 1L : Math.max(1L, spent / summary.monthlyPricedRequests);
        long projected = spent + average;
        if (budget.budgetEnabled && budget.blockAtLimit && spent >= budget.monthlyLimitWon) {
            return Decision.blocked(
                    "월간 AI 비용 한도에 도달해 기기 분석으로 전환했습니다.", budget, spent, projected);
        }

        boolean warning = budget.budgetEnabled && spent >= budget.warningAmountWon();
        String message = warning
                ? "월간 AI 비용이 경고 기준에 도달했습니다."
                : "";
        return new Decision(true, warning, message, budget, spent, projected);
    }

    private static NetworkState currentNetwork(Context context) {
        try {
            ConnectivityManager manager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return new NetworkState(false, false);
            Network network = manager.getActiveNetwork();
            if (network == null) return new NetworkState(false, false);
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities == null) return new NetworkState(false, false);
            boolean internet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            boolean wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            return new NetworkState(internet, wifi);
        } catch (Exception ignored) {
            return new NetworkState(true, false);
        }
    }

    private static final class NetworkState {
        final boolean internetAvailable;
        final boolean wifi;
        NetworkState(boolean internetAvailable, boolean wifi) {
            this.internetAvailable = internetAvailable;
            this.wifi = wifi;
        }
    }

    public static final class Decision {
        public final boolean allowed;
        public final boolean warning;
        public final String message;
        public final AiBudgetSettings settings;
        public final long spentWon;
        public final long projectedWon;

        Decision(boolean allowed, boolean warning, String message, AiBudgetSettings settings,
                 long spentWon, long projectedWon) {
            this.allowed = allowed;
            this.warning = warning;
            this.message = message == null ? "" : message;
            this.settings = settings;
            this.spentWon = spentWon;
            this.projectedWon = projectedWon;
        }

        static Decision blocked(String message, AiBudgetSettings settings,
                                long spentWon, long projectedWon) {
            return new Decision(false, false, message, settings, spentWon, projectedWon);
        }
    }
}
