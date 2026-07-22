package kr.co.mybrain.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

/**
 * 앱 시작 시 알림 권한을 확인하는 전용 화면입니다.
 * 권한 확인이 끝나면 즉시 메인 화면으로 이동합니다.
 */
public class NotificationPermissionActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 1201;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS
            );
        } else {
            openMainScreen();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            // 사용자가 허용하지 않아도 앱 자체는 정상적으로 사용할 수 있습니다.
            openMainScreen();
        }
    }

    /** 실제 MyBrain AI 메인 화면을 엽니다. */
    private void openMainScreen() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
