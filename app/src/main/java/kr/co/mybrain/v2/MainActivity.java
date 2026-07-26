package kr.co.mybrain.v2;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognizerIntent;
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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

import kr.co.mybrain.v2.assistant.KoreanNaturalLanguageParser;
import kr.co.mybrain.v2.assistant.ParsedWorkItem;
import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.data.WorkItemRepository;

/** 음성·텍스트 입력을 분석하고 저장하는 MyBrain AI v2 시작 화면입니다. */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_SPEECH = 2001;
    private EditText inputText;
    private TextView previewText;
    private TextView statusText;
    private ParsedWorkItem parsedItem;
    private WorkItemRepository repository;

    private final ActivityResultLauncher<String> microphonePermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startVoiceInput();
                else Toast.makeText(this, "음성 입력을 사용하려면 마이크 권한이 필요합니다.", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = WorkItemRepository.getInstance(this);
        setContentView(buildScreen());
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 249, 253));
        root.setPadding(dp(18), dp(12), dp(18), dp(16));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int mask = WindowInsetsCompat.Type.systemBars();
            androidx.core.graphics.Insets bars = insets.getInsets(mask);
            view.setPadding(dp(18), bars.top + dp(12), dp(18), bars.bottom + dp(16));
            return insets;
        });

        TextView title = text("MyBrain AI", 28, Color.rgb(28, 38, 52));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(54)));
        root.addView(text("말하거나 입력하면 일정·할 일·메모로 자동 정리합니다.", 15, Color.rgb(102, 116, 138)));

        inputText = new EditText(this);
        inputText.setHint("예: 내일 오전 9시부터 12시까지 교무회의");
        inputText.setTextSize(17);
        inputText.setGravity(Gravity.TOP | Gravity.START);
        inputText.setPadding(dp(16), dp(14), dp(16), dp(14));
        inputText.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(130));
        inputParams.setMargins(0, dp(16), 0, dp(10));
        root.addView(inputText, inputParams);

        Button voiceButton = button("🎤 음성으로 말하기");
        voiceButton.setOnClickListener(v -> requestVoiceInput());
        root.addView(voiceButton, new LinearLayout.LayoutParams(-1, dp(54)));

        Button analyzeButton = button("✨ 내용 분석하기");
        analyzeButton.setOnClickListener(v -> analyzeInput());
        LinearLayout.LayoutParams analyzeParams = new LinearLayout.LayoutParams(-1, dp(54));
        analyzeParams.setMargins(0, dp(8), 0, 0);
        root.addView(analyzeButton, analyzeParams);

        previewText = text("분석 결과가 여기에 표시됩니다.", 15, Color.rgb(28, 38, 52));
        previewText.setPadding(dp(14), dp(14), dp(14), dp(14));
        previewText.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        previewParams.setMargins(0, dp(12), 0, dp(10));
        root.addView(previewText, previewParams);

        Button saveButton = button("저장하기");
        saveButton.setOnClickListener(v -> saveParsedItem());
        root.addView(saveButton, new LinearLayout.LayoutParams(-1, dp(54)));

        statusText = text("3단계 · 자연어 분석 및 Room 저장", 13, Color.rgb(102, 116, 138));
        statusText.setPadding(0, dp(10), 0, 0);
        root.addView(statusText);
        return root;
    }

    private void analyzeInput() {
        String value = inputText.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "분석할 내용을 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        parsedItem = KoreanNaturalLanguageParser.parse(value, ZoneId.systemDefault());
        previewText.setText(formatResult(parsedItem));
        statusText.setText("분석 완료 · 내용을 확인한 뒤 저장하세요.");
    }

    private void saveParsedItem() {
        if (parsedItem == null) analyzeInput();
        if (parsedItem == null) return;
        repository.insert(parsedItem.toEntity(), id -> runOnUiThread(() -> {
            statusText.setText("저장 완료 · 항목 번호 " + id);
            Toast.makeText(this, "MyBrain에 저장했습니다.", Toast.LENGTH_SHORT).show();
            parsedItem = null;
            inputText.setText("");
            previewText.setText("분석 결과가 여기에 표시됩니다.");
        }));
    }

    private String formatResult(ParsedWorkItem item) {
        StringBuilder value = new StringBuilder();
        value.append("분류: ").append(typeLabel(item.type));
        value.append("\n제목: ").append(item.title);
        value.append("\n시작: ").append(formatTime(item.startAt));
        value.append("\n종료: ").append(formatTime(item.endAt));
        value.append("\n반복: ").append(item.repeatRule);
        value.append("\n중요도: ").append(item.priority);
        value.append("\n분석 신뢰도: ").append(Math.round(item.confidence * 100)).append("%");
        return value.toString();
    }

    private String typeLabel(String type) {
        if (WorkItemEntity.TYPE_SCHEDULE.equals(type)) return "일정";
        if (WorkItemEntity.TYPE_TASK.equals(type)) return "할 일";
        return "메모";
    }

    private String formatTime(Long millis) {
        if (millis == null) return "없음";
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy.MM.dd (E) HH:mm", Locale.KOREA));
    }

    private void requestVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "이 기기에서 음성인식 서비스를 사용할 수 없습니다.", Toast.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoiceInput();
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO);
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREA.toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "일정이나 할 일을 자연스럽게 말씀하세요.");
        try {
            startActivityForResult(intent, REQUEST_SPEECH);
            statusText.setText("음성을 듣고 있습니다…");
        } catch (Exception error) {
            Toast.makeText(this, "음성인식 화면을 열 수 없습니다.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SPEECH || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return;
        inputText.setText(results.get(0));
        statusText.setText("음성 인식 완료 · 자동 분석합니다.");
        analyzeInput();
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
}
