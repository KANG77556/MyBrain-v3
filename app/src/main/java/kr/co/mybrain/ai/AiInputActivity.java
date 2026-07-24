package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 기기 내부 규칙과 GPT·Gemini를 조합해 문장을 분석하고 여러 결과를 한꺼번에 저장하는 화면입니다.
 * 자동 추천은 단순 문장을 기기에서 처리하고 여러 행동이 섞인 문장만 클라우드 AI로 보냅니다.
 */
public class AiInputActivity extends Activity {
    private static final String PREFS = "mybrain_data";
    private static final String KEY_ITEMS = "items";
    private static final int REQUEST_SETTINGS = 1601;
    private static final String[] REPEAT_LABELS = {"반복 없음", "매일", "매주", "매월", "평일"};
    private static final String[] REPEAT_VALUES = {"NONE", "DAILY", "WEEKLY", "MONTHLY", "WEEKDAYS"};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView providerText;
    private TextView noticeText;
    private EditText input;
    private Button analyzeButton;

    private AlertDialog progressDialog;
    private TextView progressStatusText;
    private TextView progressHintText;
    private CloudAiAnalyzer.RequestControl currentRequest;
    private Future<?> currentTask;
    private long analysisStartedAt;
    private boolean currentOllama;
    private String currentProviderLabel = "AI";

    /** 분석 경과 시간을 1초마다 갱신합니다. */
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (currentRequest == null || currentRequest.isCancelled()) return;
            updateProgressText();
            mainHandler.postDelayed(this, 1_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        refreshProviderInfo();
    }

    @Override
    protected void onDestroy() {
        cancelCurrentAnalysis(false);
        executor.shutdownNow();
        mainHandler.removeCallbacks(progressTicker);
        super.onDestroy();
    }

    /** AI 분석 입력 화면을 구성합니다. */
    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(28));
        root.setBackgroundColor(Color.rgb(244, 247, 251));
        scroll.addView(root, fullWrap());

        TextView title = text("AI 메시지 분석", 26, Color.rgb(18, 48, 89));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, fullWrap());

        providerText = text("", 14, Color.rgb(35, 92, 190));
        providerText.setPadding(0, dp(6), 0, dp(14));
        root.addView(providerText, fullWrap());

        input = new EditText(this);
        input.setHint("예: 내일 오전 9시 교무회의, 금요일까지 보고서 제출, 김 선생님께 전화하기");
        input.setTextSize(16);
        input.setGravity(Gravity.TOP);
        input.setMinLines(8);
        input.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(230)));

        noticeText = text("", 13, Color.DKGRAY);
        noticeText.setPadding(0, dp(12), 0, dp(12));
        root.addView(noticeText, fullWrap());

        analyzeButton = primaryButton("AI로 여러 항목 분석");
        analyzeButton.setOnClickListener(v -> requestAnalysis());
        root.addView(analyzeButton, buttonParams());

        Button settingsButton = secondaryButton("AI 설정 열기");
        settingsButton.setOnClickListener(v -> startActivityForResult(
                new Intent(this, AiSettingsActivity.class), REQUEST_SETTINGS));
        root.addView(settingsButton, buttonParams());

        Button closeButton = secondaryButton("취소");
        closeButton.setOnClickListener(v -> finish());
        root.addView(closeButton, buttonParams());

        setContentView(scroll);
    }

    private void refreshProviderInfo() {
        AiSettings settings = AiSettings.load(this);
        providerText.setText("현재 방식: " + settings.providerLabel() + " · " + settings.selectedModel());

        if (settings.isAutoProvider()) {
            analyzeButton.setText("자동 추천으로 분석");
            noticeText.setText("날짜·시간 중심 문장은 휴대전화에서 처리하고, 여러 행동이 섞인 문장만 "
                    + settings.preferredCloudLabel() + "를 우선 사용합니다. API 키가 없으면 기본 규칙으로 처리합니다.");
        } else if (settings.isCloudProvider()) {
            analyzeButton.setText(settings.providerLabel() + "로 여러 항목 분석");
            noticeText.setText("입력 문장이 클라우드 API로 전송됩니다. 분석 결과는 저장 전에 선택하고 수정할 수 있습니다.");
        } else {
            analyzeButton.setText("기본 규칙으로 분석");
            noticeText.setText("인터넷이나 AI 서버 없이 휴대전화 내부 규칙으로 날짜·시간과 한 개 항목을 정리합니다.");
        }
    }

    /** 설정과 키를 확인하고 클라우드 공급자일 때만 전송 확인창을 표시합니다. */
    private void requestAnalysis() {
        if (currentRequest != null) {
            Toast.makeText(this, "이미 AI 분석이 진행 중입니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String value = input.getText().toString().trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) {
            Toast.makeText(this, "분석할 메시지를 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 날짜·요일·시간 범위는 AI 호출 전에 기기 안에서 즉시 분석합니다.
        List<AiAnalysisResult> rangeResults = KoreanScheduleRangeParser.parse(value, new java.util.Date());
        if (!rangeResults.isEmpty()) {
            showResultEditor(rangeResults);
            return;
        }

        AiSettings settings = AiSettings.load(this);
        HybridAnalysisRouter.Decision decision = HybridAnalysisRouter.resolve(this, settings, value);
        if (!decision.usesCloud()) {
            if (settings.isAutoProvider()) {
                Toast.makeText(this, decision.reason, Toast.LENGTH_SHORT).show();
            }
            openLocalAnalyzer(value);
            return;
        }

        // 자동 추천이 선택한 공급자는 현재 분석 한 번에만 사용하고 저장된 설정은 바꾸지 않습니다.
        settings.provider = decision.provider;
        String keyName = HybridAnalysisRouter.keyName(settings.provider);
        if (!SecureApiKeyStore.has(this, keyName)) {
            new AlertDialog.Builder(this)
                    .setTitle(settings.providerLabel() + " API 키 필요")
                    .setMessage("AI 설정에서 " + settings.providerLabel() + " API 키를 먼저 등록하세요.")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("설정 열기", (dialog, which) -> startActivityForResult(
                            new Intent(this, AiSettingsActivity.class), REQUEST_SETTINGS))
                    .show();
            return;
        }

        if (settings.confirmBeforeCloud) {
            new AlertDialog.Builder(this)
                    .setTitle(settings.providerLabel() + "로 전송")
                    .setMessage(decision.reason + "

입력 문장을 " + settings.providerLabel()
                            + " 클라우드 API로 보내 여러 일정·할 일·메모로 분리할까요? 개인정보가 포함됐는지 확인하세요.")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("전송 및 분석", (dialog, which) -> performAiAnalysis(settings, value))
                    .show();
            return;
        }

        performAiAnalysis(settings, value);
    }

    /** GPT 또는 Gemini 네트워크 호출은 별도 작업 스레드에서 수행합니다. */
    private void performAiAnalysis(AiSettings settings, String value) {
        String apiKey = "";
        if (AiSettings.PROVIDER_OPENAI.equals(settings.provider)) {
            apiKey = SecureApiKeyStore.read(this, SecureApiKeyStore.KEY_OPENAI);
        } else if (AiSettings.PROVIDER_GEMINI.equals(settings.provider)) {
            apiKey = SecureApiKeyStore.read(this, SecureApiKeyStore.KEY_GEMINI);
        }

        final String finalApiKey = apiKey;
        final CloudAiAnalyzer.RequestControl requestControl = new CloudAiAnalyzer.RequestControl();
        currentRequest = requestControl;
        showProgress(settings);
        analyzeButton.setEnabled(false);

        currentTask = executor.submit(() -> {
            try {
                List<AiAnalysisResult> results = CloudAiAnalyzer.analyzeMultiple(
                        settings, finalApiKey, value, requestControl);
                runOnUiThread(() -> {
                    if (!isCurrentRequest(requestControl) || isFinishing() || isDestroyed()) return;
                    finishAnalysisUi();
                    showResultEditor(results);
                });
            } catch (CloudAiAnalyzer.AnalysisCancelledException ignored) {
                runOnUiThread(() -> {
                    if (!isCurrentRequest(requestControl)) return;
                    finishAnalysisUi();
                });
            } catch (Exception e) {
                String message = safeMessage(e);
                runOnUiThread(() -> {
                    if (!isCurrentRequest(requestControl) || isFinishing() || isDestroyed()) return;
                    finishAnalysisUi();
                    handleAiError(settings, value, message);
                });
            }
        });
    }

    /** 분석 중 경과 시간과 취소 버튼이 포함된 대기창을 표시합니다. */
    private void showProgress(AiSettings settings) {
        currentOllama = settings.isOllamaProvider();
        currentProviderLabel = settings.providerLabel();
        analysisStartedAt = SystemClock.elapsedRealtime();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(14), dp(24), dp(4));

        ProgressBar progressBar = new ProgressBar(this);
        content.addView(progressBar, new LinearLayout.LayoutParams(dp(54), dp(54)));

        progressStatusText = text("", 16, Color.rgb(35, 92, 190));
        progressStatusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        progressStatusText.setGravity(Gravity.CENTER);
        progressStatusText.setPadding(0, dp(12), 0, dp(6));
        content.addView(progressStatusText, fullWrap());

        progressHintText = text("", 13, Color.DKGRAY);
        progressHintText.setGravity(Gravity.CENTER);
        content.addView(progressHintText, fullWrap());

        progressDialog = new AlertDialog.Builder(this)
                .setTitle(currentProviderLabel + " 다중 분석 중")
                .setView(content)
                .setNegativeButton("분석 취소", null)
                .create();
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setOnShowListener(dialog -> progressDialog
                .getButton(AlertDialog.BUTTON_NEGATIVE)
                .setOnClickListener(v -> cancelCurrentAnalysis(true)));
        progressDialog.show();

        updateProgressText();
        mainHandler.removeCallbacks(progressTicker);
        mainHandler.postDelayed(progressTicker, 1_000L);
    }

    /** 모델 준비와 분석 단계에 맞춰 사용자가 현재 상태를 알 수 있게 안내합니다. */
    private void updateProgressText() {
        if (progressStatusText == null || progressHintText == null) return;
        long seconds = Math.max(0L,
                (SystemClock.elapsedRealtime() - analysisStartedAt) / 1_000L);

        if (currentOllama) {
            if (seconds < 4) {
                progressStatusText.setText("Ollama 연결 중 · " + seconds + "초");
            } else if (seconds < 20) {
                progressStatusText.setText("문장 분리 및 항목 분석 중 · " + seconds + "초");
            } else {
                progressStatusText.setText("여러 결과 정리 중 · " + seconds + "초");
            }

            if (seconds < 15) {
                progressHintText.setText("첫 실행은 모델을 메모리에 올리는 시간이 필요합니다.");
            } else if (seconds < 45) {
                progressHintText.setText("최대 8개의 일정·할 일·메모를 짧은 JSON으로 생성합니다.");
            } else {
                progressHintText.setText("오래 걸리면 취소 후 더 작은 모델을 사용해 보세요.");
            }
        } else {
            progressStatusText.setText(currentProviderLabel + " 응답 대기 중 · " + seconds + "초");
            progressHintText.setText("문장을 여러 항목으로 분리하고 있습니다.");
        }
    }

    /** 취소 버튼을 누르면 작업 스레드와 실제 HTTP 연결을 함께 종료합니다. */
    private void cancelCurrentAnalysis(boolean showMessage) {
        CloudAiAnalyzer.RequestControl request = currentRequest;
        Future<?> task = currentTask;
        if (request != null) request.cancel();
        if (task != null) task.cancel(true);
        finishAnalysisUi();

        if (showMessage && !isFinishing()) {
            Toast.makeText(this,
                    "AI 분석을 취소했습니다. 입력 내용은 그대로 유지됩니다.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isCurrentRequest(CloudAiAnalyzer.RequestControl request) {
        return currentRequest == request;
    }

    /** 대기창과 타이머를 정리하고 분석 버튼을 다시 활성화합니다. */
    private void finishAnalysisUi() {
        mainHandler.removeCallbacks(progressTicker);
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
        progressDialog = null;
        progressStatusText = null;
        progressHintText = null;
        currentRequest = null;
        currentTask = null;
        if (analyzeButton != null) analyzeButton.setEnabled(true);
    }

    /** 여러 분석 결과를 선택·수정할 수 있는 스크롤 확인창으로 표시합니다. */
    private void showResultEditor(List<AiAnalysisResult> results) {
        if (results == null || results.isEmpty()) {
            Toast.makeText(this, "분석 결과가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(14), dp(4), dp(14), dp(16));

        TextView guide = text("저장하지 않을 항목은 체크를 해제하고, 잘못된 내용은 직접 수정하세요.",
                13, Color.DKGRAY);
        guide.setPadding(dp(4), 0, dp(4), dp(10));
        form.addView(guide, fullWrap());

        List<ResultEditorFields> editors = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            ResultEditorFields fields = createResultSection(i + 1, results.get(i));
            editors.add(fields);
            LinearLayout.LayoutParams sectionParams = fullWrap();
            sectionParams.setMargins(0, 0, 0, dp(10));
            form.addView(fields.section, sectionParams);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("AI 분석 결과 " + results.size() + "개")
                .setView(scroll)
                .setNegativeButton("취소", null)
                .setPositiveButton("선택 항목 저장", null)
                .create();

        dialog.setOnShowListener(value -> dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    int saved = saveSelectedItems(editors);
                    if (saved <= 0) {
                        Toast.makeText(this, "저장할 항목을 하나 이상 선택하세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dialog.dismiss();
                    Toast.makeText(this, saved + "개 항목을 저장했습니다.", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                }));
        dialog.show();
    }

    /** 결과 한 개의 선택 상자와 수정 필드를 만듭니다. */
    private ResultEditorFields createResultSection(int number, AiAnalysisResult result) {
        ResultEditorFields fields = new ResultEditorFields();
        fields.section = new LinearLayout(this);
        fields.section.setOrientation(LinearLayout.VERTICAL);
        fields.section.setPadding(dp(12), dp(10), dp(12), dp(12));
        fields.section.setBackground(sectionBackground());

        fields.selected = new CheckBox(this);
        fields.selected.setText("항목 " + number + " 저장");
        fields.selected.setTextSize(15);
        fields.selected.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        fields.selected.setChecked(true);
        fields.section.addView(fields.selected, fullWrap());

        fields.type = field("분류: 일정 / 할 일 / 메모", result.type);
        fields.title = field("제목", result.title);
        fields.date = field("날짜: yyyy-MM-dd", result.date);
        fields.time = field("시간: HH:mm", result.time);

        TextView repeatTitle = text("반복 설정", 13, Color.DKGRAY);
        repeatTitle.setPadding(0, dp(6), 0, 0);
        fields.repeat = new Spinner(this);
        ArrayAdapter<String> repeatAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, REPEAT_LABELS);
        repeatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fields.repeat.setAdapter(repeatAdapter);
        fields.repeat.setSelection(repeatIndex(result.repeatType));

        fields.content = field("메모 내용", result.content);
        fields.content.setSingleLine(false);
        fields.content.setMinLines(3);
        fields.content.setGravity(Gravity.TOP);

        fields.section.addView(fields.type);
        fields.section.addView(fields.title);
        fields.section.addView(fields.date);
        fields.section.addView(fields.time);
        fields.section.addView(repeatTitle);
        fields.section.addView(fields.repeat, fullWrap());
        fields.section.addView(fields.content);
        return fields;
    }

    /** 체크된 결과만 기존 10열 저장 형식으로 한 번에 추가합니다. */
    private int saveSelectedItems(List<ResultEditorFields> editors) {
        StringBuilder newLines = new StringBuilder();
        int count = 0;

        for (ResultEditorFields fields : editors) {
            if (!fields.selected.isChecked()) continue;
            String type = normalizeType(fields.type.getText().toString());
            String title = fields.title.getText().toString().trim();
            String date = fields.date.getText().toString().trim();
            String time = fields.time.getText().toString().trim();
            String content = fields.content.getText().toString().trim();
            String repeatType = REPEAT_VALUES[fields.repeat.getSelectedItemPosition()];
            String safeTitle = title.isEmpty() ? (content.isEmpty() ? type : content) : title;

            if (newLines.length() > 0) newLines.append('\n');
            newLines.append(escape(type)).append('\t')
                    .append(escape(safeTitle)).append('\t')
                    .append(escape(date)).append('\t')
                    .append(escape(time)).append('\t')
                    .append(escape(content)).append('\t')
                    .append("0\t0\t").append(repeatType).append("\t\tDEFAULT");
            count++;
        }

        if (count == 0) return 0;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String stored = prefs.getString(KEY_ITEMS, "");
        String updated = newLines + (stored == null || stored.isEmpty() ? "" : "\n" + stored);
        prefs.edit().putString(KEY_ITEMS, updated).apply();

        AlarmScheduler.rescheduleAll(getApplicationContext());
        TodayWidgetProvider.updateAll(getApplicationContext());
        return count;
    }

    private void handleAiError(AiSettings settings, String original, String message) {
        if (settings.fallbackToLocal) {
            Toast.makeText(this, settings.providerLabel()
                    + " 분석에 실패해 기본 규칙 분석으로 전환합니다.", Toast.LENGTH_LONG).show();
            openLocalAnalyzer(original);
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(settings.providerLabel() + " AI 분석 실패")
                .setMessage(message)
                .setNegativeButton("닫기", null)
                .setNeutralButton("AI 설정", (dialog, which) -> startActivityForResult(
                        new Intent(this, AiSettingsActivity.class), REQUEST_SETTINGS))
                .setPositiveButton("기본 규칙 분석", (dialog, which) -> openLocalAnalyzer(original))
                .show();
    }

    /** 기존 메인 화면의 규칙 분석기에 같은 문장을 전달합니다. */
    private void openLocalAnalyzer(String original) {
        Intent intent = new Intent(this, IntegratedMainActivity.class);
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, original);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SETTINGS) refreshProviderInfo();
    }

    private int repeatIndex(String value) {
        for (int i = 0; i < REPEAT_VALUES.length; i++) {
            if (REPEAT_VALUES[i].equals(value)) return i;
        }
        return 0;
    }

    private String normalizeType(String value) {
        String text = value == null ? "" : value.trim();
        if (text.contains("할")) return "할 일";
        if (text.contains("일정")) return "일정";
        return "메모";
    }

    private String escape(String value) {
        return (value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n");
    }

    private EditText field(String hint, String value) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value == null ? "" : value);
        field.setSingleLine(true);
        return field;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackgroundColor(Color.rgb(35, 92, 190));
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
        return view;
    }

    private GradientDrawable sectionBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.rgb(214, 224, 238));
        return background;
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        params.setMargins(0, dp(5), 0, dp(5));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? "알 수 없는 오류" : error.getMessage();
        return message == null || message.trim().isEmpty() ? "알 수 없는 오류" : message;
    }

    /** 확인창에 표시되는 항목별 입력 필드 묶음입니다. */
    private static final class ResultEditorFields {
        LinearLayout section;
        CheckBox selected;
        EditText type;
        EditText title;
        EditText date;
        EditText time;
        EditText content;
        Spinner repeat;
    }
}
