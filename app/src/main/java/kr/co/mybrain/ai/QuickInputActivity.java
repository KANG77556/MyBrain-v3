package kr.co.mybrain.ai;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityNotFoundException;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * 모든 빠른 입력 수단을 한곳에 모은 화면입니다.
 * 직접 입력은 기존 편집기로, 음성과 OCR은 원문 확인 화면으로 연결합니다.
 */
public class QuickInputActivity extends Activity {
    public static final String EXTRA_MODE = "quick_input_mode";
    public static final String MODE_DIRECT = "DIRECT";
    public static final String MODE_VOICE = "VOICE";
    public static final String MODE_CAMERA = "CAMERA";
    public static final String MODE_GALLERY = "GALLERY";

    private static final int REQUEST_VOICE = 2101;
    private static final int REQUEST_CAMERA = 2102;
    private static final int REQUEST_GALLERY = 2103;
    private static final int REQUEST_AUDIO_PERMISSION = 2104;

    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BORDER = Color.rgb(220, 228, 240);
    private static final int BACKGROUND = Color.rgb(247, 249, 253);

    private Uri pendingCameraUri;
    private File pendingCameraFile;
    private boolean automaticModeStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode != null && !mode.trim().isEmpty()) {
            findViewById(android.R.id.content).post(() -> runMode(mode));
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.setBackgroundColor(Color.WHITE);

        Button back = button("‹", Color.WHITE, TEXT);
        back.setTextSize(25);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text("새 기록", 22, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(18), dp(18), dp(30));
        scroll.addView(body);

        TextView guide = text("가장 편한 방법으로 기록하세요.", 20, TEXT);
        guide.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        body.addView(guide);

        TextView sub = text("음성과 사진의 내용은 확인한 뒤 일정·할 일·메모로 저장됩니다.", 14, MUTED);
        sub.setPadding(0, dp(6), 0, dp(14));
        body.addView(sub);

        body.addView(actionButton("✍  직접 입력", "제목·날짜·알림을 직접 선택", v -> runMode(MODE_DIRECT)), actionParams());
        body.addView(actionButton("🎤  음성 메모", "말한 내용을 한국어 문장으로 변환", v -> runMode(MODE_VOICE)), actionParams());
        body.addView(actionButton("📷  문서 촬영", "공문·안내문을 촬영해 글자 추출", v -> runMode(MODE_CAMERA)), actionParams());
        body.addView(actionButton("🖼  사진에서 가져오기", "사진첩에서 선택한 이미지의 글자 추출", v -> runMode(MODE_GALLERY)), actionParams());

        TextView offline = text("날짜·기간 분석과 저장은 기기에서 처리됩니다. 음성 인식은 휴대전화의 한국어 음성팩 상태에 따라 인터넷이 필요할 수 있습니다.", 12, MUTED);
        offline.setPadding(dp(2), dp(14), dp(2), 0);
        body.addView(offline);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private Button actionButton(String title, String subtitle, android.view.View.OnClickListener listener) {
        Button button = button(title + "\n" + subtitle, Color.WHITE, TEXT);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(18), dp(10), dp(18), dp(10));
        button.setTextSize(15);
        button.setLineSpacing(dp(3), 1f);
        button.setBackground(rounded(Color.WHITE, 18, BORDER, 1));
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(82));
        params.setMargins(0, 0, 0, dp(10));
        return params;
    }

    private void runMode(String mode) {
        if (automaticModeStarted) return;
        automaticModeStarted = true;
        if (MODE_DIRECT.equals(mode)) {
            startActivity(new Intent(this, WorkItemEditorActivity.class));
            finish();
        } else if (MODE_VOICE.equals(mode)) {
            startVoice();
        } else if (MODE_CAMERA.equals(mode)) {
            startCamera();
        } else if (MODE_GALLERY.equals(mode)) {
            startGallery();
        } else {
            automaticModeStarted = false;
        }
    }

    private void startVoice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
            return;
        }
        launchVoiceRecognizer();
    }

    private void launchVoiceRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR");
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "일정이나 업무를 자연스럽게 말하세요.");
        try {
            startActivityForResult(intent, REQUEST_VOICE);
        } catch (ActivityNotFoundException error) {
            automaticModeStarted = false;
            new AlertDialog.Builder(this)
                    .setTitle("음성 인식 서비스를 찾을 수 없습니다")
                    .setMessage("휴대전화의 Google 음성 인식 또는 Samsung 음성 입력을 활성화한 뒤 다시 시도하세요.")
                    .setPositiveButton("확인", null)
                    .show();
        }
    }

    private void startCamera() {
        try {
            File directory = new File(getCacheDir(), "quick_capture");
            if (!directory.exists() && !directory.mkdirs()) throw new IOException("임시 폴더 생성 실패");
            pendingCameraFile = File.createTempFile("document_", ".jpg", directory);
            pendingCameraUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", pendingCameraFile);

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            intent.setClipData(ClipData.newRawUri("문서 촬영", pendingCameraUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CAMERA);
        } catch (ActivityNotFoundException error) {
            automaticModeStarted = false;
            Toast.makeText(this, "사용 가능한 카메라 앱이 없습니다.", Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            automaticModeStarted = false;
            Toast.makeText(this, "촬영 준비에 실패했습니다: " + safeMessage(error), Toast.LENGTH_LONG).show();
        }
    }

    private void startGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_GALLERY);
        } catch (ActivityNotFoundException error) {
            automaticModeStarted = false;
            Toast.makeText(this, "사진 선택 화면을 열 수 없습니다.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        automaticModeStarted = false;
        if (resultCode != RESULT_OK) {
            cleanupCameraFile();
            return;
        }

        if (requestCode == REQUEST_VOICE) {
            ArrayList<String> results = data == null ? null
                    : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String text = results == null || results.isEmpty() ? "" : results.get(0);
            if (text.trim().isEmpty()) {
                Toast.makeText(this, "인식된 음성이 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            openReview(text, "음성 메모");
        } else if (requestCode == REQUEST_CAMERA) {
            if (pendingCameraUri == null) {
                Toast.makeText(this, "촬영 이미지를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            processOcr(pendingCameraUri, "문서 촬영");
        } else if (requestCode == REQUEST_GALLERY) {
            Uri uri = data == null ? null : data.getData();
            if (uri == null) {
                Toast.makeText(this, "선택한 사진을 읽을 수 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            processOcr(uri, "사진 OCR");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        automaticModeStarted = false;
        if (requestCode != REQUEST_AUDIO_PERMISSION) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            automaticModeStarted = true;
            launchVoiceRecognizer();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("마이크 권한이 필요합니다")
                    .setMessage("음성 메모를 사용하려면 설정에서 MyBrain AI의 마이크 권한을 허용하세요.")
                    .setPositiveButton("확인", null)
                    .show();
        }
    }

    private void processOcr(Uri uri, String source) {
        LinearLayout progressContent = new LinearLayout(this);
        progressContent.setOrientation(LinearLayout.VERTICAL);
        progressContent.setGravity(Gravity.CENTER);
        progressContent.setPadding(dp(24), dp(16), dp(24), dp(10));
        progressContent.addView(new ProgressBar(this), new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView status = text("사진에서 한글을 찾고 있습니다.", 14, TEXT);
        status.setPadding(0, dp(12), 0, 0);
        progressContent.addView(status);
        AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("문서 분석 중")
                .setView(progressContent)
                .setCancelable(false)
                .create();
        progress.show();

        final TextRecognizer recognizer = TextRecognition.getClient(
                new KoreanTextRecognizerOptions.Builder().build());
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            recognizer.process(image)
                    .addOnSuccessListener(result -> {
                        progress.dismiss();
                        recognizer.close();
                        cleanupCameraFile();
                        String text = normalizeOcr(result.getText());
                        if (text.isEmpty()) {
                            Toast.makeText(this, "사진에서 읽을 수 있는 글자를 찾지 못했습니다.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        openReview(text, source);
                    })
                    .addOnFailureListener(error -> {
                        progress.dismiss();
                        recognizer.close();
                        cleanupCameraFile();
                        Toast.makeText(this, "문자 인식에 실패했습니다: " + safeMessage(error), Toast.LENGTH_LONG).show();
                    });
        } catch (Exception error) {
            progress.dismiss();
            recognizer.close();
            cleanupCameraFile();
            Toast.makeText(this, "이미지를 읽지 못했습니다: " + safeMessage(error), Toast.LENGTH_LONG).show();
        }
    }

    private void openReview(String text, String source) {
        Intent intent = new Intent(this, QuickCaptureReviewActivity.class);
        intent.putExtra(QuickCaptureReviewActivity.EXTRA_TEXT, text);
        intent.putExtra(QuickCaptureReviewActivity.EXTRA_SOURCE, source);
        startActivity(intent);
        finish();
    }

    private String normalizeOcr(String raw) {
        return raw == null ? "" : raw.replace("\r", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private void cleanupCameraFile() {
        if (pendingCameraFile != null && pendingCameraFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            pendingCameraFile.delete();
        }
        pendingCameraFile = null;
        pendingCameraUri = null;
    }

    private Button button(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(foreground);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setMinimumHeight(0);
        button.setMinimumWidth(0);
        button.setBackground(rounded(background, 14, 0, 0));
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

    private String safeMessage(Throwable error) {
        String message = error == null ? "알 수 없는 오류" : error.getMessage();
        return message == null || message.trim().isEmpty() ? "알 수 없는 오류" : message;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
