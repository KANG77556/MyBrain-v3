package kr.co.mybrain.v2.share;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 공유된 이미지와 PDF를 휴대폰 내부 OCR로 읽습니다. */
public final class SharedDocumentTextExtractor {
    public interface Callback {
        void onCompleted(String extractedText, List<String> errors);
    }

    private static final int MAX_PDF_PAGES = 10;
    private final Context appContext;
    private final TextRecognizer recognizer;

    public SharedDocumentTextExtractor(Context context) {
        appContext = context.getApplicationContext();
        recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
    }

    public void extract(List<Uri> uris, Callback callback) {
        List<Uri> safeUris = uris == null ? new ArrayList<>() : new ArrayList<>(uris);
        StringBuilder text = new StringBuilder();
        List<String> errors = new ArrayList<>();
        extractNext(safeUris, 0, text, errors, callback);
    }

    private void extractNext(List<Uri> uris, int index, StringBuilder text,
                             List<String> errors, Callback callback) {
        if (index >= uris.size()) {
            recognizer.close();
            callback.onCompleted(text.toString().trim(), errors);
            return;
        }

        Uri uri = uris.get(index);
        String mime = appContext.getContentResolver().getType(uri);
        if (mime != null && mime.equalsIgnoreCase("application/pdf")) {
            extractPdf(uri, value -> {
                appendSection(text, "PDF " + (index + 1), value);
                extractNext(uris, index + 1, text, errors, callback);
            }, error -> {
                errors.add("PDF " + (index + 1) + ": " + error);
                extractNext(uris, index + 1, text, errors, callback);
            });
        } else if (mime != null && mime.startsWith("image/")) {
            extractImage(uri, value -> {
                appendSection(text, "이미지 " + (index + 1), value);
                extractNext(uris, index + 1, text, errors, callback);
            }, error -> {
                errors.add("이미지 " + (index + 1) + ": " + error);
                extractNext(uris, index + 1, text, errors, callback);
            });
        } else {
            errors.add("지원하지 않는 파일 형식: " + uri);
            extractNext(uris, index + 1, text, errors, callback);
        }
    }

    private void extractImage(Uri uri, Success success, Failure failure) {
        try {
            InputImage image = InputImage.fromFilePath(appContext, uri);
            recognizer.process(image)
                    .addOnSuccessListener(result -> success.accept(result.getText()))
                    .addOnFailureListener(error -> failure.accept(message(error)));
        } catch (IOException | RuntimeException error) {
            failure.accept(message(error));
        }
    }

    private void extractPdf(Uri uri, Success success, Failure failure) {
        ContentResolver resolver = appContext.getContentResolver();
        try {
            ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "r");
            if (descriptor == null) {
                failure.accept("PDF를 열 수 없습니다.");
                return;
            }
            PdfRenderer renderer = new PdfRenderer(descriptor);
            StringBuilder result = new StringBuilder();
            int pageCount = Math.min(renderer.getPageCount(), MAX_PDF_PAGES);
            extractPdfPage(renderer, descriptor, 0, pageCount, result, success, failure);
        } catch (IOException | SecurityException | IllegalArgumentException error) {
            failure.accept(message(error));
        }
    }

    private void extractPdfPage(PdfRenderer renderer, ParcelFileDescriptor descriptor,
                                int pageIndex, int pageCount, StringBuilder result,
                                Success success, Failure failure) {
        if (pageIndex >= pageCount) {
            closePdf(renderer, descriptor);
            success.accept(result.toString().trim());
            return;
        }

        PdfRenderer.Page page = renderer.openPage(pageIndex);
        int width = Math.min(1800, Math.max(1000, page.getWidth() * 2));
        int height = Math.max(1, Math.round(width * (page.getHeight() / (float) page.getWidth())));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();

        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(ocr -> {
                    String value = ocr.getText().trim();
                    if (!value.isEmpty()) {
                        if (result.length() > 0) result.append("\n\n");
                        result.append("[페이지 ").append(pageIndex + 1).append("]\n").append(value);
                    }
                    bitmap.recycle();
                    extractPdfPage(renderer, descriptor, pageIndex + 1, pageCount, result, success, failure);
                })
                .addOnFailureListener(error -> {
                    bitmap.recycle();
                    closePdf(renderer, descriptor);
                    failure.accept("페이지 " + (pageIndex + 1) + " 인식 실패: " + message(error));
                });
    }

    private void closePdf(PdfRenderer renderer, ParcelFileDescriptor descriptor) {
        try { renderer.close(); } catch (RuntimeException ignored) { }
        try { descriptor.close(); } catch (IOException ignored) { }
    }

    private void appendSection(StringBuilder target, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (target.length() > 0) target.append("\n\n");
        target.append("[").append(label).append("]\n").append(value.trim());
    }

    private String message(Throwable error) {
        String value = error == null ? null : error.getMessage();
        return value == null || value.trim().isEmpty() ? "알 수 없는 오류" : value;
    }

    private interface Success { void accept(String value); }
    private interface Failure { void accept(String error); }
}
