package kr.co.mybrain.v2;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.data.WorkItemRepository;

/** 월간 달력, 주간 일정, 오늘 할 일을 한 화면에서 보여줍니다. */
public class CalendarActivity extends AppCompatActivity {

    private final ZoneId zoneId = ZoneId.systemDefault();
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREA);
    private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA);

    private WorkItemRepository repository;
    private CalendarView calendarView;
    private LinearLayout itemContainer;
    private TextView rangeTitle;
    private LocalDate selectedDate = LocalDate.now();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = WorkItemRepository.getInstance(this);
        setContentView(buildScreen());
        showDay(selectedDate);
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
        header.addView(back, new LinearLayout.LayoutParams(dp(52), dp(46)));
        TextView title = text("일정과 할 일", 24, Color.rgb(28, 38, 52));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        titleParams.setMargins(dp(8), 0, 0, 0);
        header.addView(title, titleParams);
        root.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setWeightSum(3f);
        Button month = button("월간");
        Button week = button("주간");
        Button today = button("오늘 할 일");
        month.setOnClickListener(v -> showDay(selectedDate));
        week.setOnClickListener(v -> showWeek(selectedDate));
        today.setOnClickListener(v -> showTodayTasks());
        tabs.addView(month, new LinearLayout.LayoutParams(0, dp(48), 1f));
        tabs.addView(week, new LinearLayout.LayoutParams(0, dp(48), 1f));
        tabs.addView(today, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(tabs);

        calendarView = new CalendarView(this);
        calendarView.setDate(System.currentTimeMillis());
        calendarView.setOnDateChangeListener((view, year, monthValue, dayOfMonth) -> {
            selectedDate = LocalDate.of(year, monthValue + 1, dayOfMonth);
            showDay(selectedDate);
        });
        root.addView(calendarView, new LinearLayout.LayoutParams(-1, dp(300)));

        rangeTitle = text("", 17, Color.rgb(28, 38, 52));
        rangeTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        rangeTitle.setPadding(0, dp(12), 0, dp(8));
        root.addView(rangeTitle);

        ScrollView scroll = new ScrollView(this);
        itemContainer = new LinearLayout(this);
        itemContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(itemContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private void showDay(LocalDate date) {
        calendarView.setVisibility(View.VISIBLE);
        selectedDate = date;
        rangeTitle.setText(date.format(dateFormat) + " 일정");
        long from = date.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long to = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();
        repository.getBetween(from, to, items -> runOnUiThread(() -> renderItems(items, false)));
    }

    private void showWeek(LocalDate date) {
        calendarView.setVisibility(View.GONE);
        LocalDate monday = date.with(DayOfWeek.MONDAY);
        LocalDate nextMonday = monday.plusWeeks(1);
        rangeTitle.setText(monday.format(dateFormat) + " ~ " + nextMonday.minusDays(1).format(dateFormat));
        long from = monday.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long to = nextMonday.atStartOfDay(zoneId).toInstant().toEpochMilli();
        repository.getBetween(from, to, items -> runOnUiThread(() -> renderItems(items, false)));
    }

    private void showTodayTasks() {
        calendarView.setVisibility(View.GONE);
        LocalDate today = LocalDate.now();
        rangeTitle.setText("오늘 할 일 · " + today.format(dateFormat));
        long from = today.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long to = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();
        repository.getOpenTasks(tasks -> {
            List<WorkItemEntity> visible = new ArrayList<>();
            for (WorkItemEntity task : tasks) {
                if (task.startAt == null || (task.startAt >= from && task.startAt < to)) visible.add(task);
            }
            runOnUiThread(() -> renderItems(visible, true));
        });
    }

    private void renderItems(List<WorkItemEntity> items, boolean taskMode) {
        itemContainer.removeAllViews();
        if (items == null || items.isEmpty()) {
            TextView empty = text(taskMode ? "오늘 처리할 할 일이 없습니다." : "등록된 일정이 없습니다.", 16, Color.rgb(102, 116, 138));
            empty.setPadding(dp(12), dp(24), dp(12), dp(24));
            itemContainer.addView(empty);
            return;
        }
        for (WorkItemEntity item : items) itemContainer.addView(buildCard(item));
    }

    private View buildCard(WorkItemEntity item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(cardParams);

        if (WorkItemEntity.TYPE_TASK.equals(item.type)) {
            CheckBox completed = new CheckBox(this);
            completed.setText(item.title);
            completed.setTextSize(17);
            completed.setChecked(item.completed);
            completed.setOnCheckedChangeListener((buttonView, checked) ->
                    repository.setCompleted(item.id, checked, ignored -> runOnUiThread(() -> item.completed = checked)));
            card.addView(completed);
        } else {
            TextView title = text(typeLabel(item.type) + " · " + item.title, 17, Color.rgb(28, 38, 52));
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            card.addView(title);
        }

        String timing = item.startAt == null ? "시간 미지정" : formatDateTime(item.startAt);
        if (item.endAt != null) timing += " ~ " + formatDateTime(item.endAt);
        TextView detail = text(timing + repeatText(item.repeatRule), 14, Color.rgb(102, 116, 138));
        detail.setPadding(0, dp(5), 0, 0);
        card.addView(detail);
        return card;
    }

    private String formatDateTime(long millis) {
        return Instant.ofEpochMilli(millis).atZone(zoneId).format(DateTimeFormatter.ofPattern("MM.dd (E) HH:mm", Locale.KOREA));
    }

    private String repeatText(String rule) {
        if ("DAILY".equals(rule)) return " · 매일 반복";
        if ("WEEKDAYS".equals(rule)) return " · 평일 반복";
        if ("WEEKLY".equals(rule)) return " · 매주 반복";
        if ("MONTHLY".equals(rule)) return " · 매월 반복";
        return "";
    }

    private String typeLabel(String type) {
        if (WorkItemEntity.TYPE_SCHEDULE.equals(type)) return "일정";
        if (WorkItemEntity.TYPE_TASK.equals(type)) return "할 일";
        return "메모";
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
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
