package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
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

/**
 * 카메라 또는 사진 선택 후 OCR 전에 품질을 검토하는 화면입니다.
 * 방향 보정·보수적 자동 자르기·품질 점검을 마친 이미지만 OCR에 전달합니다.
 */
public class DocumentCaptureActivity extends Activity {

    public static final String EXTRA_MODE = "document_capture_mode";
    public static final String EXTRA_RESULT_TEXT = "document_capture_result_text";
    public static final String EXTRA_RESULT_SOURCE = "document_capture_result_source";
    public static final String EXTRA_QUALITY_SCORE = "document_capture_quality_score";

    public static final String MODE_CAMERA = "CAMERA";
    public static final String MODE_GALLERY = "GALLERY";

    private static final int REQUEST_CAMERA = 9101;
    private static final int REQUEST_GALLERY = 9102;
    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BORDER = Color.rgb(220, 228, 240);
    private static final int BACKGROUND = Color.rgb(247, 249, 253);

    private String mode;
    private Uri pendingCameraUri;
    private File pendingCameraFile;
    private DocumentImageProcessor.Result preparedResult;

    private ImageView previewImage;
    private TextView statusTitle;
    private TextView statusDetail;
    private ProgressBar progressBar;
    private Button retryButton;
    private Button useButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (!MODE_GALLERY.equals(mode)) mode = MODE_CAMERA;
        buildScreen();
        previewImage.postDelayed(this::launchSource, 180L);
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.setBackgroundColor(Color.WHITE);

        Button close = button("‹", Color.WHITE, TEXT);
        close.setTextSize(25);
        close.setOnClickListener(v -> finish());
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text(MODE_CAMERA.equals(mode) ? "문서 촬영" : "사진 OCR", 22, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(14), dp(16), dp(24));
        scroll.addView(body);

        TextView guide = text("OCR 전에 사진 상태를 확인합니다.", 18, TEXT);
        guide.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        body.addView(guide);

        TextView sub = text("방향을 자동으로 바로잡고, 문서 경계가 확실할 때만 안전하게 자릅니다.", 13, MUTED);
        sub.setPadding(0, dp(5), 0, dp(10));
        body.addView(sub);

        LinearLayout previewCard = new LinearLayout(this);
        previewCard.setOrientation(LinearLayout.VERTICAL);
        previewCard.setPadding(dp(10), dp(10), dp(10), dp(10));
        previewCard.setBackground(rounded(Color.WHITE, 18, BORDER, 1));

        previewImage = new ImageView(this);
        previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewImage.setBackgroundColor(Color.rgb(238, 242, 248));
        previewCard.addView(previewImage, new LinearLayout.LayoutParams(-1, dp(330)));

        progressBar = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.setMargins(0, dp(12), 0, dp(6));
        previewCard.addView(progressBar, progressParams);

        statusTitle = text("사진을 준비하고 있습니다.", 16, PRIMARY);
        statusTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusTitle.setGravity(Gravity.CENTER);
        previewCard.addView(statusTitle);

        statusDetail = text("카메라 또는 사진 선택 화면이 열립니다.", 13, MUTED);
        statusDetail.setGravity(Gravity.CENTER);
        statusDetail.setPadding(dp(8), dp(5), dp(8), dp(6));
        previewCard.addView(statusDetail);

        body.addView(previewCard, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, 0);

        retryButton = button(MODE_CAMERA.equals(mode) ? "다시 촬영" : "다시 선택",
                Color.rgb(235, 242, 255), PRIMARY);
        retryButton.setEnabled(false);
        retryButton.setOnClickListener(v -> launchSource());
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        retryParams.setMargins(0, 0, dp(5), 0);
        actions.addView(retryButton, retryParams);

        useButton = button("OCR 실행", PRIMARY, Color.WHITE);
        useButton.setEnabled(false);
        useButton.setAlpha(0.55f);
        useButton.setOnClickListener(v -> confirmAndRunOcr());
        LinearLayout.LayoutParams useParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        useParams.setMargins(dp(5), 0, 0, 0);
        actions.addView(useButton, useParams);
        body.addView(actions);

        TextView help = text("점수가 낮아도 OCR을 실행할 수 있지만, 재촬영하면 정확도가 좋아질 수 있습니다.", 12, MUTED);
        help.setPadding(dp(2), dp(10), dp(2), 0);
        body.addView(help);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private void launchSource() {
        cleanupPrepared();
        retryButton.setEnabled(false);
        useButton.setEnabled(false);
        useButton.setAlpha(0.55f);
        previewImage.setImageDrawable(null);
        progressBar.setVisibility(View.VISIBLE);
        statusTitle.setText("사진을 기다리고 있습니다.");
        statusDetail.setText("촬영 또는 선택을 완료하세요.");

        if (MODE_CAMERA.equals(mode)) launchCamera();
        else launchGallery();
    }

    private void launchCamera() {
        try {
            File directory = new File(getCacheDir(), "quick_capture");
            if (!directory.exists() && !directory.mkdirs()) throw new IOException("임시 폴더 생성 실패");
            pendingCameraFile = File.createTempFile("quality_", ".jpg", directory);
            pendingCameraUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", pendingCameraFile);

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            intent.setClipData(ClipData.newRawUri("문서 촬영", pendingCameraUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CAMERA);
        } catch (ActivityNotFoundException error) {
            showFailure("사용 가능한 카메라 앱이 없습니다.");
        } catch (Exception error) {
            showFailure("촬영 준비에 실패했습니다: " + safeMessage(error));
        }
    }

    private void launchGallery() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_GALLERY);
        } catch (ActivityNotFoundException error) {
            showFailure("사진 선택 화면을 열 수 없습니다.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            progressBar.setVisibility(View.GONE);
            statusTitle.setText("사진 입력이 취소되었습니다.");
            statusDetail.setText("다시 시도하거나 화면을 닫을 수 있습니다.");
            retryButton.setEnabled(true);
            return;
        }

        Uri source = null;
        if (requestCode == REQUEST_CAMERA) source = pendingCameraUri;
        else if (requestCode == REQUEST_GALLERY && data != null) source = data.getData();
        if (source == null) {
            showFailure("선택한 사진을 읽을 수 없습니다.");
            return;
        }
        prepareImage(source);
    }

    private void prepareImage(Uri source) {
        progressBar.setVisibility(View.VISIBLE);
        statusTitle.setText("사진 품질을 확인하고 있습니다.");
        statusDetail.setText("밝기·대비·선명도·회전 방향을 분석합니다.");
        retryButton.setEnabled(false);
        useButton.setEnabled(false);

        new Thread(() -> {
            try {
                DocumentImageProcessor.Result result = DocumentImageProcessor.prepare(this, source);
                runOnUiThread(() -> showPrepared(result));
            } catch (Exception error) {
                runOnUiThread(() -> showFailure("사진 처리에 실패했습니다: " + safeMessage(error)));
            } finally {
                cleanupCameraSource();
            }
        }, "mybrain-image-quality").start();
    }

    private void showPrepared(DocumentImageProcessor.Result result) {
        cleanupPrepared();
        preparedResult = result;
        Bitmap preview = BitmapFactory.decodeFile(result.imageFile.getAbsolutePath());
        previewImage.setImageBitmap(preview);
        progressBar.setVisibility(View.GONE);

        statusTitle.setText("품질 점수 " + result.qualityScore + "점"
                + (result.acceptable ? " · OCR 진행 가능" : " · 재촬영 권장"));
        statusTitle.setTextColor(result.acceptable ? Color.rgb(35, 135, 86) : Color.rgb(196, 102, 23));
        statusDetail.setText(result.width + " × " + result.height + " · " + result.qualitySummary);
        retryButton.setEnabled(true);
        useButton.setEnabled(true);
        useButton.setAlpha(1f);
        useButton.setText(result.acceptable ? "OCR 실행" : "그래도 OCR 실행");
    }

    private void confirmAndRunOcr() {
        if (preparedResult == null || preparedResult.imageFile == null) return;
        if (!preparedResult.acceptable) {
            new AlertDialog.Builder(this)
                    .setTitle("사진 품질이 낮습니다")
                    .setMessage(preparedResult.qualitySummary
                            + "\n\n글자가 잘못 인식될 수 있습니다. 그대로 진행할까요?")
                    .setNegativeButton("재촬영", (dialog, which) -> launchSource())
                    .setPositiveButton("OCR 진행", (dialog, which) -> runOcr())
                    .show();
        } else {
            runOcr();
        }
    }

    private void runOcr() {
        if (preparedResult == null) return;
        progressBar.setVisibility(View.VISIBLE);
        statusTitle.setText("문서 글자를 인식하고 있습니다.");
        statusTitle.setTextColor(PRIMARY);
        statusDetail.setText("한국어 OCR을 기기 내부에서 처리합니다.");
        retryButton.setEnabled(false);
        useButton.setEnabled(false);

        final TextRecognizer recognizer = TextRecognition.getClient(
                new KoreanTextRecognizerOptions.Builder().build());
        try {
            InputImage image = InputImage.fromFilePath(this, Uri.fromFile(preparedResult.imageFile));
            recognizer.process(image)
                    .addOnSuccessListener(result -> {
                        recognizer.close();
                        String value = normalize(result.getText());
                        if (value.isEmpty()) {
                            showFailure("사진에서 읽을 수 있는 글자를 찾지 못했습니다.");
                            return;
                        }
                        Intent data = new Intent();
                        data.putExtra(EXTRA_RESULT_TEXT, value);
                        data.putExtra(EXTRA_RESULT_SOURCE,
                                MODE_CAMERA.equals(mode) ? "문서 촬영" : "사진 OCR");
                        data.putExtra(EXTRA_QUALITY_SCORE, preparedResult.qualityScore);
                        setResult(RESULT_OK, data);
                        finish();
                    })
                    .addOnFailureListener(error -> {
                        recognizer.close();
                        showFailure("문자 인식에 실패했습니다: " + safeMessage(error));
                    });
        } catch (Exception error) {
            recognizer.close();
            showFailure("OCR용 이미지를 읽지 못했습니다: " + safeMessage(error));
        }
    }

    private void showFailure(String message) {
        progressBar.setVisibility(View.GONE);
        statusTitle.setText("처리하지 못했습니다.");
        statusTitle.setTextColor(Color.rgb(190, 62, 62));
        statusDetail.setText(message);
        retryButton.setEnabled(true);
        useButton.setEnabled(false);
        useButton.setAlpha(0.55f);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.replace("\r", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private void cleanupPrepared() {
        if (preparedResult != null && preparedResult.imageFile != null
                && preparedResult.imageFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            preparedResult.imageFile.delete();
        }
        preparedResult = null;
    }

    private void cleanupCameraSource() {
        if (pendingCameraFile != null && pendingCameraFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            pendingCameraFile.delete();
        }
        pendingCameraFile = null;
        pendingCameraUri = null;
    }

    @Override
    protected void onDestroy() {
        cleanupPrepared();
        cleanupCameraSource();
        super.onDestroy();
    }

    private Button button(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(foreground);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setMinimumHeight(0);
        button.setMinimumWidth(0);
        button.setBackground(rounded(background, 15, 0, 0));
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
        String value = error == null ? "알 수 없는 오류" : error.getMessage();
        return value == null || value.trim().isEmpty() ? "알 수 없는 오류" : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
