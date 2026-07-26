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

public class MainActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(246, 248, 252);
    private static final int TEXT = Color.rgb(24, 34, 48);
    private static final int SUBTEXT = Color.rgb(91, 106, 128);
    private static final int PRIMARY = Color.rgb(45, 91, 255);
    private static final int BORDER = Color.rgb(218, 224, 234);
    private static final int DANGER = Color.rgb(218, 53, 69);

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

    private final Handler autoAnalyzeHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoAnalyzeRunnable = () -> analyzeInput(false);

    private final ActivityResultLauncher<String> microphonePermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startContinuousVoice();
                else Toast.makeText(this, "음성 입력을 사용하려면 마이크 권한이 필요합니다.", Toast.LENGTH_LONG).show();
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
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
            @Override public void onPartialText(String committed, String partial) {
                runOnUiThread(() -> setInputText(joinSpeech(committed, partial)));
            }
            @Override public void onFinalText(String committed) {
                runOnUiThread(() -> {
                    setInputText(committed);
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
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            root.setPadding(dp(18), bars.top + dp(12), dp(18), bars.bottom + dp(30));
            return insets;
        });

        root.addView(text("MyBrain AI", 30, TEXT, true));
        TextView subtitle = text("말하거나 입력하면 AI가 자동으로 정리합니다.", 15, SUBTEXT, false);
        subtitle.setPadding(0, dp(2), 0, dp(16));
        root.addView(subtitle);

        LinearLayout nav = new LinearLayout(this);
        Button calendar = secondaryButton("📅  일정·오늘");
        calendar.setOnClickListener(v -> startActivity(new Intent(this, CalendarActivity.class)));
        Button list = secondaryButton("☰  저장 목록");
        list.setOnClickListener(v -> startActivity(new Intent(this, WorkItemListActivity.class)));
        LinearLayout.LayoutParams n1 = new LinearLayout.LayoutParams(0, dp(50), 1f);
        n1.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams n2 = new LinearLayout.LayoutParams(0, dp(50), 1f);
        n2.setMargins(dp(5), 0, 0, 0);
        nav.addView(calendar, n1); nav.addView(list, n2); root.addView(nav);

        LinearLayout inputCard = card();
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, dp(16), 0, 0);
        root.addView(inputCard, cp);
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
            @Override public void afterTextChanged(Editable s) {}
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
        });
        inputCard.addView(inputText, new LinearLayout.LayoutParams(-1, -2));

        waveText = text("▂  ▅  ▇  ▃  ▆  ▂  ▅", 22, PRIMARY, true);
        waveText.setGravity(Gravity.CENTER);
        waveText.setPadding(0, dp(12), 0, 0);
        waveText.setVisibility(View.GONE);
        inputCard.addView(waveText);

        voiceButton = primaryButton("🎤  음성으로 입력하기");
        voiceButton.setOnClickListener(v -> toggleVoiceInput());
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, dp(54));
        vp.setMargins(0, dp(12), 0, 0);
        inputCard.addView(voiceButton, vp);

        LinearLayout actions = new LinearLayout(this);
        Button clear = secondaryButton("지우기");
        clear.setOnClickListener(v -> clearInput());
        Button analyze = secondaryButton("즉시 분석");
        analyze.setOnClickListener(v -> analyzeInput(true));
        LinearLayout.LayoutParams a1 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        a1.setMargins(0, dp(9), dp(5), 0);
        LinearLayout.LayoutParams a2 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        a2.setMargins(dp(5), dp(9), 0, 0);
        actions.addView(clear, a1); actions.addView(analyze, a2); inputCard.addView(actions);

        LinearLayout resultCard = card();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.setMargins(0, dp(14), 0, 0);
        root.addView(resultCard, rp);
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
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(50));
        ep.setMargins(0, dp(10), 0, 0);
        resultCard.addView(editButton, ep);

        saveButton = primaryButton("✓  저장하기");
        saveButton.setOnClickListener(v -> saveParsedItem());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(56));
        sp.setMargins(0, dp(10), 0, 0);
        root.addView(saveButton, sp);
        Button quick = primaryButton("＋  빠른 추가");
        quick.setOnClickListener(v -> showQuickAdd());
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(-1, dp(52));
        qp.setMargins(0, dp(10), 0, 0);
        root.addView(quick, qp);

        statusText = text("입력하거나 음성 버튼을 눌러 시작하세요.", 13, SUBTEXT, false);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(dp(6), dp(12), dp(6), 0);
        root.addView(statusText);
        return scroll;
    }

    private void showQuickAdd() {
        String[] items = {"새 일정", "새 할 일", "새 메모", "음성으로 입력"};
        new AlertDialog.Builder(this).setTitle("빠른 추가").setItems(items, (d, which) -> {
            if (which == 3) { toggleVoiceInput(); return; }
            setInputText(which == 0 ? "일정: " : which == 1 ? "할 일: " : "메모: ");
            inputText.requestFocus();
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) manager.showSoftInput(inputText, InputMethodManager.SHOW_IMPLICIT);
        }).show();
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
    }

    private void toggleVoiceInput() {
        hideKeyboard();
        if (listening) { speechRecognizer.stop(); analyzeInput(false); return; }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "이 기기에서 음성인식 서비스를 사용할 수 없습니다.", Toast.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            startContinuousVoice();
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO);
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

    private String joinSpeech(String committed, String partial) {
        String a = committed == null ? "" : committed.trim();
        String b = partial == null ? "" : partial.trim();
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a + " " + b;
    }

    private void analyzeInput(boolean manual) {
        autoAnalyzeHandler.removeCallbacks(autoAnalyzeRunnable);
        if (listening) return;
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
        new WorkItemEditorDialog(parsedItem, item -> {
            parsedItem = item;
            previewText.setText(formatResult(item));
            resultLabel.setText("AI 분석 결과 · " + typeLabel(item.type));
            updateActionState(true);
        }).show(getSupportFragmentManager(), "work_item_editor");
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
        editButton.setEnabled(ready); saveButton.setEnabled(ready);
        editButton.setAlpha(ready ? 1f : .45f); saveButton.setAlpha(ready ? 1f : .45f);
    }

    private void startWaveAnimation() {
        stopWaveAnimation();
        waveAnimator = ObjectAnimator.ofFloat(waveText, View.ALPHA, .35f, 1f);
        waveAnimator.setDuration(550);
        waveAnimator.setRepeatMode(ObjectAnimator.REVERSE);
        waveAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        waveAnimator.start();
    }

    private void stopWaveAnimation() {
        if (waveAnimator != null) { waveAnimator.cancel(); waveAnimator = null; }
        if (waveText != null) waveText.setAlpha(1f);
    }

    private String formatResult(ParsedWorkItem i) {
        return "제목  " + i.title + "\n분류  " + typeLabel(i.type)
                + "\n시작  " + formatTime(i.startAt) + "\n종료  " + formatTime(i.endAt)
                + "\n반복  " + repeatLabel(i.repeatRule) + "\n중요도  " + priorityLabel(i.priority)
                + "\n신뢰도  " + Math.round(i.confidence * 100) + "%";
    }

    private String typeLabel(String type) {
        if (WorkItemEntity.TYPE_SCHEDULE.equals(type)) return "일정";
        if (WorkItemEntity.TYPE_TASK.equals(type)) return "할 일";
        return "메모";
    }
    private String repeatLabel(String v) {
        if ("DAILY".equals(v)) return "매일";
        if ("WEEKDAYS".equals(v)) return "평일";
        if ("WEEKLY".equals(v)) return "매주";
        if ("MONTHLY".equals(v)) return "매월";
        return "없음";
    }
    private String priorityLabel(String v) {
        if ("LOW".equals(v)) return "낮음";
        if ("HIGH".equals(v)) return "높음";
        return "보통";
    }
    private String formatTime(Long millis) {
        if (millis == null) return "없음";
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy.MM.dd (E) HH:mm", Locale.KOREA));
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(14), dp(14), dp(14), dp(14));
        v.setBackground(rounded(Color.WHITE, 18, BORDER));
        return v;
    }
    private TextView sectionTitle(String value) {
        TextView v = text(value, 17, TEXT, true);
        v.setPadding(0, 0, 0, dp(10)); return v;
    }
    private Button primaryButton(String value) {
        Button b = new Button(this); b.setText(value); b.setTextSize(16); b.setTextColor(Color.WHITE);
        b.setTypeface(null, Typeface.BOLD); b.setAllCaps(false); b.setGravity(Gravity.CENTER);
        b.setBackground(rounded(PRIMARY, 14, 0)); b.setStateListAnimator(null); return b;
    }
    private Button secondaryButton(String value) {
        Button b = new Button(this); b.setText(value); b.setTextSize(15); b.setTextColor(TEXT);
        b.setAllCaps(false); b.setGravity(Gravity.CENTER); b.setBackground(rounded(Color.WHITE, 14, BORDER));
        b.setStateListAnimator(null); return b;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(null, Typeface.BOLD); return v;
    }
    private GradientDrawable rounded(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke); return d;
    }
    private void hideKeyboard() {
        View focused = getCurrentFocus(); if (focused == null) return;
        InputMethodManager m = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (m != null) m.hideSoftInputFromWindow(focused.getWindowToken(), 0);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onStop() {
        super.onStop(); autoAnalyzeHandler.removeCallbacks(autoAnalyzeRunnable);
        if (listening && speechRecognizer != null) speechRecognizer.stop();
    }
    @Override protected void onDestroy() {
        autoAnalyzeHandler.removeCallbacksAndMessages(null); stopWaveAnimation();
        if (speechRecognizer != null) speechRecognizer.destroy(); super.onDestroy();
    }
}
