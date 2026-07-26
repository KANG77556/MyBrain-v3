package kr.co.mybrain.v2.transfer;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kr.co.mybrain.v2.data.WorkItemRepository;

/** 암호화 백업·복원과 서명 검증 업데이트 설치를 한 화면에서 관리합니다. */
public class BackupRestoreActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(246, 248, 252);
    private static final int TEXT = Color.rgb(24, 34, 48);
    private static final int SUBTEXT = Color.rgb(91, 106, 128);
    private static final int PRIMARY = Color.rgb(45, 91, 255);
    private static final int BORDER = Color.rgb(218, 224, 234);
    private static final int DANGER = Color.rgb(218, 53, 69);

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private WorkItemRepository repository;
    private TextView statusText;
    private char[] pendingExportPassword;
    private AppUpdateInstaller.UpdateInfo pendingUpdate;

    private final ActivityResultLauncher<String> createBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
                if (uri == null) {
                    clearPendingPassword();
                    setStatus("백업 저장이 취소되었습니다.", false);
                    return;
                }
                exportBackup(uri);
            });

    private final ActivityResultLauncher<String[]> openBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) promptRestorePassword(uri);
            });

    private final ActivityResultLauncher<String[]> openApkLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) inspectUpdate(uri);
            });

    private final ActivityResultLauncher<Intent> unknownSourceLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (pendingUpdate == null) return;
                if (AppUpdateInstaller.canInstallPackages(this)) {
                    launchPreparedUpdate();
                } else {
                    setStatus("이 앱의 APK 설치 권한이 허용되지 않았습니다.", true);
                }
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = WorkItemRepository.getInstance(this);
        setContentView(buildScreen());
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            root.setPadding(dp(18), bars.top + dp(12), dp(18), bars.bottom + dp(30));
            return insets;
        });

        Button back = secondaryButton("←  백업·복원·업데이트");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView title = text("데이터와 앱 보호", 28, TEXT, true);
        title.setPadding(0, dp(18), 0, dp(4));
        root.addView(title);
        TextView subtitle = text("암호화 백업을 만들고 검증된 새 버전을 설치합니다.", 15, SUBTEXT, false);
        subtitle.setPadding(0, 0, 0, dp(12));
        root.addView(subtitle);

        LinearLayout backupCard = card();
        root.addView(backupCard, cardParams());
        backupCard.addView(sectionTitle("1. 암호화 백업 만들기"));
        backupCard.addView(text("일정·할 일·메모와 비밀값이 아닌 설정을 하나의 .mybrain 파일로 저장합니다. API 키는 포함하지 않습니다.", 14, SUBTEXT, false));
        Button export = primaryButton("💾  새 백업 파일 만들기");
        export.setOnClickListener(v -> promptExportPassword());
        backupCard.addView(export, actionParams());

        LinearLayout restoreCard = card();
        root.addView(restoreCard, cardParams());
        restoreCard.addView(sectionTitle("2. 백업 복원"));
        restoreCard.addView(text("백업 비밀번호를 입력한 뒤 기존 데이터와 병합하거나 현재 데이터를 백업 상태로 교체할 수 있습니다.", 14, SUBTEXT, false));
        Button restore = secondaryButton("↺  .mybrain 백업 파일 선택");
        restore.setOnClickListener(v -> openBackupLauncher.launch(new String[]{"application/octet-stream", "application/json", "*/*"}));
        restoreCard.addView(restore, actionParams());

        LinearLayout updateCard = card();
        root.addView(updateCard, cardParams());
        updateCard.addView(sectionTitle("3. APK 업데이트 설치"));
        updateCard.addView(text("선택한 APK의 패키지명, 새 버전 여부, 서명 인증서를 검사합니다. 현재 설치 앱과 서명이 다르면 차단합니다.", 14, SUBTEXT, false));
        Button update = secondaryButton("⬆  새 버전 APK 선택");
        update.setOnClickListener(v -> openApkLauncher.launch(new String[]{"application/vnd.android.package-archive", "application/octet-stream"}));
        updateCard.addView(update, actionParams());

        statusText = text("준비됨 · 백업 파일과 API 키는 서로 분리해 보관하세요.", 13, SUBTEXT, false);
        statusText.setGravity(Gravity.CENTER);
        statusText.setLineSpacing(dp(2), 1f);
        statusText.setPadding(dp(8), dp(16), dp(8), 0);
        root.addView(statusText);
        return scroll;
    }

    private void promptExportPassword() {
        LinearLayout fields = passwordFields(true);
        EditText password = (EditText) fields.getChildAt(0);
        EditText confirm = (EditText) fields.getChildAt(1);
        new AlertDialog.Builder(this)
                .setTitle("백업 비밀번호 설정")
                .setMessage("6자 이상으로 입력하세요. 비밀번호를 잊으면 백업을 복원할 수 없습니다.")
                .setView(fields)
                .setNegativeButton("취소", null)
                .setPositiveButton("파일 위치 선택", (dialog, which) -> {
                    String first = password.getText().toString();
                    String second = confirm.getText().toString();
                    if (first.length() < 6 || !first.equals(second)) {
                        Toast.makeText(this, "비밀번호는 6자 이상이며 두 입력이 같아야 합니다.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    clearPendingPassword();
                    pendingExportPassword = first.toCharArray();
                    String date = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.KOREA).format(new Date());
                    createBackupLauncher.launch("MyBrain-backup-" + date + ".mybrain");
                }).show();
    }

    private void exportBackup(Uri uri) {
        if (pendingExportPassword == null) return;
        setStatus("백업 데이터를 암호화하고 있습니다…", false);
        repository.getAllForBackup(items -> {
            char[] password = pendingExportPassword;
            pendingExportPassword = null;
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new IllegalStateException("선택한 위치에 파일을 만들 수 없습니다.");
                String payload = BackupDataManager.createPayload(this, items);
                BackupCrypto.encryptToStream(payload, password, output);
                runOnUiThread(() -> setStatus("암호화 백업 완료 · " + items.size() + "개 항목을 저장했습니다.", false));
            } catch (Exception error) {
                runOnUiThread(() -> setStatus("백업 실패 · " + safeMessage(error), true));
            } finally {
                if (password != null) Arrays.fill(password, '\0');
            }
        });
    }

    private void promptRestorePassword(Uri uri) {
        LinearLayout fields = passwordFields(false);
        EditText password = (EditText) fields.getChildAt(0);
        new AlertDialog.Builder(this)
                .setTitle("백업 비밀번호 입력")
                .setView(fields)
                .setNegativeButton("취소", null)
                .setPositiveButton("백업 확인", (dialog, which) -> {
                    char[] value = password.getText().toString().toCharArray();
                    if (value.length < 6) {
                        Arrays.fill(value, '\0');
                        Toast.makeText(this, "백업 비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    decryptBackup(uri, value);
                }).show();
    }

    private void decryptBackup(Uri uri, char[] password) {
        setStatus("백업 파일을 복호화하고 검사하고 있습니다…", false);
        ioExecutor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                String json = BackupCrypto.decryptFromStream(input, password);
                BackupDataManager.BackupPayload payload = BackupDataManager.parsePayload(json);
                runOnUiThread(() -> chooseRestoreMode(payload));
            } catch (Exception error) {
                runOnUiThread(() -> setStatus("복원 준비 실패 · " + safeMessage(error), true));
            } finally {
                Arrays.fill(password, '\0');
            }
        });
    }

    private void chooseRestoreMode(BackupDataManager.BackupPayload payload) {
        String detail = "백업 앱 버전: " + payload.sourceAppVersion
                + "\n항목 수: " + payload.items.size() + "개"
                + (payload.timezone.isEmpty() ? "" : "\n백업 시간대: " + payload.timezone)
                + "\n\n병합은 같은 외부 ID 항목을 갱신하고 새 항목만 추가합니다. 전체 교체는 현재 데이터를 삭제한 뒤 복원합니다.";
        new AlertDialog.Builder(this)
                .setTitle("복원 방법 선택")
                .setMessage(detail)
                .setNegativeButton("취소", null)
                .setNeutralButton("병합 복원", (dialog, which) -> restorePayload(payload, false))
                .setPositiveButton("전체 교체", (dialog, which) -> confirmReplace(payload))
                .show();
    }

    private void confirmReplace(BackupDataManager.BackupPayload payload) {
        new AlertDialog.Builder(this)
                .setTitle("현재 데이터를 교체할까요?")
                .setMessage("현재 일정·할 일·메모와 휴지통 항목을 삭제하고 백업 상태로 교체합니다. 실행 전에 최신 백업을 만들어 두는 것이 안전합니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("교체 복원", (dialog, which) -> restorePayload(payload, true))
                .show();
    }

    private void restorePayload(BackupDataManager.BackupPayload payload, boolean replaceAll) {
        setStatus(replaceAll ? "현재 데이터를 백업 상태로 교체하고 있습니다…" : "백업 데이터를 병합하고 있습니다…", false);
        repository.restoreBackup(payload.items, replaceAll, result -> runOnUiThread(() -> {
            if (!result.success) {
                setStatus("복원 실패 · " + safeMessage(result.error), true);
                return;
            }
            BackupDataManager.restoreNonSecretSettings(this, payload.settings);
            setStatus("복원 완료 · 추가 " + result.inserted + "개 · 갱신 " + result.updated
                    + "개 · API 키는 복원되지 않았습니다.", false);
            Toast.makeText(this, "백업 복원이 완료됐습니다.", Toast.LENGTH_SHORT).show();
        }));
    }

    private void inspectUpdate(Uri uri) {
        setStatus("APK 패키지·버전·서명을 검사하고 있습니다…", false);
        ioExecutor.execute(() -> {
            try {
                AppUpdateInstaller.UpdateInfo update = AppUpdateInstaller.prepare(this, uri);
                runOnUiThread(() -> confirmUpdate(update));
            } catch (Exception error) {
                runOnUiThread(() -> setStatus("업데이트 검사 실패 · " + safeMessage(error), true));
            }
        });
    }

    private void confirmUpdate(AppUpdateInstaller.UpdateInfo update) {
        pendingUpdate = update;
        String cert = update.certificateSha256.length() > 16
                ? update.certificateSha256.substring(0, 16) + "…" : update.certificateSha256;
        new AlertDialog.Builder(this)
                .setTitle("검증된 업데이트")
                .setMessage("버전: " + update.versionName + "\n버전 코드: " + update.versionCode
                        + "\n서명 SHA-256: " + cert
                        + "\n\n현재 앱과 동일한 서명임을 확인했습니다. Android 설치 화면을 열까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("설치 화면 열기", (dialog, which) -> requestOrLaunchInstall())
                .show();
    }

    private void requestOrLaunchInstall() {
        if (pendingUpdate == null) return;
        if (!AppUpdateInstaller.canInstallPackages(this)) {
            setStatus("Android 설정에서 이 앱의 APK 설치 권한을 허용한 뒤 돌아오세요.", false);
            unknownSourceLauncher.launch(AppUpdateInstaller.unknownSourceSettings(this));
            return;
        }
        launchPreparedUpdate();
    }

    private void launchPreparedUpdate() {
        if (pendingUpdate == null) return;
        try {
            AppUpdateInstaller.launchInstall(this, pendingUpdate);
            setStatus("Android 설치 화면을 열었습니다. 표시된 버전을 확인하고 업데이트하세요.", false);
        } catch (Exception error) {
            setStatus("설치 화면을 열지 못했습니다 · " + safeMessage(error), true);
        }
    }

    private LinearLayout passwordFields(boolean confirmation) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int side = dp(20);
        fields.setPadding(side, dp(8), side, 0);
        EditText password = passwordInput("비밀번호 6자 이상");
        fields.addView(password, new LinearLayout.LayoutParams(-1, dp(54)));
        if (confirmation) {
            EditText confirm = passwordInput("비밀번호 다시 입력");
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
            params.setMargins(0, dp(8), 0, 0);
            fields.addView(confirm, params);
        }
        return fields;
    }

    private EditText passwordInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTextColor(TEXT);
        input.setHintTextColor(Color.rgb(125, 136, 153));
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(rounded(Color.WHITE, 12, BORDER));
        return input;
    }

    private void setStatus(String message, boolean danger) {
        if (statusText == null) return;
        statusText.setText(message);
        statusText.setTextColor(danger ? DANGER : SUBTEXT);
    }

    private void clearPendingPassword() {
        if (pendingExportPassword != null) Arrays.fill(pendingExportPassword, '\0');
        pendingExportPassword = null;
    }

    private String safeMessage(Throwable error) {
        String value = error == null ? "알 수 없는 오류" : error.getMessage();
        if (value == null || value.trim().isEmpty()) value = error == null ? "알 수 없는 오류" : error.getClass().getSimpleName();
        String text = value.replaceAll("\\s+", " ").trim();
        return text.length() <= 180 ? text : text.substring(0, 180) + "…";
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(14), dp(14), dp(14), dp(14));
        view.setBackground(rounded(Color.WHITE, 18, BORDER));
        return view;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(12), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.setMargins(0, dp(12), 0, 0);
        return params;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 17, TEXT, true);
        view.setPadding(0, 0, 0, dp(10));
        return view;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setTypeface(null, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(PRIMARY, 14, 0));
        button.setStateListAnimator(null);
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(15);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(Color.WHITE, 14, BORDER));
        button.setStateListAnimator(null);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private GradientDrawable rounded(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        clearPendingPassword();
        ioExecutor.shutdownNow();
        super.onDestroy();
    }
}