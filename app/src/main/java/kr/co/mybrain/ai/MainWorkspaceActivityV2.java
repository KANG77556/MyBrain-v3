package kr.co.mybrain.ai;

import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;

/**
 * Galaxy 스마트폰과 태블릿에서 하단 시스템 내비게이션 영역을 피하는 최신 메인 화면입니다.
 */
public class MainWorkspaceActivityV2 extends MainWorkspaceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installSystemInsets();
    }

    private void installSystemInsets() {
        View root = findViewById(android.R.id.content);
        if (root == null) return;
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int bottom = 0;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            } else {
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                    view.getPaddingRight(), bottom);
            return insets;
        });
        root.requestApplyInsets();
    }
}
