package kr.co.mybrain.ai;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * MyBrain AI 1.8.9 통합 입력 화면입니다.
 * 기존 1.8.8 입력 기능 위에 개인정보 마스킹, 긴 OCR 핵심 정리,
 * 전체 원문 확인과 앱 내부 음성 입력 상태 화면을 추가합니다.
 */
public class UnifiedQuickInputActivityV2 extends UnifiedQuickInputActivity {

    public static final String EXTRA_PREFILL_TEXT = "unified_v2_prefill_text";
    public static final String EXTRA_PREFILL_SOURCE = "unified_v2_prefill_source";

    private static final int REQUEST_VOICE = 7601;
    private static final int REQUEST_MEDIA = 7602;
    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BORDER = Color.rgb(220, 228, 240);

    private final Handler privacyHandler = new Handler(Looper.getMainLooper());
    private final Runnable privacyRunnable = this::maskManualInputIfNeeded;

    private EditText inputField;
    private LinearLayout protectionCard;
    private TextView protectionTitle;
    private TextView protectionDetail;
    private Button fullTextButton;
    private String lastFullMaskedText = "";
    private String lastSummary = "";
    private String pendingSource = "";
    private boolean privacyBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inputField = findEditText(findViewById(android.R.id.content));
        installProtectionCard();
        patchMediaButtons();
        installManualPrivacyWatcher();

        String prefill = safe(getIntent().getStringExtra(EXTRA_PREFILL_TEXT)).trim();
        String source = safe(getIntent().getStringExtra(EXTRA_PREFILL_SOURCE));
        if (!prefill.isEmpty()) {
            inputField.post(() -> appendProcessed(prefill, source.isEmpty() ? "음성" : source));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        patchMediaButtons();
    }

    /** 기존 미디어 버튼을 새 음성 상태 화면과 개인정보 보호 OCR 흐름으로 연결합니다. */
    private void patchMediaButtons() {
        List<View> views = new ArrayList<>();
        collect(findViewById(android.R.id.content), views);
        for (View view : views) {
            if (!(view instanceof Button)) continue;
            Button button = (Button) view;
            String label = safe(button.getText() == null ? "" : button.getText().toString()).trim();
            if (label.startsWith("🎤")) {
                button.setOnClickListener(v -> {
                    pendingSource = "음성 입력";
                    startActivityForResult(new Intent(this, VoiceCaptureActivity.class), REQUEST_VOICE);
                });
            } else if (label.startsWith("📷")) {
                button.setOnClickListener(v -> launchMedia(QuickInputActivity.MODE_CAMERA, "문서 촬영"));
            } else if (label.startsWith("🖼")) {
                button.setOnClickListener(v -> launchMedia(QuickInputActivity.MODE_GALLERY, "사진 OCR"));
            }
        }
    }

    private void launchMedia(String mode, String source) {
        pendingSource = source;
        Intent intent = new Intent(this, QuickInputActivity.class);
        intent.putExtra(QuickInputActivity.EXTRA_MODE, mode);
        intent.putExtra(QuickInputActivity.EXTRA_RETURN_RESULT, true);
        startActivityForResult(intent, REQUEST_MEDIA);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_VOICE) {
            if (resultCode == RESULT_OK && data != null) {
                String value = safe(data.getStringExtra(VoiceCaptureActivity.EXTRA_RESULT_TEXT)).trim();
                if (!value.isEmpty()) appendProcessed(value, "음성 입력");
            }
            return;
        }
        if (requestCode == REQUEST_MEDIA) {
            if (resultCode == RESULT_OK && data != null) {
                String value = safe(data.getStringExtra(QuickInputActivity.EXTRA_RESULT_TEXT)).trim();
                String source = safe(data.getStringExtra(QuickInputActivity.EXTRA_RESULT_SOURCE));
                if (source.isEmpty()) source = pendingSource;
                if (!value.isEmpty()) appendProcessed(value, source);
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** OCR·음성 결과를 마스킹·정리한 뒤 현재 입력창에 이어 붙입니다. */
    private void appendProcessed(String raw, String source) {
        if (inputField == null) return;
        OcrPrivacyProcessor.ProcessedText processed = OcrPrivacyProcessor.process(raw, source);
        String current = inputField.getText().toString().trim();
        String addition = processed.storedText;

        privacyBinding = true;
        inputField.setText(current.isEmpty() ? addition : current + "\n\n" + addition);
        inputField.setSelection(inputField.length());
        privacyBinding = false;

        lastFullMaskedText = processed.fullMaskedText;
        lastSummary = processed.summary;
        showProtectionResult(processed, source);
    }

    /** 직접 입력한 전화번호·주민등록번호·이메일도 잠시 후 자동 마스킹합니다. */
    private void installManualPrivacyWatcher() {
        if (inputField == null) return;
        inputField.setMaxLines(7);
        inputField.setVerticalScrollBarEnabled(true);
        inputField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (privacyBinding) return;
                privacyHandler.removeCallbacks(privacyRunnable);
                privacyHandler.postDelayed(privacyRunnable, 720L);
            }
        });
    }

    private void maskManualInputIfNeeded() {
        if (inputField == null || privacyBinding) return;
        String raw = inputField.getText().toString();
        OcrPrivacyProcessor.ProcessedText processed = OcrPrivacyProcessor.process(raw, "직접 입력");
        if (processed.maskedCount <= 0 || processed.fullMaskedText.equals(raw)) return;

        privacyBinding = true;
        inputField.setText(processed.fullMaskedText);
        inputField.setSelection(inputField.length());
        privacyBinding = false;
        lastFullMaskedText = processed.fullMaskedText;
        lastSummary = processed.summary;
        showProtectionResult(processed, "직접 입력");
    }

    /** 입력창 아래에 개인정보 보호와 핵심 추출 결과를 간단히 표시합니다. */
    private void installProtectionCard() {
        if (inputField == null || !(inputField.getParent() instanceof LinearLayout)) return;
        LinearLayout body = (LinearLayout) inputField.getParent();

        protectionCard = new LinearLayout(this);
        protectionCard.setOrientation(LinearLayout.VERTICAL);
        protectionCard.setPadding(dp(13), dp(11), dp(13), dp(11));
        protectionCard.setBackground(rounded(Color.rgb(241, 247, 255), 16, Color.rgb(202, 220, 248), 1));
        protectionCard.setVisibility(View.GONE);

        protectionTitle = text("개인정보 보호", 14, PRIMARY);
        protectionTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        protectionCard.addView(protectionTitle);

        protectionDetail = text("", 12, MUTED);
        protectionDetail.setPadding(0, dp(4), 0, 0);
        protectionCard.addView(protectionDetail);

        fullTextButton = button("전체 원문 보기", Color.TRANSPARENT, PRIMARY);
        fullTextButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        fullTextButton.setPadding(0, dp(3), 0, 0);
        fullTextButton.setVisibility(View.GONE);
        fullTextButton.setOnClickListener(v -> showFullTextDialog());
        protectionCard.addView(fullTextButton, new LinearLayout.LayoutParams(-1, dp(40)));

        int inputIndex = body.indexOfChild(inputField);
        int insertIndex = Math.min(inputIndex + 2, body.getChildCount());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        body.addView(protectionCard, insertIndex, params);
    }

    private void showProtectionResult(OcrPrivacyProcessor.ProcessedText processed, String source) {
        if (protectionCard == null) return;
        List<String> messages = new ArrayList<>();
        if (processed.maskedCount > 0) messages.add("개인정보 " + processed.maskedCount + "건 마스킹");
        if (processed.summarized) messages.add("긴 문서의 핵심 내용을 위쪽에 배치");
        if (!processed.deadlineSummary.isEmpty()) messages.add("제출기한·일시 후보를 우선 추출");
        if (messages.isEmpty()) {
            protectionCard.setVisibility(View.GONE);
            return;
        }

        protectionCard.setVisibility(View.VISIBLE);
        protectionTitle.setText(source == null || source.isEmpty()
                ? "안전하게 정리했습니다" : source + " 내용을 안전하게 정리했습니다");
        protectionDetail.setText(join(messages, " · ")
                + (processed.deadlineSummary.isEmpty() ? "" : "\n" + processed.deadlineSummary));
        fullTextButton.setVisibility(processed.summarized ? View.VISIBLE : View.GONE);
    }

    private void showFullTextDialog() {
        if (lastFullMaskedText.trim().isEmpty()) return;
        ScrollView scroll = new ScrollView(this);
        TextView content = text(lastFullMaskedText, 14, TEXT);
        content.setTextIsSelectable(true);
        content.setPadding(dp(18), dp(12), dp(18), dp(18));
        scroll.addView(content);

        new AlertDialog.Builder(this)
                .setTitle("마스킹된 전체 원문")
                .setView(scroll)
                .setNegativeButton("닫기", null)
                .setNeutralButton("전체 원문만 사용", (dialog, which) -> {
                    privacyBinding = true;
                    inputField.setText(lastFullMaskedText);
                    inputField.setSelection(inputField.length());
                    privacyBinding = false;
                })
                .setPositiveButton("핵심 정리 유지", null)
                .show();
    }

    private EditText findEditText(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            EditText found = findEditText(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private void collect(View view, List<View> output) {
        if (view == null) return;
        output.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), output);
    }

    @Override
    protected void onDestroy() {
        privacyHandler.removeCallbacks(privacyRunnable);
        super.onDestroy();
    }

    private Button button(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(foreground);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinimumHeight(0);
        button.setMinimumWidth(0);
        button.setBackground(rounded(background, 12, 0, 0));
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private String join(List<String> values, String separator) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(separator);
            result.append(value);
        }
        return result.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
