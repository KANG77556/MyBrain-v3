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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.data.WorkItemRepository;

/** 정식 서명 상태와 백업 복원 예상 결과를 실제 데이터 변경 없이 확인합니다. */
public class ReleaseRecoveryDiagnosticsActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(246, 248, 252);
    private static final int TEXT = Color.rgb(24, 34, 48);
    private static final int SUBTEXT = Color.rgb(91, 106, 128);
    private static final int PRIMARY = Color.rgb(45, 91, 255);
    private static final int SUCCESS = Color.rgb(29, 128, 75);
    private static final int WARNING = Color.rgb(185, 108, 0);
    private static final int DANGER = Color.rgb(218, 53, 69);
    private static final int BORDER = Color.rgb(218, 224, 234);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private WorkItemRepository repository;
    private TextView releaseState;
    private TextView rehearsalState;

    private final ActivityResultLauncher<String[]> openBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) askPassword(uri);
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = WorkItemRepository.getInstance(this);
        setContentView(buildScreen());
        refreshReleaseStatus();
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            root.setPadding(dp(18), bars.top + dp(12), dp(18), bars.bottom + dp(32));
            return insets;
        });

        Button back = secondaryButton("←  배포·복구 진단");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView title = text("정식 배포와 복구 준비", 28, TEXT, true);
        title.setPadding(0, dp(18), 0, dp(4));
        root.addView(title);
        TextView subtitle = text("설치된 앱의 서명 상태와 백업 복원 결과를 데이터 변경 없이 검사합니다.", 15, SUBTEXT, false);
        subtitle.setPadding(0, 0, 0, dp(8));
        root.addView(subtitle);

        LinearLayout releaseCard = card();
        root.addView(releaseCard, cardParams());
        releaseCard.addView(sectionTitle("1. Release 업데이트 준비 상태"));
        releaseState = text("앱 서명을 확인하고 있습니다…", 14, SUBTEXT, false);
        releaseState.setLineSpacing(dp(3), 1f);
        releaseCard.addView(releaseState);
        Button refresh = secondaryButton("↻  서명 상태 다시 확인");
        refresh.setOnClickListener(v -> refreshReleaseStatus());
        releaseCard.addView(refresh, actionParams());

        LinearLayout rehearsalCard = card();
        root.addView(rehearsalCard, cardParams());
        rehearsalCard.addView(sectionTitle("2. 백업 복원 리허설"));
        rehearsalCard.addView(text("암호화 백업을 복호화하고 현재 DB와 비교하지만 일정·메모·설정은 변경하지 않습니다.", 14, SUBTEXT, false));
        Button rehearse = primaryButton("🛡  백업 검사·복원 리허설");
        rehearse.setOnClickListener(v -> openBackupLauncher.launch(
                new String[]{"application/octet-stream", "application/json", "*/*"}));
        rehearsalCard.addView(rehearse, actionParams());
        rehearsalState = text("아직 검사한 백업이 없습니다.", 14, SUBTEXT, false);
        rehearsalState.setLineSpacing(dp(3), 1f);
        rehearsalState.setPadding(0, dp(12), 0, 0);
        rehearsalCard.addView(rehearsalState);

        LinearLayout actionCard = card();
        root.addView(actionCard, cardParams());
        actionCard.addView(sectionTitle("3. 실제 백업·복원·업데이트"));
        actionCard.addView(text("리허설 결과를 확인한 뒤 기존 관리 화면에서 백업 생성, 병합·교체 복원, 검증된 APK 설치를 실행합니다.", 14, SUBTEXT, false));
        Button open = secondaryButton("백업·복원·업데이트 화면 열기");
        open.setOnClickListener(v -> startActivity(new Intent(this, BackupRestoreActivity.class)));
        actionCard.addView(open, actionParams());
        return scroll;
    }

    private void refreshReleaseStatus() {
        ReleaseReadiness.Report report = ReleaseReadiness.inspect(this);
        String cert = report.certificateSha256.isEmpty() ? "확인 불가"
                : report.certificateSha256.substring(0, Math.min(20, report.certificateSha256.length())) + "…";
        StringBuilder value = new StringBuilder()
                .append(report.state)
                .append("\n버전 ").append(report.versionName).append(" · 코드 ").append(report.versionCode)
                .append("\n패키지 ").append(report.packageName)
                .append("\n현재 서명 ").append(cert)
                .append("\n예상 고정 서명 ")
                .append(ReleaseReadiness.EXPECTED_CERT_SHA256.substring(0, 20)).append("…");
        for (String row : ReleaseReadiness.checklist(report)) value.append("\n").append(row);
        if (report.debuggable) {
            value.append("\n\n현재 APK는 개발용입니다. 고정 JKS를 GitHub Secrets에 등록한 뒤 생성된 Release APK부터 동일 서명 업데이트가 가능합니다.");
        }
        releaseState.setText(value.toString());
        releaseState.setTextColor(report.updateReady ? SUCCESS : report.debuggable ? WARNING : DANGER);
    }

    private void askPassword(Uri uri) {
        EditText input = new EditText(this);
        input.setHint("백업 비밀번호 6자 이상");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, dp(54)));
        new AlertDialog.Builder(this)
                .setTitle("백업 복원 리허설")
                .setMessage("비밀번호는 검사 후 즉시 메모리에서 지웁니다. 실제 데이터는 변경하지 않습니다.")
                .setView(box)
                .setNegativeButton("취소", null)
                .setPositiveButton("검사 시작", (dialog, which) -> {
                    char[] password = input.getText().toString().toCharArray();
                    if (password.length < 6) {
                        Arrays.fill(password, '\0');
                        Toast.makeText(this, "백업 비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    runRehearsal(uri, password);
                }).show();
    }

    private void runRehearsal(Uri uri, char[] password) {
        rehearsalState.setTextColor(SUBTEXT);
        rehearsalState.setText("백업 암호화 무결성과 데이터 형식을 검사하고 있습니다…");
        executor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                String json = BackupCrypto.decryptFromStream(input, password);
                BackupDataManager.BackupPayload payload = BackupDataManager.parsePayload(json);
                repository.getAllForBackup(current -> showPlan(payload, current));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    rehearsalState.setTextColor(DANGER);
                    rehearsalState.setText("리허설 실패\n" + safeMessage(error));
                });
            } finally {
                Arrays.fill(password, '\0');
            }
        });
    }

    private void showPlan(BackupDataManager.BackupPayload payload, List<WorkItemEntity> current) {
        BackupRestorePlanner.Plan plan = BackupRestorePlanner.compare(current, payload.items);
        runOnUiThread(() -> {
            String header = plan.safeToRestore ? "✓ 복원 리허설 통과" : "✗ 복원 전 확인 필요";
            String result = header
                    + "\n백업 앱 " + payload.sourceAppVersion
                    + (payload.timezone.isEmpty() ? "" : "\n백업 시간대 " + payload.timezone)
                    + "\n\n" + plan.summary()
                    + "\n\n병합 복원: 현재 기기에만 존재하는 " + plan.currentOnlyCount + "개 항목을 유지합니다."
                    + "\n전체 교체: 현재 기기 전용 항목은 삭제됩니다."
                    + "\nAPI 키는 두 방식 모두 복원하지 않습니다.";
            rehearsalState.setText(result);
            rehearsalState.setTextColor(plan.safeToRestore ? SUCCESS : DANGER);
        });
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
        Button button = secondaryButton(value);
        button.setTextColor(Color.WHITE);
        button.setTypeface(null, Typeface.BOLD);
        button.setBackground(rounded(PRIMARY, 14, 0));
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(15);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        button.setBackground(rounded(Color.WHITE, 14, BORDER));
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
        executor.shutdownNow();
        super.onDestroy();
    }
}
