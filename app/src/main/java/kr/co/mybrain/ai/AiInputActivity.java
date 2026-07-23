package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GPT 또는 Gemini에 보낼 문장을 입력하고, 분석 결과를 확인한 뒤 저장하는 화면입니다.
 * 로컬 분석은 기존 MainActivity 분석기를 그대로 사용하므로 코드가 중복되지 않습니다.
 */
public class AiInputActivity extends Activity {
    private static final String PREFS = "mybrain_data";
    private static final String KEY_ITEMS = "items";
    private static final int REQUEST_SETTINGS = 1601;
    private static final String[] REPEAT_LABELS = {"반복 없음", "매일", "매주", "매월", "평일"};
    private static final String[] REPEAT_VALUES = {"NONE", "DAILY", "WEEKLY", "MONTHLY", "WEEKDAYS"};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView providerText;
    private EditText input;
    private Button analyzeButton;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        refreshProviderInfo();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
        super.onDestroy();
    }

    /** 클라우드 분석 전용 입력 화면을 구성합니다. */
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
        input.setHint("예: 다음 주 월요일 오후 2시 교무실 회의 준비 자료 확인");
        input.setTextSize(16);
        input.setGravity(Gravity.TOP);
        input.setMinLines(8);
        input.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(230)));

        TextView notice = text(
                "GPT 또는 Gemini를 선택하면 입력한 문장이 해당 클라우드 API로 전송됩니다. 분석 결과는 저장 전에 직접 수정할 수 있습니다.",
                13, Color.DKGRAY);
        notice.setPadding(0, dp(12), 0, dp(12));
        root.addView(notice, fullWrap());

        analyzeButton = primaryButton("AI로 분석");
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
        analyzeButton.setText(settings.isCloudProvider()
                ? settings.providerLabel() + "로 분석" : "로컬 분석으로 전환");
    }

    /** 설정과 키를 확인하고 필요하면 클라우드 전송 확인창을 표시합니다. */
    private void requestAnalysis() {
        String value = input.getText().toString().trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) {
            Toast.makeText(this, "분석할 메시지를 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        AiSettings settings = AiSettings.load(this);
        if (!settings.isCloudProvider()) {
            openLocalAnalyzer(value);
            return;
        }

        String keyName = AiSettings.PROVIDER_OPENAI.equals(settings.provider)
                ? SecureApiKeyStore.KEY_OPENAI : SecureApiKeyStore.KEY_GEMINI;
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
                    .setMessage("입력 문장을 " + settings.providerLabel()
                            + " 클라우드 API로 보내 분석할까요? 개인정보가 포함됐는지 확인하세요.")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("전송 및 분석", (dialog, which) -> performCloudAnalysis(settings, value))
                    .show();
        } else {
            performCloudAnalysis(settings, value);
        }
    }

    /** 네트워크 호출은 별도 작업 스레드에서 수행합니다. */
    private void performCloudAnalysis(AiSettings settings, String value) {
        String keyName = AiSettings.PROVIDER_OPENAI.equals(settings.provider)
                ? SecureApiKeyStore.KEY_OPENAI : SecureApiKeyStore.KEY_GEMINI;
        String apiKey = SecureApiKeyStore.read(this, keyName);
        showProgress(settings.providerLabel() + " 분석 중...");
        analyzeButton.setEnabled(false);

        executor.execute(() -> {
            try {
                AiAnalysisResult result = CloudAiAnalyzer.analyze(settings, apiKey, value);
                runOnUiThread(() -> {
                    hideProgress();
                    analyzeButton.setEnabled(true);
                    showResultEditor(result);
                });
            } catch (Exception e) {
                String message = safeMessage(e);
                runOnUiThread(() -> {
                    hideProgress();
                    analyzeButton.setEnabled(true);
                    handleCloudError(settings, value, message);
                });
            }
        });
    }

    /** 분석 결과를 수정 가능한 확인창으로 보여주고 사용자 승인 후 저장합니다. */
    private void showResultEditor(AiAnalysisResult result) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), 0, dp(18), 0);

        EditText type = field("분류: 일정 / 할 일 / 메모", result.type);
        EditText title = field("제목", result.title);
        EditText date = field("날짜: yyyy-MM-dd", result.date);
        EditText time = field("시간: HH:mm", result.time);
        TextView repeatTitle = text("반복 설정", 14, Color.DKGRAY);
        Spinner repeatSpinner = new Spinner(this);
        ArrayAdapter<String> repeatAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, REPEAT_LABELS);
        repeatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        repeatSpinner.setAdapter(repeatAdapter);
        repeatSpinner.setSelection(repeatIndex(result.repeatType));
        EditText content = field("메모 내용", result.content);
        content.setSingleLine(false);
        content.setMinLines(4);
        content.setGravity(Gravity.TOP);

        form.addView(type);
        form.addView(title);
        form.addView(date);
        form.addView(time);
        form.addView(repeatTitle);
        form.addView(repeatSpinner, fullWrap());
        form.addView(content);

        new AlertDialog.Builder(this)
                .setTitle("AI 분석 결과 확인")
                .setView(form)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> saveItem(
                        normalizeType(type.getText().toString()),
                        title.getText().toString().trim(),
                        date.getText().toString().trim(),
                        time.getText().toString().trim(),
                        content.getText().toString().trim(),
                        REPEAT_VALUES[repeatSpinner.getSelectedItemPosition()]))
                .show();
    }

    /** 기존 10열 저장 형식을 유지하여 이전 버전 데이터와 호환되게 항목을 추가합니다. */
    private void saveItem(String type, String title, String date, String time,
                          String content, String repeatType) {
        String safeTitle = title.isEmpty() ? (content.isEmpty() ? type : content) : title;
        String line = escape(type) + "\t"
                + escape(safeTitle) + "\t"
                + escape(date) + "\t"
                + escape(time) + "\t"
                + escape(content) + "\t"
                + "0\t0\t" + repeatType + "\t\tDEFAULT";

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String stored = prefs.getString(KEY_ITEMS, "");
        String updated = stored == null || stored.isEmpty() ? line : line + "\n" + stored;
        prefs.edit().putString(KEY_ITEMS, updated).apply();

        AlarmScheduler.rescheduleAll(getApplicationContext());
        TodayWidgetProvider.updateAll(getApplicationContext());
        Toast.makeText(this, "AI 분석 결과를 저장했습니다.", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private void handleCloudError(AiSettings settings, String original, String message) {
        if (settings.fallbackToLocal) {
            Toast.makeText(this, settings.providerLabel()
                    + " 연결에 실패해 로컬 분석으로 전환합니다.", Toast.LENGTH_LONG).show();
            openLocalAnalyzer(original);
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("클라우드 AI 분석 실패")
                .setMessage(message)
                .setNegativeButton("닫기", null)
                .setNeutralButton("AI 설정", (dialog, which) -> startActivityForResult(
                        new Intent(this, AiSettingsActivity.class), REQUEST_SETTINGS))
                .setPositiveButton("로컬 분석", (dialog, which) -> openLocalAnalyzer(original))
                .show();
    }

    /** 기존 메인 화면의 검증된 로컬 분석기에 같은 문장을 전달합니다. */
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

    private void showProgress(String message) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(message);
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    private void hideProgress() {
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
        progressDialog = null;
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

    private String safeMessage(Exception e) {
        String message = e == null ? "알 수 없는 오류" : e.getMessage();
        return message == null || message.trim().isEmpty() ? "알 수 없는 오류" : message;
    }
}
