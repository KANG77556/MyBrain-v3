package kr.co.mybrain.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * MyBrain AI 1.9.6 음성 입력 화면입니다.
 * 사용자가 매번 버튼을 누르지 않아도 문장이 끝난 뒤 자동으로 다음 음성을 이어서 듣습니다.
 */
public class VoiceCaptureActivityV4 extends Activity implements RecognitionListener {

    public static final String EXTRA_RESULT_TEXT = VoiceCaptureActivityV2.EXTRA_RESULT_TEXT;

    private static final int REQUEST_AUDIO_PERMISSION = 9501;
    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int PRIMARY_LIGHT = Color.rgb(235, 242, 255);
    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BORDER = Color.rgb(220, 228, 240);
    private static final int BACKGROUND = Color.rgb(247, 249, 253);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<String> segments = new ArrayList<>();

    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private TextView statusText;
    private TextView resultText;
    private Button autoButton;
    private Button listenButton;
    private Button removeButton;
    private Button applyButton;

    private boolean listening;
    private boolean autoContinue = true;
    private boolean userPaused;
    private boolean destroyed;
    private boolean restartScheduled;
    private String partialText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        ensurePermissionAndStart();
    }

    /** 마이크 권한을 확인한 뒤 자동으로 첫 음성 인식을 시작합니다. */
    private void ensurePermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
            return;
        }
        prepareRecognizer();
        resultText.postDelayed(() -> startListening(false), 250L);
    }

    /** 한 손 조작에 맞춘 간단한 음성 입력 화면을 구성합니다. */
    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(10), dp(16), dp(14));
        root.setBackgroundColor(BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        Button back = button("‹", Color.TRANSPARENT, TEXT);
        back.setTextSize(26);
        back.setContentDescription("이전 화면으로 이동");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text("음성으로 입력", 23, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(header);

        TextView guide = text("말을 잠시 멈추면 문장으로 저장하고 자동으로 계속 듣습니다.", 14, MUTED);
        guide.setPadding(dp(2), dp(3), dp(2), dp(12));
        root.addView(guide);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(rounded(Color.WHITE, 20, BORDER, 1));

        statusText = text("음성 인식을 준비하고 있습니다.", 16, PRIMARY);
        statusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusText.setGravity(Gravity.CENTER);
        card.addView(statusText);

        ScrollView resultScroll = new ScrollView(this);
        resultText = text("말한 내용이 여기에 표시됩니다.", 18, TEXT);
        resultText.setPadding(dp(2), dp(18), dp(2), dp(18));
        resultText.setLineSpacing(0f, 1.18f);
        resultScroll.addView(resultText);
        card.addView(resultScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(card, new LinearLayout.LayoutParams(-1, 0, 1f));

        autoButton = button("자동 이어 듣기 켜짐", PRIMARY_LIGHT, PRIMARY);
        autoButton.setOnClickListener(v -> toggleAutoContinue());
        LinearLayout.LayoutParams autoParams = new LinearLayout.LayoutParams(-1, dp(48));
        autoParams.setMargins(0, dp(10), 0, dp(6));
        root.addView(autoButton, autoParams);

        listenButton = button("듣기 중지", PRIMARY_LIGHT, PRIMARY);
        listenButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        listenButton.setOnClickListener(v -> {
            if (listening || restartScheduled) pauseByUser();
            else startListening(true);
        });
        root.addView(listenButton, new LinearLayout.LayoutParams(-1, dp(54)));

        LinearLayout secondary = new LinearLayout(this);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        secondary.setPadding(0, dp(5), 0, dp(5));

        Button close = button("닫기", Color.TRANSPARENT, MUTED);
        close.setOnClickListener(v -> finish());
        secondary.addView(close, weighted(48, 0, 4));

        removeButton = button("마지막 문장 삭제", Color.TRANSPARENT, MUTED);
        removeButton.setEnabled(false);
        removeButton.setOnClickListener(v -> removeLastSegment());
        secondary.addView(removeButton, weighted(48, 4, 0));
        root.addView(secondary);

        applyButton = button("입력창에 추가", PRIMARY, Color.WHITE);
        applyButton.setTextSize(16);
        applyButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        applyButton.setEnabled(false);
        applyButton.setAlpha(0.55f);
        applyButton.setOnClickListener(v -> returnResult());
        root.addView(applyButton, new LinearLayout.LayoutParams(-1, dp(58)));

        setContentView(root);
    }

    /** 단말기의 음성 인식 서비스를 준비합니다. */
    private void prepareRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.setText("음성 인식 서비스를 사용할 수 없습니다");
            resultText.setText("Google 음성 인식 또는 Samsung 음성 입력을 활성화해 주세요.");
            listenButton.setEnabled(false);
            return;
        }
        destroyRecognizer();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        // 음성팩이 있으면 오프라인을 사용하고, 없으면 단말기 서비스가 자동으로 연결 방식을 선택합니다.
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L);
        recognizerIntent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 650L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200L);
    }

    /** 수동 시작과 자동 재시작을 같은 방식으로 처리합니다. */
    private void startListening(boolean manualStart) {
        cancelRestart();
        if (destroyed) return;
        if (manualStart) userPaused = false;
        if (recognizer == null || recognizerIntent == null) prepareRecognizer();
        if (recognizer == null) return;

        try {
            partialText = "";
            listening = true;
            statusText.setText("말씀하세요");
            listenButton.setText("듣기 중지");
            refreshText();
            recognizer.startListening(recognizerIntent);
        } catch (Exception error) {
            listening = false;
            statusText.setText("음성 인식을 시작하지 못했습니다");
            listenButton.setText("다시 듣기");
            Toast.makeText(this, safeMessage(error), Toast.LENGTH_LONG).show();
        }
    }

    /** 사용자가 중지했을 때는 자동 재시작도 함께 멈춥니다. */
    private void pauseByUser() {
        userPaused = true;
        listening = false;
        cancelRestart();
        if (recognizer != null) {
            try { recognizer.stopListening(); }
            catch (Exception ignored) { recognizer.cancel(); }
        }
        statusText.setText("음성 듣기를 멈췄습니다");
        listenButton.setText("다시 듣기");
    }

    private void toggleAutoContinue() {
        autoContinue = !autoContinue;
        autoButton.setText(autoContinue ? "자동 이어 듣기 켜짐" : "자동 이어 듣기 꺼짐");
        autoButton.setContentDescription(autoContinue
                ? "문장이 끝나면 자동으로 다음 음성을 듣습니다"
                : "문장마다 듣기 버튼을 눌러야 합니다");
        if (!autoContinue) cancelRestart();
        if (autoContinue && !listening && !userPaused) scheduleRestart(250L);
    }

    /** 한 문장 처리가 끝난 후 짧게 쉬었다가 다음 문장을 자동으로 듣습니다. */
    private void scheduleRestart(long delayMillis) {
        if (!autoContinue || userPaused || destroyed || restartScheduled) return;
        restartScheduled = true;
        statusText.setText("다음 문장을 이어서 들을 준비 중입니다");
        listenButton.setText("듣기 중지");
        handler.postDelayed(() -> {
            restartScheduled = false;
            if (!destroyed && autoContinue && !userPaused) startListening(false);
        }, delayMillis);
    }

    private void cancelRestart() {
        restartScheduled = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void addFinalSegment(String value) {
        String cleaned = safe(value).trim();
        if (cleaned.isEmpty()) return;
        if (segments.isEmpty() || !segments.get(segments.size() - 1).equals(cleaned)) {
            segments.add(cleaned);
        }
        partialText = "";
        listening = false;
        statusText.setText(segments.size() + "개 문장을 입력했습니다");
        refreshText();
    }

    private void removeLastSegment() {
        if (!segments.isEmpty()) segments.remove(segments.size() - 1);
        refreshText();
        statusText.setText(segments.isEmpty()
                ? "새 문장을 말씀해 주세요"
                : segments.size() + "개 문장이 남았습니다");
    }

    private void refreshText() {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            if (value.length() > 0) value.append("\n");
            value.append(i + 1).append(". ").append(segments.get(i));
        }
        if (!partialText.trim().isEmpty()) {
            if (value.length() > 0) value.append("\n");
            value.append("• ").append(partialText.trim());
        }
        resultText.setText(value.length() == 0 ? "말한 내용이 여기에 표시됩니다." : value.toString());
        boolean hasResult = !segments.isEmpty() || !partialText.trim().isEmpty();
        applyButton.setEnabled(hasResult);
        applyButton.setAlpha(hasResult ? 1f : 0.55f);
        removeButton.setEnabled(!segments.isEmpty());
    }

    private void returnResult() {
        userPaused = true;
        cancelRestart();
        if (recognizer != null) recognizer.cancel();
        if (!partialText.trim().isEmpty()) addFinalSegment(partialText);
        if (segments.isEmpty()) {
            Toast.makeText(this, "인식된 문장이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder result = new StringBuilder();
        for (String segment : segments) {
            if (result.length() > 0) result.append("\n");
            result.append(segment);
        }
        Intent data = new Intent();
        data.putExtra(EXTRA_RESULT_TEXT, result.toString());
        setResult(RESULT_OK, data);
        finish();
    }

    private void updateRecognition(Bundle results, boolean finalResult) {
        ArrayList<String> values = results == null ? null
                : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (values == null || values.isEmpty()) return;
        String value = safe(values.get(0)).trim();
        if (value.isEmpty()) return;
        if (finalResult) {
            addFinalSegment(value);
            scheduleRestart(420L);
        } else {
            partialText = value;
            statusText.setText("듣고 있습니다 · 중간 결과");
            refreshText();
        }
    }

    @Override public void onReadyForSpeech(Bundle params) { statusText.setText("말씀하세요"); }
    @Override public void onBeginningOfSpeech() { statusText.setText("음성을 듣고 있습니다"); }

    @Override
    public void onRmsChanged(float rmsdB) {
        if (!listening) return;
        int level = Math.max(1, Math.min(5, Math.round((rmsdB + 2f) / 2f)));
        StringBuilder indicator = new StringBuilder("듣고 있습니다 ");
        for (int i = 0; i < level; i++) indicator.append("●");
        statusText.setText(indicator.toString());
    }

    @Override public void onBufferReceived(byte[] buffer) { }

    @Override
    public void onEndOfSpeech() {
        listening = false;
        statusText.setText("현재 문장을 정리하고 있습니다");
    }

    @Override
    public void onError(int error) {
        listening = false;
        partialText = "";
        refreshText();

        if (error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            statusText.setText(error == SpeechRecognizer.ERROR_NO_MATCH
                    ? "문장을 인식하지 못해 다시 듣습니다"
                    : "음성이 없어 다시 듣습니다");
            scheduleRestart(650L);
            return;
        }

        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            statusText.setText("음성 인식기를 다시 준비하고 있습니다");
            prepareRecognizer();
            scheduleRestart(700L);
            return;
        }

        statusText.setText(errorMessage(error));
        listenButton.setText("다시 듣기");
    }

    @Override public void onResults(Bundle results) { updateRecognition(results, true); }
    @Override public void onPartialResults(Bundle partialResults) { updateRecognition(partialResults, false); }
    @Override public void onEvent(int eventType, Bundle params) { }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_AUDIO_PERMISSION) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            prepareRecognizer();
            startListening(true);
        } else {
            statusText.setText("마이크 권한이 필요합니다");
            resultText.setText("설정에서 MyBrain AI의 마이크 권한을 허용하세요.");
            listenButton.setEnabled(false);
        }
    }

    private String errorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "마이크 입력 오류가 발생했습니다";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "마이크 권한이 필요합니다";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "음성 서비스 연결을 확인해 주세요";
            case SpeechRecognizer.ERROR_SERVER: return "음성 인식 서비스가 응답하지 않습니다";
            case SpeechRecognizer.ERROR_CLIENT: return userPaused ? "음성 듣기를 멈췄습니다" : "음성 인식을 다시 시도해 주세요";
            default: return "음성 인식을 다시 시도해 주세요";
        }
    }

    private void destroyRecognizer() {
        if (recognizer == null) return;
        try { recognizer.cancel(); } catch (Exception ignored) { }
        recognizer.destroy();
        recognizer = null;
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        cancelRestart();
        destroyRecognizer();
        super.onDestroy();
    }

    private LinearLayout.LayoutParams weighted(int height, int left, int right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(height), 1f);
        params.setMargins(dp(left), 0, dp(right), 0);
        return params;
    }

    private Button button(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(foreground);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinimumHeight(0);
        button.setMinimumWidth(0);
        button.setBackground(rounded(background, 16, background, 0));
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeMessage(Exception error) {
        String value = error == null ? "" : error.getMessage();
        return value == null || value.trim().isEmpty() ? "음성 인식을 다시 시도해 주세요." : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
