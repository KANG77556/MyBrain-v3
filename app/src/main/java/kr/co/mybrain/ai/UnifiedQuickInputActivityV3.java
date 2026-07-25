package kr.co.mybrain.ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * MyBrain AI 통합 빠른 입력 화면입니다.
 * 개인정보 보호와 문서 품질 검토, 연속 음성 입력, 종료 시간 기능을 함께 사용합니다.
 */
public class UnifiedQuickInputActivityV3 extends UnifiedQuickInputActivityV5 {

    private static final int REQUEST_VOICE_V2 = 9601;
    private static final int REQUEST_DOCUMENT = 9602;
    private static final int REQUEST_PRIVACY_REVIEW = 9603;

    private EditText inputFieldV3;
    private String pendingRawText = "";
    private String pendingSource = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inputFieldV3 = findInput(findViewById(android.R.id.content));
        findViewById(android.R.id.content).postDelayed(this::patchInputButtons, 360L);
    }

    @Override
    protected void onResume() {
        super.onResume();
        View root = findViewById(android.R.id.content);
        if (root != null) root.postDelayed(this::patchInputButtons, 360L);
    }

    private void patchInputButtons() {
        List<View> views = new ArrayList<>();
        collect(findViewById(android.R.id.content), views);
        for (View view : views) {
            if (!(view instanceof Button)) continue;
            Button button = (Button) view;
            String label = textOf(button);
            if (label.startsWith("🎤")) {
                button.setText("🎤 이어 말하기");
                button.setOnClickListener(v -> startActivityForResult(
                        new Intent(this, VoiceCaptureActivityV2.class), REQUEST_VOICE_V2));
                button.setContentDescription("여러 음성 문장을 이어서 입력");
            } else if (label.startsWith("📷")) {
                button.setOnClickListener(v -> launchDocument(DocumentCaptureActivity.MODE_CAMERA));
                button.setContentDescription("문서 촬영 후 품질 확인과 OCR");
            } else if (label.startsWith("🖼")) {
                button.setOnClickListener(v -> launchDocument(DocumentCaptureActivity.MODE_GALLERY));
                button.setContentDescription("사진 선택 후 품질 확인과 OCR");
            }
        }
    }

    private void launchDocument(String mode) {
        Intent intent = new Intent(this, DocumentCaptureActivity.class);
        intent.putExtra(DocumentCaptureActivity.EXTRA_MODE, mode);
        startActivityForResult(intent, REQUEST_DOCUMENT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_VOICE_V2) {
            if (resultCode == RESULT_OK && data != null) {
                String value = safe(data.getStringExtra(VoiceCaptureActivityV2.EXTRA_RESULT_TEXT)).trim();
                if (!value.isEmpty()) reviewOrAppend(value, "음성 입력");
            }
            return;
        }
        if (requestCode == REQUEST_DOCUMENT) {
            if (resultCode == RESULT_OK && data != null) {
                String value = safe(data.getStringExtra(DocumentCaptureActivity.EXTRA_RESULT_TEXT)).trim();
                String source = safe(data.getStringExtra(DocumentCaptureActivity.EXTRA_RESULT_SOURCE));
                int score = data.getIntExtra(DocumentCaptureActivity.EXTRA_QUALITY_SCORE, -1);
                if (!value.isEmpty()) {
                    reviewOrAppend(value, source.isEmpty() ? "문서 OCR" : source);
                    if (score >= 0) {
                        Toast.makeText(this, "OCR 품질 점수 " + score + "점으로 처리했습니다.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
            return;
        }
        if (requestCode == REQUEST_PRIVACY_REVIEW) {
            if (resultCode == RESULT_OK && data != null) {
                String value = safe(data.getStringExtra(PrivacyReviewActivity.EXTRA_RESULT_TEXT)).trim();
                if (!value.isEmpty()) appendText(value);
            }
            pendingRawText = "";
            pendingSource = "";
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void reviewOrAppend(String raw, String source) {
        OcrPrivacyProcessor.ProcessedText processed = OcrPrivacyProcessor.process(raw, source);
        if (processed.maskedCount > 0) {
            pendingRawText = raw;
            pendingSource = source;
            Intent intent = new Intent(this, PrivacyReviewActivity.class);
            intent.putExtra(PrivacyReviewActivity.EXTRA_RAW_TEXT, raw);
            intent.putExtra(PrivacyReviewActivity.EXTRA_SOURCE, source);
            startActivityForResult(intent, REQUEST_PRIVACY_REVIEW);
        } else {
            appendText(processed.storedText);
        }
    }

    private void appendText(String addition) {
        if (inputFieldV3 == null) inputFieldV3 = findInput(findViewById(android.R.id.content));
        if (inputFieldV3 == null) return;
        String current = inputFieldV3.getText().toString().trim();
        String value = addition == null ? "" : addition.trim();
        if (value.isEmpty()) return;
        inputFieldV3.setText(current.isEmpty() ? value : current + "\n\n" + value);
        inputFieldV3.setSelection(inputFieldV3.length());
        inputFieldV3.requestFocus();
    }

    private EditText findInput(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            EditText found = findInput(group.getChildAt(i));
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

    private String textOf(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
