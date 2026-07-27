package kr.co.mybrain.v2.settings;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kr.co.mybrain.v2.assistant.CloudAiWorkItemAnalyzer;
import kr.co.mybrain.v2.assistant.KoreanNaturalLanguageParser;
import kr.co.mybrain.v2.assistant.ParsedWorkItem;
import kr.co.mybrain.v2.data.WorkItemEntity;

public class AiSettingsActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(246, 248, 252);
    private static final int TEXT = Color.rgb(24, 34, 48);
    private static final int SUBTEXT = Color.rgb(91, 106, 128);
    private static final int PRIMARY = Color.rgb(45, 91, 255);
    private static final int BORDER = Color.rgb(218, 224, 234);
    private static final int DANGER = Color.rgb(218, 53, 69);
    private static final int SUCCESS = Color.rgb(29, 128, 75);
    private static final String CUSTOM_MODEL = "직접 입력";

    private static final String[] OPENAI_MODELS = {
            "gpt-5-mini", "gpt-5.1", "gpt-5-nano", CUSTOM_MODEL
    };
    private static final String[] GEMINI_MODELS = {
            "gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-2.5-flash", CUSTOM_MODEL
    };

    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private AiSettings settings;
    private RadioButton openAiRadio;
    private RadioButton geminiRadio;
    private Spinner modelSpinner;
    private EditText customModelInput;
    private EditText credentialInput;
    private TextView connectionStatus;
    private TextView credentialStatus;
    private TextView providerSummary;
    private TextView usageStatus;
    private Button testButton;
    private Button analysisTestButton;
    private boolean refreshing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = AiSettings.load(this);
        setContentView(buildScreen());
        refreshProviderUi();
    }

    @Override protected void onResume() {
        super.onResume();
        if (usageStatus != null) updateUsageStatus();
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

        Button back = secondaryButton("←  AI 설정");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView title = text("AI 연결 관리", 28, TEXT, true);
        title.setPadding(0, dp(18), 0, dp(4));
        root.addView(title);
        TextView subtitle = text("GPT 또는 Gemini를 선택하고 실제 분석 상태를 확인합니다.", 15, SUBTEXT, false);
        subtitle.setPadding(0, 0, 0, dp(14));
        root.addView(subtitle);

        LinearLayout providerCard = card();
        root.addView(providerCard, cardParams());
        providerCard.addView(sectionTitle("1. AI 제공자 선택"));
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        openAiRadio = radio("GPT");
        geminiRadio = radio("Gemini");
        group.addView(openAiRadio, new RadioGroup.LayoutParams(0, dp(48), 1f));
        group.addView(geminiRadio, new RadioGroup.LayoutParams(0, dp(48), 1f));
        providerCard.addView(group);
        providerSummary = text("", 14, SUBTEXT, false);
        providerSummary.setPadding(0, dp(8), 0, 0);
        providerCard.addView(providerSummary);
        group.setOnCheckedChangeListener((g, checkedId) -> {
            if (refreshing) return;
            captureSelectedModel();
            settings.provider = checkedId == geminiRadio.getId()
                    ? AiSettings.PROVIDER_GEMINI : AiSettings.PROVIDER_OPENAI;
            settings.save(this);
            refreshProviderUi();
        });

        LinearLayout modelCard = card();
        root.addView(modelCard, cardParams());
        modelCard.addView(sectionTitle("2. 사용 모델"));
        modelSpinner = new Spinner(this);
        modelSpinner.setBackground(rounded(Color.WHITE, 12, BORDER));
        modelCard.addView(modelSpinner, new LinearLayout.LayoutParams(-1, dp(54)));
        customModelInput = input("모델 ID를 직접 입력하세요");
        LinearLayout.LayoutParams customParams = new LinearLayout.LayoutParams(-1, dp(54));
        customParams.setMargins(0, dp(8), 0, 0);
        modelCard.addView(customModelInput, customParams);
        customModelInput.setVisibility(View.GONE);
        modelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (refreshing) return;
                boolean custom = CUSTOM_MODEL.equals(String.valueOf(parent.getItemAtPosition(position)));
                customModelInput.setVisibility(custom ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        LinearLayout credentialCard = card();
        root.addView(credentialCard, cardParams());
        credentialCard.addView(sectionTitle("3. 연결 정보 등록"));
        credentialInput = input("새 값을 입력할 때만 작성하세요");
        credentialInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        credentialCard.addView(credentialInput, new LinearLayout.LayoutParams(-1, dp(54)));
        credentialStatus = text("", 13, SUBTEXT, false);
        credentialStatus.setPadding(0, dp(8), 0, 0);
        credentialCard.addView(credentialStatus);

        LinearLayout credentialActions = new LinearLayout(this);
        Button saveCredential = secondaryButton("저장");
        saveCredential.setOnClickListener(v -> saveSettings(true));
        Button deleteCredential = secondaryButton("삭제");
        deleteCredential.setTextColor(DANGER);
        deleteCredential.setOnClickListener(v -> confirmDeleteCredential());
        LinearLayout.LayoutParams c1 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        c1.setMargins(0, dp(10), dp(5), 0);
        LinearLayout.LayoutParams c2 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        c2.setMargins(dp(5), dp(10), 0, 0);
        credentialActions.addView(saveCredential, c1);
        credentialActions.addView(deleteCredential, c2);
        credentialCard.addView(credentialActions);

        Button issueGuide = secondaryButton("API 키 발급 방법 보기");
        issueGuide.setOnClickListener(v -> showIssueGuide());
        LinearLayout.LayoutParams guideParams = new LinearLayout.LayoutParams(-1, dp(50));
        guideParams.setMargins(0, dp(10), 0, 0);
        credentialCard.addView(issueGuide, guideParams);

        LinearLayout statusCard = card();
        root.addView(statusCard, cardParams());
        statusCard.addView(sectionTitle("4. 연결·실제 분석 검증"));
        connectionStatus = text("연결 테스트 기록이 없습니다.", 14, SUBTEXT, false);
        connectionStatus.setLineSpacing(dp(3), 1f);
        statusCard.addView(connectionStatus);

        testButton = primaryButton("연결 테스트");
        testButton.setOnClickListener(v -> testConnection());
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(-1, dp(54));
        testParams.setMargins(0, dp(12), 0, 0);
        statusCard.addView(testButton, testParams);

        analysisTestButton = secondaryButton("실제 일정 분석 테스트");
        analysisTestButton.setOnClickListener(v -> testRealAnalysis());
        LinearLayout.LayoutParams analysisTestParams = new LinearLayout.LayoutParams(-1, dp(52));
        analysisTestParams.setMargins(0, dp(9), 0, 0);
        statusCard.addView(analysisTestButton, analysisTestParams);

        LinearLayout usageCard = card();
        root.addView(usageCard, cardParams());
        usageCard.addView(sectionTitle("5. 기기 내 AI 사용 기록"));
        usageStatus = text("사용 기록이 없습니다.", 14, SUBTEXT, false);
        usageStatus.setLineSpacing(dp(3), 1f);
        usageCard.addView(usageStatus);
        TextView usageNote = text("토큰 수와 응답시간만 저장하며 입력 문장·응답 원문·API 키는 기록하지 않습니다.", 12, SUBTEXT, false);
        usageNote.setPadding(0, dp(10), 0, 0);
        usageCard.addView(usageNote);
        Button resetUsage = secondaryButton("선택한 AI 사용 기록 초기화");
        resetUsage.setOnClickListener(v -> confirmResetUsage());
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(-1, dp(48));
        resetParams.setMargins(0, dp(10), 0, 0);
        usageCard.addView(resetUsage, resetParams);

        Button saveAll = primaryButton("✓  AI 설정 저장");
        saveAll.setOnClickListener(v -> saveSettings(false));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(56));
        saveParams.setMargins(0, dp(14), 0, 0);
        root.addView(saveAll, saveParams);

        TextView security = text("연결 정보는 Android Keystore 기반 AES-GCM으로 기기 안에 암호화 저장됩니다.", 13, SUBTEXT, false);
        security.setGravity(Gravity.CENTER);
        security.setPadding(dp(8), dp(12), dp(8), 0);
        root.addView(security);
        return scroll;
    }

    private void refreshProviderUi() {
        refreshing = true;
        boolean gemini = AiSettings.PROVIDER_GEMINI.equals(settings.provider);
        openAiRadio.setChecked(!gemini);
        geminiRadio.setChecked(gemini);
        String[] models = gemini ? GEMINI_MODELS : OPENAI_MODELS;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, models);
        modelSpinner.setAdapter(adapter);
        String selected = gemini ? settings.geminiModel : settings.openAiModel;
        int index = Arrays.asList(models).indexOf(selected);
        if (index < 0) {
            index = models.length - 1;
            customModelInput.setText(selected);
            customModelInput.setVisibility(View.VISIBLE);
        } else {
            customModelInput.setText("");
            customModelInput.setVisibility(CUSTOM_MODEL.equals(models[index]) ? View.VISIBLE : View.GONE);
        }
        modelSpinner.setSelection(index);
        providerSummary.setText(gemini
                ? "빠른 문서 정리와 구조화 분석에 사용할 Gemini 연결입니다."
                : "일정·할 일·메모의 구조화 분석에 사용할 GPT 연결입니다.");
        credentialInput.setText("");
        boolean has = EncryptedValueStore.has(this, credentialName());
        credentialStatus.setText(has ? "등록 상태: 저장됨" : "등록 상태: 미등록");
        updateConnectionStatus();
        updateUsageStatus();
        refreshing = false;
    }

    private void captureSelectedModel() {
        if (modelSpinner == null) return;
        String value = String.valueOf(modelSpinner.getSelectedItem());
        if (CUSTOM_MODEL.equals(value)) value = customModelInput.getText().toString().trim();
        if (AiSettings.PROVIDER_GEMINI.equals(settings.provider)) {
            settings.geminiModel = AiSettings.normalizeModel(value, AiSettings.DEFAULT_GEMINI_MODEL);
        } else {
            settings.openAiModel = AiSettings.normalizeModel(value, AiSettings.DEFAULT_OPENAI_MODEL);
        }
    }

    private boolean saveSettings(boolean requireCredentialInput) {
        captureSelectedModel();
        if (settings.selectedModel().trim().isEmpty()) {
            Toast.makeText(this, "모델 이름을 입력하세요.", Toast.LENGTH_SHORT).show();
            return false;
        }
        String credential = credentialInput.getText().toString().trim();
        if (requireCredentialInput && credential.isEmpty()) {
            Toast.makeText(this, "저장할 값을 입력하세요.", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            if (!credential.isEmpty()) EncryptedValueStore.save(this, credentialName(), credential);
            settings.save(this);
            credentialInput.setText("");
            credentialStatus.setText(EncryptedValueStore.has(this, credentialName())
                    ? "등록 상태: 저장됨" : "등록 상태: 미등록");
            Toast.makeText(this, "AI 설정을 저장했습니다.", Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception error) {
            Toast.makeText(this, "안전 저장에 실패했습니다.", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void testConnection() {
        if (!saveSettings(false)) return;
        String credential = EncryptedValueStore.read(this, credentialName());
        if (credential.isEmpty()) {
            Toast.makeText(this, "먼저 연결 정보를 등록하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String provider = settings.provider;
        final String model = settings.selectedModel();
        setTestsBusy(true, "연결 확인 중…");
        connectionStatus.setText("서버와 모델 접근 권한을 확인하고 있습니다.");
        networkExecutor.execute(() -> {
            boolean success = false;
            String message;
            long startedAt = System.nanoTime();
            try {
                message = CloudConnectionTester.test(provider, model, credential);
                success = true;
            } catch (Exception error) {
                message = safeMessage(error);
            }
            long elapsedMs = Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L);
            message = message + " · " + formatElapsed(elapsedMs);
            AiSettings.saveConnectionResult(this, provider, success, message, System.currentTimeMillis());
            boolean result = success;
            runOnUiThread(() -> {
                setTestsBusy(false, null);
                updateConnectionStatus();
                Toast.makeText(this, result ? "연결에 성공했습니다." : "연결에 실패했습니다.", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void testRealAnalysis() {
        if (!saveSettings(false)) return;
        String credential = EncryptedValueStore.read(this, credentialName());
        if (credential.isEmpty()) {
            Toast.makeText(this, "먼저 연결 정보를 등록하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String provider = settings.provider;
        final String model = settings.selectedModel();
        final String providerLabel = settings.providerLabel();
        final String sample = "내일 오전 9시 교무회의 30분 전에 알려줘";
        final ZoneId zoneId = ZoneId.systemDefault();
        final ParsedWorkItem baseline = KoreanNaturalLanguageParser.parse(sample, zoneId);
        setTestsBusy(true, "실제 분석 중…");
        connectionStatus.setText("예제 문장으로 날짜·시간·알림 구조화 분석을 검증하고 있습니다.");

        networkExecutor.execute(() -> {
            CloudAiWorkItemAnalyzer.AnalysisResult result = null;
            String errorMessage = null;
            long startedAt = System.nanoTime();
            try {
                result = CloudAiWorkItemAnalyzer.analyze(
                        provider, model, credential, sample, zoneId, baseline);
                if (!WorkItemEntity.TYPE_SCHEDULE.equals(result.item.type)
                        || result.item.startAt == null || result.item.reminderAt == null) {
                    throw new IllegalStateException("응답은 도착했지만 일정·시간·알림 구조 검증을 통과하지 못했습니다.");
                }
            } catch (Exception error) {
                errorMessage = safeMessage(error);
            }
            long failedElapsedMs = Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L);
            CloudAiWorkItemAnalyzer.AnalysisResult finalResult = result;
            String finalErrorMessage = errorMessage;
            runOnUiThread(() -> {
                setTestsBusy(false, null);
                if (finalResult != null && finalErrorMessage == null) {
                    AiUsageStore.record(
                            this,
                            provider,
                            finalResult.modelVersion.isEmpty() ? model : finalResult.modelVersion,
                            true,
                            finalResult.inputTokens,
                            finalResult.outputTokens,
                            finalResult.totalTokens,
                            finalResult.elapsedMs,
                            finalResult.privacyMasked,
                            finalResult.corrections,
                            "실제 분석 테스트 · " + finalResult.validationSummary);
                    String message = providerLabel + " 실제 분석 성공 · "
                            + formatElapsed(finalResult.elapsedMs)
                            + " · 전체 " + finalResult.totalTokens + "토큰"
                            + " · " + finalResult.validationSummary;
                    AiSettings.saveConnectionResult(this, provider, true, message, System.currentTimeMillis());
                    Toast.makeText(this, "실제 일정 분석에 성공했습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    AiUsageStore.record(
                            this, provider, model, false, 0, 0, 0,
                            failedElapsedMs, false, 0, "실제 분석 테스트 · " + finalErrorMessage);
                    AiSettings.saveConnectionResult(
                            this, provider, false,
                            providerLabel + " 실제 분석 실패 · " + formatElapsed(failedElapsedMs)
                                    + " · " + finalErrorMessage,
                            System.currentTimeMillis());
                    Toast.makeText(this, "실제 분석 테스트에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
                updateConnectionStatus();
                updateUsageStatus();
            });
        });
    }

    private void setTestsBusy(boolean busy, String activeText) {
        testButton.setEnabled(!busy);
        analysisTestButton.setEnabled(!busy);
        testButton.setAlpha(busy ? .55f : 1f);
        analysisTestButton.setAlpha(busy ? .55f : 1f);
        testButton.setText(busy && activeText != null ? activeText : "연결 테스트");
        analysisTestButton.setText(busy ? "검증 작업 진행 중…" : "실제 일정 분석 테스트");
    }

    private void updateConnectionStatus() {
        if (connectionStatus == null) return;
        AiSettings.ConnectionRecord record = AiSettings.loadConnectionRecord(this, settings.provider);
        if (record.testedAt <= 0L) {
            connectionStatus.setText("연결 테스트 기록이 없습니다.");
            connectionStatus.setTextColor(SUBTEXT);
            return;
        }
        String time = formatDateTime(record.testedAt);
        connectionStatus.setText((record.success ? "● 연결됨" : "● 연결 실패")
                + "\n마지막 확인: " + time + "\n" + record.message);
        connectionStatus.setTextColor(record.success ? SUCCESS : DANGER);
    }

    private void updateUsageStatus() {
        if (usageStatus == null) return;
        AiUsageStore.Summary summary = AiUsageStore.load(this, settings.provider);
        if (summary.requests <= 0L) {
            usageStatus.setText(settings.providerLabel() + " 사용 기록이 없습니다.");
            usageStatus.setTextColor(SUBTEXT);
            return;
        }
        String lastTime = summary.lastAt <= 0L ? "없음" : formatDateTime(summary.lastAt);
        String lastModel = summary.lastModel.isEmpty() ? settings.selectedModel() : summary.lastModel;
        usageStatus.setText("요청 " + summary.requests + "회 · 성공 " + summary.successes
                + " · 실패 " + summary.failures
                + "\n누적 토큰: 입력 " + summary.inputTokens + " / 출력 " + summary.outputTokens
                + " / 전체 " + summary.totalTokens
                + "\n평균 응답: " + formatElapsed(summary.averageElapsedMs())
                + "\n마지막 요청: " + lastTime + " · " + lastModel
                + "\n마지막 사용: " + formatElapsed(summary.lastElapsedMs)
                + " · " + summary.lastTotalTokens + "토큰"
                + (summary.lastCorrections > 0 ? " · 자동 보정 " + summary.lastCorrections + "건" : " · 검증 통과")
                + (summary.lastPrivacyMasked ? " · 개인정보 마스킹" : ""));
        usageStatus.setTextColor(summary.lastSuccess ? TEXT : DANGER);
    }

    private void confirmDeleteCredential() {
        new AlertDialog.Builder(this)
                .setTitle(settings.providerLabel() + " 연결 정보 삭제")
                .setMessage("기기에 저장된 연결 정보를 삭제합니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> {
                    EncryptedValueStore.clear(this, credentialName());
                    credentialInput.setText("");
                    credentialStatus.setText("등록 상태: 미등록");
                    Toast.makeText(this, "삭제했습니다.", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void confirmResetUsage() {
        new AlertDialog.Builder(this)
                .setTitle(settings.providerLabel() + " 사용 기록 초기화")
                .setMessage("기기에 저장된 요청 횟수, 토큰 수, 응답시간 기록을 삭제합니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("초기화", (dialog, which) -> {
                    AiUsageStore.clear(this, settings.provider);
                    updateUsageStatus();
                    Toast.makeText(this, "사용 기록을 초기화했습니다.", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void showIssueGuide() {
        boolean gemini = AiSettings.PROVIDER_GEMINI.equals(settings.provider);
        String message = gemini
                ? "1. Google AI Studio에 로그인합니다.\n2. API 키 만들기를 선택합니다.\n3. 발급된 값을 복사해 이 화면에 등록합니다."
                : "1. OpenAI Platform에 로그인합니다.\n2. API Keys에서 새 키를 만듭니다.\n3. 발급된 값을 복사해 이 화면에 등록합니다.";
        String url = gemini
                ? "https://aistudio.google.com/app/apikey"
                : "https://platform.openai.com/api-keys";
        new AlertDialog.Builder(this)
                .setTitle(settings.providerLabel() + " 발급 안내")
                .setMessage(message)
                .setNegativeButton("닫기", null)
                .setPositiveButton("발급 페이지 열기", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception error) {
                        Toast.makeText(this, "브라우저를 열 수 없습니다.", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private String credentialName() {
        return AiSettings.PROVIDER_GEMINI.equals(settings.provider)
                ? EncryptedValueStore.GEMINI_CREDENTIAL : EncryptedValueStore.OPENAI_CREDENTIAL;
    }

    private String formatDateTime(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.KOREA));
    }

    private String formatElapsed(long elapsedMs) {
        if (elapsedMs < 1000L) return elapsedMs + "ms";
        return String.format(Locale.KOREA, "%.1f초", elapsedMs / 1000.0);
    }

    private String safeMessage(Throwable error) {
        String value = error == null ? "알 수 없는 오류" : error.getMessage();
        if (value == null || value.trim().isEmpty()) return "알 수 없는 오류";
        String text = value.replaceAll("\\s+", " ").trim();
        return text.length() <= 180 ? text : text.substring(0, 180) + "…";
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(14), dp(14), dp(14), dp(14));
        view.setBackground(rounded(Color.WHITE, 18, BORDER));
        return view;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(12), 0, 0);
        return params;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 17, TEXT, true);
        view.setPadding(0, 0, 0, dp(10));
        return view;
    }

    private RadioButton radio(String value) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(value);
        button.setTextSize(16);
        button.setTextColor(TEXT);
        button.setGravity(Gravity.CENTER_VERTICAL);
        return button;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(Color.rgb(125, 136, 153));
        input.setTextColor(TEXT);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(rounded(Color.WHITE, 12, BORDER));
        return input;
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

    private GradientDrawable rounded(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        networkExecutor.shutdownNow();
        super.onDestroy();
    }
}
