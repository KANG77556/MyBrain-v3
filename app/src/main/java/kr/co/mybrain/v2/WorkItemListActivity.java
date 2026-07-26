package kr.co.mybrain.v2;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.data.WorkItemRepository;

/** 저장된 일정·할 일·메모를 조회하고 관리하는 화면입니다. */
public class WorkItemListActivity extends AppCompatActivity {

    private WorkItemRepository repository;
    private LinearLayout listContainer;
    private TextView emptyText;
    private String currentFilter = "ALL";
    private boolean showingTrash;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = WorkItemRepository.getInstance(this);
        setContentView(buildScreen());
        loadItems();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (listContainer != null) loadItems();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 249, 253));
        root.setPadding(dp(16), dp(10), dp(16), dp(14));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(dp(16), bars.top + dp(10), dp(16), bars.bottom + dp(14));
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("←");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(52), dp(48)));
        TextView title = text("저장된 항목", 24, Color.rgb(28, 38, 52));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(header);

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        addFilter(filters, "전체", "ALL");
        addFilter(filters, "일정", WorkItemEntity.TYPE_SCHEDULE);
        addFilter(filters, "할 일", WorkItemEntity.TYPE_TASK);
        addFilter(filters, "메모", WorkItemEntity.TYPE_MEMO);
        addFilter(filters, "휴지통", "TRASH");
        root.addView(filters, new LinearLayout.LayoutParams(-1, dp(50)));

        emptyText = text("저장된 항목이 없습니다.", 16, Color.rgb(102, 116, 138));
        emptyText.setGravity(Gravity.CENTER);

        ScrollView scroll = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private void addFilter(LinearLayout parent, String label, String value) {
        Button button = button(label);
        button.setTextSize(13);
        button.setOnClickListener(v -> {
            currentFilter = value;
            showingTrash = "TRASH".equals(value);
            loadItems();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
        params.setMargins(dp(2), dp(3), dp(2), dp(3));
        parent.addView(button, params);
    }

    private void loadItems() {
        if (showingTrash) {
            repository.getDeleted(items -> runOnUiThread(() -> render(items)));
        } else if ("ALL".equals(currentFilter)) {
            repository.getAll(items -> runOnUiThread(() -> render(items)));
        } else {
            repository.getByType(currentFilter, items -> runOnUiThread(() -> render(items)));
        }
    }

    private void render(List<WorkItemEntity> items) {
        listContainer.removeAllViews();
        if (items == null || items.isEmpty()) {
            listContainer.addView(emptyText, new LinearLayout.LayoutParams(-1, dp(220)));
            return;
        }
        for (WorkItemEntity item : items) listContainer.addView(buildCard(item));
    }

    private View buildCard(WorkItemEntity item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, dp(7), 0, dp(7));
        card.setLayoutParams(cardParams);

        TextView type = text(typeLabel(item.type) + priorityMark(item.priority), 13, Color.rgb(70, 92, 130));
        card.addView(type);

        TextView title = text(item.title, 18, Color.rgb(28, 38, 52));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(4), 0, dp(3));
        card.addView(title);

        String detail = formatTime(item.startAt);
        if (item.endAt != null) detail += " ~ " + formatTime(item.endAt);
        if (!"NONE".equals(item.repeatRule)) detail += " · " + repeatLabel(item.repeatRule);
        TextView time = text(detail, 14, Color.rgb(102, 116, 138));
        card.addView(time);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        if (!showingTrash && WorkItemEntity.TYPE_TASK.equals(item.type)) {
            CheckBox complete = new CheckBox(this);
            complete.setText(item.completed ? "완료" : "미완료");
            complete.setChecked(item.completed);
            complete.setOnCheckedChangeListener((buttonView, checked) -> repository.setCompleted(item.id, checked,
                    count -> runOnUiThread(this::loadItems)));
            actions.addView(complete, new LinearLayout.LayoutParams(0, dp(48), 1f));
        } else {
            View spacer = new View(this);
            actions.addView(spacer, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }

        if (showingTrash) {
            Button restore = button("복구");
            restore.setOnClickListener(v -> repository.restore(item.id, count -> runOnUiThread(() -> {
                Toast.makeText(this, "항목을 복구했습니다.", Toast.LENGTH_SHORT).show();
                loadItems();
            })));
            actions.addView(restore, new LinearLayout.LayoutParams(dp(78), dp(46)));
        } else {
            Button edit = button("수정");
            edit.setOnClickListener(v -> editItem(item));
            actions.addView(edit, new LinearLayout.LayoutParams(dp(78), dp(46)));

            Button delete = button("삭제");
            delete.setOnClickListener(v -> confirmDelete(item));
            actions.addView(delete, new LinearLayout.LayoutParams(dp(78), dp(46)));
        }
        card.addView(actions);
        return card;
    }

    private void editItem(WorkItemEntity item) {
        EditText title = new EditText(this);
        title.setText(item.title);
        title.setSelection(title.length());
        title.setHint("제목");
        new AlertDialog.Builder(this)
                .setTitle("항목 제목 수정")
                .setView(title)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> {
                    String value = title.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(this, "제목을 입력하세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    item.title = value;
                    repository.update(item, count -> runOnUiThread(() -> {
                        Toast.makeText(this, "수정했습니다.", Toast.LENGTH_SHORT).show();
                        loadItems();
                    }));
                }).show();
    }

    private void confirmDelete(WorkItemEntity item) {
        new AlertDialog.Builder(this)
                .setTitle("휴지통으로 이동")
                .setMessage("'" + item.title + "' 항목을 삭제하시겠습니까? 휴지통에서 복구할 수 있습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> repository.softDelete(item.id,
                        count -> runOnUiThread(() -> {
                            Toast.makeText(this, "휴지통으로 이동했습니다.", Toast.LENGTH_SHORT).show();
                            loadItems();
                        }))).show();
    }

    private String typeLabel(String type) {
        if (WorkItemEntity.TYPE_SCHEDULE.equals(type)) return "일정";
        if (WorkItemEntity.TYPE_TASK.equals(type)) return "할 일";
        return "메모";
    }

    private String priorityMark(String priority) {
        if ("HIGH".equals(priority)) return " · 중요";
        if ("LOW".equals(priority)) return " · 낮음";
        return "";
    }

    private String repeatLabel(String value) {
        if ("DAILY".equals(value)) return "매일";
        if ("WEEKDAYS".equals(value)) return "평일";
        if ("WEEKLY".equals(value)) return "매주";
        if ("MONTHLY".equals(value)) return "매월";
        return "";
    }

    private String formatTime(Long millis) {
        if (millis == null) return "날짜 없음";
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy.MM.dd (E) HH:mm", Locale.KOREA));
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(15);
        button.setAllCaps(false);
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
