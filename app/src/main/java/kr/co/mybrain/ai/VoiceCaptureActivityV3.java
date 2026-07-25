package kr.co.mybrain.ai;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * MyBrain AI 1.9.1 음성 이어 말하기 화면입니다.
 * 상단 뒤로가기와 제목을 통일하고 보조 버튼을 한 줄로 정리합니다.
 */
public class VoiceCaptureActivityV3 extends VoiceCaptureActivityV2 {

    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BORDER = Color.rgb(220, 228, 240);
    private static final String TAG_HEADER = "mybrain_voice_v3_header";
    private static final String TAG_SECONDARY = "mybrain_voice_v3_secondary";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyLayout();
    }

    private void applyLayout() {
        LinearLayout root = findRootLayout();
        if (root == null) return;
        installHeader(root);
        groupSecondaryActions(root);
        improvePrimaryButtons(root);
    }

    /** 다른 보조 화면과 같은 형태의 상단 이동 영역을 추가합니다. */
    private void installHeader(LinearLayout root) {
        if (findTagged(root, TAG_HEADER) != null) return;

        TextView oldTitle = findText(root, "음성 이어 말하기");
        if (oldTitle == null) return;
        if (oldTitle.getParent() instanceof ViewGroup) {
            ((ViewGroup) oldTitle.getParent()).removeView(oldTitle);
        }

        LinearLayout header = new LinearLayout(this);
        header.setTag(TAG_HEADER);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(6));

        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(TEXT);
        back.setAllCaps(false);
        back.setMinimumHeight(0);
        back.setMinimumWidth(0);
        back.setContentDescription("이전 화면으로 이동");
        back.setBackground(rounded(Color.TRANSPARENT, 14, 0, 0));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = new TextView(this);
        title.setText("음성으로 입력");
        title.setTextSize(23);
        title.setTextColor(TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));

        root.addView(header, 0);
    }

    /** 취소와 마지막 문장 삭제를 같은 보조 버튼 줄로 정리합니다. */
    private void groupSecondaryActions(LinearLayout root) {
        if (findTagged(root, TAG_SECONDARY) != null) return;
        Button remove = findButton(root, "마지막 문장 지우기");
        Button cancel = findButton(root, "취소");
        Button apply = findButton(root, "입력창에 모두 추가");
        if (remove == null || cancel == null || apply == null) return;

        detach(remove);
        detach(cancel);

        LinearLayout row = new LinearLayout(this);
        row.setTag(TAG_SECONDARY);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        cancel.setText("닫기");
        cancel.setTextColor(MUTED);
        remove.setText("마지막 문장 삭제");

        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(48), 1f);
        left.setMargins(0, 0, dp(4), 0);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(48), 1f);
        right.setMargins(dp(4), 0, 0, 0);
        row.addView(cancel, left);
        row.addView(remove, right);

        int index = root.indexOfChild(apply);
        root.addView(row, Math.max(0, index));
    }

    private void improvePrimaryButtons(LinearLayout root) {
        Button listen = findButtonStarting(root, "듣기");
        if (listen == null) listen = findButtonStarting(root, "한 문장");
        Button apply = findButton(root, "입력창에 모두 추가");
        if (listen != null) {
            listen.setTextSize(15);
            listen.setMinHeight(dp(52));
            listen.setContentDescription("음성 듣기 시작 또는 중지");
        }
        if (apply != null) {
            apply.setText("입력창에 추가");
            apply.setTextSize(16);
            apply.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            apply.setMinHeight(dp(56));
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

    private TextView findText(View root, String value) {
        for (View view : findViews(root)) {
            if (view instanceof TextView && value.equals(textOf((TextView) view))) {
                return (TextView) view;
            }
        }
        return null;
    }

    private Button findButton(View root, String value) {
        for (View view : findViews(root)) {
            if (view instanceof Button && value.equals(textOf((TextView) view))) {
                return (Button) view;
            }
        }
        return null;
    }

    private Button findButtonStarting(View root, String prefix) {
        for (View view : findViews(root)) {
            if (view instanceof Button && textOf((TextView) view).startsWith(prefix)) {
                return (Button) view;
            }
        }
        return null;
    }

    private List<View> findViews(View root) {
        List<View> result = new ArrayList<>();
        collect(root, result);
        return result;
    }

    private void collect(View view, List<View> result) {
        if (view == null) return;
        result.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), result);
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

    private void detach(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private String textOf(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
