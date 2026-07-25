package kr.co.mybrain.ai;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * MyBrain AI 1.9.1 문서 촬영 화면입니다.
 * 기존 회전·품질 검사·OCR 처리는 그대로 사용하고,
 * 재촬영과 OCR 버튼을 화면 아래에 고정해 한 손 조작성을 높입니다.
 */
public class DocumentCaptureActivityV2 extends DocumentCaptureActivity {

    private static final String TAG_FIXED_DOCUMENT_ACTION = "mybrain_document_fixed_action";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyResponsiveLayout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        View content = findViewById(android.R.id.content);
        if (content != null) content.post(this::applyResponsiveLayout);
    }

    private void applyResponsiveLayout() {
        LinearLayout root = findRootLayout();
        if (root == null) return;
        adjustPreviewHeight(root);
        moveActionsToBottom(root);
        improveBackButton(root);
    }

    /** 화면 높이와 방향에 맞춰 문서 미리보기 높이를 자동 조정합니다. */
    private void adjustPreviewHeight(View root) {
        ImageView preview = findImageView(root);
        if (preview == null || preview.getLayoutParams() == null) return;

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float heightDp = metrics.heightPixels / metrics.density;
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        int target;
        if (landscape) {
            target = 180;
        } else {
            target = Math.round(Math.max(220f, Math.min(330f, heightDp * 0.36f)));
        }
        ViewGroup.LayoutParams params = preview.getLayoutParams();
        params.height = dp(target);
        preview.setLayoutParams(params);
        preview.setContentDescription("OCR 처리 전 문서 사진 미리보기");
    }

    /** 다시 촬영과 OCR 실행 버튼을 스크롤 영역 밖의 하단 고정 영역으로 이동합니다. */
    private void moveActionsToBottom(LinearLayout root) {
        if (findTagged(root, TAG_FIXED_DOCUMENT_ACTION) != null) return;

        Button retry = findButtonStarting(root, "다시");
        Button use = findOcrButton(root);
        if (retry == null || use == null || retry.getParent() != use.getParent()
                || !(retry.getParent() instanceof LinearLayout)) return;

        LinearLayout actions = (LinearLayout) retry.getParent();
        if (!(actions.getParent() instanceof ViewGroup)) return;
        ((ViewGroup) actions.getParent()).removeView(actions);

        actions.setTag(TAG_FIXED_DOCUMENT_ACTION);
        actions.setPadding(dp(14), dp(9), dp(14), dp(11));
        actions.setBackgroundColor(Color.WHITE);
        actions.setElevation(dp(8));

        // 기존 좌우 버튼 높이는 유지하면서 최소 터치 영역을 확보합니다.
        for (int i = 0; i < actions.getChildCount(); i++) {
            View child = actions.getChildAt(i);
            if (child.getLayoutParams() != null) child.getLayoutParams().height = dp(54);
        }
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));
    }

    private void improveBackButton(View root) {
        for (Button button : findButtons(root)) {
            if ("‹".equals(textOf(button))) {
                button.setContentDescription("이전 화면으로 이동");
                button.setTextSize(26);
                ViewGroup.LayoutParams params = button.getLayoutParams();
                if (params != null) {
                    params.width = dp(48);
                    params.height = dp(48);
                    button.setLayoutParams(params);
                }
                return;
            }
        }
    }

    private LinearLayout findRootLayout() {
        View content = findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) content;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i) instanceof LinearLayout) {
                return (LinearLayout) group.getChildAt(i);
            }
        }
        return null;
    }

    private ImageView findImageView(View view) {
        if (view instanceof ImageView) return (ImageView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            ImageView found = findImageView(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private Button findButtonStarting(View root, String prefix) {
        for (Button button : findButtons(root)) {
            if (textOf(button).startsWith(prefix)) return button;
        }
        return null;
    }

    private Button findOcrButton(View root) {
        for (Button button : findButtons(root)) {
            String value = textOf(button);
            if (value.contains("OCR 실행")) return button;
        }
        return null;
    }

    private List<Button> findButtons(View root) {
        List<Button> result = new ArrayList<>();
        collectButtons(root, result);
        return result;
    }

    private void collectButtons(View view, List<Button> result) {
        if (view instanceof Button) result.add((Button) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectButtons(group.getChildAt(i), result);
        }
    }

    private View findTagged(View view, String tag) {
        if (tag.equals(view.getTag())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findTagged(group.getChildAt(i), tag);
            if (found != null) return found;
        }
        return null;
    }

    private String textOf(Button button) {
        return button.getText() == null ? "" : button.getText().toString().trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
