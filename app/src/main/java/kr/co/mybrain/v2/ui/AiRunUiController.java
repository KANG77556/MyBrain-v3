package kr.co.mybrain.v2.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import kr.co.mybrain.v2.AdaptiveMainActivity;
import kr.co.mybrain.v2.MainActivity;
import kr.co.mybrain.v2.assistant.AnalysisUiPolicy;
import kr.co.mybrain.v2.assistant.KoreanNaturalLanguageParser;
import kr.co.mybrain.v2.assistant.ParsedWorkItem;
import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.settings.AiSettings;

/**
 * 검증된 AdaptiveMainActivity 런처를 변경하지 않고 AI 진행 UI만 안전하게 보강합니다.
 * 연결 실패 시 기존 화면 동작을 유지하도록 모든 보조 경로를 예외 보호합니다.
 */
public final class AiRunUiController {
    private static final Map<Activity, Session> SESSIONS = new HashMap<>();
    private static boolean installed;

    private AiRunUiController() {}

    public static synchronized void install(Application application) {
        if (installed) return;
        installed = true;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}

            @Override public void onActivityResumed(Activity activity) {
                if (!(activity instanceof AdaptiveMainActivity)) return;
                activity.getWindow().getDecorView().postDelayed(() -> attach(activity), 180L);
            }

            @Override public void onActivityPaused(Activity activity) {
                Session session = SESSIONS.get(activity);
                if (session != null) session.onPause();
            }

            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

            @Override public void onActivityDestroyed(Activity activity) {
                Session session = SESSIONS.remove(activity);
                if (session != null) session.destroy();
            }
        });
    }

    private static void attach(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        Session existing = SESSIONS.get(activity);
        if (existing != null) {
            existing.onResume();
            return;
        }
        Session session = new Session((AdaptiveMainActivity) activity);
        if (session.attach()) SESSIONS.put(activity, session);
    }

    private static final class Session {
        private static final int RESULT_TEXT = Color.rgb(24, 34, 48);
        private static final long NETWORK_SWITCH_GRACE_MS = 400L;

        private final AdaptiveMainActivity activity;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private EditText inputText;
        private TextView previewText;
        private TextView statusText;
        private TextView analysisMetaText;
        private TextView resultLabel;
        private Button analyzeButton;
        private ConnectivityManager connectivityManager;
        private ConnectivityManager.NetworkCallback networkCallback;
        private boolean touchConsumed;
        private boolean applyingFallback;
        private boolean active = true;
        private long analysisStartedAt;
        private String elapsedBaseText = "AI 요청 중";

        private final Runnable elapsedTicker = new Runnable() {
            @Override public void run() {
                if (!active || !isAnalysisRunning() || analysisMetaText == null || analyzeButton == null) {
                    stopElapsedTimer();
                    return;
                }
                long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - analysisStartedAt);
                analysisMetaText.setText(elapsedBaseText + " · 경과 " + AnalysisUiPolicy.elapsed(elapsed));
                analyzeButton.setEnabled(true);
                analyzeButton.setAlpha(1f);
                analyzeButton.setText("분석 취소");
                analyzeButton.setContentDescription("진행 중인 AI 분석 취소");
                handler.postDelayed(this, 400L);
            }
        };

        Session(AdaptiveMainActivity activity) {
            this.activity = activity;
        }

        boolean attach() {
            try {
                inputText = readMainField("inputText", EditText.class);
                previewText = readMainField("previewText", TextView.class);
                statusText = readMainField("statusText", TextView.class);
                analysisMetaText = readMainField("analysisMetaText", TextView.class);
                resultLabel = readMainField("resultLabel", TextView.class);
                analyzeButton = readMainField("analyzeButton", Button.class);
                if (inputText == null || previewText == null || statusText == null
                        || analysisMetaText == null || resultLabel == null || analyzeButton == null) {
                    return false;
                }

                analyzeButton.setOnTouchListener((view, event) -> handleAnalyzeTouch(event));
                statusText.addTextChangedListener(new SimpleWatcher() {
                    @Override public void afterTextChanged(Editable editable) {
                        onStatusChanged(editable == null ? "" : editable.toString());
                    }
                });
                previewText.addTextChangedListener(new SimpleWatcher() {
                    @Override public void afterTextChanged(Editable editable) {
                        String value = editable == null ? "" : editable.toString();
                        if (value.startsWith("제목")) forceResultDetailsVisible();
                    }
                });
                registerNetworkCallback();
                if (String.valueOf(previewText.getText()).startsWith("제목")) {
                    forceResultDetailsVisible();
                }
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        void onResume() {
            active = true;
            if (isAnalysisRunning()) startElapsedTimer();
        }

        void onPause() {
            active = false;
            handler.removeCallbacks(elapsedTicker);
        }

        private boolean handleAnalyzeTouch(MotionEvent event) {
            try {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    if (isAnalysisRunning()) {
                        cancelToLocal("AI 분석 취소 · 기기 분석 결과를 표시합니다.", true);
                        touchConsumed = true;
                        return true;
                    }
                    if (!hasInternetConnection()) {
                        cancelToLocal("인터넷 연결이 없어 기기 분석 결과를 사용했습니다.", false);
                        touchConsumed = true;
                        return true;
                    }
                    touchConsumed = false;
                    return false;
                }
                if (touchConsumed) {
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        touchConsumed = false;
                    }
                    return true;
                }
            } catch (Throwable ignored) {
                touchConsumed = false;
            }
            return false;
        }

        private void onStatusChanged(String value) {
            if (applyingFallback) return;
            try {
                if (AnalysisUiPolicy.isCloudRunning(value)) {
                    startElapsedTimer();
                    return;
                }
                if (AnalysisUiPolicy.isCloudSuccess(value)) {
                    repairCloudPresentation(value);
                }
                if (AnalysisUiPolicy.isTerminal(value)) {
                    stopElapsedTimer();
                    restoreAnalyzeButton();
                }
            } catch (Throwable ignored) {
                // 상태 보강 실패는 원래 분석 결과에 영향을 주지 않습니다.
            }
        }

        private void startElapsedTimer() {
            if (!active) return;
            if (analysisStartedAt == 0L) {
                analysisStartedAt = SystemClock.elapsedRealtime();
                String current = analysisMetaText == null ? "" : String.valueOf(analysisMetaText.getText()).trim();
                elapsedBaseText = current.isEmpty() ? "AI 요청 중"
                        : current.replaceAll("\\s*·\\s*경과.*$", "");
            }
            handler.removeCallbacks(elapsedTicker);
            handler.post(elapsedTicker);
        }

        private void stopElapsedTimer() {
            handler.removeCallbacks(elapsedTicker);
            analysisStartedAt = 0L;
        }

        private void restoreAnalyzeButton() {
            if (analyzeButton == null) return;
            analyzeButton.setEnabled(true);
            analyzeButton.setAlpha(1f);
            analyzeButton.setText("더 정확히");
            analyzeButton.setContentDescription("AI로 더 정확히 분석");
        }

        private void cancelToLocal(String status, boolean userRequested) {
            if (inputText == null) return;
            String text = inputText.getText().toString().trim();
            if (text.isEmpty()) return;
            applyingFallback = true;
            try {
                invalidateCurrentRequest();
                writeMainBoolean("analysisRunning", false);
                invokeMainVoid("setAnalyzeButtonBusy", new Class<?>[]{boolean.class}, false);
                stopElapsedTimer();

                ParsedWorkItem local = KoreanNaturalLanguageParser.parse(text, ZoneId.systemDefault());
                local.aiProvider = "LOCAL";
                String meta = userRequested
                        ? "사용자 취소 · 기기 분석 · 토큰 0 · 결과를 확인한 뒤 저장하세요."
                        : "오프라인 자동 전환 · 기기 분석 · 토큰 0 · 네트워크 미사용";
                invokeApplyAnalysisResult(local, status, meta);
                forceResultDetailsVisible();
                Toast.makeText(activity,
                        userRequested ? "AI 분석 화면 대기를 취소했습니다."
                                : "인터넷 연결이 없어 기기 분석으로 전환했습니다.",
                        Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {
                if (statusText != null) statusText.setText(status);
            } finally {
                applyingFallback = false;
                restoreAnalyzeButton();
            }
        }

        /** 완료 문구와 결과 공급자를 맞추고 상세 결과 영역을 다시 표시합니다. */
        private void repairCloudPresentation(String status) {
            try {
                ParsedWorkItem item = readMainField("parsedItem", ParsedWorkItem.class);
                if (item == null) return;
                String provider = AnalysisUiPolicy.providerFromStatus(status);
                if ("LOCAL".equals(provider)) provider = AiSettings.load(activity).provider;
                item.aiProvider = provider;
                previewText.setText(invokeFormatResult(item));
                resultLabel.setText("AI 분석 결과 · " + typeLabel(item.type)
                        + " · " + providerLabel(provider));
                forceResultDetailsVisible();
            } catch (Throwable ignored) {
                forceResultDetailsVisible();
            }
        }

        private void forceResultDetailsVisible() {
            if (previewText != null) {
                previewText.setVisibility(View.VISIBLE);
                previewText.setAlpha(1f);
                previewText.setTextColor(RESULT_TEXT);
                previewText.setMinHeight(dp(112));
                previewText.setContentDescription("AI 분석 상세 결과 " + previewText.getText());
                if (previewText.getParent() instanceof View) {
                    View parent = (View) previewText.getParent();
                    parent.setVisibility(View.VISIBLE);
                    parent.setAlpha(1f);
                }
            }
            if (resultLabel != null) resultLabel.setVisibility(View.VISIBLE);
            if (analysisMetaText != null) analysisMetaText.setVisibility(View.VISIBLE);
        }

        private void registerNetworkCallback() {
            connectivityManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null || networkCallback != null) return;
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onLost(Network network) {
                    handler.postDelayed(() -> {
                        if (active && isAnalysisRunning() && !hasInternetConnection()) {
                            cancelToLocal("인터넷 연결이 끊겨 기기 분석 결과로 전환했습니다.", false);
                        }
                    }, NETWORK_SWITCH_GRACE_MS);
                }

                @Override public void onUnavailable() {
                    handler.post(() -> {
                        if (active && isAnalysisRunning() && !hasInternetConnection()) {
                            cancelToLocal("인터넷 연결을 사용할 수 없어 기기 분석 결과로 전환했습니다.", false);
                        }
                    });
                }
            };
            try {
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
            } catch (Throwable ignored) {
                networkCallback = null;
            }
        }

        private boolean hasInternetConnection() {
            ConnectivityManager manager = connectivityManager != null ? connectivityManager
                    : (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return false;
            try {
                Network activeNetwork = manager.getActiveNetwork();
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(activeNetwork);
                return capabilities != null
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            } catch (Throwable ignored) {
                return false;
            }
        }

        private void invalidateCurrentRequest() throws Exception {
            Field field = MainActivity.class.getDeclaredField("analysisRequestId");
            field.setAccessible(true);
            field.setInt(activity, field.getInt(activity) + 1);
        }

        private boolean isAnalysisRunning() {
            try {
                return Boolean.TRUE.equals(readMainField("analysisRunning", Boolean.class));
            } catch (Throwable ignored) {
                return false;
            }
        }

        private void writeMainBoolean(String name, boolean value) throws Exception {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setBoolean(activity, value);
        }

        private void invokeApplyAnalysisResult(ParsedWorkItem item, String status, String meta) throws Exception {
            Method method = MainActivity.class.getDeclaredMethod(
                    "applyAnalysisResult", ParsedWorkItem.class, String.class, String.class);
            method.setAccessible(true);
            method.invoke(activity, item, status, meta);
        }

        private String invokeFormatResult(ParsedWorkItem item) throws Exception {
            Method method = MainActivity.class.getDeclaredMethod("formatResult", ParsedWorkItem.class);
            method.setAccessible(true);
            return String.valueOf(method.invoke(activity, item));
        }

        private void invokeMainVoid(String name, Class<?>[] types, Object... args) throws Exception {
            Method method = MainActivity.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(activity, args);
        }

        @SuppressWarnings("unchecked")
        private <T> T readMainField(String name, Class<T> type) throws Exception {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(activity);
            if (value == null) return null;
            if (type == Boolean.class && value instanceof Boolean) return (T) value;
            return type.cast(value);
        }

        private String typeLabel(String type) {
            if (WorkItemEntity.TYPE_SCHEDULE.equals(type)) return "일정";
            if (WorkItemEntity.TYPE_TASK.equals(type)) return "할 일";
            return "메모";
        }

        private String providerLabel(String provider) {
            if (AiSettings.PROVIDER_GEMINI.equals(provider)) return "Gemini";
            if (AiSettings.PROVIDER_OPENAI.equals(provider)) return "GPT";
            return "기기 분석";
        }

        private int dp(int value) {
            return Math.round(value * activity.getResources().getDisplayMetrics().density);
        }

        void destroy() {
            active = false;
            stopElapsedTimer();
            handler.removeCallbacksAndMessages(null);
            if (connectivityManager != null && networkCallback != null) {
                try {
                    connectivityManager.unregisterNetworkCallback(networkCallback);
                } catch (Throwable ignored) {
                    // 이미 해제된 경우 무시합니다.
                }
            }
            networkCallback = null;
        }
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}