package kr.co.mybrain.ai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/**
 * MyBrain AI 1.9.9 음성 입력 화면입니다.
 * 마이크 음량 표시, 말하기 길이 선택, 마지막 문장 수정 기능을 제공합니다.
 */
public class VoiceCaptureActivityV5 extends Activity implements RecognitionListener {

    public static final String EXTRA_RESULT_TEXT = VoiceCaptureActivityV2.EXTRA_RESULT_TEXT;
    private static final int REQUEST_AUDIO_PERMISSION = 11990;
    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BACKGROUND = Color.rgb(247, 249, 253);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<String> segments = new ArrayList<>();

    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private TextView statusText;
    private TextView resultText;
    private ProgressBar volumeBar;
    private Button listenButton;
    private Button applyButton;
    private Button removeButton;
    private Button editButton;

    private boolean listening;
    private boolean userPaused;
    private boolean destroyed;
    private int retryCount;
    private String partialText = "";
    private long completeSilenceMs = 1800L;
    private long possibleSilenceMs = 1100L;
    private long minimumLengthMs = 900L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        ensurePermission();
    }

    /** 마이크 권한 확인 후 자동으로 듣기를 시작합니다. */
    private void ensurePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
            return;
        }
        prepareRecognizer();
        handler.postDelayed(this::startListening, 300L);
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(18));
        root.setBackgroundColor(BACKGROUND);

        TextView title = text("음성으로 입력", 24, TEXT);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView guide = text("말하기 속도에 맞는 길이를 선택하고 자연스럽게 말씀하세요.", 14, MUTED);
        guide.setPadding(0, 0, 0, dp(8));
        root.addView(guide);

        RadioGroup modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton shortMode = radio("짧게");
        RadioButton normalMode = radio("보통");
        RadioButton longMode = radio("길게");
        modeGroup.addView(shortMode, new RadioGroup.LayoutParams(0, dp(44), 1f));
        modeGroup.addView(normalMode, new RadioGroup.LayoutParams(0, dp(44), 1f));
        modeGroup.addView(longMode, new RadioGroup.LayoutParams(0, dp(44), 1f));
        normalMode.setChecked(true);
        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == shortMode.getId()) setSpeechMode(1300L, 750L, 600L, "짧게 말하기");
            else if (checkedId == longMode.getId()) setSpeechMode(2400L, 1600L, 1100L, "길게 말하기");
            else setSpeechMode(1800L, 1100L, 900L, "보통 말하기");
        });
        root.addView(modeGroup);

        statusText = text("음성 인식을 준비하고 있습니다.", 16, PRIMARY);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText, new LinearLayout.LayoutParams(-1, dp(42)));

        volumeBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        volumeBar.setMax(100);
        volumeBar.setProgress(0);
        root.addView(volumeBar, new LinearLayout.LayoutParams(-1, dp(12)));

        ScrollView scroll = new ScrollView(this);
        resultText = text("말한 내용이 여기에 실시간으로 표시됩니다.", 18, TEXT);
        resultText.setPadding(dp(14), dp(16), dp(14), dp(16));
        scroll.addView(resultText);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        listenButton = button("잠시 멈춤", Color.rgb(235, 242, 255), PRIMARY);
        listenButton.setOnClickListener(v -> toggleListening());
        root.addView(listenButton, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout editRow = new LinearLayout(this);
        editRow.setOrientation(LinearLayout.HORIZONTAL);
        removeButton = button("마지막 삭제", Color.TRANSPARENT, MUTED);
        removeButton.setEnabled(false);
        removeButton.setOnClickListener(v -> removeLast());
        editRow.addView(removeButton, new LinearLayout.LayoutParams(0, dp(48), 1f));

        editButton = button("마지막 수정", Color.TRANSPARENT, PRIMARY);
        editButton.setEnabled(false);
        editButton.setOnClickListener(v -> editLast());
        editRow.addView(editButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(editRow);

        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        Button close = button("닫기", Color.TRANSPARENT, MUTED);
        close.setOnClickListener(v -> finish());
        bottomRow.addView(close, new LinearLayout.LayoutParams(0, dp(56), 1f));

        applyButton = button("입력창에 추가", PRIMARY, Color.WHITE);
        applyButton.setEnabled(false);
        applyButton.setAlpha(0.55f);
        applyButton.setOnClickListener(v -> returnResult());
        bottomRow.addView(applyButton, new LinearLayout.LayoutParams(0, dp(56), 2f));
        root.addView(bottomRow);

        setContentView(root);
    }

    private RadioButton radio(String label) {
        RadioButton button = new RadioButton(this);
        button.setId(android.view.View.generateViewId());
        button.setText(label);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(14);
        return button;
    }

    /** 선택한 말하기 길이에 맞춰 무음 감지 시간을 변경합니다. */
    private void setSpeechMode(long complete, long possible, long minimum, String label) {
        completeSilenceMs = complete;
        possibleSilenceMs = possible;
        minimumLengthMs = minimum;
        statusText.setText(label + " 모드로 설정했습니다.");
        if (recognizer != null) prepareRecognizer();
        if (!userPaused) handler.postDelayed(this::startListening, 250L);
    }

    private void prepareRecognizer() {
        destroyRecognizer();
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.setText("음성 인식 서비스를 사용할 수 없습니다.");
            listenButton.setEnabled(false);
            return;
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREA.toLanguageTag());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, completeSilenceMs);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, possibleSilenceMs);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, minimumLengthMs);
    }

    private void startListening() {
        if (destroyed || userPaused || listening) return;
        if (recognizer == null) prepareRecognizer();
        if (recognizer == null) return;
        try {
            partialText = "";
            recognizer.cancel();
            handler.postDelayed(() -> {
                if (destroyed || userPaused || recognizer == null) return;
                try {
                    recognizer.startListening(recognizerIntent);
                    listening = true;
                    statusText.setText("말씀해 주세요.");
                    listenButton.setText("잠시 멈춤");
                } catch (Exception e) {
                    scheduleRetry("음성 인식을 다시 준비하고 있습니다.");
                }
            }, 140L);
        } catch (Exception e) {
            scheduleRetry("음성 인식을 다시 준비하고 있습니다.");
        }
    }

    private void toggleListening() {
        userPaused = !userPaused;
        if (userPaused) {
            listening = false;
            if (recognizer != null) recognizer.cancel();
            volumeBar.setProgress(0);
            statusText.setText("음성 입력을 잠시 멈췄습니다.");
            listenButton.setText("계속 듣기");
        } else {
            retryCount = 0;
            listenButton.setText("잠시 멈춤");
            startListening();
        }
    }

    private void addFinal(String raw) {
        String value = clean(raw);
        if (value.isEmpty()) return;
        if (segments.isEmpty() || !isDuplicate(segments.get(segments.size() - 1), value)) segments.add(value);
        partialText = "";
        listening = false;
        retryCount = 0;
        volumeBar.setProgress(0);
        refresh();
        statusText.setText(segments.size() + "개 문장을 기록했습니다. 계속 듣는 중입니다.");
        handler.postDelayed(this::startListening, 450L);
    }

    private void updateResult(Bundle bundle, boolean finalResult) {
        ArrayList<String> values = bundle == null ? null : bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (values == null || values.isEmpty()) return;
        String best = chooseBest(values);
        if (finalResult) addFinal(best);
        else {
            partialText = clean(best);
            statusText.setText("듣는 중... 인식 내용을 확인하세요.");
            refresh();
        }
    }

    private String chooseBest(ArrayList<String> values) {
        String best = "";
        for (String candidate : values) {
            String cleaned = clean(candidate);
            if (cleaned.length() > best.length()) best = cleaned;
        }
        return best;
    }

    private void scheduleRetry(String message) {
        listening = false;
        volumeBar.setProgress(0);
        if (userPaused || destroyed) return;
        if (retryCount >= 2) {
            statusText.setText("인식이 원활하지 않습니다. ‘계속 듣기’를 눌러 다시 시도하세요.");
            userPaused = true;
            listenButton.setText("계속 듣기");
            return;
        }
        retryCount++;
        statusText.setText(message);
        handler.postDelayed(() -> {
            prepareRecognizer();
            startListening();
        }, 700L);
    }

    private void refresh() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            if (text.length() > 0) text.append("\n");
            text.append(i + 1).append(". ").append(segments.get(i));
        }
        String partial = clean(partialText);
        if (!partial.isEmpty()) {
            if (text.length() > 0) text.append("\n");
            text.append("• ").append(partial);
        }
        resultText.setText(text.length() == 0 ? "말한 내용이 여기에 실시간으로 표시됩니다." : text.toString());
        boolean hasText = !segments.isEmpty() || !partial.isEmpty();
        applyButton.setEnabled(hasText);
        applyButton.setAlpha(hasText ? 1f : 0.55f);
        removeButton.setEnabled(!segments.isEmpty());
        editButton.setEnabled(!segments.isEmpty());
    }

    private void removeLast() {
        if (!segments.isEmpty()) segments.remove(segments.size() - 1);
        refresh();
    }

    /** 마지막으로 확정된 문장을 사용자가 직접 고칠 수 있게 합니다. */
    private void editLast() {
        if (segments.isEmpty()) return;
        int index = segments.size() - 1;
        EditText input = new EditText(this);
        input.setText(segments.get(index));
        input.setSelection(input.length());
        input.setPadding(dp(18), dp(12), dp(18), dp(12));

        new AlertDialog.Builder(this)
                .setTitle("마지막 문장 수정")
                .setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> {
                    String value = clean(input.getText().toString());
                    if (value.isEmpty()) {
                        Toast.makeText(this, "문장을 입력해 주세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    segments.set(index, value);
                    refresh();
                })
                .show();
    }

    private void returnResult() {
        if (!partialText.trim().isEmpty()) addFinal(partialText);
        if (segments.isEmpty()) {
            Toast.makeText(this, "인식된 문장이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        userPaused = true;
        StringBuilder value = new StringBuilder();
        for (String segment : segments) {
            if (value.length() > 0) value.append("\n");
            value.append(segment);
        }
        Intent data = new Intent();
        data.putExtra(EXTRA_RESULT_TEXT, value.toString());
        setResult(RESULT_OK, data);
        finish();
    }

    private String clean(String raw) {
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
        return result.toString();
    }

    private boolean isDuplicate(String a, String b) {
        String left = clean(a).replace(" ", "");
        String right = clean(b).replace(" ", "");
        return left.equals(right) || (left.length() > 6 && right.contains(left))
                || (right.length() > 6 && left.contains(right));
    }

    @Override public void onReadyForSpeech(Bundle params) { statusText.setText("말씀해 주세요."); }
    @Override public void onBeginningOfSpeech() { statusText.setText("음성을 듣고 있습니다."); }

    /** 시스템이 전달한 마이크 세기를 0~100 범위로 표시합니다. */
    @Override
    public void onRmsChanged(float rmsdB) {
        int level = Math.max(0, Math.min(100, Math.round((rmsdB + 2f) * 7f)));
        volumeBar.setProgress(level);
    }

    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { listening = false; volumeBar.setProgress(0); statusText.setText("문장을 정리하고 있습니다."); }
    @Override public void onResults(Bundle results) { updateResult(results, true); }
    @Override public void onPartialResults(Bundle results) { updateResult(results, false); }
    @Override public void onEvent(int eventType, Bundle params) { }

    @Override
    public void onError(int error) {
        listening = false;
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            statusText.setText("마이크 권한이 필요합니다.");
            return;
        }
        if (error == SpeechRecognizer.ERROR_AUDIO) scheduleRetry("마이크 연결을 다시 확인하고 있습니다.");
        else if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
            scheduleRetry("음성 서비스 연결을 다시 확인하고 있습니다.");
        else scheduleRetry("잘 듣지 못해 다시 듣고 있습니다.");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_AUDIO_PERMISSION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            prepareRecognizer();
            startListening();
        } else {
            statusText.setText("설정에서 마이크 권한을 허용해 주세요.");
            listenButton.setEnabled(false);
        }
    }

    private void destroyRecognizer() {
        if (recognizer == null) return;
        try { recognizer.cancel(); } catch (Exception ignored) { }
        recognizer.destroy();
        recognizer = null;
        listening = false;
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        destroyRecognizer();
        super.onDestroy();
    }

    private Button button(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(foreground);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackgroundColor(background);
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
