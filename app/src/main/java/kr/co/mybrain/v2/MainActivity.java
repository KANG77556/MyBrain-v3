package kr.co.mybrain.v2;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import kr.co.mybrain.v2.assistant.KoreanNaturalLanguageParser;
import kr.co.mybrain.v2.assistant.ParsedWorkItem;
import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.data.WorkItemRepository;
import kr.co.mybrain.v2.ui.WorkItemEditorDialog;
import kr.co.mybrain.v2.voice.ContinuousSpeechRecognizer;

/** 음성·텍스트 입력을 자동 분석하고 저장하는 MyBrain AI 시작 화면입니다. */
public class MainActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(246, 248, 252);
    private static final int TEXT = Color.rgb(24, 34, 48);
    private static final int SUBTEXT = Color.rgb(91, 106, 128);
    private static final int PRIMARY = Color.rgb(45, 91, 255);
    private static final int BORDER = Color.rgb(218, 224, 234);
    private static final int DANGER = Color.rgb(218, 53, 69);

    private final Handler autoAnalyzeHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoAnalyzeRunnable = () -> {
        if (!listening && inputText != null && inputText.getText().toString().trim().length() >= 3) {
            analyzeInput(false);
        }
    };

    private EditText inputText;
    private TextView previewText;
    private TextView statusText;
    private TextView resultLabel;
    private TextView waveText;
    private Button voiceButton;
    private Button editButton;
    private Button saveButton;
    private ParsedWorkItem parsedItem;
    private WorkItemRepository repository;
    private ContinuousSpeechRecognizer speechRecognizer;
    private ObjectAnimator waveAnimator;
    private boolean listening;
    private boolean internalTextChange;

    private final ActivityResultLauncher<String> microphonePermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startContinuousVoice();
                else Toast.makeText(this, "음성 입력을 사용하려면 마이크 권한이 필요합니다.", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = WorkItemRepository.getInstance(this);
        setContentView(buildScreen());
        createSpeechRecognizer();
        updateActionState(false);
    }

    private void createSpeechRecognizer() {
        speechRecognizer = new ContinuousSpeechRecognizer(this, new ContinuousSpeechRecognizer.Listener() {
            @Override public void onListeningStateChanged(boolean active) {
                listening = active;
                runOnUiThread(() -> {
                    voiceButton.setText(active ? "■  음성 입력 끝내기" : "🎤  음성으로 입력하기");
                    voiceButton.setBackground(rounded(active ? DANGER : PRIMARY, 14, 0));
                    waveText.setVisibility(active ? View.VISIBLE : View.GONE);
                    if (active) startWaveAnimation(); else stopWaveAnimation();
                    statusText.setText(active ? "듣는 중입니다. 자연스럽게 말씀하세요." : "음성 입력을 마쳤습니다.");
                });
            }

            @Override public void onPartialText(String committedText, String partialText) {
                runOnUiThread(() -> setInputText(joinSpeech(committedText, partialText)));
            }

            @Override public void onFinalText(String committedText) {
                runOnUiThread(() -> {
                    setInputText(committedText);
                    if (listening) statusText.setText("문장을 기록했습니다. 계속 말씀하셔도 됩니다.");
                });
            }

            @Override public void onRecoverableError(String message) {
                runOnUiThread(() -> statusText.setText(message));
            }
        });
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            root.setPadding(dp(18), bars.top + dp(12), dp(18), bars.bottom + dp(30));
            return insets;
        });

        TextView title = text("MyBrain AI", 30, TEXT, true);
        root.addView(title);
        TextView subtitle = text("말하거나 입력하면 AI가 자동으로 정리합니다.", 15, SUBTEXT, false);
        subtitle.setPadding(0, dp(2), 0, dp(16));
        root.addView(subtitle);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        Button calendarButton = secondaryButton("📅  일정·오늘");
        calendarButton.setOnClickListener(v -> startActivity(new Intent(this, CalendarActivity.class)));
        Button listButton = secondaryButton("☰  저장 목록");
        listButton.setOnClickListener(v -> startActivity(new Intent(this, WorkItemListActivity.class)));
        LinearLayout.LayoutParams navLeft = new LinearLayout.LayoutParams(0, dp(50), 1f);
        navLeft.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams navRight = new LinearLayout.LayoutParams(0, dp(50), 1f);
        navRight.setMargins(dp(5), 0, 0, 0);
        navigation.addView(calendarButton, navLeft);
        navigation.addView(listButton, navRight);
        root.addView(navigation);

        LinearLayout inputCard = card();
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, dp(16), 0, 0);
        root.addView(inputCard, cardParams);
        inputCard.addView(sectionTitle("무엇을 기억할까요?"));

        inputText = new EditText(this);
        inputText.setHint("예: 내일 오전 9시 교무회의");
        inputText.setHintTextColor(Color.rgb(125, 136, 153));
        inputText.setTextColor(TEXT);
        inputText.setTextSize(18);
        inputText.setGravity(Gravity.TOP | Gravity.START);
        inputText.setPadding(dp(14), dp(13), dp(14), dp(13));
        inputText.setMinHeight(dp(132));
        inputText.setBackground(rounded(Color.WHITE, 12, BORDER));
        inputText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (internalTextChange || listening) return;
                parsedItem = null;
                updateActionState(false);
                autoAnalyzeHandler.removeCallbacks(autoAnalyzeRunnable);
                if (s.toString().trim().length() >= 3) {
                    statusText.setText("입력이 끝나면 자동으로 분석합니다.");
                    autoAnalyzeHandler.postDelayed(autoAnalyzeRunnable, 900);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        inputCard.addView(inputText, new LinearLayout.LayoutParams(-1, -2));

        waveText = text("▂  ▅  ▇  ▃  ▆  ▂  ▅", 22, PRIMARY, true);
        waveText.setGravity(Gravity.CENTER);
        waveText.setPadding(0, dp(12), 0, 0);
        waveText.setVisibility(View.GONE);
        inputCard.addView(waveText);

        voiceButton = primaryButton("🎤  음성으로 입력하기");
        voiceButton.setOnClickListener(v -> toggleVoiceInput());
        LinearLayout.LayoutParams voiceParams = new LinearLayout.LayoutParams(-1, dp(54));
        voiceParams.setMargins(0, dp(12), 0, 0);
        inputCard.addView(voiceButton, voiceParams);

        LinearLayout actions = new LinearLayout(this);
        Button clearButton = secondaryButton("지우기");
        clearButton.setOnClickListener(v -> clearInput());
        Button analyzeButton = secondaryButton("즉시 분석");
        analyzeButton.setOnClickListener(v -> analyzeInput(true));
        LinearLayout.LayoutParams halfLeft = new LinearLayout.LayoutParams(0, dp(48), 1f);
        halfLeft.setMargins(0, dp(9), dp(5), 0);
        LinearLayout.LayoutParams halfRight = new LinearLayout.LayoutParams(0, dp(48), 1f);
        halfRight.setMargins(dp(5), dp(9), 0, 0);
        actions.addView(clearButton, halfLeft);
        actions.addView(analyzeButton, halfRight);
        inputCard.addView(actions);

        LinearLayout resultCard = card();
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(-1, -2);
        resultParams.setMargins(0, dp(14), 0, 0);
        root.addView(resultCard, resultParams);
        resultLabel = sectionTitle("AI 분석 결과");
        resultCard.addView(resultLabel);
        previewText = text("내용을 입력하면 자동으로 분석됩니다.", 15, SUBTEXT, false);
        previewText.setLineSpacing(dp(3), 1f);
        previewText.setPadding(dp(14), dp(13), dp(14), dp(13));
        previewText.setBackground(rounded(Color.rgb(250, 251, 253), 12, BORDER));
        previewText.setMinHeight(dp(112));
        resultCard.addView(previewText, new LinearLayout.LayoutParams(-1, -2));

        editButton = secondaryButton("✏️  결과 확인·수정");
        editButton.setOnClickListener(v -> openEditor());
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(-1, dp(50));
        editParams.setMargins(0, dp(10), 0, 0);
        resultCard.addView(editButton, editParams);

        saveButton = primaryButton("✓  저장하기");
        saveButton.setOnClickListener(v -> saveParsedItem());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(56));
        saveParams.setMargins(0, dp(10), 0, 0);
        root.addView(saveButton, saveParams);

        Button quickAddButton = primaryButton("＋  빠른 추가");
        quickAddButton.setOnClickListener(v -> showQuickAdd());
        LinearLayout.LayoutParams quickParams = new LinearLayout.LayoutParams(-1, dp(52));
        quickParams.setMargins(0, dp(10), 0, 0);
        root.addView(quickAddButton, quickParams);

        statusText = text("입력하거나 음성 버튼을 눌러 시작하세요.", 13, SUBTEXT, false);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(dp(6), dp(12), dp(6), 0);
        root.addView(statusText);
        return scroll;
    }

    private void showQuickAdd() {
        String[] items = {"새 일정", "새 할 일", "새 메모", "음성으로 입력"};
        new AlertDialog.Builder(this)
                .setTitle("빠른 추가")
                .setItems(items, (dialog, which) -> {
                    if (which == 3) {
                        toggleVoiceInput();
                        return;
                    }
                    String prefix = which == 0 ? "일정: " : which == 1 ? "할 일: " : "메모: ";
                    setInputText(prefix);
                    inputText.requestFocus();
                    inputText.setSelection(inputText.length());
                    InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (manager != null) manager.showSoftInput(inputText, InputMethodManager.SHOW_IMPLICIT);
                }).show();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(Color.WHITE, 18, BORDER));
        return card;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 17, TEXT, true);
        view.setPadding(0, 0, 0, dp(10));
        return view;
    }

    private void clearInput() {
        autoAnalyzeHandler.removeCallbacks(autoAnalyzeRunnable);
        if (listening && speechRecognizer != null) speechRecognizer.stop();
        if (speechRecognizer != null) speechRecognizer.clearText();
        setInputText("");
        parsedItem = null;
        previewText.setText("내용을 입력하면 자동으로 분석됩니다.");
        previewText.setTextColor(SUBTEXT);
        resultLabel.setText("AI 분석 결과");
        statusText.setText("입력 내용을 지웠습니다.");
        updateActionState(false);
        inputText.requestFocus();
    }

    private void toggleVoiceInput() {
        hideKeyboard();
        if (listening) {
            speechRecognizer.stop();
            analyzeInput(false);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "이 기기에서 음성인식 서비스를 사용할 수 없습니다.", Toast.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startContinuousVoice();
        } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO);
    }

    private void startContinuousVoice() {
        autoAnalyzeHandler.removeCallbacks(autoAnalyzeRunnable);
        parsedItem = null;
        updateActionState(false);
        speechRecognizer.clearText();
        setInputText("");
        previewText.setText("말을 마치고 음성 입력을 끝내면 자동 분석합니다.");
        previewText.setTextColor(SUBTEXT);
        speechRecognizer.start();
    }

    private void setInputText(String value) {
        internalTextChange = true;
        inputText.setText(value == null ? "" : value);
        inputText.setSelection(inputText.length());
        internalTextChange = false;
    }

    private String joinSpeech(String committedText, String partialText) {
        String committed = committedText == null ? "" : committedText.trim();
        String partial = partialText == null ? "" : partialText.trim();
        if (committed.isEmpty()) return partial;
        if (partial.isEmpty()) return committed;
        return committed + " " + partial;
    }

    private void analyzeInput(boolean manual) {
        autoAnalyzeHandler.removeCallbacks(autoAnalyzeRunnable);
        String value = inputText.getText().toString().trim();
        if (value.isEmpty()) {
            if (manual) Toast.makeText(this, "분석할 내용을 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        statusText.setText("AI가 내용을 분석하고 있습니다…");
        parsedItem = KoreanNaturalLanguageParser.parse(value, ZoneId.systemDefault());
        previewText.setText(formatResult(parsedItem));
        previewText.setTextColor(TEXT);
        resultLabel.setText("AI 분석 결과 · " + typeLabel(parsedItem.type));
        statusText.setText("분석 완료 · 확인 후 저장하세요.");
        updateActionState(true);
        hideKeyboard();
    }

    private void openEditor() {
        if (parsedItem == null) analyzeInput(true);
        if (parsedItem == null) return;
        WorkItemEditorDialog dialog = new WorkItemEditorDialog(parsedItem, item -> {
            parsedItem = item;
            previewText.setText(formatResult(item));
            previewText.setTextColor(TEXT);
            resultLabel.setText("AI 분석 결과 · " + typeLabel(item.type));
            statusText.setText("수정 사항을 적용했습니다.");
            updateActionState(true);
        });
        dialog.show(getSupportFragmentManager(), "work_item_editor");
    }

    private void saveParsedItem() {
        if (listening) speechRecognizer.stop();
        if (parsedItem == null) analyzeInput(true);
        if (parsedItem == null) return;
        saveButton.setEnabled(false);
        saveButton.setText("저장 중…");
        repository.insert(parsedItem.toEntity(), id -> runOnUiThread(() -> {
            Toast.makeText(this, "저장되었습니다 ✓", Toast.LENGTH_SHORT).show();
            clearInput();
            saveButton.setText("✓  저장하기");
            statusText.setText("저장 완료 · 일정과 저장 목록에서 확인할 수 있습니다.");
        }));
    }

    private void updateActionState(boolean ready) {
        if (editButton == null || saveButton == null) return;
        editButton.setEnabled(ready);
        saveButton.setEnabled(ready);
        editButton.setAlpha(ready ? 1f : 0.45f);
        saveButton.setAlpha(ready ? 1f : 0.45f);
    }

    private void startWaveAnimation() {
        stopWaveAnimation();
        waveAnimator = ObjectAnimator.ofFloat(waveText, View.ALPHA, 0.35f, 1f);
        waveAnimator.setDuration(550);
        waveAnimator.setRepeatMode(ObjectAnimator.REVERSE);
        waveAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        waveAnimator.start();
    }

    private void stopWaveAnimation() {
        if (waveAnimator != null) {
            waveAnimator.cancel();
            waveAnimator = null;
        }
        if (waveText != null) waveText.setAlpha(1f);
    }

    private String formatResult(ParsedWorkItem item) {
        return "제목  " + item.title
                + "\n분류  " + typeLabel(item.type)
                + "\n시작  " + formatTime(item.startAt)
                + "\n종료  " + formatTime(item.endAt)
                + "\n반복  " + repeatLabel(item.repeatRule)
                + "\n중요도  " + priorityLabel(item.priority)
                + "\n신뢰도  " + Math.round(item.confidence * 100) + "%";
    }

    private String typeLabel(String type) {
        if (WorkItemEntity.TYPE_SCHEDULE.equals(type)) return "일정";
        if (WorkItemEntity.TYPE_TASK.equals(type)) return "할 일";
        return "메모";
    }

    private String repeatLabel(String value) {
        if ("DAILY".equals(value)) return "매일";
        if ("WEEKDAYS".equals(value)) return "평일";
        if ("WEEKLY".equals(value)) return "매주";
        if ("MONTHLY".equals(value)) return "매월";
        return "없음";
    }

    private String priorityLabel(String value) {
        if ("LOW".equals(value)) return "낮음";
        if ("HIGH".equals(value)) return "높음";
        return "보통";
    }

    private String formatTime(Long millis) {
        if (millis == null) return "없음";
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy.MM.dd (E) HH:mm", Locale.KOREA));
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setTypeface(null, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(PRIMARY, 14, 0));
        button.setStateListAnimator(null);
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(15);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(Color.WHITE, 14, BORDER));
        button.setStateListAnimator(null);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private GradientDrawable rounded(int fill, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) return;
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onStop() {
        super.onStop();
        autoAnalyzeHandler.removeCallbacks(autoAnalyzeRunnable);
        if (listening && speechRecognizer != null) speechRecognizer.stop();
    }

    @Override
    protected void onDestroy() {
        autoAnalyzeHandler.removeCallbacksAndMessages(null);
        stopWaveAnimation();
        if (speechRecognizer != null) speechRecognizer.destroy();
        super.onDestroy();
    }
}
