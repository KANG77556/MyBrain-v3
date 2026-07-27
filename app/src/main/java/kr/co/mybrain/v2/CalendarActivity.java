package kr.co.mybrain.v2;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
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
import kr.co.mybrain.v2.ui.AppUi;
import kr.co.mybrain.v2.ui.CalendarDetailLayoutPolicy;
import kr.co.mybrain.v2.ui.UiPreferences;
import kr.co.mybrain.v2.ui.UiSelection;

/** 월간 달력부터 선택 날짜 상세 목록까지 화면 전체를 한 번에 스크롤합니다. */
public class CalendarActivity extends AppCompatActivity {
    public static final String EXTRA_FOCUS_AT = "kr.co.mybrain.v2.extra.FOCUS_AT";
    public static final String EXTRA_HIGHLIGHT_ID = "kr.co.mybrain.v2.extra.HIGHLIGHT_ID";

    private static final String MODE_DAY = "DAY";
    private static final String MODE_WEEK = "WEEK";
    private static final String MODE_TODAY = "TODAY";

    private final ZoneId zoneId = ZoneId.systemDefault();
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREA);

    private WorkItemRepository repository;
    private ScrollView screenScroll;
    private CalendarView calendarView;
    private LinearLayout itemContainer;
    private View detailAnchor;
    private View highlightedCard;
    private TextView rangeTitle;
    private TextView countText;
    private Button dayButton;
    private Button weekButton;
    private Button todayButton;
    private LocalDate selectedDate = LocalDate.now();
    private String currentMode = MODE_DAY;
    private long highlightId = -1L;
    private boolean screenReady;
    private boolean moveToDetailsAfterRender;

    public static Intent focusIntent(Context context, WorkItemEntity item) {
        Intent intent = new Intent(context, CalendarActivity.class);
        if (item != null) {
            if (item.startAt != null) intent.putExtra(EXTRA_FOCUS_AT, item.startAt);
            if (item.id > 0L) intent.putExtra(EXTRA_HIGHLIGHT_ID, item.id);
        }
        return intent;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = WorkItemRepository.getInstance(this);
        readNavigationTarget();
        setContentView(buildScreen());
        screenReady = true;
        moveToDetailsAfterRender = highlightId > 0L;
        showDay(selectedDate);
    }

    @Override protected void onResume() {
        super.onResume();
        if (screenReady) refreshCurrentMode();
    }

    private void readNavigationTarget() {
        Intent intent = getIntent();
        if (intent == null) return;
        long focusAt = intent.getLongExtra(EXTRA_FOCUS_AT, -1L);
        highlightId = intent.getLongExtra(EXTRA_HIGHLIGHT_ID, -1L);
        if (focusAt > 0L) selectedDate = Instant.ofEpochMilli(focusAt).atZone(zoneId).toLocalDate();
    }

    private void refreshCurrentMode() {
        if (MODE_WEEK.equals(currentMode)) showWeek(selectedDate);
        else if (MODE_TODAY.equals(currentMode)) showTodayTasks();
        else showDay(selectedDate);
    }

    private View buildScreen() {
        screenScroll = new ScrollView(this);
        screenScroll.setFillViewport(true);
        screenScroll.setClipToPadding(false);
        screenScroll.setBackgroundColor(AppUi.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppUi.BG);
        int side = AppUi.isTablet(this) ? AppUi.dp(this, 28) : AppUi.dp(this, 16);
        UiPreferences preferences = UiPreferences.load(this);
        int bottomExtra = CalendarDetailLayoutPolicy.bottomContentPaddingDp(
                preferences.oneHandMode, preferences.textScalePercent);
        root.setPadding(side, AppUi.dp(this, 10), side, AppUi.dp(this, bottomExtra));
        screenScroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        ViewCompat.setOnApplyWindowInsetsListener(screenScroll, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int horizontal = AppUi.isTablet(this) ? AppUi.dp(this, 28) : AppUi.dp(this, 16);
            UiPreferences current = UiPreferences.load(this);
            int contentGap = CalendarDetailLayoutPolicy.bottomContentPaddingDp(
                    current.oneHandMode, current.textScalePercent);
            root.setPadding(horizontal, bars.top + AppUi.dp(this, 10), horizontal,
                    bars.bottom + AppUi.dp(this, contentGap));
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = AppUi.compactButton(this, "←");
        back.setContentDescription("홈으로 돌아가기");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(AppUi.dp(this, 52), AppUi.dp(this, 48)));
        TextView title = AppUi.text(this, "일정", 25, AppUi.TEXT, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, AppUi.dp(this, 48), 1f);
        titleParams.setMargins(AppUi.dp(this, 10), 0, 0, 0);
        header.addView(title, titleParams);
        root.addView(header);

        TextView subtitle = AppUi.body(this, "날짜별 일정과 오늘 할 일을 빠르게 확인합니다.");
        subtitle.setPadding(0, AppUi.dp(this, 4), 0, AppUi.dp(this, 10));
        root.addView(subtitle);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setWeightSum(3f);
        dayButton = UiSelection.button(this, "날짜");
        weekButton = UiSelection.button(this, "주간");
        todayButton = UiSelection.button(this, "오늘 할 일");
        dayButton.setOnClickListener(v -> {
            moveToDetailsAfterRender = true;
            showDay(selectedDate);
        });
        weekButton.setOnClickListener(v -> {
            moveToDetailsAfterRender = true;
            showWeek(selectedDate);
        });
        todayButton.setOnClickListener(v -> {
            moveToDetailsAfterRender = true;
            showTodayTasks();
        });
        addTab(tabs, dayButton, true, false);
        addTab(tabs, weekButton, false, false);
        addTab(tabs, todayButton, false, true);
        root.addView(tabs, new LinearLayout.LayoutParams(-1, AppUi.dp(this, 48)));

        calendarView = new CalendarView(this);
        calendarView.setNestedScrollingEnabled(false);
        long calendarMillis = selectedDate.atStartOfDay(zoneId).toInstant().toEpochMilli();
        calendarView.setDate(calendarMillis, false, true);
        calendarView.setOnDateChangeListener((view, year, monthValue, dayOfMonth) -> {
            selectedDate = LocalDate.of(year, monthValue + 1, dayOfMonth);
            highlightId = -1L;
            moveToDetailsAfterRender = true;
            showDay(selectedDate);
        });
        int calendarHeight = CalendarDetailLayoutPolicy.calendarHeightDp(AppUi.isTablet(this));
        LinearLayout.LayoutParams calendarParams = new LinearLayout.LayoutParams(-1, AppUi.dp(this, calendarHeight));
        calendarParams.setMargins(0, AppUi.dp(this, 8), 0, 0);
        root.addView(calendarView, calendarParams);

        LinearLayout rangeRow = new LinearLayout(this);
        rangeRow.setGravity(Gravity.CENTER_VERTICAL);
        detailAnchor = rangeRow;
        rangeTitle = AppUi.text(this, "", 17, AppUi.TEXT, true);
        rangeTitle.setGravity(Gravity.CENTER_VERTICAL);
        countText = AppUi.text(this, "", 13, AppUi.SUBTEXT, false);
        countText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rangeParams = new LinearLayout.LayoutParams(0, AppUi.dp(this, 52), 1f);
        rangeParams.setMargins(0, AppUi.dp(this, 8), 0, 0);
        rangeRow.addView(rangeTitle, rangeParams);
        rangeRow.addView(countText, new LinearLayout.LayoutParams(-2, AppUi.dp(this, 52)));
        root.addView(rangeRow);

        itemContainer = new LinearLayout(this);
        itemContainer.setOrientation(LinearLayout.VERTICAL);
        itemContainer.setPadding(0, 0, 0, AppUi.dp(this, 8));
        root.addView(itemContainer, new LinearLayout.LayoutParams(-1, -2));

        Button add = AppUi.primaryButton(this, "＋  새 항목 추가");
        add.setOnClickListener(v -> openHomeForAdd());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-1, AppUi.dp(this, 56));
        addParams.setMargins(0, AppUi.dp(this, 14), 0, 0);
        root.addView(add, addParams);

        ViewCompat.requestApplyInsets(screenScroll);
        return screenScroll;
    }

    private void addTab(LinearLayout parent, Button button, boolean first, boolean last) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, AppUi.dp(this, 44), 1f);
        params.setMargins(first ? 0 : AppUi.dp(this, 4), AppUi.dp(this, 2),
                last ? 0 : AppUi.dp(this, 4), AppUi.dp(this, 2));
        parent.addView(button, params);
    }

    private void updateTabs() {
        UiSelection.apply(this, dayButton, MODE_DAY.equals(currentMode));
        UiSelection.apply(this, weekButton, MODE_WEEK.equals(currentMode));
        UiSelection.apply(this, todayButton, MODE_TODAY.equals(currentMode));
    }

    private void showDay(LocalDate date) {
        if (calendarView == null) return;
        currentMode = MODE_DAY;
        updateTabs();
        calendarView.setVisibility(View.VISIBLE);
        selectedDate = date;
        long selectedMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli();
        if (Math.abs(calendarView.getDate() - selectedMillis) >= 86_400_000L) {
            calendarView.setDate(selectedMillis, false, true);
        }
        rangeTitle.setText(date.format(dateFormat));
        long from = date.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long to = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();
        repository.getBetween(from, to, items -> runOnUiThread(() -> renderItems(items, false)));
    }

    private void showWeek(LocalDate date) {
        currentMode = MODE_WEEK;
        updateTabs();
        calendarView.setVisibility(View.GONE);
        LocalDate monday = date.with(DayOfWeek.MONDAY);
        LocalDate nextMonday = monday.plusWeeks(1);
        rangeTitle.setText(monday.format(dateFormat) + " ~ " + nextMonday.minusDays(1).format(dateFormat));
        long from = monday.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long to = nextMonday.atStartOfDay(zoneId).toInstant().toEpochMilli();
        repository.getBetween(from, to, items -> runOnUiThread(() -> renderItems(items, false)));
    }

    private void showTodayTasks() {
        currentMode = MODE_TODAY;
        updateTabs();
        calendarView.setVisibility(View.GONE);
        LocalDate today = LocalDate.now();
        rangeTitle.setText(today.format(dateFormat));
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
        highlightedCard = null;
        int count = items == null ? 0 : items.size();
        countText.setText(count + "개");
        if (count == 0) {
            String title = taskMode ? "오늘 할 일이 없습니다" : "등록된 일정이 없습니다";
            String description = taskMode
                    ? "새 할 일을 추가하면 오늘 목록에서 바로 확인할 수 있습니다."
                    : "아래 버튼으로 일정·할 일·메모를 추가해 보세요.";
            itemContainer.addView(AppUi.emptyState(this, title, description), AppUi.cardParams(this));
        } else {
            for (WorkItemEntity item : items) itemContainer.addView(buildCard(item));
        }
        moveToDetailSectionIfNeeded();
    }

    private View buildCard(WorkItemEntity item) {
        LinearLayout card = AppUi.card(this);
        card.setLayoutParams(AppUi.cardParams(this));
        if (item.id == highlightId) {
            card.setBackground(AppUi.round(this, AppUi.SURFACE, 18, AppUi.PRIMARY));
            card.setContentDescription("방금 저장한 항목 " + item.title);
            highlightedCard = card;
        }

        if (WorkItemEntity.TYPE_TASK.equals(item.type)) {
            CheckBox completed = new CheckBox(this);
            completed.setText(item.title);
            completed.setTextSize(17);
            completed.setTextColor(AppUi.TEXT);
            completed.setTypeface(null, Typeface.BOLD);
            completed.setChecked(item.completed);
            completed.setOnCheckedChangeListener((buttonView, checked) ->
                    repository.setCompleted(item.id, checked, ignored -> runOnUiThread(() -> item.completed = checked)));
            card.addView(completed);
        } else {
            TextView title = AppUi.text(this, typeLabel(item.type) + " · " + item.title, 17, AppUi.TEXT, true);
            card.addView(title);
        }

        String timing = item.startAt == null ? "시간 미지정" : formatDateTime(item.startAt);
        if (item.endAt != null) timing += " ~ " + formatDateTime(item.endAt);
        TextView detail = AppUi.body(this, timing + repeatText(item.repeatRule));
        detail.setPadding(0, AppUi.dp(this, 6), 0, 0);
        card.addView(detail);
        return card;
    }

    private void moveToDetailSectionIfNeeded() {
        if (!moveToDetailsAfterRender || screenScroll == null || detailAnchor == null) return;
        moveToDetailsAfterRender = false;
        screenScroll.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            View target = highlightedCard == null ? detailAnchor : highlightedCard;
            int top = topInsideScroll(target);
            UiPreferences preferences = UiPreferences.load(this);
            int offsetDp = CalendarDetailLayoutPolicy.detailTopOffsetDp(preferences.textScalePercent);
            screenScroll.smoothScrollTo(0, Math.max(0, top - AppUi.dp(this, offsetDp)));
        }, 90L);
    }

    private int topInsideScroll(View target) {
        int top = 0;
        View current = target;
        while (current != null && current != screenScroll) {
            top += current.getTop();
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return top;
    }

    private void openHomeForAdd() {
        Intent intent = new Intent(this, AdaptiveMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private String formatDateTime(long millis) {
        return Instant.ofEpochMilli(millis).atZone(zoneId)
                .format(DateTimeFormatter.ofPattern("MM.dd (E) HH:mm", Locale.KOREA));
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
}
