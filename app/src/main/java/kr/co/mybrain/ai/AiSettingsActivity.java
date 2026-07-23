package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 로컬·GPT·Gemini 공급자와 모델, API 키를 설정하는 화면입니다.
 * 저장된 API 키는 다시 평문으로 표시하지 않으며 새 값을 입력한 경우에만 교체합니다.
 */
public class AiSettingsActivity extends Activity {
    private static final String[] PROVIDER_LABELS = {
            "로컬 분석 (무료·오프라인)",
            "GPT (OpenAI API)",
            "Gemini (Google API)"
    };
    private static final String[] PROVIDER_VALUES = {
            AiSettings.PROVIDER_LOCAL,
            AiSettings.PROVIDER_OPENAI,
            AiSettings.PROVIDER_GEMINI
    };

    private static final int COLOR_PRIMARY = Color.rgb(35, 92, 190);
    private static final int COLOR_SUCCESS = Color.rgb(20, 125, 72);
    private static final int COLOR_ERROR = Color.rgb(190, 52, 52);
    private static final int COLOR_MUTED = Color.rgb(84, 96, 112);

    private Spinner providerSpinner;
    private EditText openAiModelInput;
    private EditText geminiModelInput;
    private EditText openAiKeyInput;
    private EditText geminiKeyInput;
    private TextView openAiKeyStatus;
    private TextView geminiKeyStatus;
    private TextView openAiTestStatus;
    private TextView geminiTestStatus;
    private Button openAiTestButton;
    private Button geminiTestButton;
    private CheckBox confirmCloudCheck;
    private CheckBox fallbackLocalCheck;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        loadValues();
    }

    /** 초보자도 한 화면에서 공급자, 모델, 키, 연결 상태를 관리하도록 설정 화면을 구성합니다. */
    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(30));
        root.setBackgroundColor(Color.rgb(244, 247, 251));
        scroll.addView(root, fullWrap());

        TextView title = text("MyBrain AI 설정", 26, Color.rgb(18, 48, 89));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, fullWrap());

        TextView guide = text(
                "로컬은 인터넷과 API 키 없이 동작합니다. GPT 또는 Gemini를 선택하면 입력 문장이 해당 회사의 클라우드 API로 전송됩니다.",
                14, Color.DKGRAY);
        guide.setPadding(0, dp(8), 0, dp(18));
        root.addView(guide, fullWrap());

        root.addView(sectionTitle("사용할 AI"), fullWrap());
        providerSpinner = new Spinner(this);
        ArrayAdapter<String> providerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, PROVIDER_LABELS);
        providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(providerAdapter);
        root.addView(providerSpinner, fieldParams());

        root.addView(sectionTitle("GPT 설정"), fullWrap());
        openAiModelInput = field("GPT 모델 이름", AiSettings.DEFAULT_OPENAI_MODEL, false);
        root.addView(openAiModelInput, fieldParams());
        openAiKeyStatus = text("", 13, COLOR_MUTED);
        root.addView(openAiKeyStatus, fullWrap());
        openAiKeyInput = field("새 OpenAI API 키 입력 (빈칸이면 기존 키 유지)", "", true);
        root.addView(openAiKeyInput, fieldParams());
        openAiTestButton = secondaryButton("GPT 연결 테스트");
        openAiTestButton.setOnClickListener(v -> testProvider(AiSettings.PROVIDER_OPENAI));
        root.addView(openAiTestButton, buttonParams());
        openAiTestStatus = statusText();
        root.addView(openAiTestStatus, statusParams());
        Button clearOpenAi = secondaryButton("저장된 GPT 키 삭제");
        clearOpenAi.setOnClickListener(v -> confirmClearKey(SecureApiKeyStore.KEY_OPENAI, "GPT"));
        root.addView(clearOpenAi, buttonParams());

        root.addView(sectionTitle("Gemini 설정"), fullWrap());
        geminiModelInput = field("Gemini 모델 이름", AiSettings.DEFAULT_GEMINI_MODEL, false);
        root.addView(geminiModelInput, fieldParams());
        geminiKeyStatus = text("", 13, COLOR_MUTED);
        root.addView(geminiKeyStatus, fullWrap());
        geminiKeyInput = field("새 Gemini API 키 입력 (빈칸이면 기존 키 유지)", "", true);
        root.addView(geminiKeyInput, fieldParams());
        geminiTestButton = secondaryButton("Gemini 연결 테스트");
        geminiTestButton.setOnClickListener(v -> testProvider(AiSettings.PROVIDER_GEMINI));
        root.addView(geminiTestButton, buttonParams());
        geminiTestStatus = statusText();
        root.addView(geminiTestStatus, statusParams());
        Button clearGemini = secondaryButton("저장된 Gemini 키 삭제");
        clearGemini.setOnClickListener(v -> confirmClearKey(SecureApiKeyStore.KEY_GEMINI, "Gemini"));
        root.addView(clearGemini, buttonParams());

        TextView testGuide = text(
                "연결 테스트는 모델 정보만 조회합니다. 일정·메모 문장은 전송하지 않으며 분석 사용량도 발생시키지 않습니다.",
                13, Color.rgb(50, 92, 145));
        testGuide.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(testGuide, fullWrap());

        root.addView(sectionTitle("보호 설정"), fullWrap());
        confirmCloudCheck = new CheckBox(this);
        confirmCloudCheck.setText("클라우드로 보내기 전에 매번 확인");
        confirmCloudCheck.setTextSize(15);
        root.addView(confirmCloudCheck, fullWrap());

        fallbackLocalCheck = new CheckBox(this);
        fallbackLocalCheck.setText("클라우드 오류 시 로컬 분석으로 자동 전환");
        fallbackLocalCheck.setTextSize(15);
        root.addView(fallbackLocalCheck, fullWrap());

        TextView security = text(
                "API 키는 Android Keystore로 암호화해 이 휴대전화에 저장합니다. 저장된 키는 화면에 다시 표시하지 않습니다. 앱 삭제 시 키도 삭제됩니다.",
                13, Color.rgb(125, 74, 20));
        security.setPadding(dp(12), dp(12), dp(12), dp(16));
        root.addView(security, fullWrap());

        Button save = primaryButton("설정 저장");
        save.setOnClickListener(v -> saveValues());
        root.addView(save, buttonParams());

        Button close = secondaryButton("취소");
        close.setOnClickListener(v -> finish());
        root.addView(close, buttonParams());

        setContentView(scroll);
    }

    private void loadValues() {
        AiSettings settings = AiSettings.load(this);
        providerSpinner.setSelection(providerIndex(settings.provider));
        openAiModelInput.setText(settings.openAiModel);
        geminiModelInput.setText(settings.geminiModel);
        confirmCloudCheck.setChecked(settings.confirmBeforeCloud);
        fallbackLocalCheck.setChecked(settings.fallbackToLocal);
        updateKeyStatuses();
    }

    /** 입력창의 새 키를 우선 사용하고, 비어 있으면 기기에 저장된 암호화 키로 연결을 검사합니다. */
    private void testProvider(String provider) {
        final boolean openAi = AiSettings.PROVIDER_OPENAI.equals(provider);
        final EditText modelInput = openAi ? openAiModelInput : geminiModelInput;
        final EditText keyInput = openAi ? openAiKeyInput : geminiKeyInput;
        final TextView statusView = openAi ? openAiTestStatus : geminiTestStatus;
        final String keyName = openAi ? SecureApiKeyStore.KEY_OPENAI : SecureApiKeyStore.KEY_GEMINI;
        final String providerLabel = openAi ? "GPT" : "Gemini";

        String model = modelInput.getText().toString().trim();
        String typedKey = keyInput.getText().toString().trim();
        String apiKey = typedKey.isEmpty() ? SecureApiKeyStore.read(this, keyName) : typedKey;

        if (apiKey.isEmpty()) {
            setStatus(statusView, providerLabel + " API 키를 먼저 입력하세요.", false);
            return;
        }

        setTestButtonsEnabled(false);
        statusView.setTextColor(COLOR_PRIMARY);
        statusView.setText(providerLabel + " 연결을 확인하고 있습니다…");

        new Thread(() -> {
            try {
                String message = AiConnectionTester.test(provider, model, apiKey);
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    setStatus(statusView, message, true);
                    setTestButtonsEnabled(true);
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                String message = safeMessage(e);
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    setStatus(statusView, message, false);
                    setTestButtonsEnabled(true);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            }
        }, "mybrain-ai-connection-test").start();
    }

    private void setStatus(TextView view, String message, boolean success) {
        view.setTextColor(success ? COLOR_SUCCESS : COLOR_ERROR);
        view.setText(message);
    }

    private void setTestButtonsEnabled(boolean enabled) {
        openAiTestButton.setEnabled(enabled);
        geminiTestButton.setEnabled(enabled);
    }

    /** 일반 설정과 새로 입력된 API 키를 각각 안전한 저장소에 기록합니다. */
    private void saveValues() {
        try {
            AiSettings settings = AiSettings.load(this);
            settings.provider = PROVIDER_VALUES[providerSpinner.getSelectedItemPosition()];
            settings.openAiModel = openAiModelInput.getText().toString().trim();
            settings.geminiModel = geminiModelInput.getText().toString().trim();
            settings.confirmBeforeCloud = confirmCloudCheck.isChecked();
            settings.fallbackToLocal = fallbackLocalCheck.isChecked();
            settings.save(this);

            String openAiKey = openAiKeyInput.getText().toString().trim();
            String geminiKey = geminiKeyInput.getText().toString().trim();
            if (!openAiKey.isEmpty()) SecureApiKeyStore.save(this, SecureApiKeyStore.KEY_OPENAI, openAiKey);
            if (!geminiKey.isEmpty()) SecureApiKeyStore.save(this, SecureApiKeyStore.KEY_GEMINI, geminiKey);

            if (AiSettings.PROVIDER_OPENAI.equals(settings.provider)
                    && !SecureApiKeyStore.has(this, SecureApiKeyStore.KEY_OPENAI)) {
                Toast.makeText(this, "GPT가 선택됐지만 OpenAI API 키가 없습니다.", Toast.LENGTH_LONG).show();
            } else if (AiSettings.PROVIDER_GEMINI.equals(settings.provider)
                    && !SecureApiKeyStore.has(this, SecureApiKeyStore.KEY_GEMINI)) {
                Toast.makeText(this, "Gemini가 선택됐지만 Gemini API 키가 없습니다.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "AI 설정을 저장했습니다.", Toast.LENGTH_SHORT).show();
            }

            openAiKeyInput.setText("");
            geminiKeyInput.setText("");
            updateKeyStatuses();
            setResult(RESULT_OK);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "API 키 저장 실패: " + safeMessage(e), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmClearKey(String keyName, String label) {
        new AlertDialog.Builder(this)
                .setTitle(label + " API 키 삭제")
                .setMessage("이 휴대전화에 저장된 " + label + " API 키를 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> {
                    SecureApiKeyStore.clear(this, keyName);
                    updateKeyStatuses();
                    if (SecureApiKeyStore.KEY_OPENAI.equals(keyName)) openAiTestStatus.setText("");
                    if (SecureApiKeyStore.KEY_GEMINI.equals(keyName)) geminiTestStatus.setText("");
                    Toast.makeText(this, label + " API 키를 삭제했습니다.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void updateKeyStatuses() {
        openAiKeyStatus.setText(SecureApiKeyStore.has(this, SecureApiKeyStore.KEY_OPENAI)
                ? "상태: GPT API 키 저장됨" : "상태: GPT API 키 미등록");
        geminiKeyStatus.setText(SecureApiKeyStore.has(this, SecureApiKeyStore.KEY_GEMINI)
                ? "상태: Gemini API 키 저장됨" : "상태: Gemini API 키 미등록");
    }

    private int providerIndex(String provider) {
        for (int i = 0; i < PROVIDER_VALUES.length; i++) {
            if (PROVIDER_VALUES[i].equals(provider)) return i;
        }
        return 0;
    }

    private EditText field(String hint, String value, boolean password) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setText(value);
        editText.setTextSize(15);
        editText.setSingleLine(true);
        editText.setPadding(dp(12), 0, dp(12), 0);
        if (password) {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        return editText;
    }

    private TextView statusText() {
        TextView view = text("", 13, COLOR_MUTED);
        view.setPadding(dp(8), 0, dp(8), 0);
        return view;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 17, Color.rgb(31, 42, 55));
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(14), 0, dp(6));
        return view;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackgroundColor(COLOR_PRIMARY);
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(15);
        button.setAllCaps(false);
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.START);
        return view;
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        params.setMargins(0, 0, 0, dp(8));
        return params;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        params.setMargins(0, 0, 0, dp(8));
        return params;
    }

    private LinearLayout.LayoutParams statusParams() {
        LinearLayout.LayoutParams params = fullWrap();
        params.setMargins(0, 0, 0, dp(8));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? "알 수 없는 오류" : error.getMessage();
        return message == null || message.trim().isEmpty() ? "알 수 없는 오류" : message;
    }
}
