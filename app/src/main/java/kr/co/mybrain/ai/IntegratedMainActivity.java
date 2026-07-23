package kr.co.mybrain.ai;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 기존 메인 기능을 그대로 유지하면서 백업·복원 진입 버튼을 추가하는 통합 화면입니다.
 * MainActivity의 일정·할 일·메모 코드를 복사하지 않고 상속하므로 중복 코드가 생기지 않습니다.
 */
public class IntegratedMainActivity extends MainActivity {
    private static final int COLOR_PRIMARY = Color.rgb(35, 92, 190);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addBackupButtonToMainScreen();
    }

    /**
     * MainActivity가 만든 최상위 화면에 백업·복원 버튼을 안전하게 삽입합니다.
     * 화면 구조를 찾지 못하면 앱을 종료하지 않고 기존 메인 화면만 표시합니다.
     */
    private void addBackupButtonToMainScreen() {
        View contentView = findViewById(android.R.id.content);
        if (!(contentView instanceof ViewGroup)) return;

        ViewGroup contentRoot = (ViewGroup) contentView;
        if (contentRoot.getChildCount() == 0) return;

        View mainRootView = contentRoot.getChildAt(0);
        if (!(mainRootView instanceof LinearLayout)) return;

        LinearLayout mainRoot = (LinearLayout) mainRootView;

        // 기존 버전 안내 문구를 이번 통합 버전에 맞게 변경합니다.
        if (mainRoot.getChildCount() > 1 && mainRoot.getChildAt(1) instanceof TextView) {
            TextView versionText = (TextView) mainRoot.getChildAt(1);
            versionText.setText("v1.5.3 · 백업·복원 통합");
        }

        Button backupButton = new Button(this);
        backupButton.setText("백업·복원");
        backupButton.setTextSize(15);
        backupButton.setTextColor(COLOR_PRIMARY);
        backupButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        backupButton.setAllCaps(false);
        backupButton.setBackground(createButtonBackground());
        backupButton.setOnClickListener(v ->
                startActivity(new Intent(this, BackupActivity.class)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        params.setMargins(0, 0, 0, dp(8));

        // 제목·요약·메시지 추가 버튼 다음, 검색창 바로 앞에 배치합니다.
        int insertIndex = Math.min(4, mainRoot.getChildCount());
        mainRoot.addView(backupButton, insertIndex, params);
    }

    /** 백업 버튼의 흰색 배경과 파란 테두리를 만듭니다. */
    private GradientDrawable createButtonBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), COLOR_PRIMARY);
        return background;
    }

    /** 화면 밀도에 맞춰 dp 값을 실제 픽셀로 변환합니다. */
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
