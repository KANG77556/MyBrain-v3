package kr.co.mybrain.ai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 개인정보 마스킹 전·후 내용을 비교하는 검토 화면입니다.
 * 원문은 화면 안에서만 보여주며, 적용 시 마스킹된 텍스트만 호출 화면으로 반환합니다.
 */
public class PrivacyReviewActivity extends Activity {

    public static final String EXTRA_RAW_TEXT = "privacy_review_raw_text";
    public static final String EXTRA_SOURCE = "privacy_review_source";
    public static final String EXTRA_RESULT_TEXT = "privacy_review_result_text";

    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BORDER = Color.rgb(220, 228, 240);
    private static final int BACKGROUND = Color.rgb(247, 249, 253);

    private OcrPrivacyProcessor.ProcessedText processed;
    private String rawText;
    private String source;
    private TextView content;
    private Button originalTab;
    private Button maskedTab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 개인정보가 표시되는 동안 최근 앱 화면이나 스크린샷에 남지 않도록 보호합니다.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        rawText = safe(getIntent().getStringExtra(EXTRA_RAW_TEXT)).trim();
        source = safe(getIntent().getStringExtra(EXTRA_SOURCE)).trim();
        processed = OcrPrivacyProcessor.process(rawText, source);
        buildScreen();
        showMasked();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.setBackgroundColor(Color.WHITE);

        Button close = button("‹", Color.WHITE, TEXT);
        close.setTextSize(25);
        close.setOnClickListener(v -> finish());
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text("개인정보 확인", 22, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(header);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(14), dp(16), dp(16));

        TextView guide = text(processed.maskedCount + "건의 개인정보 후보를 찾았습니다.", 18, TEXT);
        guide.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        body.addView(guide);

        TextView sub = text("원문과 보호 결과를 비교한 뒤 마스킹된 내용만 입력창에 추가합니다.", 13, MUTED);
        sub.setPadding(0, dp(5), 0, dp(10));
        body.addView(sub);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        originalTab = button("마스킹 전", Color.WHITE, MUTED);
        originalTab.setOnClickListener(v -> showOriginal());
        maskedTab = button("마스킹 후", PRIMARY, Color.WHITE);
        maskedTab.setOnClickListener(v -> showMasked());
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(46), 1f);
        left.setMargins(0, 0, dp(4), 0);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(46), 1f);
        right.setMargins(dp(4), 0, 0, 0);
        tabs.addView(originalTab, left);
        tabs.addView(maskedTab, right);
        body.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(rounded(Color.WHITE, 18, BORDER, 1));
        content = text("", 15, TEXT);
        content.setTextIsSelectable(true);
        content.setPadding(dp(16), dp(14), dp(16), dp(18));
        scroll.addView(content);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollParams.setMargins(0, dp(10), 0, dp(10));
        body.addView(scroll, scrollParams);

        TextView warning = text("주민등록번호·전화번호·이메일 형식을 규칙으로 찾아 가립니다. 특수한 형식은 직접 확인해 주세요.", 12, MUTED);
        warning.setPadding(dp(2), 0, dp(2), dp(10));
        body.addView(warning);

        Button apply = button("마스킹 결과 적용", PRIMARY, Color.WHITE);
        apply.setTextSize(16);
        apply.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        apply.setOnClickListener(v -> applyMaskedText());
        body.addView(apply, new LinearLayout.LayoutParams(-1, dp(56)));

        Button cancel = button("취소", Color.TRANSPARENT, MUTED);
        cancel.setOnClickListener(v -> finish());
        body.addView(cancel, new LinearLayout.LayoutParams(-1, dp(48)));

        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private void showOriginal() {
        content.setText(rawText);
        originalTab.setBackground(rounded(PRIMARY, 14, 0, 0));
        originalTab.setTextColor(Color.WHITE);
        maskedTab.setBackground(rounded(Color.WHITE, 14, BORDER, 1));
        maskedTab.setTextColor(MUTED);
    }

    private void showMasked() {
        content.setText(processed.storedText);
        maskedTab.setBackground(rounded(PRIMARY, 14, 0, 0));
        maskedTab.setTextColor(Color.WHITE);
        originalTab.setBackground(rounded(Color.WHITE, 14, BORDER, 1));
        originalTab.setTextColor(MUTED);
    }

    private void applyMaskedText() {
        Intent data = new Intent();
        data.putExtra(EXTRA_RESULT_TEXT, processed.storedText);
        setResult(RESULT_OK, data);
        finish();
    }

    private Button button(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(foreground);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinimumHeight(0);
        button.setMinimumWidth(0);
        button.setBackground(rounded(background, 14, background == Color.WHITE ? BORDER : 0,
                background == Color.WHITE ? 1 : 0));
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
