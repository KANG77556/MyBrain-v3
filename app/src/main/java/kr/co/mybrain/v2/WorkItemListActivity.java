package kr.co.mybrain.v2;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.data.WorkItemRepository;
import kr.co.mybrain.v2.ui.AppUi;
import kr.co.mybrain.v2.ui.UiSelection;

/** 저장된 일정·할 일·메모를 검색하고 관리하는 화면입니다. */
public class WorkItemListActivity extends AppCompatActivity {
    private WorkItemRepository repository;
    private LinearLayout listContainer;
    private TextView countText;
    private EditText searchInput;
    private String currentFilter = "ALL";
    private boolean showingTrash;
    private List<WorkItemEntity> loadedItems = new ArrayList<>();
    private final Map<String, Button> filterButtons = new LinkedHashMap<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = WorkItemRepository.getInstance(this);
        setContentView(buildScreen());
        loadItems();
    }

    @Override protected void onResume() {
        super.onResume();
        if (listContainer != null) loadItems();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppUi.BG);
        int side = AppUi.isTablet(this) ? AppUi.dp(this, 28) : AppUi.dp(this, 16);
        root.setPadding(side, AppUi.dp(this, 10), side, AppUi.dp(this, 14));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int horizontal = AppUi.isTablet(this) ? AppUi.dp(this, 28) : AppUi.dp(this, 16);
            view.setPadding(horizontal, bars.top + AppUi.dp(this, 10), horizontal,
                    bars.bottom + AppUi.dp(this, 14));
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = AppUi.compactButton(this, "←");
        back.setContentDescription("홈으로 돌아가기");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(AppUi.dp(this, 52), AppUi.dp(this, 48)));
        TextView title = AppUi.text(this, "저장 목록", 25, AppUi.TEXT, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, AppUi.dp(this, 48), 1f);
        titleParams.setMargins(AppUi.dp(this, 10), 0, 0, 0);
        header.addView(title, titleParams);
        countText = AppUi.text(this, "", 13, AppUi.SUBTEXT, false);
        countText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(countText, new LinearLayout.LayoutParams(-2, AppUi.dp(this, 48)));
        root.addView(header);

        TextView subtitle = AppUi.body(this, "검색하거나 분류를 선택해 필요한 기록을 빠르게 찾습니다.");
        subtitle.setPadding(0, AppUi.dp(this, 4), 0, AppUi.dp(this, 10));
        root.addView(subtitle);

        searchInput = AppUi.input(this, "제목이나 내용 검색");
        searchInput.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0);
        searchInput.setCompoundDrawablePadding(AppUi.dp(this, 8));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderFiltered(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        root.addView(searchInput, new LinearLayout.LayoutParams(-1, AppUi.dp(this, 52)));

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setPadding(0, AppUi.dp(this, 8), 0, AppUi.dp(this, 6));
        addFilter(filters, "전체", "ALL");
        addFilter(filters, "일정", WorkItemEntity.TYPE_SCHEDULE);
        addFilter(filters, "할 일", WorkItemEntity.TYPE_TASK);
        addFilter(filters, "메모", WorkItemEntity.TYPE_MEMO);
        addFilter(filters, "휴지통", "TRASH");
        filterScroll.addView(filters, new HorizontalScrollView.LayoutParams(-2, -2));
        root.addView(filterScroll, new LinearLayout.LayoutParams(-1, AppUi.dp(this, 60)));
        updateFilterUi();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button add = AppUi.primaryButton(this, "＋  새 항목 추가");
        add.setOnClickListener(v -> openHomeForAdd());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-1, AppUi.dp(this, 54));
        addParams.setMargins(0, AppUi.dp(this, 10), 0, 0);
        root.addView(add, addParams);
        return root;
    }

    private void addFilter(LinearLayout parent, String label, String value) {
        Button button = UiSelection.button(this, label);
        button.setOnClickListener(v -> {
            currentFilter = value;
            showingTrash = "TRASH".equals(value);
            updateFilterUi();
            loadItems();
        });
        filterButtons.put(value, button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(AppUi.dp(this, 82), AppUi.dp(this, 44));
        params.setMargins(0, 0, AppUi.dp(this, 8), 0);
        parent.addView(button, params);
    }

    private void updateFilterUi() {
        for (Map.Entry<String, Button> entry : filterButtons.entrySet()) {
            UiSelection.apply(this, entry.getValue(), entry.getKey().equals(currentFilter));
        }
    }

    private void loadItems() {
        if (showingTrash) {
            repository.getDeleted(items -> runOnUiThread(() -> acceptItems(items)));
        } else if ("ALL".equals(currentFilter)) {
            repository.getAll(items -> runOnUiThread(() -> acceptItems(items)));
        } else {
            repository.getByType(currentFilter, items -> runOnUiThread(() -> acceptItems(items)));
        }
    }

    private void acceptItems(List<WorkItemEntity> items) {
        loadedItems = items == null ? new ArrayList<>() : new ArrayList<>(items);
        renderFiltered();
    }

    private void renderFiltered() {
        if (listContainer == null || searchInput == null) return;
        String query = searchInput.getText().toString().trim().toLowerCase(Locale.KOREA);
        List<WorkItemEntity> visible = new ArrayList<>();
        for (WorkItemEntity item : loadedItems) {
            if (query.isEmpty() || contains(item.title, query) || contains(item.content, query)
                    || contains(item.sourceText, query)) {
                visible.add(item);
            }
        }
        render(visible, !query.isEmpty());
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.KOREA).contains(query);
    }

    private void render(List<WorkItemEntity> items, boolean searching) {
        listContainer.removeAllViews();
        int count = items == null ? 0 : items.size();
        countText.setText(count + "개");
        if (count == 0) {
            String title = searching ? "검색 결과가 없습니다" : showingTrash ? "휴지통이 비어 있습니다" : "저장된 항목이 없습니다";
            String description = searching
                    ? "검색어를 줄이거나 다른 분류를 선택해 보세요."
                    : showingTrash ? "삭제한 항목은 이곳에서 복구할 수 있습니다."
                    : "아래 버튼으로 일정·할 일·메모를 추가해 보세요.";
            listContainer.addView(AppUi.emptyState(this, title, description), AppUi.cardParams(this));
            return;
        }
        for (WorkItemEntity item : items) listContainer.addView(buildCard(item));
    }

    private View buildCard(WorkItemEntity item) {
        LinearLayout card = AppUi.card(this);
        card.setLayoutParams(AppUi.cardParams(this));

        TextView type = AppUi.text(this, typeLabel(item.type) + priorityMark(item.priority), 13, AppUi.PRIMARY, true);
        card.addView(type);

        TextView title = AppUi.text(this, item.title, 18, AppUi.TEXT, true);
        title.setPadding(0, AppUi.dp(this, 4), 0, AppUi.dp(this, 4));
        card.addView(title);

        String detail = formatTime(item.startAt);
        if (item.endAt != null) detail += " ~ " + formatTime(item.endAt);
        if (!"NONE".equals(item.repeatRule)) detail += " · " + repeatLabel(item.repeatRule);
        TextView time = AppUi.body(this, detail);
        card.addView(time);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, AppUi.dp(this, 8), 0, 0);

        if (!showingTrash && WorkItemEntity.TYPE_TASK.equals(item.type)) {
            CheckBox complete = new CheckBox(this);
            complete.setText(item.completed ? "완료" : "미완료");
            complete.setTextColor(AppUi.TEXT);
            complete.setChecked(item.completed);
            complete.setOnCheckedChangeListener((buttonView, checked) -> repository.setCompleted(item.id, checked,
                    count -> runOnUiThread(this::loadItems)));
            actions.addView(complete, new LinearLayout.LayoutParams(0, AppUi.dp(this, 48), 1f));
        } else {
            View spacer = new View(this);
            actions.addView(spacer, new LinearLayout.LayoutParams(0, AppUi.dp(this, 48), 1f));
        }

        if (showingTrash) {
            Button restore = AppUi.compactButton(this, "복구");
            restore.setOnClickListener(v -> repository.restore(item.id, count -> runOnUiThread(() -> {
                Toast.makeText(this, "항목을 복구했습니다.", Toast.LENGTH_SHORT).show();
                loadItems();
            })));
            actions.addView(restore, actionButtonParams(false));
        } else {
            Button edit = AppUi.compactButton(this, "수정");
            edit.setOnClickListener(v -> editItem(item));
            actions.addView(edit, actionButtonParams(false));

            Button delete = AppUi.compactButton(this, "삭제");
            delete.setTextColor(AppUi.DANGER);
            delete.setOnClickListener(v -> confirmDelete(item));
            actions.addView(delete, actionButtonParams(true));
        }
        card.addView(actions);
        return card;
    }

    private LinearLayout.LayoutParams actionButtonParams(boolean withLeftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(AppUi.dp(this, 78), AppUi.dp(this, 46));
        if (withLeftMargin) params.setMargins(AppUi.dp(this, 8), 0, 0, 0);
        return params;
    }

    private void editItem(WorkItemEntity item) {
        EditText title = AppUi.input(this, "제목");
        title.setText(item.title);
        title.setSelection(title.length());
        int side = AppUi.dp(this, 18);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(side, AppUi.dp(this, 8), side, 0);
        wrapper.addView(title, new LinearLayout.LayoutParams(-1, AppUi.dp(this, 54)));
        new AlertDialog.Builder(this)
                .setTitle("제목 수정")
                .setView(wrapper)
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
                .setMessage("'" + item.title + "' 항목을 휴지통으로 이동할까요? 나중에 복구할 수 있습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("이동", (dialog, which) -> repository.softDelete(item.id,
                        count -> runOnUiThread(() -> {
                            Toast.makeText(this, "휴지통으로 이동했습니다.", Toast.LENGTH_SHORT).show();
                            loadItems();
                        }))).show();
    }

    private void openHomeForAdd() {
        Intent intent = new Intent(this, AdaptiveMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
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
        return "반복";
    }

    private String formatTime(Long millis) {
        if (millis == null) return "날짜 없음";
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy.MM.dd (E) HH:mm", Locale.KOREA));
    }
}