package kr.co.mybrain.v2;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

/** 음성·텍스트 입력을 분석하고 저장하는 MyBrain AI v2 시작 화면입니다. */
public class MainActivity extends AppCompatActivity {

    private EditText inputText;
    private TextView previewText;
    private TextView statusText;
    private Button voiceButton;
    private ParsedWorkItem parsedItem;
    private WorkItemRepository repository;
    private ContinuousSpeechRecognizer speechRecognizer;
    private boolean listening;

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
    }

    private void createSpeechRecognizer() {
        speechRecognizer = new ContinuousSpeechRecognizer(this, new ContinuousSpeechRecognizer.Listener() {
            @Override public void onListeningStateChanged(boolean active) {
                listening = active;
                runOnUiThread(() -> {
                    voiceButton.setText(active ? "⏹ 음성 입력 끝내기" : "🎤 연속 음성으로 말하기");
                    statusText.setText(active ? "듣는 중 · 잠시 쉬어도 자동으로 계속 듣습니다." : "음성 입력 중지 · 내용을 확인하세요.");
                });
            }

            @Override public void onPartialText(String committedText, String partialText) {
                runOnUiThread(() -> inputText.setText(joinSpeech(committedText, partialText)));
            }

            @Override public void onFinalText(String committedText) {
                runOnUiThread(() -> {
                    inputText.setText(committedText);
                    inputText.setSelection(inputText.length());
                    statusText.setText("문장을 기록했습니다 · 계속 말씀하세요.");
                });
            }

            @Override public void onRecoverableError(String message) {
                runOnUiThread(() -> statusText.setText(message));
            }
        });
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 249, 253));
        root.setPadding(dp(18), dp(12), dp(18), dp(16));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(dp(18), bars.top + dp(12), dp(18), bars.bottom + dp(16));
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("MyBrain AI", 28, Color.rgb(28, 38, 52));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(54), 1f));
        Button listButton = button("저장 목록");
        listButton.setTextSize(14);
        listButton.setOnClickListener(v -> startActivity(new Intent(this, WorkItemListActivity.class)));
        header.addView(listButton, new LinearLayout.LayoutParams(dp(104), dp(48)));
        root.addView(header);

        root.addView(text("말하거나 입력하면 일정·할 일·메모로 자동 정리합니다.", 15, Color.rgb(102, 116, 138)));

        inputText = new EditText(this);
        inputText.setHint("예: 내일 오전 9시부터 12시까지 교무회의");
        inputText.setTextSize(17);
        inputText.setGravity(Gravity.TOP | Gravity.START);
        inputText.setPadding(dp(16), dp(14), dp(16), dp(14));
        inputText.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(145));
        inputParams.setMargins(0, dp(16), 0, dp(10));
        root.addView(inputText, inputParams);

        voiceButton = button("🎤 연속 음성으로 말하기");
        voiceButton.setOnClickListener(v -> toggleVoiceInput());
        root.addView(voiceButton, new LinearLayout.LayoutParams(-1, dp(54)));

        Button clearButton = button("입력 내용 지우기");
        clearButton.setOnClickListener(v -> clearInput());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(-1, dp(48));
        clearParams.setMargins(0, dp(6), 0, 0);
        root.addView(clearButton, clearParams);

        Button analyzeButton = button("✨ 내용 분석하기");
        analyzeButton.setOnClickListener(v -> analyzeInput());
        LinearLayout.LayoutParams analyzeParams = new LinearLayout.LayoutParams(-1, dp(54));
        analyzeParams.setMargins(0, dp(8), 0, 0);
        root.addView(analyzeButton, analyzeParams);

        previewText = text("분석 결과가 여기에 표시됩니다.", 15, Color.rgb(28, 38, 52));
        previewText.setPadding(dp(14), dp(14), dp(14), dp(14));
        previewText.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        previewParams.setMargins(0, dp(12), 0, dp(8));
        root.addView(previewText, previewParams);

        Button editButton = button("✏️ 분석 결과 확인·수정");
        editButton.setOnClickListener(v -> openEditor());
        root.addView(editButton, new LinearLayout.LayoutParams(-1, dp(50)));

        Button saveButton = button("저장하기");
        saveButton.setOnClickListener(v -> saveParsedItem());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(54));
        saveParams.setMargins(0, dp(6), 0, 0);
        root.addView(saveButton, saveParams);

        statusText = text("6단계 · 저장 항목 관리 화면 연결", 13, Color.rgb(102, 116, 138));
        statusText.setPadding(0, dp(10), 0, 0);
        root.addView(statusText);
        return root;
    }

    private void clearInput() {
        if (speechRecognizer != null) speechRecognizer.clearText();
        inputText.setText("");
        parsedItem = null;
        previewText.setText("분석 결과가 여기에 표시됩니다.");
        statusText.setText("입력 내용을 지웠습니다.");
    }

    private void toggleVoiceInput() {
        if (listening) {
            speechRecognizer.stop();
            analyzeInput();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "이 기기에서 음성인식 서비스를 사용할 수 없습니다.", Toast.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startContinuousVoice();
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startContinuousVoice() {
        parsedItem = null;
        speechRecognizer.clearText();
        inputText.setText("");
        speechRecognizer.start();
    }

    private String joinSpeech(String committedText, String partialText) {
        String committed = committedText == null ? "" : committedText.trim();
        String partial = partialText == null ? "" : partialText.trim();
        if (committed.isEmpty()) return partial;
        if (partial.isEmpty()) return committed;
        return committed + " " + partial;
    }

    private void analyzeInput() {
        String value = inputText.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "분석할 내용을 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        parsedItem = KoreanNaturalLanguageParser.parse(value, ZoneId.systemDefault());
        previewText.setText(formatResult(parsedItem));
        statusText.setText("분석 완료 · 필요하면 결과를 수정하세요.");
    }

    private void openEditor() {
        if (parsedItem == null) analyzeInput();
        if (parsedItem == null) return;
        WorkItemEditorDialog dialog = new WorkItemEditorDialog(parsedItem, item -> {
            parsedItem = item;
            previewText.setText(formatResult(item));
            statusText.setText("수정 사항 적용 완료 · 저장할 수 있습니다.");
        });
        dialog.show(getSupportFragmentManager(), "work_item_editor");
    }

    private void saveParsedItem() {
        if (listening) speechRecognizer.stop();
        if (parsedItem == null) analyzeInput();
        if (parsedItem == null) return;
        repository.insert(parsedItem.toEntity(), id -> runOnUiThread(() -> {
            Toast.makeText(this, "MyBrain에 저장했습니다.", Toast.LENGTH_SHORT).show();
            clearInput();
            statusText.setText("저장 완료 · 저장 목록에서 확인할 수 있습니다.");
        }));
    }

    private String formatResult(ParsedWorkItem item) {
        return "분류: " + typeLabel(item.type)
                + "\n제목: " + item.title
                + "\n시작: " + formatTime(item.startAt)
                + "\n종료: " + formatTime(item.endAt)
                + "\n종일: " + (item.allDay ? "예" : "아니오")
                + "\n반복: " + repeatLabel(item.repeatRule)
                + "\n중요도: " + priorityLabel(item.priority)
                + "\n분석 신뢰도: " + Math.round(item.confidence * 100) + "%";
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

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(16);
        button.setAllCaps(false);
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (listening && speechRecognizer != null) speechRecognizer.stop();
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        super.onDestroy();
    }
}
