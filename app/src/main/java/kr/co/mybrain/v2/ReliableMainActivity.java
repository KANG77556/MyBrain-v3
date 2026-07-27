package kr.co.mybrain.v2;

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

import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.ZoneId;

import kr.co.mybrain.v2.assistant.AnalysisUiPolicy;
import kr.co.mybrain.v2.assistant.KoreanNaturalLanguageParser;
import kr.co.mybrain.v2.assistant.ParsedWorkItem;
import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.settings.AiSettings;

/**
 * AI 분석 중 취소, 경과 시간, 오프라인 전환과 결과 표시 복구를 기존 홈에 추가합니다.
 */
public class ReliableMainActivity extends OptimizedMainActivity {
    private static final int RESULT_TEXT = Color.rgb(24, 34, 48);
    private static final long NETWORK_SWITCH_GRACE_MS = 400L;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
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
    private long analysisStartedAt;
    private String elapsedBaseText = "AI 요청 중";

    private final Runnable elapsedTicker = new Runnable() {
        @Override public void run() {
            if (!isAnalysisRunning() || analysisMetaText == null || analyzeButton == null) {
                stopElapsedTimer();
                return;
            }
            long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - analysisStartedAt);
            analysisMetaText.setText(elapsedBaseText + " · 경과 " + AnalysisUiPolicy.elapsed(elapsed));
            analyzeButton.setEnabled(true);
            analyzeButton.setAlpha(1f);
            analyzeButton.setText("분석 취소");
            analyzeButton.setContentDescription("진행 중인 AI 분석 취소");
            uiHandler.postDelayed(this, 400L);
        }
    };

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().postDelayed(this::installReliableControls, 140L);
    }

    private void installReliableControls() {
        try {
            inputText = readMainField("inputText", EditText.class);
            previewText = readMainField("previewText", TextView.class);
            statusText = readMainField("statusText", TextView.class);
            analysisMetaText = readMainField("analysisMetaText", TextView.class);
            resultLabel = readMainField("resultLabel", TextView.class);
            analyzeButton = readMainField("analyzeButton", Button.class);
            if (inputText == null || previewText == null || statusText == null
                    || analysisMetaText == null || resultLabel == null || analyzeButton == null) {
                return;
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
            forceResultDetailsVisible();
        } catch (Exception ignored) {
            // 보조 기능을 연결하지 못해도 기존 홈과 분석 기능은 그대로 유지합니다.
        }
    }

    private boolean handleAnalyzeTouch(MotionEvent event) {
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
            touchConsumed = invokeOptimizedPrepareRequest();
            return touchConsumed;
        }
        if (touchConsumed) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                touchConsumed = false;
            }
            return true;
        }
        return false;
    }

    private void onStatusChanged(String value) {
        if (applyingFallback) return;
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
    }

    private void startElapsedTimer() {
        if (analysisStartedAt == 0L) {
            analysisStartedAt = SystemClock.elapsedRealtime();
            String current = analysisMetaText == null ? "" : String.valueOf(analysisMetaText.getText()).trim();
            elapsedBaseText = current.isEmpty() ? "AI 요청 중" : current.replaceAll("\\s*·\\s*경과.*$", "");
        }
        uiHandler.removeCallbacks(elapsedTicker);
        uiHandler.post(elapsedTicker);
    }

    private void stopElapsedTimer() {
        uiHandler.removeCallbacks(elapsedTicker);
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
            invokeOptimizedClearRequest();
            stopElapsedTimer();

            ParsedWorkItem local = KoreanNaturalLanguageParser.parse(text, ZoneId.systemDefault());
            local.aiProvider = "LOCAL";
            String meta = userRequested
                    ? "사용자 취소 · 기기 분석 · 토큰 0 · 결과를 확인한 뒤 저장하세요."
                    : "오프라인 자동 전환 · 기기 분석 · 토큰 0 · 네트워크 미사용";
            invokeApplyAnalysisResult(local, status, meta);
            forceResultDetailsVisible();
            Toast.makeText(this,
                    userRequested ? "AI 분석을 취소했습니다." : "인터넷 연결이 없어 기기 분석으로 전환했습니다.",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
            if (statusText != null) statusText.setText(status);
        } finally {
            applyingFallback = false;
            restoreAnalyzeButton();
        }
    }

    /** 완료 문구와 실제 결과 공급자를 맞추고 상세 결과 영역을 강제로 복구합니다. */
    private void repairCloudPresentation(String status) {
        try {
            ParsedWorkItem item = readMainField("parsedItem", ParsedWorkItem.class);
            if (item == null) return;
            String provider = AnalysisUiPolicy.providerFromStatus(status);
            if ("LOCAL".equals(provider)) provider = AiSettings.load(this).provider;
            item.aiProvider = provider;
            previewText.setText(invokeFormatResult(item));
            resultLabel.setText("AI 분석 결과 · " + typeLabel(item.type) + " · " + providerLabel(provider));
            forceResultDetailsVisible();
        } catch (Exception ignored) {
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
            View parent = previewText.getParent() instanceof View ? (View) previewText.getParent() : null;
            if (parent != null) {
                parent.setVisibility(View.VISIBLE);
                parent.setAlpha(1f);
            }
        }
        if (resultLabel != null) resultLabel.setVisibility(View.VISIBLE);
        if (analysisMetaText != null) analysisMetaText.setVisibility(View.VISIBLE);
    }

    private void registerNetworkCallback() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null || networkCallback != null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onLost(Network network) {
                uiHandler.postDelayed(() -> {
                    if (isAnalysisRunning() && !hasInternetConnection()) {
                        cancelToLocal("인터넷 연결이 끊겨 기기 분석 결과로 전환했습니다.", false);
                    }
                }, NETWORK_SWITCH_GRACE_MS);
            }

            @Override public void onUnavailable() {
                uiHandler.post(() -> {
                    if (isAnalysisRunning() && !hasInternetConnection()) {
                        cancelToLocal("인터넷 연결을 사용할 수 없어 기기 분석 결과로 전환했습니다.", false);
                    }
                });
            }
        };
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception ignored) {
            networkCallback = null;
        }
    }

    private boolean hasInternetConnection() {
        ConnectivityManager manager = connectivityManager != null
                ? connectivityManager : (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        try {
            Network active = manager.getActiveNetwork();
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(active);
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean invokeOptimizedPrepareRequest() {
        try {
            Method method = OptimizedMainActivity.class.getDeclaredMethod("prepareCloudRequest");
            method.setAccessible(true);
            Object value = method.invoke(this);
            return Boolean.TRUE.equals(value);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void invokeOptimizedClearRequest() {
        try {
            Method method = OptimizedMainActivity.class.getDeclaredMethod("clearActiveRequest");
            method.setAccessible(true);
            method.invoke(this);
        } catch (Exception ignored) {
            // 중복 요청 보호 상태를 지우지 못해도 화면 취소는 유지합니다.
        }
    }

    private void invalidateCurrentRequest() throws Exception {
        Field field = MainActivity.class.getDeclaredField("analysisRequestId");
        field.setAccessible(true);
        field.setInt(this, field.getInt(this) + 1);
    }

    private boolean isAnalysisRunning() {
        try {
            return Boolean.TRUE.equals(readMainField("analysisRunning", Boolean.class));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void writeMainBoolean(String name, boolean value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(this, value);
    }

    private void invokeApplyAnalysisResult(ParsedWorkItem item, String status, String meta) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(
                "applyAnalysisResult", ParsedWorkItem.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(this, item, status, meta);
    }

    private String invokeFormatResult(ParsedWorkItem item) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod("formatResult", ParsedWorkItem.class);
        method.setAccessible(true);
        return String.valueOf(method.invoke(this, item));
    }

    private void invokeMainVoid(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(this, args);
    }

    @SuppressWarnings("unchecked")
    private <T> T readMainField(String name, Class<T> type) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(this);
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
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        stopElapsedTimer();
        uiHandler.removeCallbacksAndMessages(null);
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
                // 이미 해제된 경우 무시합니다.
            }
        }
        networkCallback = null;
        super.onDestroy();
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}