package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 여러 업무를 선택해 일괄 완료하거나 삭제하는 관리 화면입니다.
 */
public class WorkItemManagerActivity extends Activity {
    public static final String EXTRA_PRESELECT_INDEX = "preselect_index";

    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BORDER = Color.rgb(220, 228, 240);
    private static final int BACKGROUND = Color.rgb(247, 249, 253);
    private static final int DANGER = Color.rgb(190, 52, 52);

    private final List<Integer> selected = new ArrayList<>();
    private final List<CheckBox> checks = new ArrayList<>();
    private List<WorkItemRecord> items;
    private TextView countText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        items = WorkItemStore.load(this);
        int preselect = getIntent().getIntExtra(EXTRA_PRESELECT_INDEX, -1);
        if (preselect >= 0 && preselect < items.size()) selected.add(preselect);
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

        TextView title = text("항목 선택", 22, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button all = button("전체 선택", Color.rgb(232, 240, 255), PRIMARY);
        all.setOnClickListener(v -> toggleAll());
        header.addView(all, new LinearLayout.LayoutParams(dp(96), dp(46)));
        root.addView(header);

        countText = text("0개 선택", 14, MUTED);
        countText.setPadding(dp(18), dp(12), dp(18), dp(8));
        root.addView(countText);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), 0, dp(14), dp(14));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        checks.clear();
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            WorkItemRecord item = items.get(i);
            CheckBox check = new CheckBox(this);
            check.setText(buildLabel(item));
            check.setTextSize(15);
            check.setTextColor(TEXT);
            check.setGravity(Gravity.CENTER_VERTICAL);
            check.setPadding(dp(12), dp(9), dp(12), dp(9));
            check.setBackground(shape(Color.WHITE, 16, BORDER, 1));
            check.setChecked(selected.contains(index));
            check.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && !selected.contains(index)) selected.add(index);
                else if (!isChecked) selected.remove(Integer.valueOf(index));
                updateCount();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(72));
            params.setMargins(0, 0, 0, dp(8));
            list.addView(check, params);
            checks.add(check);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(12), dp(8), dp(12), dp(10));
        actions.setBackgroundColor(Color.WHITE);

        Button complete = button("완료 처리", Color.rgb(255, 245, 230), Color.rgb(190, 96, 20));
        complete.setOnClickListener(v -> completeSelected());
        actions.addView(complete, weighted());

        Button delete = button("선택 삭제", Color.rgb(255, 239, 239), DANGER);
        delete.setOnClickListener(v -> confirmDelete());
        actions.addView(delete, weighted());
        root.addView(actions);

        setContentView(root);
        updateCount();
    }

    private String buildLabel(WorkItemRecord item) {
        StringBuilder value = new StringBuilder();
        value.append(item.title.isEmpty() ? "제목 없음" : item.title)
                .append("\n")
                .append(item.type);
        if (!item.date.isEmpty()) value.append(" · ").append(item.date);
        if (!item.time.isEmpty()) value.append(" · ").append(item.time);
        if (item.completed) value.append(" · 완료");
        return value.toString();
    }

    private void toggleAll() {
        boolean selectAll = selected.size() != items.size();
        selected.clear();
        if (selectAll) for (int i = 0; i < items.size(); i++) selected.add(i);
        for (int i = 0; i < checks.size(); i++) checks.get(i).setChecked(selectAll);
        updateCount();
    }

    private void completeSelected() {
        if (selected.isEmpty()) {
            Toast.makeText(this, "선택한 항목이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        int changed = 0;
        for (int index : selected) {
            if (index >= 0 && index < items.size() && "할 일".equals(items.get(index).type)) {
                items.get(index).completed = true;
                changed++;
            }
        }
        if (changed == 0) {
            Toast.makeText(this, "선택 항목 중 할 일이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        WorkItemStore.save(this, items);
        Toast.makeText(this, changed + "개 할 일을 완료했습니다.", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDelete() {
        if (selected.isEmpty()) {
            Toast.makeText(this, "선택한 항목이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("선택 항목 삭제")
                .setMessage(selected.size() + "개 항목을 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> {
                    Collections.sort(selected, Collections.reverseOrder());
                    for (int index : selected) {
                        if (index >= 0 && index < items.size()) items.remove(index);
                    }
                    WorkItemStore.save(this, items);
                    finish();
                })
                .show();
    }

    private void updateCount() {
        if (countText != null) countText.setText(selected.size() + "개 선택");
    }

    private Button button(String label, int background, int color) {
        Button value = new Button(this);
        value.setText(label);
        value.setTextSize(14);
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

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
