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
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * 앱 내부에서 음성 입력 상태와 중간 인식 결과를 보여주는 화면입니다.
 * 사용자는 듣기 중지·다시 듣기·입력창에 추가를 직접 선택할 수 있습니다.
 */
public class VoiceCaptureActivity extends Activity implements RecognitionListener {

    public static final String EXTRA_RESULT_TEXT = "voice_capture_result_text";

    private static final int REQUEST_AUDIO_PERMISSION = 7401;
    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BACKGROUND = Color.rgb(247, 249, 253);
    private static final int BORDER = Color.rgb(220, 228, 240);

    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private TextView statusText;
    private TextView resultText;
    private Button listenButton;
    private Button applyButton;
    private boolean listening;
    private String recognizedText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
        } else {
            prepareRecognizer();
            resultText.postDelayed(this::startListening, 260L);
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(20));
        root.setBackgroundColor(BACKGROUND);

        TextView title = text("음성으로 입력", 24, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView guide = text("일정이나 업무를 자연스럽게 말하세요. 인식되는 문장을 화면에서 바로 확인할 수 있습니다.", 14, MUTED);
        guide.setPadding(0, dp(8), 0, dp(18));
        root.addView(guide);

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        statusCard.setBackground(rounded(Color.WHITE, 20, BORDER, 1));

        statusText = text("음성 인식을 준비하고 있습니다.", 16, PRIMARY);
        statusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusText.setGravity(Gravity.CENTER);
        statusCard.addView(statusText);

        resultText = text("말한 내용이 여기에 표시됩니다.", 18, TEXT);
        resultText.setGravity(Gravity.CENTER);
        resultText.setMinHeight(dp(150));
        resultText.setPadding(dp(4), dp(20), dp(4), dp(14));
        statusCard.addView(resultText, new LinearLayout.LayoutParams(-1, -2));

        root.addView(statusCard, new LinearLayout.LayoutParams(-1, 0, 1f));

        listenButton = button("듣기 중지", Color.rgb(235, 242, 255), PRIMARY);
        listenButton.setOnClickListener(v -> {
            if (listening) stopListening();
            else startListening();
        });
        LinearLayout.LayoutParams listenParams = new LinearLayout.LayoutParams(-1, dp(52));
        listenParams.setMargins(0, dp(14), 0, dp(8));
        root.addView(listenButton, listenParams);

        applyButton = button("입력창에 추가", PRIMARY, Color.WHITE);
        applyButton.setEnabled(false);
        applyButton.setAlpha(0.55f);
        applyButton.setOnClickListener(v -> returnResult());
        root.addView(applyButton, new LinearLayout.LayoutParams(-1, dp(56)));

        Button cancel = button("취소", Color.TRANSPARENT, MUTED);
        cancel.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, dp(48));
        cancelParams.setMargins(0, dp(6), 0, 0);
        root.addView(cancel, cancelParams);

        setContentView(root);
    }

    private void prepareRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showUnavailable();
            return;
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
    }

    private void startListening() {
        if (recognizer == null || recognizerIntent == null) {
            prepareRecognizer();
            if (recognizer == null) return;
        }
        try {
            recognizedText = "";
            resultText.setText("말씀하세요…");
            statusText.setText("듣고 있습니다");
            listenButton.setText("듣기 중지");
            applyButton.setEnabled(false);
            applyButton.setAlpha(0.55f);
            listening = true;
            recognizer.startListening(recognizerIntent);
        } catch (Exception error) {
            listening = false;
            statusText.setText("음성 인식을 시작하지 못했습니다");
            listenButton.setText("다시 듣기");
            Toast.makeText(this, safeMessage(error), Toast.LENGTH_LONG).show();
        }
    }

    private void stopListening() {
        listening = false;
        statusText.setText("인식 결과를 정리하고 있습니다");
        listenButton.setText("다시 듣기");
        if (recognizer != null) recognizer.stopListening();
    }

    private void returnResult() {
        String value = recognizedText == null ? "" : recognizedText.trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "인식된 문장이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent data = new Intent();
        data.putExtra(EXTRA_RESULT_TEXT, value);
        setResult(RESULT_OK, data);
        finish();
    }

    private void updateRecognizedText(Bundle results, boolean finalResult) {
        ArrayList<String> values = results == null ? null
                : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (values == null || values.isEmpty()) return;
        recognizedText = values.get(0).trim();
        if (recognizedText.isEmpty()) return;
        resultText.setText(recognizedText);
        applyButton.setEnabled(true);
        applyButton.setAlpha(1f);
        if (finalResult) {
            listening = false;
            statusText.setText("인식이 완료되었습니다");
            listenButton.setText("다시 듣기");
        } else {
            statusText.setText("듣고 있습니다 · 문장을 확인하세요");
        }
    }

    private void showUnavailable() {
        statusText.setText("음성 인식 서비스를 사용할 수 없습니다");
        resultText.setText("Google 음성 인식 또는 Samsung 음성 입력을 활성화한 뒤 다시 시도하세요.");
        listenButton.setText("다시 확인");
        listening = false;
    }

    @Override public void onReadyForSpeech(Bundle params) {
        statusText.setText("말씀하세요");
    }

    @Override public void onBeginningOfSpeech() {
        statusText.setText("음성을 듣고 있습니다");
    }

    @Override public void onRmsChanged(float rmsdB) {
        if (!listening) return;
        int level = Math.max(1, Math.min(5, Math.round((rmsdB + 2f) / 2f)));
        StringBuilder indicator = new StringBuilder("듣고 있습니다 ");
        for (int i = 0; i < level; i++) indicator.append("●");
        statusText.setText(indicator.toString());
    }

    @Override public void onBufferReceived(byte[] buffer) { }

    @Override public void onEndOfSpeech() {
        listening = false;
        statusText.setText("인식 결과를 정리하고 있습니다");
        listenButton.setText("다시 듣기");
    }

    @Override public void onError(int error) {
        listening = false;
        String message = errorMessage(error);
        statusText.setText(message);
        listenButton.setText("다시 듣기");
        if (recognizedText.isEmpty()) resultText.setText("다시 눌러 천천히 말씀해 주세요.");
    }

    @Override public void onResults(Bundle results) {
        updateRecognizedText(results, true);
    }

    @Override public void onPartialResults(Bundle partialResults) {
        updateRecognizedText(partialResults, false);
    }

    @Override public void onEvent(int eventType, Bundle params) { }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_AUDIO_PERMISSION) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            prepareRecognizer();
            startListening();
        } else {
            statusText.setText("마이크 권한이 필요합니다");
            resultText.setText("설정에서 MyBrain AI의 마이크 권한을 허용하세요.");
            listenButton.setEnabled(false);
        }
    }

    private String errorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "마이크 입력 오류가 발생했습니다";
            case SpeechRecognizer.ERROR_CLIENT: return "음성 입력이 취소되었습니다";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "마이크 권한이 필요합니다";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "음성 서비스 연결을 확인해 주세요";
            case SpeechRecognizer.ERROR_NO_MATCH: return "문장을 인식하지 못했습니다";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "음성 인식기가 사용 중입니다";
            case SpeechRecognizer.ERROR_SERVER: return "음성 인식 서비스 오류가 발생했습니다";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "음성이 들리지 않았습니다";
            default: return "음성 인식을 다시 시도해 주세요";
        }
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.destroy();
            recognizer = null;
        }
        super.onDestroy();
    }

    private Button button(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(foreground);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setMinimumHeight(0);
        button.setMinimumWidth(0);
        button.setBackground(rounded(background, 16, 0, 0));
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

    private String safeMessage(Throwable error) {
        String value = error == null ? "알 수 없는 오류" : error.getMessage();
        return value == null || value.trim().isEmpty() ? "알 수 없는 오류" : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
