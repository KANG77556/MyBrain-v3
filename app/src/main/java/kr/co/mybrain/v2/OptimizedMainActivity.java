package kr.co.mybrain.v2;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.ZoneId;

import kr.co.mybrain.v2.assistant.AiAnalysisCache;
import kr.co.mybrain.v2.assistant.KoreanNaturalLanguageParser;
import kr.co.mybrain.v2.assistant.ParsedWorkItem;
import kr.co.mybrain.v2.settings.AiSettings;

/**
 * 기존 홈 UI와 분석 기능은 유지하면서 동일 AI 요청의 연속 실행과 불필요한 재호출을 막습니다.
 */
public class OptimizedMainActivity extends AdaptiveMainActivity {
    private static final long ACTIVE_REQUEST_TIMEOUT_MS = 45_000L;

    private final Handler requestHandler = new Handler(Looper.getMainLooper());
    private EditText inputText;
    private TextView statusText;
    private TextView analysisMetaText;
    private Button analyzeButton;
    private String activeRequestKey;
    private boolean consumeCurrentTouch;
    private boolean cacheApplying;

    private final Runnable clearStaleRequest = () -> activeRequestKey = null;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().post(this::installRequestProtection);
    }

    private void installRequestProtection() {
        try {
            inputText = readField("inputText", EditText.class);
            statusText = readField("statusText", TextView.class);
            analysisMetaText = readField("analysisMetaText", TextView.class);
            analyzeButton = readField("analyzeButton", Button.class);
            if (inputText == null || statusText == null || analysisMetaText == null || analyzeButton == null) {
                return;
            }

            analyzeButton.setOnTouchListener((view, event) -> handleAnalyzeTouch(event));
            statusText.addTextChangedListener(new SimpleWatcher() {
                @Override public void afterTextChanged(Editable editable) {
                    onStatusChanged(editable == null ? "" : editable.toString());
                }
            });
        } catch (Exception ignored) {
            // 보호 기능을 연결하지 못해도 기존 분석 기능은 그대로 동작합니다.
        }
    }

    private boolean handleAnalyzeTouch(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            consumeCurrentTouch = prepareCloudRequest();
            return consumeCurrentTouch;
        }
        if (consumeCurrentTouch) {
            boolean consumed = true;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                consumeCurrentTouch = false;
            }
            return consumed;
        }
        return false;
    }

    /** true를 반환하면 기존 클릭을 소비하고 API 호출을 시작하지 않습니다. */
    private boolean prepareCloudRequest() {
        String text = inputText == null ? "" : inputText.getText().toString().trim();
        if (text.isEmpty()) return false;

        AiSettings settings = AiSettings.load(this);
        ZoneId zoneId = ZoneId.systemDefault();
        ParsedWorkItem baseline = KoreanNaturalLanguageParser.parse(text, zoneId);
        String key = AiAnalysisCache.createKey(
                settings.provider,
                settings.selectedModel(),
                text,
                zoneId,
                baseline);

        AiAnalysisCache.Entry cached = AiAnalysisCache.get(key);
        if (cached != null && !isAnalysisRunning()) {
            applyCachedResult(cached);
            return true;
        }

        if (key.equals(activeRequestKey)) {
            statusText.setText("같은 내용을 이미 분석하고 있습니다. 잠시 기다려 주세요.");
            Toast.makeText(this, "동일한 AI 요청은 한 번만 실행합니다.", Toast.LENGTH_SHORT).show();
            return true;
        }

        activeRequestKey = key;
        requestHandler.removeCallbacks(clearStaleRequest);
        requestHandler.postDelayed(clearStaleRequest, ACTIVE_REQUEST_TIMEOUT_MS);
        return false;
    }

    private void onStatusChanged(String value) {
        if (cacheApplying) return;
        if (value.contains("정밀 분석 완료")) {
            cacheCurrentCloudResult();
            clearActiveRequest();
        } else if (value.contains("자동 전환")
                || value.contains("연결 실패")
                || value.contains("AI 연결 정보가 없어")) {
            clearActiveRequest();
        }
    }

    private void cacheCurrentCloudResult() {
        if (activeRequestKey == null || activeRequestKey.isEmpty()) return;
        try {
            ParsedWorkItem item = readField("parsedItem", ParsedWorkItem.class);
            if (item == null || (!AiSettings.PROVIDER_GEMINI.equals(item.aiProvider)
                    && !AiSettings.PROVIDER_OPENAI.equals(item.aiProvider))) {
                return;
            }
            String providerLabel = AiSettings.PROVIDER_GEMINI.equals(item.aiProvider) ? "Gemini" : "GPT";
            AiAnalysisCache.put(
                    activeRequestKey,
                    item,
                    extractValidationSummary(analysisMetaText.getText().toString()),
                    providerLabel);
        } catch (Exception ignored) {
            // 캐시 저장 실패는 실제 분석 결과와 저장 기능에 영향을 주지 않습니다.
        }
    }

    private void applyCachedResult(AiAnalysisCache.Entry cached) {
        cacheApplying = true;
        try {
            String provider = cached.providerLabel.isEmpty() ? "AI" : cached.providerLabel;
            String summary = cached.validationSummary.isEmpty() ? "최근 정상 결과" : cached.validationSummary;
            invokeApplyAnalysisResult(
                    cached.item,
                    provider + " 최근 분석 결과 재사용 · 추가 API 호출과 비용 없이 바로 표시했습니다.",
                    provider + " · 최근 결과 재사용 · 토큰 0 · 추가 비용 없음\n결과 검증: " + summary);
            Toast.makeText(this, "최근 정상 분석 결과를 재사용했습니다.", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
            Toast.makeText(this, "최근 결과를 불러오지 못해 다시 분석합니다.", Toast.LENGTH_SHORT).show();
        } finally {
            cacheApplying = false;
            clearActiveRequest();
        }
    }

    private void clearActiveRequest() {
        activeRequestKey = null;
        requestHandler.removeCallbacks(clearStaleRequest);
    }

    private boolean isAnalysisRunning() {
        try {
            Boolean value = readField("analysisRunning", Boolean.class);
            return Boolean.TRUE.equals(value);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String extractValidationSummary(String meta) {
        if (meta == null) return "";
        String marker = "결과 검증:";
        int index = meta.indexOf(marker);
        if (index < 0) return "";
        String value = meta.substring(index + marker.length()).trim();
        return value.length() <= 180 ? value : value.substring(0, 180) + "…";
    }

    private void invokeApplyAnalysisResult(ParsedWorkItem item, String status, String meta) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(
                "applyAnalysisResult", ParsedWorkItem.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(this, item, status, meta);
    }

    @SuppressWarnings("unchecked")
    private <T> T readField(String name, Class<T> type) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(this);
        if (value == null) return null;
        if (type == Boolean.class && value instanceof Boolean) return (T) value;
        return type.cast(value);
    }

    @Override protected void onDestroy() {
        requestHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
