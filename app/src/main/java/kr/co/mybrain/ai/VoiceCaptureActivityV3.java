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
import java.util.Locale;

/**
 * MyBrain AI 1.9.6 음성 입력 화면입니다.
 *
 * 중간 결과와 최종 결과를 분리해 중복 입력을 막고,
 * 짧은 무음·인식 실패는 한 번 자동 재시도합니다.
 */
public class VoiceCaptureActivityV3 extends Activity implements RecognitionListener {

    public static final String EXTRA_RESULT_TEXT = VoiceCaptureActivityV2.EXTRA_RESULT_TEXT;

    private static final int REQUEST_AUDIO_PERMISSION = 11620;
    private static final int PRIMARY = Color.rgb(34, 96, 214);
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
    private Button listenButton;
    private Button applyButton;
    private Button removeButton;

    private boolean listening;
    private boolean startPending;
    private int automaticRetryCount;
    private String partialText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        ensurePermissionAndPrepare();
    }

    /** 마이크 권한을 확인한 뒤 인식기를 준비합니다. */
    private void ensurePermissionAndPrepare() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
            return;
        }
        prepareRecognizer();
        handler.postDelayed(this::startListening, 300L);
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(18));
        root.setBackgroundColor(BACKGROUND);

        LinearLayout header = new LinearLayout(this);
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

        TextView guide = text("자연스럽게 말하면 문장별로 이어서 기록합니다. 인식 결과를 확인한 뒤 입력창에 추가하세요.", 14, MUTED);
        guide.setPadding(0, dp(5), 0, dp(12));
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
        resultText = text("말한 문장이 여기에 표시됩니다.", 18, TEXT);
        resultText.setPadding(dp(2), dp(18), dp(2), dp(18));
        resultScroll.addView(resultText);
        card.addView(resultScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(card, new LinearLayout.LayoutParams(-1, 0, 1f));

        listenButton = button("듣기 시작", Color.rgb(235, 242, 255), PRIMARY);
        listenButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        listenButton.setOnClickListener(v -> {
            if (listening || startPending) stopListening();
            else {
                automaticRetryCount = 0;
                startListening();
            }
        });
        LinearLayout.LayoutParams listenParams = new LinearLayout.LayoutParams(-1, dp(54));
        listenParams.setMargins(0, dp(12), 0, dp(8));
        root.addView(listenButton, listenParams);

        LinearLayout secondary = new LinearLayout(this);
        secondary.setOrientation(LinearLayout.HORIZONTAL);

        Button close = button("닫기", Color.TRANSPARENT, MUTED);
        close.setOnClickListener(v -> finish());
        secondary.addView(close, weighted(dp(48), 0, 4));

        removeButton = button("마지막 문장 삭제", Color.TRANSPARENT, MUTED);
        removeButton.setEnabled(false);
        removeButton.setOnClickListener(v -> removeLastSegment());
        secondary.addView(removeButton, weighted(dp(48), 4, 0));
        root.addView(secondary);

        applyButton = button("입력창에 추가", PRIMARY, Color.WHITE);
        applyButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        applyButton.setEnabled(false);
        applyButton.setAlpha(0.55f);
        applyButton.setOnClickListener(v -> returnResult());
        root.addView(applyButton, new LinearLayout.LayoutParams(-1, dp(58)));

        setContentView(root);
    }

    /** Galaxy 기기의 기본 음성 서비스가 안정적으로 동작하도록 한국어 자유 발화를 설정합니다. */
    private void prepareRecognizer() {
        destroyRecognizer();
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.setText("음성 인식 서비스를 사용할 수 없습니다.");
            resultText.setText("Google 음성 인식 또는 Samsung 음성 입력을 활성화해 주세요.");
            listenButton.setEnabled(false);
            return;
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREA.toLanguageTag());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 700L);
    }

    private void startListening() {
        if (listening || startPending) return;
        if (recognizer == null || recognizerIntent == null) prepareRecognizer();
        if (recognizer == null) return;

        try {
            startPending = true;
            partialText = "";
            statusText.setText("말씀해 주세요.");
            listenButton.setText("듣기 중지");
            recognizer.cancel();
            handler.postDelayed(() -> {
                if (recognizer == null || isFinishing()) return;
                try {
                    recognizer.startListening(recognizerIntent);
                    listening = true;
                } catch (Exception error) {
                    listening = false;
                    statusText.setText("음성 인식을 시작하지 못했습니다.");
                    listenButton.setText("다시 듣기");
                } finally {
                    startPending = false;
                }
            }, 120L);
        } catch (Exception error) {
            startPending = false;
            listening = false;
            statusText.setText("음성 인식을 시작하지 못했습니다.");
            listenButton.setText("다시 듣기");
            Toast.makeText(this, safeMessage(error), Toast.LENGTH_LONG).show();
        }
    }

    private void stopListening() {
        startPending = false;
        listening = false;
        statusText.setText("음성을 처리하고 있습니다.");
        listenButton.setText("한 문장 더 말하기");
        if (recognizer != null) recognizer.stopListening();
    }

    /** 최종 결과는 중간 결과에 이어 붙이지 않고 하나의 확정 문장으로 저장합니다. */
    private void addFinalSegment(String value) {
        String cleaned = cleanRecognizedText(value);
        if (cleaned.isEmpty()) return;
        if (segments.isEmpty() || !isDuplicate(segments.get(segments.size() - 1), cleaned)) {
            segments.add(cleaned);
        }
        partialText = "";
        listening = false;
        automaticRetryCount = 0;
        statusText.setText("인식이 완료되었습니다. · " + segments.size() + "개 문장");
        listenButton.setText("한 문장 더 말하기");
        refreshText();
    }

    private void removeLastSegment() {
        if (!segments.isEmpty()) segments.remove(segments.size() - 1);
        refreshText();
        statusText.setText(segments.isEmpty() ? "문장을 다시 말씀해 주세요." : segments.size() + "개 문장이 남았습니다.");
    }

    private void refreshText() {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            if (value.length() > 0) value.append("\n");
            value.append(i + 1).append(". ").append(segments.get(i));
        }
        String partial = cleanRecognizedText(partialText);
        if (!partial.isEmpty()) {
            if (value.length() > 0) value.append("\n");
            value.append("• ").append(partial);
        }
        resultText.setText(value.length() == 0 ? "말한 문장이 여기에 표시됩니다." : value.toString());
        boolean hasResult = !segments.isEmpty() || !partial.isEmpty();
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

    private void updateRecognition(Bundle results, boolean finalResult) {
        ArrayList<String> values = results == null ? null
                : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (values == null || values.isEmpty()) return;
        String value = cleanRecognizedText(values.get(0));
        if (value.isEmpty()) return;
        if (finalResult) addFinalSegment(value);
        else {
            partialText = value;
            statusText.setText("듣는 중... 문장을 확인하세요.");
            refreshText();
        }
    }

    @Override public void onReadyForSpeech(Bundle params) { statusText.setText("말씀해 주세요."); }
    @Override public void onBeginningOfSpeech() { statusText.setText("음성을 듣고 있습니다."); }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }

    @Override
    public void onEndOfSpeech() {
        listening = false;
        statusText.setText("음성을 처리하고 있습니다.");
        listenButton.setText("한 문장 더 말하기");
    }

    @Override
    public void onError(int error) {
        listening = false;
        startPending = false;
        boolean retryable = error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY;

        if (retryable && automaticRetryCount < 1 && !isFinishing()) {
            automaticRetryCount++;
            statusText.setText("잘 듣지 못해 한 번 더 준비합니다.");
            listenButton.setText("준비 중...");
            handler.postDelayed(() -> {
                prepareRecognizer();
                startListening();
            }, 650L);
            return;
        }

        partialText = "";
        statusText.setText(errorMessage(error));
        listenButton.setText(segments.isEmpty() ? "다시 듣기" : "한 문장 더 말하기");
        refreshText();
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
            startListening();
        } else {
            statusText.setText("마이크 권한이 필요합니다.");
            resultText.setText("설정에서 MyBrain AI의 마이크 권한을 허용하세요.");
            listenButton.setEnabled(false);
        }
    }

    /** 반복 공백과 연속 중복 어절을 정리하되 사용자가 말한 내용은 임의로 바꾸지 않습니다. */
    private String cleanRecognizedText(String raw) {
        String value = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) return "";
        String[] words = value.split(" ");
        StringBuilder result = new StringBuilder();
        String previous = "";
        for (String word : words) {
            if (word.isEmpty() || word.equals(previous)) continue;
            if (result.length() > 0) result.append(' ');
            result.append(word);
            previous = word;
        }
        return result.toString().trim();
    }

    private boolean isDuplicate(String previous, String current) {
        String left = cleanRecognizedText(previous).replace(" ", "");
        String right = cleanRecognizedText(current).replace(" ", "");
        return left.equals(right) || (left.length() > 5 && right.contains(left))
                || (right.length() > 5 && left.contains(right));
    }

    private String errorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "마이크 입력 오류가 발생했습니다.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "마이크 권한이 필요합니다.";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "음성 서비스 연결을 확인해 주세요.";
            case SpeechRecognizer.ERROR_NO_MATCH: return "문장을 정확히 인식하지 못했습니다. 다시 말해 주세요.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "다른 앱이 마이크를 사용 중인지 확인해 주세요.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "음성이 들리지 않았습니다. 다시 말해 주세요.";
            default: return "음성 인식을 다시 시도해 주세요.";
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
        handler.removeCallbacksAndMessages(null);
        destroyRecognizer();
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

    private LinearLayout.LayoutParams weighted(int height, int leftMargin, int rightMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, height, 1f);
        params.setMargins(dp(leftMargin), 0, dp(rightMargin), 0);
        return params;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private String safeMessage(Exception error) {
        String value = error == null ? "" : error.getMessage();
        return value == null || value.trim().isEmpty() ? "음성 인식을 다시 시도해 주세요." : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}