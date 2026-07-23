package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 일정·할 일·메모 데이터를 파일로 백업하거나 복원하는 화면입니다.
 * Android의 문서 선택기를 사용하므로 별도 저장소 권한이 필요하지 않습니다.
 */
public class BackupActivity extends Activity {
    private static final String PREFS = "mybrain_data";
    private static final String KEY_ITEMS = "items";
    private static final int REQUEST_EXPORT = 501;
    private static final int REQUEST_IMPORT = 502;
    private static final String HEADER = "MYBRAIN_BACKUP_V1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
    }

    /** 초보자도 쉽게 사용할 수 있도록 백업 전용 화면을 구성합니다. */
    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(244, 247, 251));

        TextView title = text("MyBrain AI 백업·복원", 26, Color.rgb(18, 48, 89));
        root.addView(title, fullWrap());

        TextView guide = text(
                "앱을 삭제하기 전에 백업 파일을 저장하세요.\n복원하면 일정·할 일·메모·알림·반복·색상 정보가 함께 돌아옵니다.",
                15, Color.DKGRAY);
        guide.setPadding(0, dp(10), 0, dp(20));
        root.addView(guide, fullWrap());

        Button exportButton = button("1. 백업 파일 저장");
        exportButton.setOnClickListener(v -> exportBackup());
        root.addView(exportButton, buttonParams());

        Button importButton = button("2. 백업 파일 불러오기");
        importButton.setOnClickListener(v -> confirmImport());
        root.addView(importButton, buttonParams());

        TextView warning = text(
                "주의: 복원은 현재 자료를 백업 파일의 자료로 교체합니다. 복원 전에 현재 자료도 먼저 백업하세요.",
                14, Color.rgb(180, 55, 55));
        warning.setPadding(0, dp(18), 0, dp(18));
        root.addView(warning, fullWrap());

        Button openApp = button("메인 화면으로 돌아가기");
        openApp.setOnClickListener(v -> openIntegratedMainScreen());
        root.addView(openApp, buttonParams());

        setContentView(root);
    }

    /** 사용자가 원하는 폴더에 백업 파일을 생성합니다. */
    private void exportBackup() {
        String date = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.KOREA).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "MyBrain_backup_" + date + ".mybrain");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    /** 기존 자료가 교체된다는 사실을 확인한 뒤 파일 선택기를 엽니다. */
    private void confirmImport() {
        new AlertDialog.Builder(this)
                .setTitle("백업 자료 복원")
                .setMessage("현재 앱의 자료를 선택한 백업 파일로 교체할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("파일 선택", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    startActivityForResult(intent, REQUEST_IMPORT);
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        if (requestCode == REQUEST_EXPORT) {
            writeBackup(data);
        } else if (requestCode == REQUEST_IMPORT) {
            readBackup(data);
        }
    }

    /** 내부 저장 문자열을 헤더와 함께 파일에 기록합니다. */
    private void writeBackup(Intent data) {
        try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
            if (output == null) throw new IllegalStateException("파일을 열 수 없습니다.");
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String items = prefs.getString(KEY_ITEMS, "");
            String body = HEADER + "\n" + (items == null ? "" : items);
            output.write(body.getBytes(StandardCharsets.UTF_8));
            output.flush();
            Toast.makeText(this, "백업 파일을 저장했습니다.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "백업 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 파일의 형식을 검사한 뒤 내부 저장 자료를 교체합니다. */
    private void readBackup(Intent data) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getContentResolver().openInputStream(data.getData()), StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();
            if (!HEADER.equals(firstLine)) {
                Toast.makeText(this, "MyBrain 백업 파일이 아닙니다.", Toast.LENGTH_LONG).show();
                return;
            }

            StringBuilder items = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (items.length() > 0) items.append("\n");
                items.append(line);
            }

            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_ITEMS, items.toString())
                    .apply();

            AlarmScheduler.rescheduleAll(getApplicationContext());
            TodayWidgetProvider.updateAll(getApplicationContext());
            Toast.makeText(this, "자료를 복원했습니다.", Toast.LENGTH_LONG).show();
            openIntegratedMainScreen();
        } catch (Exception e) {
            Toast.makeText(this, "복원 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 복원된 자료를 즉시 다시 읽도록 통합 메인 화면을 새로 엽니다. */
    private void openIntegratedMainScreen() {
        Intent intent = new Intent(this, IntegratedMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16);
        button.setAllCaps(false);
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.START);
        return view;
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        params.setMargins(0, 0, 0, dp(12));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
