package kr.co.mybrain.ai;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityNotFoundException;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
 * 통합 입력창에서 사용하는 음성·카메라·사진 OCR 전용 화면입니다.
 * 별도 확인 화면을 열지 않고 추출한 문자열만 호출 화면으로 반환합니다.
 */
public class UnifiedCaptureActivity extends Activity {
    public static final String EXTRA_MODE = "unified_capture_mode";
    public static final String EXTRA_RESULT_TEXT = "unified_capture_text";
    public static final String EXTRA_RESULT_SOURCE = "unified_capture_source";

    public static final String MODE_VOICE = "VOICE";
    public static final String MODE_CAMERA = "CAMERA";
    public static final String MODE_GALLERY = "GALLERY";

    private static final int REQUEST_VOICE = 3301;
    private static final int REQUEST_CAMERA = 3302;
    private static final int REQUEST_GALLERY = 3303;
    private static final int REQUEST_AUDIO_PERMISSION = 3304;

    private Uri pendingCameraUri;
    private File pendingCameraFile;
    private String mode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showWaitingScreen("입력 도구를 준비하고 있습니다.");
        mode = safe(getIntent().getStringExtra(EXTRA_MODE));
        findViewById(android.R.id.content).post(this::launchMode);
    }

    private void launchMode() {
        if (MODE_VOICE.equals(mode)) startVoice();
        else if (MODE_CAMERA.equals(mode)) startCamera();
        else if (MODE_GALLERY.equals(mode)) startGallery();
        else finishCanceled("지원하지 않는 입력 방식입니다.");
    }

    private void startVoice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR");
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "일정이나 업무를 자연스럽게 말하세요.");
        try {
            startActivityForResult(intent, REQUEST_VOICE);
        } catch (ActivityNotFoundException error) {
            new AlertDialog.Builder(this)
                    .setTitle("음성 인식 서비스를 찾을 수 없습니다")
                    .setMessage("Google 음성 인식 또는 Samsung 음성 입력을 활성화한 뒤 다시 시도하세요.")
                    .setPositiveButton("확인", (dialog, which) -> finish())
                    .setOnCancelListener(dialog -> finish())
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
            finishCanceled("사용 가능한 카메라 앱이 없습니다.");
        } catch (Exception error) {
            finishCanceled("촬영 준비에 실패했습니다: " + safeMessage(error));
        }
    }

    private void startGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_GALLERY);
        } catch (ActivityNotFoundException error) {
            finishCanceled("사진 선택 화면을 열 수 없습니다.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            cleanupCameraFile();
            finish();
            return;
        }

        if (requestCode == REQUEST_VOICE) {
            ArrayList<String> results = data == null ? null
                    : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String text = results == null || results.isEmpty() ? "" : results.get(0);
            if (text.trim().isEmpty()) finishCanceled("인식된 음성이 없습니다.");
            else returnText(text, "음성 메모");
        } else if (requestCode == REQUEST_CAMERA) {
            if (pendingCameraUri == null) finishCanceled("촬영 이미지를 찾을 수 없습니다.");
            else processOcr(pendingCameraUri, "문서 촬영");
        } else if (requestCode == REQUEST_GALLERY) {
            Uri uri = data == null ? null : data.getData();
            if (uri == null) finishCanceled("선택한 사진을 읽을 수 없습니다.");
            else processOcr(uri, "사진 OCR");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_AUDIO_PERMISSION) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoice();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("마이크 권한이 필요합니다")
                    .setMessage("음성 입력을 사용하려면 설정에서 MyBrain AI의 마이크 권한을 허용하세요.")
                    .setPositiveButton("확인", (dialog, which) -> finish())
                    .setOnCancelListener(dialog -> finish())
                    .show();
        }
    }

    private void processOcr(Uri uri, String source) {
        showWaitingScreen("사진에서 한글을 찾고 있습니다.");
        TextRecognizer recognizer = TextRecognition.getClient(
                new KoreanTextRecognizerOptions.Builder().build());
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            recognizer.process(image)
                    .addOnSuccessListener(result -> {
                        recognizer.close();
                        cleanupCameraFile();
                        String text = normalizeOcr(result.getText());
                        if (text.isEmpty()) finishCanceled("사진에서 읽을 수 있는 글자를 찾지 못했습니다.");
                        else returnText(text, source);
                    })
                    .addOnFailureListener(error -> {
                        recognizer.close();
                        cleanupCameraFile();
                        finishCanceled("문자 인식에 실패했습니다: " + safeMessage(error));
                    });
        } catch (Exception error) {
            recognizer.close();
            cleanupCameraFile();
            finishCanceled("이미지를 읽지 못했습니다: " + safeMessage(error));
        }
    }

    private void returnText(String text, String source) {
        Intent result = new Intent();
        result.putExtra(EXTRA_RESULT_TEXT, text);
        result.putExtra(EXTRA_RESULT_SOURCE, source);
        setResult(RESULT_OK, result);
        finish();
    }

    private void finishCanceled(String message) {
        if (message != null && !message.isEmpty()) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        setResult(RESULT_CANCELED);
        finish();
    }

    private void showWaitingScreen(String message) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(30), dp(30), dp(30), dp(30));
        root.setBackgroundColor(Color.rgb(247, 249, 253));

        ProgressBar progress = new ProgressBar(this);
        root.addView(progress, new LinearLayout.LayoutParams(dp(58), dp(58)));
        TextView status = new TextView(this);
        status.setText(message);
        status.setTextSize(15);
        status.setTextColor(Color.rgb(28, 38, 52));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(16), 0, 0);
        root.addView(status);
        setContentView(root);
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? "알 수 없는 오류" : error.getMessage();
        return message == null || message.trim().isEmpty() ? "알 수 없는 오류" : message;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
