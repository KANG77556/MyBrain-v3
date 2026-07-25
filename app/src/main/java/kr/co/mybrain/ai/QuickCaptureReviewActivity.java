package kr.co.mybrain.ai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 음성 인식 또는 OCR 원문을 사용자가 확인·수정하는 화면입니다.
 * 복합 문장은 자동 분석으로 보내고, 단순 문장은 기존 전체 화면 편집기로 보낼 수 있습니다.
 */
public class QuickCaptureReviewActivity extends Activity {
    public static final String EXTRA_TEXT = "captured_text";
    public static final String EXTRA_SOURCE = "captured_source";

    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BORDER = Color.rgb(220, 228, 240);
    private static final int BACKGROUND = Color.rgb(247, 249, 253);

    private EditText textField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.setBackgroundColor(Color.WHITE);

        Button back = button("‹", Color.WHITE, TEXT);
        back.setTextSize(25);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text("추출 내용 확인", 22, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(16), dp(18), dp(30));
        scroll.addView(body);

        String source = safe(getIntent().getStringExtra(EXTRA_SOURCE));
        TextView sourceChip = text(source.isEmpty() ? "빠른 입력" : source, 13, PRIMARY);
        sourceChip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sourceChip.setPadding(dp(12), dp(8), dp(12), dp(8));
        sourceChip.setBackground(rounded(Color.rgb(232, 240, 255), 14, 0, 0));
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, -2);
        chipParams.setMargins(0, 0, 0, dp(12));
        body.addView(sourceChip, chipParams);

        TextView guide = text("잘못 인식된 부분을 고친 뒤 원하는 저장 방식을 선택하세요.", 14, MUTED);
        guide.setPadding(0, 0, 0, dp(10));
        body.addView(guide);

        textField = new EditText(this);
        textField.setText(safe(getIntent().getStringExtra(EXTRA_TEXT)));
        textField.setHint("인식된 내용이 여기에 표시됩니다.");
        textField.setTextSize(16);
        textField.setGravity(Gravity.TOP);
        textField.setMinLines(9);
        textField.setPadding(dp(14), dp(14), dp(14), dp(14));
        textField.setBackground(rounded(Color.WHITE, 16, BORDER, 1));
        body.addView(textField, new LinearLayout.LayoutParams(-1, dp(260)));

        Button analyze = button("자동 분석으로 정리", PRIMARY, Color.WHITE);
        analyze.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        analyze.setOnClickListener(v -> openAutomaticAnalysis());
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(-1, dp(56));
        mainParams.setMargins(0, dp(14), 0, dp(8));
        body.addView(analyze, mainParams);

        Button edit = button("한 건으로 직접 편집", Color.WHITE, PRIMARY);
        edit.setBackground(rounded(Color.WHITE, 14, PRIMARY, 1));
        edit.setOnClickListener(v -> openSingleEditor());
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(-1, dp(54));
        editParams.setMargins(0, 0, 0, dp(8));
        body.addView(edit, editParams);

        Button cancel = button("취소", Color.rgb(235, 238, 243), TEXT);
        cancel.setOnClickListener(v -> finish());
        body.addView(cancel, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView privacy = text("음성과 OCR 원문은 저장 버튼을 누르기 전까지 업무 목록에 저장되지 않습니다.", 12, MUTED);
        privacy.setPadding(dp(2), dp(16), dp(2), 0);
        body.addView(privacy);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private void openAutomaticAnalysis() {
        String value = currentText();
        if (value.isEmpty()) return;
        Intent intent = new Intent(this, AiInputActivityV2.class);
        intent.putExtra(AiInputActivityV2.EXTRA_INPUT_TEXT, value);
        startActivity(intent);
        finish();
    }

    private void openSingleEditor() {
        String value = currentText();
        if (value.isEmpty()) return;
        QuickInputPrefill.openEditor(this, QuickInputParser.parseSingle(value));
    }

    private String currentText() {
        String value = textField.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "저장할 내용을 입력하세요.", Toast.LENGTH_SHORT).show();
        }
        return value;
    }

    private Button button(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(15);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setMinimumHeight(0);
        button.setMinimumWidth(0);
        button.setBackground(rounded(background, 14, 0, 0));
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
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
