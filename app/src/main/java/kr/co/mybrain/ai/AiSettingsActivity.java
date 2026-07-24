package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
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
 * 기본 규칙·GPT·Gemini 공급자와 API 키를 설정하는 화면입니다.
 * 휴대전화 Ollama는 기본 경로에서 제외하고 사용자가 직접 켜는 고급 실험 기능으로만 제공합니다.
 */
public class AiSettingsActivity extends Activity {
    private static final String OPENAI_API_KEY_URL = "https://platform.openai.com/api-keys";
    private static final String GEMINI_API_KEY_URL = "https://aistudio.google.com/app/apikey";
    private static final String OLLAMA_ANDROID_URL =
            "https://github.com/sunshine0523/OllamaServer/releases";

    private static final String[] PROVIDER_LABELS = {
            "기본 규칙 분석 (권장·인터넷 불필요)",
            "GPT (OpenAI API)",
            "Gemini (Google API)",
            "Ollama (고급 실험 기능)"
    };
    private static final String[] PROVIDER_VALUES = {
            AiSettings.PROVIDER_LOCAL,
            AiSettings.PROVIDER_OPENAI,
            AiSettings.PROVIDER_GEMINI,
            AiSettings.PROVIDER_OLLAMA
    };

    private static final int COLOR_PRIMARY = Color.rgb(35, 92, 190);
    private static final int COLOR_SUCCESS = Color.rgb(20, 125, 72);
    private static final int COLOR_ERROR = Color.rgb(190, 52, 52);
    private static final int COLOR_MUTED = Color.rgb(84, 96, 112);

    private Spinner providerSpinner;
    private EditText ollamaAddressInput;
    private EditText ollamaModelInput;
    private EditText openAiModelInput;
    private EditText geminiModelInput;
    private EditText openAiKeyInput;
    private EditText geminiKeyInput;
    private TextView openAiKeyStatus;
    private TextView geminiKeyStatus;
    private TextView ollamaTestStatus;
    private TextView openAiTestStatus;
    private TextView geminiTestStatus;
    private Button ollamaTestButton;
    private Button openAiTestButton;
    private Button geminiTestButton;
    private CheckBox confirmCloudCheck;
    private CheckBox fallbackLocalCheck;
    private CheckBox experimentalOllamaCheck;
    private LinearLayout ollamaSection;
    private boolean loadingValues;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        loadValues();
    }

    /** 초보자도 안정적인 분석 방식과 클라우드 API 설정을 한 화면에서 관리하도록 구성합니다. */
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
                "날짜·요일·시간은 기기 내부 규칙으로 먼저 계산합니다. GPT와 Gemini는 긴 문장의 의미 분리와 제목 정리에 사용합니다. 휴대전화 Ollama는 안정성 문제로 기본 기능에서 제외했습니다.",
                14, Color.DKGRAY);
        guide.setPadding(0, dp(8), 0, dp(18));
        root.addView(guide, fullWrap());

        root.addView(sectionTitle("사용할 분석 방식"), fullWrap());
        providerSpinner = new Spinner(this);
        ArrayAdapter<String> providerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, PROVIDER_LABELS);
        providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(providerAdapter);
        providerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (loadingValues) return;
                if (AiSettings.PROVIDER_OLLAMA.equals(PROVIDER_VALUES[position])
                        && !experimentalOllamaCheck.isChecked()) {
                    Toast.makeText(AiSettingsActivity.this,
                            "Ollama를 사용하려면 아래의 고급 실험 기능을 먼저 켜세요.",
                            Toast.LENGTH_LONG).show();
                    providerSpinner.setSelection(providerIndex(AiSettings.PROVIDER_LOCAL));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 선택이 없는 경우 기본 규칙 분석을 사용합니다.
            }
        });
        root.addView(providerSpinner, fieldParams());

        TextView routingGuide = text(
                "권장: 기본 규칙 분석을 평소에 사용하고, 복잡한 문장을 여러 항목으로 나눌 때 GPT 또는 Gemini를 선택하세요. 클라우드 AI를 선택해도 날짜 범위는 앱이 먼저 검증합니다.",
                13, Color.rgb(50, 92, 145));
        routingGuide.setPadding(dp(10), dp(6), dp(10), dp(12));
        root.addView(routingGuide, fullWrap());

        root.addView(sectionTitle("GPT 설정"), fullWrap());
        Button openAiKeyPage = secondaryButton("OpenAI API 키 만들기 페이지 열기");
        openAiKeyPage.setOnClickListener(v -> openExternalPage(OPENAI_API_KEY_URL));
        root.addView(openAiKeyPage, buttonParams());
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
        Button geminiKeyPage = secondaryButton("Gemini API 키 만들기 페이지 열기");
        geminiKeyPage.setOnClickListener(v -> openExternalPage(GEMINI_API_KEY_URL));
        root.addView(geminiKeyPage, buttonParams());
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
                "GPT·Gemini 연결 테스트는 API 키와 모델 접근 가능 여부를 확인합니다. 실제 일정 문장은 보내지 않습니다.",
                13, Color.rgb(50, 92, 145));
        testGuide.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(testGuide, fullWrap());

        root.addView(sectionTitle("보호 설정"), fullWrap());
        confirmCloudCheck = new CheckBox(this);
        confirmCloudCheck.setText("GPT·Gemini 클라우드로 보내기 전에 매번 확인");
        confirmCloudCheck.setTextSize(15);
        root.addView(confirmCloudCheck, fullWrap());

        fallbackLocalCheck = new CheckBox(this);
        fallbackLocalCheck.setText("선택한 AI 오류 시 기본 규칙 분석으로 자동 전환");
        fallbackLocalCheck.setTextSize(15);
        root.addView(fallbackLocalCheck, fullWrap());

        TextView security = text(
                "GPT·Gemini API 키는 Android Keystore로 암호화해 이 휴대전화에 저장합니다. 학생 개인정보나 민감한 상담 내용은 클라우드로 보내지 않는 것이 안전합니다.",
                13, Color.rgb(125, 74, 20));
        security.setPadding(dp(12), dp(12), dp(12), dp(16));
        root.addView(security, fullWrap());

        root.addView(sectionTitle("고급 실험 기능"), fullWrap());
        experimentalOllamaCheck = new CheckBox(this);
        experimentalOllamaCheck.setText("휴대전화 Ollama 실험 기능 사용");
        experimentalOllamaCheck.setTextSize(15);
        experimentalOllamaCheck.setOnCheckedChangeListener((buttonView, checked) -> {
            updateOllamaSectionVisibility(checked);
            if (!checked && selectedProvider().equals(AiSettings.PROVIDER_OLLAMA)) {
                providerSpinner.setSelection(providerIndex(AiSettings.PROVIDER_LOCAL));
            }
        });
        root.addView(experimentalOllamaCheck, fullWrap());

        TextView experimentWarning = text(
                "Ollama는 별도 커뮤니티 서버 앱, 큰 메모리, 긴 모델 적재 시간이 필요합니다. 발열·배터리 소모·시간 초과가 발생할 수 있습니다.",
                13, COLOR_ERROR);
        experimentWarning.setPadding(dp(10), dp(4), dp(10), dp(8));
        root.addView(experimentWarning, fullWrap());

        ollamaSection = new LinearLayout(this);
        ollamaSection.setOrientation(LinearLayout.VERTICAL);

        Button ollamaInstall = secondaryButton("Ollama Server 설치 페이지 열기");
        ollamaInstall.setOnClickListener(v -> openExternalPage(OLLAMA_ANDROID_URL));
        ollamaSection.addView(ollamaInstall, buttonParams());

        ollamaAddressInput = field("Ollama 주소", AiSettings.DEFAULT_OLLAMA_BASE_URL, false);
        ollamaSection.addView(ollamaAddressInput, fieldParams());
        ollamaModelInput = field("Ollama 모델 이름", AiSettings.DEFAULT_OLLAMA_MODEL, false);
        ollamaSection.addView(ollamaModelInput, fieldParams());

        TextView modelGuide = text(
                "휴대전화에서는 qwen3:0.6b처럼 작은 모델을 권장합니다. Ollama Server 앱에서 모델을 먼저 내려받아야 합니다.",
                13, COLOR_MUTED);
        modelGuide.setPadding(dp(8), 0, dp(8), dp(8));
        ollamaSection.addView(modelGuide, fullWrap());

        ollamaTestButton = secondaryButton("휴대전화 Ollama 실제 분석 테스트");
        ollamaTestButton.setOnClickListener(v -> testProvider(AiSettings.PROVIDER_OLLAMA));
        ollamaSection.addView(ollamaTestButton, buttonParams());
        ollamaTestStatus = statusText();
        ollamaSection.addView(ollamaTestStatus, statusParams());
        root.addView(ollamaSection, fullWrap());

        Button save = primaryButton("설정 저장");
        save.setOnClickListener(v -> saveValues());
        root.addView(save, buttonParams());

        Button close = secondaryButton("취소");
        close.setOnClickListener(v -> finish());
        root.addView(close, buttonParams());

        setContentView(scroll);
    }

    private void loadValues() {
        loadingValues = true;
        AiSettings settings = AiSettings.load(this);
        experimentalOllamaCheck.setChecked(settings.experimentalOllamaEnabled);
        updateOllamaSectionVisibility(settings.experimentalOllamaEnabled);
        providerSpinner.setSelection(providerIndex(settings.provider));
        ollamaAddressInput.setText(settings.ollamaBaseUrl);
        ollamaModelInput.setText(settings.ollamaModel);
        openAiModelInput.setText(settings.openAiModel);
        geminiModelInput.setText(settings.geminiModel);
        confirmCloudCheck.setChecked(settings.confirmBeforeCloud);
        fallbackLocalCheck.setChecked(settings.fallbackToLocal);
        updateKeyStatuses();
        loadingValues = false;
    }

    private void updateOllamaSectionVisibility(boolean visible) {
        if (ollamaSection != null) ollamaSection.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** 새로 입력한 키를 우선 사용하고, 비어 있으면 기기에 저장된 암호화 키로 연결을 검사합니다. */
    private void testProvider(String provider) {
        final boolean ollama = AiSettings.PROVIDER_OLLAMA.equals(provider);
        final boolean openAi = AiSettings.PROVIDER_OPENAI.equals(provider);

        if (ollama && !experimentalOllamaCheck.isChecked()) {
            Toast.makeText(this, "Ollama 실험 기능을 먼저 켜세요.", Toast.LENGTH_LONG).show();
            return;
        }

        final EditText modelInput = ollama ? ollamaModelInput
                : (openAi ? openAiModelInput : geminiModelInput);
        final TextView statusView = ollama ? ollamaTestStatus
                : (openAi ? openAiTestStatus : geminiTestStatus);
        final String providerLabel = ollama ? "Ollama" : (openAi ? "GPT" : "Gemini");

        String model = modelInput.getText().toString().trim();
        String apiKey = "";
        if (!ollama) {
            EditText keyInput = openAi ? openAiKeyInput : geminiKeyInput;
            String keyName = openAi ? SecureApiKeyStore.KEY_OPENAI : SecureApiKeyStore.KEY_GEMINI;
            String typedKey = keyInput.getText().toString().trim();
            apiKey = typedKey.isEmpty() ? SecureApiKeyStore.read(this, keyName) : typedKey;
            if (apiKey.isEmpty()) {
                setStatus(statusView, providerLabel + " API 키를 먼저 입력하세요.", false);
                return;
            }
        }

        String ollamaBaseUrl = ollamaAddressInput.getText().toString().trim();
        if (ollama && !AiSettings.isAllowedOllamaBaseUrl(ollamaBaseUrl)) {
            setStatus(statusView,
                    "Ollama 주소는 http://127.0.0.1:11434 또는 localhost 형식만 사용할 수 있습니다.", false);
            return;
        }

        setTestButtonsEnabled(false);
        statusView.setTextColor(COLOR_PRIMARY);
        statusView.setText(providerLabel + " 연결을 확인하고 있습니다…");

        final String finalApiKey = apiKey;
        final String finalBaseUrl = ollamaBaseUrl;
        new Thread(() -> {
            try {
                String message = AiConnectionTester.test(
                        provider, model, finalApiKey, finalBaseUrl);
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
        if (ollamaTestButton != null) ollamaTestButton.setEnabled(enabled);
        openAiTestButton.setEnabled(enabled);
        geminiTestButton.setEnabled(enabled);
    }

    /** 일반 설정과 새로 입력된 API 키를 각각 안전한 저장소에 기록합니다. */
    private void saveValues() {
        try {
            String selected = selectedProvider();
            boolean experimental = experimentalOllamaCheck.isChecked();
            String ollamaAddress = ollamaAddressInput.getText().toString().trim();

            if (AiSettings.PROVIDER_OLLAMA.equals(selected) && !experimental) {
                selected = AiSettings.PROVIDER_LOCAL;
            }
            if (experimental && !AiSettings.isAllowedOllamaBaseUrl(ollamaAddress)) {
                Toast.makeText(this,
                        "Ollama 주소는 같은 휴대전화의 127.0.0.1 또는 localhost만 사용할 수 있습니다.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            AiSettings settings = AiSettings.load(this);
            settings.provider = selected;
            settings.experimentalOllamaEnabled = experimental;
            settings.ollamaBaseUrl = ollamaAddress;
            settings.ollamaModel = ollamaModelInput.getText().toString().trim();
            settings.openAiModel = openAiModelInput.getText().toString().trim();
            settings.geminiModel = geminiModelInput.getText().toString().trim();
            settings.confirmBeforeCloud = confirmCloudCheck.isChecked();
            settings.fallbackToLocal = fallbackLocalCheck.isChecked();
            settings.save(this);

            String openAiKey = openAiKeyInput.getText().toString().trim();
            String geminiKey = geminiKeyInput.getText().toString().trim();
            if (!openAiKey.isEmpty()) {
                SecureApiKeyStore.save(this, SecureApiKeyStore.KEY_OPENAI, openAiKey);
            }
            if (!geminiKey.isEmpty()) {
                SecureApiKeyStore.save(this, SecureApiKeyStore.KEY_GEMINI, geminiKey);
            }

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
            Toast.makeText(this, "AI 설정 저장 실패: " + safeMessage(e), Toast.LENGTH_LONG).show();
        }
    }

    private String selectedProvider() {
        int position = providerSpinner.getSelectedItemPosition();
        if (position < 0 || position >= PROVIDER_VALUES.length) return AiSettings.PROVIDER_LOCAL;
        return PROVIDER_VALUES[position];
    }

    private void openExternalPage(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "웹 페이지를 열 수 없습니다.", Toast.LENGTH_LONG).show();
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
