package kr.co.mybrain.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 문서 사진을 OCR에 적합한 상태로 준비하는 기기 내부 처리기입니다.
 *
 * - 갤러리 URI를 앱 캐시로 복사합니다.
 * - EXIF 회전 정보를 반영합니다.
 * - 해상도·밝기·대비·선명도를 점수로 계산합니다.
 * - 흰 문서 경계가 충분히 명확할 때만 보수적으로 자동 자르기합니다.
 */
public final class DocumentImageProcessor {

    private static final int MAX_DECODE_EDGE = 1800;

    private DocumentImageProcessor() { }

    /** 화면 표시와 OCR 실행에 필요한 이미지 처리 결과입니다. */
    public static final class Result {
        public final File imageFile;
        public final int width;
        public final int height;
        public final int qualityScore;
        public final boolean acceptable;
        public final boolean autoCropped;
        public final int rotationDegrees;
        public final String qualitySummary;

        Result(File imageFile, int width, int height, int qualityScore,
               boolean acceptable, boolean autoCropped, int rotationDegrees,
               String qualitySummary) {
            this.imageFile = imageFile;
            this.width = width;
            this.height = height;
            this.qualityScore = qualityScore;
            this.acceptable = acceptable;
            this.autoCropped = autoCropped;
            this.rotationDegrees = rotationDegrees;
            this.qualitySummary = qualitySummary;
        }
    }

    /** 원본 URI를 읽어 회전·품질 분석·보수적 자동 자르기를 수행합니다. */
    public static Result prepare(Context context, Uri sourceUri) throws IOException {
        if (context == null || sourceUri == null) throw new IOException("이미지를 찾을 수 없습니다.");

        File directory = new File(context.getCacheDir(), "document_review");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("문서 검토 폴더를 만들 수 없습니다.");
        }

        File sourceFile = File.createTempFile("source_", ".jpg", directory);
        copyUri(context, sourceUri, sourceFile);

        int rotation = readRotation(sourceFile);
        Bitmap bitmap = decodeSampled(sourceFile, MAX_DECODE_EDGE);
        if (bitmap == null) throw new IOException("사진을 읽을 수 없습니다.");

        Bitmap rotated = rotate(bitmap, rotation);
        if (rotated != bitmap) bitmap.recycle();

        Rect crop = detectDocumentBounds(rotated);
        boolean cropped = crop != null;
        Bitmap prepared = rotated;
        if (crop != null) {
            prepared = Bitmap.createBitmap(rotated, crop.left, crop.top, crop.width(), crop.height());
            if (prepared != rotated) rotated.recycle();
        }

        Quality quality = analyze(prepared);
        File output = File.createTempFile("prepared_", ".jpg", directory);
        try (FileOutputStream stream = new FileOutputStream(output)) {
            if (!prepared.compress(Bitmap.CompressFormat.JPEG, 93, stream)) {
                throw new IOException("OCR용 사진 저장에 실패했습니다.");
            }
        }

        int width = prepared.getWidth();
        int height = prepared.getHeight();
        prepared.recycle();
        //noinspection ResultOfMethodCallIgnored
        sourceFile.delete();

        String summary = quality.summary;
        if (rotation != 0) summary += " · 방향 자동 보정";
        if (cropped) summary += " · 문서 경계 자동 자르기";

        return new Result(output, width, height, quality.score,
                quality.score >= 58, cropped, rotation, summary);
    }

    private static void copyUri(Context context, Uri uri, File destination) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(destination)) {
            if (input == null) throw new IOException("사진 파일을 열 수 없습니다.");
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        }
    }

    private static Bitmap decodeSampled(File file, int maxEdge) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > maxEdge) {
            sample *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private static int readRotation(File file) {
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Exception ignored) { }
        return 0;
    }

    private static Bitmap rotate(Bitmap source, int degrees) {
        if (degrees == 0) return source;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    /**
     * 어두운 배경 위의 밝은 문서가 명확할 때만 축 정렬된 경계를 잘라냅니다.
     * 경계가 애매하면 null을 반환해 원본 범위를 유지합니다.
     */
    private static Rect detectDocumentBounds(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width < 500 || height < 500) return null;

        int block = Math.max(8, Math.min(width, height) / 18);
        double corner = (averageLuma(bitmap, 0, 0, block, block)
                + averageLuma(bitmap, width - block, 0, width, block)
                + averageLuma(bitmap, 0, height - block, block, height)
                + averageLuma(bitmap, width - block, height - block, width, height)) / 4.0;
        double center = averageLuma(bitmap, width / 4, height / 4,
                width * 3 / 4, height * 3 / 4);

        // 밝은 배경에서는 문서 경계를 안전하게 구분하기 어려우므로 자르지 않습니다.
        if (center - corner < 28.0 || corner > 190.0) return null;

        int threshold = (int) Math.min(238, corner + 32.0);
        int step = Math.max(2, Math.min(width, height) / 550);
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        int count = 0;
        int total = 0;

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                total++;
                int color = bitmap.getPixel(x, y);
                int luma = luma(color);
                int spread = Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color)))
                        - Math.min(Color.red(color), Math.min(Color.green(color), Color.blue(color)));
                if (luma >= threshold && spread < 58) {
                    count++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX <= minX || maxY <= minY || count < total * 0.12) return null;

        int cropWidth = maxX - minX;
        int cropHeight = maxY - minY;
        double areaRatio = (cropWidth * (double) cropHeight) / (width * (double) height);
        if (areaRatio < 0.28 || areaRatio > 0.94) return null;

        int padX = Math.max(8, cropWidth / 35);
        int padY = Math.max(8, cropHeight / 35);
        int left = Math.max(0, minX - padX);
        int top = Math.max(0, minY - padY);
        int right = Math.min(width, maxX + padX);
        int bottom = Math.min(height, maxY + padY);

        if (right - left < width * 0.42 || bottom - top < height * 0.42) return null;
        return new Rect(left, top, right, bottom);
    }

    private static Quality analyze(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int step = Math.max(2, Math.min(width, height) / 320);

        long count = 0;
        double sum = 0;
        double square = 0;
        double edge = 0;
        long edgeCount = 0;

        for (int y = 0; y < height - step; y += step) {
            for (int x = 0; x < width - step; x += step) {
                int value = luma(bitmap.getPixel(x, y));
                sum += value;
                square += value * (double) value;
                count++;

                int right = luma(bitmap.getPixel(x + step, y));
                int down = luma(bitmap.getPixel(x, y + step));
                edge += Math.abs(value - right) + Math.abs(value - down);
                edgeCount += 2;
            }
        }

        double mean = count == 0 ? 0 : sum / count;
        double variance = count == 0 ? 0 : Math.max(0, square / count - mean * mean);
        double contrast = Math.sqrt(variance);
        double sharpness = edgeCount == 0 ? 0 : edge / edgeCount;

        int minEdge = Math.min(width, height);
        int maxEdge = Math.max(width, height);
        int resolutionScore = clamp((int) Math.round(25.0 * Math.min(1.0,
                Math.min(minEdge / 850.0, maxEdge / 1200.0))), 0, 25);

        int brightnessScore;
        if (mean >= 65 && mean <= 215) brightnessScore = 20;
        else if (mean >= 42 && mean <= 232) brightnessScore = 12;
        else brightnessScore = 4;

        int contrastScore = clamp((int) Math.round(Math.min(25.0, contrast * 0.82)), 0, 25);
        int sharpnessScore = clamp((int) Math.round(Math.min(30.0, sharpness * 2.15)), 0, 30);
        int score = resolutionScore + brightnessScore + contrastScore + sharpnessScore;

        List<String> notes = new ArrayList<>();
        if (minEdge < 700 || maxEdge < 1000) notes.add("해상도가 낮습니다");
        if (mean < 48) notes.add("사진이 어둡습니다");
        else if (mean > 228) notes.add("사진이 너무 밝습니다");
        if (contrast < 20) notes.add("글자와 배경 대비가 낮습니다");
        if (sharpness < 7.5) notes.add("흔들림 가능성이 있습니다");

        String summary;
        if (notes.isEmpty()) summary = "OCR에 적합한 품질입니다";
        else summary = join(notes, " · ");
        return new Quality(score, summary);
    }

    private static double averageLuma(Bitmap bitmap, int left, int top, int right, int bottom) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        left = clamp(left, 0, width - 1);
        right = clamp(right, left + 1, width);
        top = clamp(top, 0, height - 1);
        bottom = clamp(bottom, top + 1, height);
        int step = Math.max(2, Math.min(right - left, bottom - top) / 28);
        long count = 0;
        double sum = 0;
        for (int y = top; y < bottom; y += step) {
            for (int x = left; x < right; x += step) {
                sum += luma(bitmap.getPixel(x, y));
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    private static int luma(int color) {
        return (int) Math.round(Color.red(color) * 0.299
                + Color.green(color) * 0.587
                + Color.blue(color) * 0.114);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String join(List<String> values, String separator) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(separator);
            result.append(value);
        }
        return result.toString();
    }

    private static final class Quality {
        final int score;
        final String summary;

        Quality(int score, String summary) {
            this.score = score;
            this.summary = summary;
        }
    }
}
