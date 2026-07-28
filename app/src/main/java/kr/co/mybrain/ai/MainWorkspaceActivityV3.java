package kr.co.mybrain.ai;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

/** 최신 메인 화면에 실기기 진단 바로가기를 추가합니다. */
public class MainWorkspaceActivityV3 extends MainWorkspaceActivityV2 {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ViewGroup content = findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) return;
        Button button = new Button(this);
        button.setText("진단"); button.setTextSize(12); button.setTextColor(Color.rgb(34,96,214));
        button.setAllCaps(false); button.setContentDescription("기기 진단 및 테스트 열기");
        button.setOnClickListener(v -> startActivity(new Intent(this, DiagnosticsActivity.class)));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(64), dp(42));
        params.gravity = Gravity.TOP | Gravity.END; params.setMargins(0, dp(70), dp(10), 0);
        ((FrameLayout) content).addView(button, params);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
