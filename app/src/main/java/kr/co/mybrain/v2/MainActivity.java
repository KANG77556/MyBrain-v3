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

import java.util.ArrayList;
import java.util.Locale;

/** MyBrain AI v2의 단일 시작 화면입니다. */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_SPEECH = 2001;
    private TextView inputText;
    private TextView statusText;

    private final ActivityResultLauncher<String> microphonePermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startVoiceInput();
                else Toast.makeText(this, "음성 입력을 사용하려면 마이크 권한이 필요합니다.", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildScreen());
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 249, 253));
        root.setPadding(dp(18), dp(12), dp(18), dp(16));

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            WindowInsetsCompat.Type.InsetsTypeMask mask = WindowInsetsCompat.Type.systemBars();
            androidx.core.graphics.Insets bars = insets.getInsets(mask);
            view.setPadding(dp(18), bars.top + dp(12), dp(18), bars.bottom + dp(16));
            return insets;
        });

        TextView title = text("MyBrain AI", 28, Color.rgb(28, 38, 52));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView guide = text("말하거나 입력하면 일정·할 일·메모로 정리합니다.", 15, Color.rgb(102, 116, 138));
        root.addView(guide);

        inputText = text("아직 입력된 내용이 없습니다.", 18, Color.rgb(28, 38, 52));
        inputText.setGravity(Gravity.TOP | Gravity.START);
        inputText.setPadding(dp(16), dp(16), dp(16), dp(16));
        inputText.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        inputParams.setMargins(0, dp(18), 0, dp(14));
        root.addView(inputText, inputParams);

        Button voiceButton = new Button(this);
        voiceButton.setText("🎤 음성으로 말하기");
        voiceButton.setTextSize(17);
        voiceButton.setAllCaps(false);
        voiceButton.setOnClickListener(v -> requestVoiceInput());
        root.addView(voiceButton, new LinearLayout.LayoutParams(-1, dp(58)));

        Button assistantButton = new Button(this);
        assistantButton.setText("✨ AI 비서에게 정리 요청");
        assistantButton.setTextSize(16);
        assistantButton.setAllCaps(false);
        assistantButton.setOnClickListener(v -> {
            String value = inputText.getText().toString().trim();
            if (value.isEmpty() || value.startsWith("아직")) {
                Toast.makeText(this, "먼저 내용을 말하거나 입력하세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            statusText.setText("AI 분석 계층 연결 준비 완료 · 다음 단계에서 GPT/Gemini와 로컬 규칙 분석을 연결합니다.");
        });
        LinearLayout.LayoutParams assistantParams = new LinearLayout.LayoutParams(-1, dp(56));
        assistantParams.setMargins(0, dp(10), 0, 0);
        root.addView(assistantButton, assistantParams);

        statusText = text("새 프로젝트 1단계 · 단일 화면과 음성 입력 기반 구축", 13, Color.rgb(102, 116, 138));
        statusText.setPadding(0, dp(14), 0, 0);
        root.addView(statusText);
        return root;
    }

    private void requestVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "이 기기에서 음성인식 서비스를 사용할 수 없습니다.", Toast.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startVoiceInput();
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO);
        }
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
        statusText.setText("음성 인식 완료 · AI 분석 대기");
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
