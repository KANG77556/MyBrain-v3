package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 항목을 실수로 수정하지 않고 읽을 수 있는 전용 상세 화면입니다.
 */
public class WorkItemDetailActivity extends Activity {
    public static final String EXTRA_INDEX = "item_index";

    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BORDER = Color.rgb(220, 228, 240);
    private static final int BACKGROUND = Color.rgb(247, 249, 253);
    private static final int DANGER = Color.rgb(190, 52, 52);

    private int itemIndex;
    private List<WorkItemRecord> items;
    private WorkItemRecord item;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        itemIndex = getIntent().getIntExtra(EXTRA_INDEX, -1);
        loadCurrent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCurrent();
    }

    private void loadCurrent() {
        items = WorkItemStore.load(this);
        if (itemIndex < 0 || itemIndex >= items.size()) {
            Toast.makeText(this, "항목을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        item = items.get(itemIndex);
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

        TextView headerTitle = text(item.type + " 상세", 21, TEXT);
        headerTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button edit = button("수정", Color.rgb(232, 240, 255), PRIMARY);
        edit.setOnClickListener(v -> {
            Intent intent = new Intent(this, WorkItemEditorActivity.class);
            intent.putExtra(WorkItemEditorActivity.EXTRA_INDEX, itemIndex);
            startActivity(intent);
        });
        header.addView(edit, new LinearLayout.LayoutParams(dp(72), dp(46)));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(36));
        scroll.addView(content);

        TextView type = text(item.type, 14, itemColor(item));
        type.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(type);

        TextView title = text(item.title.isEmpty() ? "제목 없음" : item.title, 27, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, dp(6), 0, dp(18));
        content.addView(title);

        content.addView(infoCard());
        content.addView(section("내용"));

        TextView body = text(item.original.isEmpty() ? "내용이 없습니다." : item.original, 16, TEXT);
        body.setPadding(dp(16), dp(16), dp(16), dp(16));
        body.setBackground(shape(Color.WHITE, 18, BORDER, 1));
        body.setLineSpacing(0f, 1.2f);
        content.addView(body, new LinearLayout.LayoutParams(-1, -2));

        if ("할 일".equals(item.type)) {
            Button complete = button(item.completed ? "완료 취소" : "완료 처리",
                    item.completed ? Color.rgb(235, 239, 245) : Color.rgb(234, 120, 35),
                    item.completed ? TEXT : Color.WHITE);
            complete.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            complete.setOnClickListener(v -> toggleComplete());
            LinearLayout.LayoutParams completeParams = new LinearLayout.LayoutParams(-1, dp(52));
            completeParams.setMargins(0, dp(18), 0, 0);
            content.addView(complete, completeParams);
        }

        Button delete = button("삭제", Color.rgb(255, 239, 239), DANGER);
        delete.setOnClickListener(v -> confirmDelete());
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(-1, dp(52));
        deleteParams.setMargins(0, dp(10), 0, 0);
        content.addView(delete, deleteParams);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private LinearLayout infoCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(shape(Color.WHITE, 18, BORDER, 1));

        addInfo(card, "날짜", item.date.isEmpty() ? "없음" : displayDate(item.date));
        addInfo(card, "시간", item.time.isEmpty() ? "없음" : item.time);
        addInfo(card, "알림", reminderLabel(item.reminderMinutes));
        addInfo(card, "반복", repeatLabel(item.repeatType));
        addInfo(card, "상태", item.completed ? "완료" : "진행 중");
        return card;
    }

    private void addInfo(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(5), 0, dp(5));
        TextView name = text(label, 14, MUTED);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(name, new LinearLayout.LayoutParams(dp(80), -2));
        row.addView(text(value, 15, TEXT), new LinearLayout.LayoutParams(0, -2, 1f));
        parent.addView(row);
    }

    private void toggleComplete() {
        if (itemIndex < 0 || itemIndex >= items.size()) return;
        items.get(itemIndex).completed = !items.get(itemIndex).completed;
        WorkItemStore.save(this, items);
        loadCurrent();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("항목 삭제")
                .setMessage("이 항목을 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> {
                    if (itemIndex >= 0 && itemIndex < items.size()) items.remove(itemIndex);
                    WorkItemStore.save(this, items);
                    finish();
                })
                .show();
    }

    private TextView section(String value) {
        TextView text = text(value, 18, TEXT);
        text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setPadding(0, dp(20), 0, dp(8));
        return text;
    }

    private Button button(String label, int background, int color) {
        Button value = new Button(this);
        value.setText(label);
        value.setTextSize(15);
        value.setTextColor(color);
        value.setAllCaps(false);
        value.setMinimumWidth(0);
        value.setMinimumHeight(0);
        value.setBackground(shape(background, 14, BORDER, 1));
        return value;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable shape(int fill, int radius, int stroke, int width) {
        GradientDrawable value = new GradientDrawable();
        value.setColor(fill);
        value.setCornerRadius(dp(radius));
        if (width > 0) value.setStroke(dp(width), stroke);
        return value;
    }

    private int itemColor(WorkItemRecord value) {
        if ("일정".equals(value.type)) return PRIMARY;
        if ("할 일".equals(value.type)) return Color.rgb(234, 120, 35);
        return Color.rgb(123, 86, 188);
    }

    private String displayDate(String value) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(value);
            return date == null ? value : new SimpleDateFormat("yyyy년 M월 d일 E요일", Locale.KOREA).format(date);
        } catch (Exception ignored) {
            return value;
        }
    }

    private String reminderLabel(int minutes) {
        if (minutes < 0) return "알림 없음";
        if (minutes == 0) return "정각";
        if (minutes == 60) return "1시간 전";
        return minutes + "분 전";
    }

    private String repeatLabel(String value) {
        if ("DAILY".equals(value)) return "매일";
        if ("WEEKLY".equals(value)) return "매주";
        if ("MONTHLY".equals(value)) return "매월";
        if ("WEEKDAYS".equals(value)) return "평일";
        return "반복 없음";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
