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
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * 여러 음성 문장을 이어서 입력할 수 있는 앱 내부 음성 화면입니다.
 * 한 문장의 인식이 끝나도 누적 문장을 유지하며, 사용자가 완료할 때 한 번에 반환합니다.
 */
public class VoiceCaptureActivityV2 extends Activity implements RecognitionListener {

    public static final String EXTRA_RESULT_TEXT = "voice_capture_v2_result_text";

    private static final int REQUEST_AUDIO_PERMISSION = 9401;
    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BORDER = Color.rgb(220, 228, 240);
    private static final int BACKGROUND = Color.rgb(247, 249, 253);

    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private TextView statusText;
    private TextView resultText;
    private Button listenButton;
    private Button applyButton;
    private Button removeButton;
    private boolean listening;
    private String partialText = "";
    private final ArrayList<String> segments = new ArrayList<>();

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
            resultText.postDelayed(this::startListening, 240L);
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(18));
        root.setBackgroundColor(BACKGROUND);

        TextView title = text("음성 이어 말하기", 24, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView guide = text("한 문장씩 말한 뒤 ‘한 문장 더 말하기’를 누르세요. 마지막에 입력창에 한 번에 추가합니다.", 14, MUTED);
        guide.setPadding(0, dp(7), 0, dp(14));
        root.addView(guide);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(Color.WHITE, 20, BORDER, 1));

        statusText = text("음성 인식을 준비하고 있습니다.", 16, PRIMARY);
        statusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusText.setGravity(Gravity.CENTER);
        card.addView(statusText);

        ScrollView resultScroll = new ScrollView(this);
        resultText = text("말한 문장이 여기에 누적됩니다.", 18, TEXT);
        resultText.setGravity(Gravity.START);
        resultText.setPadding(dp(2), dp(18), dp(2), dp(18));
        resultScroll.addView(resultText);
        card.addView(resultScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(card, new LinearLayout.LayoutParams(-1, 0, 1f));

        listenButton = button("듣기 중지", Color.rgb(235, 242, 255), PRIMARY);
        listenButton.setOnClickListener(v -> {
            if (listening) stopListening();
            else startListening();
        });
        LinearLayout.LayoutParams listenParams = new LinearLayout.LayoutParams(-1, dp(52));
        listenParams.setMargins(0, dp(12), 0, dp(7));
        root.addView(listenButton, listenParams);

        removeButton = button("마지막 문장 지우기", Color.TRANSPARENT, MUTED);
        removeButton.setEnabled(false);
        removeButton.setOnClickListener(v -> removeLastSegment());
        root.addView(removeButton, new LinearLayout.LayoutParams(-1, dp(46)));

        applyButton = button("입력창에 모두 추가", PRIMARY, Color.WHITE);
        applyButton.setEnabled(false);
        applyButton.setAlpha(0.55f);
        applyButton.setOnClickListener(v -> returnResult());
        root.addView(applyButton, new LinearLayout.LayoutParams(-1, dp(56)));

        Button cancel = button("취소", Color.TRANSPARENT, MUTED);
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel, new LinearLayout.LayoutParams(-1, dp(46)));

        setContentView(root);
    }

    private void prepareRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.setText("음성 인식 서비스를 사용할 수 없습니다");
            resultText.setText("Google 음성 인식 또는 Samsung 음성 입력을 활성화해 주세요.");
            listenButton.setEnabled(false);
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

    private void stopListening() {
        listening = false;
        statusText.setText("현재 문장을 정리하고 있습니다");
        listenButton.setText("한 문장 더 말하기");
        if (recognizer != null) recognizer.stopListening();
    }

    private void addFinalSegment(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isEmpty()) return;
        if (segments.isEmpty() || !segments.get(segments.size() - 1).equals(cleaned)) {
            segments.add(cleaned);
        }
        partialText = "";
        listening = false;
        statusText.setText(segments.size() + "개 문장을 입력했습니다");
        listenButton.setText("한 문장 더 말하기");
        refreshText();
    }

    private void removeLastSegment() {
        if (!segments.isEmpty()) segments.remove(segments.size() - 1);
        refreshText();
        statusText.setText(segments.isEmpty() ? "문장을 다시 말씀해 주세요" : segments.size() + "개 문장이 남았습니다");
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
        resultText.setText(value.length() == 0 ? "말한 문장이 여기에 누적됩니다." : value.toString());
        boolean hasResult = !segments.isEmpty() || !partialText.trim().isEmpty();
        applyButton.setEnabled(hasResult);
        applyButton.setAlpha(hasResult ? 1f : 0.55f);
        removeButton.setEnabled(!segments.isEmpty());
    }

    private void returnResult() {
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

    private void updatePartial(Bundle results, boolean finalResult) {
        ArrayList<String> values = results == null ? null
                : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (values == null || values.isEmpty()) return;
        String value = values.get(0).trim();
        if (value.isEmpty()) return;
        if (finalResult) addFinalSegment(value);
        else {
            partialText = value;
            statusText.setText("듣고 있습니다 · 문장을 확인하세요");
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
        listenButton.setText("한 문장 더 말하기");
    }

    @Override
    public void onError(int error) {
        listening = false;
        partialText = "";
        statusText.setText(errorMessage(error));
        listenButton.setText(segments.isEmpty() ? "다시 듣기" : "한 문장 더 말하기");
        refreshText();
    }

    @Override public void onResults(Bundle results) { updatePartial(results, true); }
    @Override public void onPartialResults(Bundle partialResults) { updatePartial(partialResults, false); }
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
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "마이크 권한이 필요합니다";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "음성 서비스 연결을 확인해 주세요";
            case SpeechRecognizer.ERROR_NO_MATCH: return "문장을 인식하지 못했습니다";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "음성 인식기가 사용 중입니다";
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
