package com.seongho.allfilehub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 모든 확장자를 선택하고 Android에서 가능한 방식으로 처리하는 메인 화면입니다.
 * 텍스트와 이미지는 내부에서 표시하고, 전용 형식은 설치된 호환 앱으로 전달합니다.
 */
public class MainActivity extends Activity {

    private static final int REQUEST_OPEN_FILE = 1001;
    private static final int MAX_TEXT_BYTES = 2 * 1024 * 1024;
    private static final int MAX_HEX_BYTES = 512;
    private static final String PREFS = "all_file_hub";
    private static final String LAST_URI = "last_uri";

    private Uri currentUri;
    private TextView statusView;
    private LinearLayout previewArea;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildMainScreen();
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    /** 블루·화이트 기반의 단순한 메인 화면을 만듭니다. */
    private void buildMainScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(244, 248, 253));

        TextView title = new TextView(this);
        title.setText("모든파일");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(20, 56, 105));
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("모든 확장자 선택 · 내부 미리보기 · 호환 앱 실행");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle, matchWrap());

        Button openButton = actionButton("파일 선택", Color.rgb(31, 96, 196), Color.WHITE);
        openButton.setOnClickListener(v -> chooseFile());
        root.addView(openButton, matchWrapWithBottom(8));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button reopenButton = actionButton("최근 파일", Color.WHITE, Color.rgb(31, 96, 196));
        reopenButton.setOnClickListener(v -> reopenLastFile());
        actions.addView(reopenButton, weightedButton());

        Button externalButton = actionButton("호환 앱 실행", Color.WHITE, Color.rgb(31, 96, 196));
        externalButton.setOnClickListener(v -> {
            if (currentUri == null) {
                toast("먼저 파일을 선택하세요.");
            } else {
                openExternally(currentUri, resolveMime(currentUri));
            }
        });
        LinearLayout.LayoutParams externalParams = weightedButton();
        externalParams.setMargins(dp(6), 0, 0, 0);
        actions.addView(externalButton, externalParams);
        root.addView(actions, matchWrapWithBottom(10));

        statusView = new TextView(this);
        statusView.setText("파일을 선택하면 이름, 형식, 크기와 실행 방식을 표시합니다.");
        statusView.setTextSize(15);
        statusView.setTextColor(Color.rgb(35, 48, 65));
        statusView.setPadding(dp(14), dp(12), dp(14), dp(12));
        statusView.setBackground(rounded(Color.WHITE, Color.rgb(211, 222, 238), 14));
        root.addView(statusView, matchWrapWithBottom(10));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        previewArea = new LinearLayout(this);
        previewArea.setOrientation(LinearLayout.VERTICAL);
        previewArea.setPadding(0, dp(6), 0, dp(20));
        scrollView.addView(previewArea, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    /** Android 문서 선택기를 열어 확장자 제한 없이 파일을 선택합니다. */
    private void chooseFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_OPEN_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_FILE || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) return;

        int flags = data.getFlags() &
                (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (SecurityException ignored) {
            // 일부 문서 공급자는 장기 권한을 지원하지 않습니다.
        }
        processFile(uri);
    }

    /** 메신저나 파일 관리자에서 전달된 파일도 동일하게 처리합니다. */
    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (uri != null) processFile(uri);
    }

    /** 파일 정보를 분석하고 가장 적합한 실행 방식을 선택합니다. */
    private void processFile(Uri uri) {
        currentUri = uri;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(LAST_URI, uri.toString()).apply();

        String name = queryName(uri);
        long size = querySize(uri);
        String mime = resolveMime(uri);
        String extension = extensionOf(name);

        statusView.setText("파일명: " + name + "\n" +
                "확장자: " + (extension.isEmpty() ? "없음" : extension) + "\n" +
                "형식: " + mime + "\n" +
                "크기: " + formatBytes(size));
        previewArea.removeAllViews();

        if (mime.startsWith("image/")) {
            showImage(uri);
        } else if (isTextLike(mime, extension)) {
            showText(uri);
        } else if ("application/vnd.android.package-archive".equals(mime) || "apk".equals(extension)) {
            addInfoCard("APK 파일", "설치 버튼을 누르면 Android 설치 확인 화면으로 이동합니다.");
            addPreviewButton("APK 설치 화면 열기", () -> openApk(uri));
        } else {
            addInfoCard("호환 앱 실행 형식",
                    "이 형식은 Android 또는 설치된 전용 앱이 처리합니다. " +
                            "연결할 앱이 없으면 파일 정보와 앞부분 데이터를 확인할 수 있습니다.");
            addPreviewButton("호환 앱으로 열기", () -> openExternally(uri, mime));
            showHexPreview(uri);
        }
    }

    /** 저장된 최근 파일 접근 권한이 남아 있으면 다시 엽니다. */
    private void reopenLastFile() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String value = prefs.getString(LAST_URI, "");
        if (TextUtils.isEmpty(value)) {
            toast("저장된 최근 파일이 없습니다.");
            return;
        }
        try {
            processFile(Uri.parse(value));
        } catch (Exception e) {
            toast("최근 파일에 다시 접근할 수 없습니다.");
        }
    }

    /** 텍스트·소스·설정 파일을 최대 2MB까지 내부 표시합니다. */
    private void showText(Uri uri) {
        TextView textView = new TextView(this);
        textView.setTextSize(15);
        textView.setTextColor(Color.rgb(30, 38, 48));
        textView.setTextIsSelectable(true);
        textView.setPadding(dp(14), dp(14), dp(14), dp(14));
        textView.setBackground(rounded(Color.WHITE, Color.rgb(211, 222, 238), 12));
        textView.setText("읽는 중...");
        previewArea.addView(textView, matchWrap());

        new Thread(() -> {
            try {
                byte[] bytes = readLimited(uri, MAX_TEXT_BYTES);
                String text = new String(bytes, StandardCharsets.UTF_8);
                if (text.indexOf('\uFFFD') >= 0) {
                    text = new String(bytes, Charset.forName("EUC-KR"));
                }
                final String output = text;
                runOnUiThread(() -> textView.setText(output));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    textView.setText("텍스트를 내부에서 읽지 못했습니다.");
                    addPreviewButton("호환 앱으로 열기", () -> openExternally(uri, resolveMime(uri)));
                });
            }
        }).start();
    }

    /** 이미지 크기를 제한해 메모리 부족 가능성을 줄이고 내부 표시합니다. */
    private void showImage(Uri uri) {
        ImageView imageView = new ImageView(this);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setBackgroundColor(Color.WHITE);
        previewArea.addView(imageView, matchWrap());

        new Thread(() -> {
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(input, null, bounds);
                }
                int sample = 1;
                while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) {
                    sample *= 2;
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = Math.max(1, sample);
                final Bitmap bitmap;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    bitmap = BitmapFactory.decodeStream(input, null, options);
                }
                runOnUiThread(() -> {
                    if (bitmap == null) {
                        addInfoCard("이미지 오류", "이미지를 내부에서 해석하지 못했습니다.");
                    } else {
                        imageView.setImageBitmap(bitmap);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> addInfoCard("이미지 오류", "이미지 파일이 손상되었거나 지원되지 않습니다."));
            }
        }).start();
    }

    /** 알 수 없는 파일의 앞부분을 16진수로 표시합니다. */
    private void showHexPreview(Uri uri) {
        new Thread(() -> {
            try {
                byte[] data = readLimited(uri, MAX_HEX_BYTES);
                StringBuilder builder = new StringBuilder("파일 앞부분 16진수\n\n");
                for (int i = 0; i < data.length; i++) {
                    if (i > 0 && i % 16 == 0) builder.append('\n');
                    builder.append(String.format(Locale.US, "%02X ", data[i] & 0xFF));
                }
                runOnUiThread(() -> addInfoCard("원시 데이터", builder.toString()));
            } catch (Exception ignored) {
                // 읽을 수 없는 파일은 외부 앱 연결만 제공합니다.
            }
        }).start();
    }

    /** Android가 등록한 앱 선택 화면으로 파일을 전달합니다. */
    private void openExternally(Uri uri, String mime) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, TextUtils.isEmpty(mime) ? "*/*" : mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(Intent.createChooser(intent, "파일을 열 앱 선택"));
        } catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(this)
                    .setTitle("호환 앱 없음")
                    .setMessage("이 파일 형식을 처리할 앱이 설치되어 있지 않습니다.\n\n" +
                            "HWP는 한컴오피스, Office 문서는 Microsoft 365, " +
                            "RAR·7Z는 압축 앱, EXE는 호환 실행환경이 필요합니다.")
                    .setPositiveButton("확인", null)
                    .show();
        }
    }

    /** APK는 자동 설치하지 않고 반드시 Android 설치 확인 화면을 엽니다. */
    private void openApk(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            toast("APK 설치 화면을 열 수 없습니다.");
        }
    }

    private String resolveMime(Uri uri) {
        String mime = getContentResolver().getType(uri);
        if (!TextUtils.isEmpty(mime) && !"application/octet-stream".equals(mime)) return mime;
        String extension = extensionOf(queryName(uri));
        String mapped = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return TextUtils.isEmpty(mapped) ? "application/octet-stream" : mapped;
    }

    private boolean isTextLike(String mime, String extension) {
        if (mime.startsWith("text/")) return true;
        String list = "txt,csv,json,xml,html,htm,md,log,ini,conf,properties," +
                "java,kt,kts,gradle,py,js,ts,css,c,cpp,h,hpp,sql,yaml,yml,sh,bat,ps1";
        return ("," + list + ",").contains("," + extension + ",");
    }

    private String queryName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        String path = uri.getLastPathSegment();
        return TextUtils.isEmpty(path) ? "알 수 없는 파일" : path;
    }

    private long querySize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index);
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private byte[] readLimited(Uri uri, int limit) throws IOException {
        ContentResolver resolver = getContentResolver();
        try (InputStream input = resolver.openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("입력 스트림을 열 수 없습니다.");
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while (total < limit &&
                    (count = input.read(buffer, 0, Math.min(buffer.length, limit - total))) > 0) {
                output.write(buffer, 0, count);
                total += count;
            }
            return output.toByteArray();
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "확인 불가";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.KOREA, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.KOREA, "%.1f MB", mb);
        return String.format(Locale.KOREA, "%.2f GB", mb / 1024.0);
    }

    private void addInfoCard(String title, String body) {
        TextView card = new TextView(this);
        card.setText(title + "\n\n" + body);
        card.setTextSize(14);
        card.setTextColor(Color.rgb(32, 43, 57));
        card.setTextIsSelectable(true);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(Color.WHITE, Color.rgb(211, 222, 238), 12));
        previewArea.addView(card, matchWrapWithBottom(10));
    }

    private void addPreviewButton(String label, Runnable action) {
        Button button = actionButton(label, Color.rgb(31, 96, 196), Color.WHITE);
        button.setOnClickListener(v -> action.run());
        previewArea.addView(button, matchWrapWithBottom(10));
    }

    private Button actionButton(String text, int background, int foreground) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(foreground);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(background, Color.rgb(31, 96, 196), 12));
        return button;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapWithBottom(int bottomDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(bottomDp));
        return params;
    }

    private LinearLayout.LayoutParams weightedButton() {
        return new LinearLayout.LayoutParams(0, dp(52), 1f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
