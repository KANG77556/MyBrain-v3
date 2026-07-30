package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 사용자가 승인한 목업 구조를 실제 앱에 반영한 메인 화면입니다.
 * 홈은 오늘 요약 → 검색 → 빠른 작업 → 기록 분류 순서이며,
 * 하단에는 홈·캘린더·설정만 배치합니다.
 */
public final class RedesignedMainActivity extends Activity {

    private static final String PAGE_HOME = "홈";
    private static final String PAGE_CALENDAR = "캘린더";
    private static final String PAGE_SETTINGS = "설정";
    private static final String PAGE_RECORDS = "기록";

    private static final int BLUE = Color.rgb(47, 116, 245);
    private static final int GREEN = Color.rgb(34, 197, 94);
    private static final int ORANGE = Color.rgb(245, 158, 11);
    private static final int RED = Color.rgb(239, 68, 68);
    private static final int PURPLE = Color.rgb(99, 102, 241);

    private final List<WorkItemRecord> items = new ArrayList<>();
    private final List<TextView> bottomTabs = new ArrayList<>();

    private FrameLayout pageHost;
    private TextView titleView;
    private String page = PAGE_HOME;
    private String selectedDate = LocalDate.now().toString();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeController.applyWindow(this);
        loadItems();
        buildShell();
        renderPage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadItems();
        if (pageHost != null) renderPage();
    }

    @Override
    public void onBackPressed() {
        if (PAGE_RECORDS.equals(page)) {
            page = PAGE_HOME;
            renderPage();
            return;
        }
        super.onBackPressed();
    }

    private void loadItems() {
        items.clear();
        items.addAll(WorkItemStore.load(this));
    }

    /** 상단 제목, 본문, 하단 3개 메뉴와 중앙 추가 버튼을 구성합니다. */
    private void buildShell() {
        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(background());

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(background());

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(7), dp(10), dp(7));
        header.setBackgroundColor(background());

        titleView = text("MyBrain AI", 25, textColor(), true);
        header.addView(titleView, new LinearLayout.LayoutParams(0, dp(58), 1f));

        Button more = iconButton("⋮");
        more.setContentDescription("설정 열기");
        more.setOnClickListener(v -> {
            page = PAGE_SETTINGS;
            renderPage();
        });
        header.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        shell.addView(header);

        pageHost = new FrameLayout(this);
        shell.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(8), dp(5), dp(8), dp(5));
        bottom.setBackground(shape(card(), 24, border(), 1));
        addBottomTab(bottom, "⌂", PAGE_HOME);
        addBottomTab(bottom, "▦", PAGE_CALENDAR);
        addBottomTab(bottom, "⚙", PAGE_SETTINGS);
        shell.addView(bottom, new LinearLayout.LayoutParams(-1, dp(70)));

        outer.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        Button add = button("＋", BLUE, Color.WHITE, 31, 99);
        add.setContentDescription("새 기록 작성");
        add.setElevation(dp(9));
        add.setOnClickListener(v -> openInput("메모", ""));
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(dp(64), dp(64));
        addParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        addParams.setMargins(0, 0, 0, dp(49));
        outer.addView(add, addParams);

        setContentView(outer);
        ThemeController.applyTree(this, outer);
    }

    private void addBottomTab(LinearLayout parent, String icon, String label) {
        TextView tab = text(icon + "\n" + label, 12, muted(), true);
        tab.setTag(label);
        tab.setGravity(Gravity.CENTER);
        tab.setContentDescription(label + " 화면");
        tab.setOnClickListener(v -> {
            page = String.valueOf(v.getTag());
            renderPage();
        });
        bottomTabs.add(tab);
        parent.addView(tab, new LinearLayout.LayoutParams(0, dp(58), 1f));
    }

    private void renderPage() {
        if (pageHost == null) return;
        pageHost.removeAllViews();
        titleView.setText(PAGE_HOME.equals(page) ? "MyBrain AI" : page);

        View view;
        if (PAGE_CALENDAR.equals(page)) view = buildCalendarPage();
        else if (PAGE_SETTINGS.equals(page)) view = buildSettingsPage();
        else if (PAGE_RECORDS.equals(page)) view = buildRecordsPage("전체", "");
        else view = buildHomePage();

        pageHost.addView(view, new FrameLayout.LayoutParams(-1, -1));
        updateBottomTabs();
        ThemeController.applyTree(this, findViewById(android.R.id.content));
    }

    /** 목업과 같은 오늘 요약·검색·빠른 작업·기록 분류 홈입니다. */
    private View buildHomePage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = column(dp(16), dp(6), dp(16), dp(100));
        scroll.addView(body);

        EditText search = new EditText(this);
        search.setHint("기록 검색 (메모, 일정, 할 일, D-Day, 제출)");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(shape(card(), 16, border(), 1));
        search.setOnEditorActionListener((v, actionId, event) -> {
            openRecords("전체", search.getText().toString());
            return true;
        });
        body.addView(search, heightBottom(54, 14));

        body.addView(sectionHeader("오늘 요약", "더보기 ›", v -> openRecords("전체", "")));
        LinearLayout summaryCard = column(dp(10), dp(10), dp(10), dp(10));
        summaryCard.setBackground(shape(card(), 18, border(), 1));
        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(metric("전체", items.size(), BLUE), weighted(66));
        metrics.addView(metric("지연", overdueCount(), ORANGE), weighted(66));
        metrics.addView(metric("미완료", incompleteCount(), GREEN), weighted(66));
        metrics.addView(metric("중요", importantCount(), RED), weighted(66));
        summaryCard.addView(metrics);
        body.addView(summaryCard, matchBottom(16));

        body.addView(sectionHeader("빠른 작업", "", null));
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.addView(menuTile("▤", "메모 작성", BLUE, v -> openInput("메모", "")), weighted(82));
        quick.addView(menuTile("☑", "할 일 추가", PURPLE, v -> openInput("할 일", "")), weighted(82));
        quick.addView(menuTile("▦", "일정 추가", GREEN, v -> openInput("일정", "")), weighted(82));
        quick.addView(menuTile("⚑", "D-Day 추가", ORANGE, v -> openInput("할 일", "D-Day: ")), weighted(82));
        body.addView(quick, matchBottom(18));

        body.addView(sectionHeader("기록", "", null));
        body.addView(recordRow(
                menuTile("☷", "전체 기록", BLUE, v -> openRecords("전체", "")),
                menuTile("▤", "메모", PURPLE, v -> openRecords("메모", "")),
                menuTile("☑", "할 일", ORANGE, v -> openRecords("할 일", ""))
        ), matchBottom(10));
        body.addView(recordRow(
                menuTile("▦", "일정(캘린더)", GREEN, v -> { page = PAGE_CALENDAR; renderPage(); }),
                menuTile("⚑", "D-Day", RED, v -> openRecords("D-Day", "")),
                menuTile("▧", "제출", BLUE, v -> openRecords("제출", ""))
        ), matchBottom(10));

        return scroll;
    }

    private LinearLayout recordRow(View first, View second, View third) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(first, weighted(92));
        row.addView(second, weighted(92));
        row.addView(third, weighted(92));
        return row;
    }

    private View buildRecordsPage(String filter, String initialQuery) {
        LinearLayout root = column(dp(16), dp(8), dp(16), 0);

        TextView back = text("‹  전체 기록", 18, textColor(), true);
        back.setPadding(dp(2), dp(6), 0, dp(10));
        back.setOnClickListener(v -> { page = PAGE_HOME; renderPage(); });
        root.addView(back);

        EditText search = new EditText(this);
        search.setHint(filter + " 검색");
        search.setText(initialQuery == null ? "" : initialQuery);
        search.setSingleLine(true);
        search.setPadding(dp(15), 0, dp(15), 0);
        search.setBackground(shape(card(), 16, border(), 1));
        root.addView(search, heightBottom(52, 10));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = column(0, 0, 0, dp(100));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Runnable refresh = () -> fillRecords(list, filter, search.getText().toString());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { refresh.run(); }
        });
        refresh.run();
        return root;
    }

    private void openRecords(String filter, String query) {
        page = PAGE_RECORDS;
        pageHost.removeAllViews();
        titleView.setText("기록");
        pageHost.addView(buildRecordsPage(filter, query), new FrameLayout.LayoutParams(-1, -1));
        updateBottomTabs();
        ThemeController.applyTree(this, findViewById(android.R.id.content));
    }

    private void fillRecords(LinearLayout list, String filter, String queryValue) {
        list.removeAllViews();
        String query = safe(queryValue).trim().toLowerCase(Locale.KOREA);
        int shown = 0;
        for (WorkItemRecord item : items) {
            if (!matchesFilter(item, filter)) continue;
            String haystack = (safe(item.title) + " " + safe(item.original)).toLowerCase(Locale.KOREA);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            list.addView(recordCard(item), matchBottom(8));
            shown++;
        }
        if (shown == 0) list.addView(emptyCard("조건에 맞는 기록이 없습니다."));
    }

    private boolean matchesFilter(WorkItemRecord item, String filter) {
        if ("전체".equals(filter)) return true;
        if ("D-Day".equals(filter)) return containsLabel(item, "d-day") || containsLabel(item, "디데이");
        if ("제출".equals(filter)) return containsLabel(item, "제출");
        return filter.equals(item.type);
    }

    private boolean containsLabel(WorkItemRecord item, String token) {
        String value = (safe(item.title) + " " + safe(item.original)).toLowerCase(Locale.KOREA);
        return value.contains(token.toLowerCase(Locale.KOREA));
    }

    private View recordCard(WorkItemRecord item) {
        LinearLayout card = column(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(shape(card(), 16, border(), 1));
        TextView title = text(safe(item.title).isEmpty() ? "제목 없음" : item.title, 16, textColor(), true);
        title.setMaxLines(2);
        card.addView(title);
        String info = item.type;
        if (!safe(item.date).isEmpty()) info += " · " + item.date;
        if (!safe(item.time).isEmpty()) info += " · " + item.time;
        if (item.completed) info += " · 완료";
        TextView meta = text(info, 13, muted(), false);
        meta.setPadding(0, dp(5), 0, 0);
        card.addView(meta);
        card.setOnClickListener(v -> {
            int index = WorkItemStore.findBestIndex(items, item.title, item.date);
            Intent intent = new Intent(this, WorkItemDetailActivity.class);
            intent.putExtra(WorkItemDetailActivity.EXTRA_INDEX, index);
            startActivity(intent);
        });
        return card;
    }

    /** 하단 캘린더 메뉴입니다. */
    private View buildCalendarPage() {
        LinearLayout root = column(dp(16), dp(8), dp(16), 0);
        EventCalendarView calendar = new EventCalendarView(this);
        calendar.setSelectedDate(selectedDate);
        calendar.setEventMarkerProvider(this::markerForDate);
        calendar.setOnDateSelectedListener(value -> {
            selectedDate = value;
            renderPage();
        });
        root.addView(calendar, matchBottom(8));

        TextView selected = text(koreanDate(parseDate(selectedDate)), 19, textColor(), true);
        selected.setPadding(dp(2), dp(6), 0, dp(9));
        root.addView(selected);

        ScrollView scroll = new ScrollView(this);
        LinearLayout agenda = column(0, 0, 0, dp(100));
        scroll.addView(agenda);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        int count = 0;
        LocalDate date = parseDate(selectedDate);
        for (WorkItemRecord item : items) {
            if (occurs(item, date)) {
                agenda.addView(recordCard(item), matchBottom(8));
                count++;
            }
        }
        if (count == 0) agenda.addView(emptyCard("선택한 날짜의 일정이 없습니다."));
        return root;
    }

    private EventCalendarView.EventMarker markerForDate(String value) {
        LocalDate date = parseDate(value);
        int count = 0;
        for (WorkItemRecord item : items) if (occurs(item, date)) count++;
        return new EventCalendarView.EventMarker(count, BLUE);
    }

    /** 설정에는 테마 선택과 기존 관리 기능을 모읍니다. */
    private View buildSettingsPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = column(dp(16), dp(8), dp(16), dp(100));
        scroll.addView(body);

        body.addView(settingsRow("화면 테마", currentThemeLabel(), v -> showThemeDialog()), matchBottom(9));
        body.addView(settingsRow("AI 설정", "제공자·모델·연결 상태", v -> startActivity(new Intent(this, AiSettingsActivity.class))), matchBottom(9));
        body.addView(settingsRow("데이터 및 동기화", "백업·복원", v -> startActivity(new Intent(this, BackupActivity.class))), matchBottom(9));
        body.addView(settingsRow("기록 관리", "완료·삭제·일괄 관리", v -> startActivity(new Intent(this, WorkItemManagerActivity.class))), matchBottom(9));
        body.addView(settingsRow("기기 진단", "권한·알람·배터리 확인", v -> startActivity(new Intent(this, DiagnosticsActivity.class))), matchBottom(9));
        body.addView(settingsRow("앱 정보", "MyBrain AI 1.10.4", null), matchBottom(9));
        return scroll;
    }

    private View settingsRow(String label, String detail, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(12), dp(12), dp(12));
        row.setBackground(shape(card(), 16, border(), 1));
        LinearLayout textArea = column(0, 0, 0, 0);
        textArea.addView(text(label, 16, textColor(), true));
        TextView sub = text(detail, 13, muted(), false);
        sub.setPadding(0, dp(4), 0, 0);
        textArea.addView(sub);
        row.addView(textArea, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = text("›", 25, muted(), false);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(32), -2));
        if (listener != null) row.setOnClickListener(listener);
        return row;
    }

    private void showThemeDialog() {
        String[] options = {"시스템 설정 따르기", "밝은 모드", "어두운 모드"};
        new AlertDialog.Builder(this)
                .setTitle("화면 테마")
                .setSingleChoiceItems(options, ThemeController.selectedIndex(this), (dialog, which) -> {
                    ThemeController.setMode(this, ThemeController.modeForIndex(which));
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private String currentThemeLabel() {
        int index = ThemeController.selectedIndex(this);
        if (index == 1) return "밝은 모드";
        if (index == 2) return "어두운 모드";
        return "시스템 설정 따르기";
    }

    private void openInput(String type, String prefill) {
        Intent intent = new Intent(this, UnifiedQuickInputActivityV4.class);
        intent.putExtra(UnifiedQuickInputActivity.EXTRA_DEFAULT_TYPE, type);
        if (!safe(prefill).isEmpty()) {
            intent.putExtra(UnifiedQuickInputActivityV2.EXTRA_PREFILL_TEXT, prefill);
            intent.putExtra(UnifiedQuickInputActivityV2.EXTRA_PREFILL_SOURCE, "빠른 작업");
        }
        startActivity(intent);
    }

    private TextView sectionHeader(String label, String action, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(label, 18, textColor(), true);
        row.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView more = text(action, 13, BLUE, false);
        more.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        if (listener != null) more.setOnClickListener(listener);
        row.addView(more, new LinearLayout.LayoutParams(dp(80), dp(42)));
        return row;
    }

    private TextView metric(String label, int value, int color) {
        TextView view = text(value + "\n" + label, 13, color, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(shape(softCard(), 14, softCard(), 0));
        return view;
    }

    private TextView menuTile(String icon, String label, int accent, View.OnClickListener listener) {
        TextView view = text(icon + "\n" + label, 13, textColor(), true);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(label);
        view.setBackground(shape(card(), 15, border(), 1));
        view.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(accent));
        view.setOnClickListener(listener);
        return view;
    }

    private TextView emptyCard(String value) {
        TextView view = text(value, 15, muted(), false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(25), dp(12), dp(25));
        view.setBackground(shape(card(), 16, border(), 1));
        return view;
    }

    private Button iconButton(String value) {
        return button(value, card(), BLUE, 24, 15);
    }

    private Button button(String label, int fill, int color, int size, int radius) {
        Button value = new Button(this);
        value.setText(label);
        value.setTextSize(size);
        value.setTextColor(color);
        value.setAllCaps(false);
        value.setMinimumWidth(0);
        value.setMinimumHeight(0);
        value.setGravity(Gravity.CENTER);
        value.setBackground(shape(fill, radius, fill, 0));
        return value;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout column(int left, int top, int right, int bottom) {
        LinearLayout value = new LinearLayout(this);
        value.setOrientation(LinearLayout.VERTICAL);
        value.setPadding(left, top, right, bottom);
        return value;
    }

    private GradientDrawable shape(int fill, int radius, int stroke, int width) {
        GradientDrawable value = new GradientDrawable();
        value.setColor(fill);
        value.setCornerRadius(dp(radius));
        if (width > 0) value.setStroke(dp(width), stroke);
        return value;
    }

    private LinearLayout.LayoutParams weighted(int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(height), 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private LinearLayout.LayoutParams matchBottom(int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams heightBottom(int height, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(height));
        params.setMargins(0, 0, 0, dp(bottom));
        return params;
    }

    private void updateBottomTabs() {
        for (TextView tab : bottomTabs) {
            boolean selected = page.equals(String.valueOf(tab.getTag()));
            tab.setTextColor(selected ? BLUE : muted());
            tab.setBackground(shape(selected ? selectedCard() : card(), 17,
                    selected ? BLUE : card(), selected ? 1 : 0));
        }
    }

    private int overdueCount() {
        LocalDate today = LocalDate.now();
        int count = 0;
        for (WorkItemRecord item : items) {
            LocalDate date = parseDate(item.date);
            if (!item.completed && date != null && date.isBefore(today)) count++;
        }
        return count;
    }

    private int incompleteCount() {
        int count = 0;
        for (WorkItemRecord item : items) if (!item.completed && "할 일".equals(item.type)) count++;
        return count;
    }

    private int importantCount() {
        int count = 0;
        for (WorkItemRecord item : items) {
            String value = (safe(item.title) + " " + safe(item.original)).toLowerCase(Locale.KOREA);
            if (value.contains("중요") || "RED".equals(item.colorValue) || "ORANGE".equals(item.colorValue)) count++;
        }
        return count;
    }

    private boolean occurs(WorkItemRecord item, LocalDate target) {
        LocalDate start = parseDate(item.date);
        if (start == null || target == null || target.isBefore(start)) return false;
        LocalDate end = parseDate(item.repeatEndDate);
        if (end != null && target.isAfter(end)) return false;
        if (target.equals(start)) return true;
        switch (safe(item.repeatType)) {
            case "DAILY": return true;
            case "WEEKLY": return start.getDayOfWeek() == target.getDayOfWeek();
            case "MONTHLY": return start.getDayOfMonth() == target.getDayOfMonth();
            case "WEEKDAYS":
                DayOfWeek day = target.getDayOfWeek();
                return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
            default: return false;
        }
    }

    private String koreanDate(LocalDate date) {
        if (date == null) date = LocalDate.now();
        return date.getYear() + "년 " + date.getMonthValue() + "월 " + date.getDayOfMonth() + "일";
    }

    private LocalDate parseDate(String value) {
        try { return LocalDate.parse(safe(value), DateTimeFormatter.ISO_LOCAL_DATE); }
        catch (DateTimeParseException ignored) { return null; }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int background() { return ThemeController.isDark(this) ? Color.rgb(10, 18, 32) : Color.rgb(248, 250, 253); }
    private int card() { return ThemeController.isDark(this) ? Color.rgb(20, 31, 49) : Color.WHITE; }
    private int softCard() { return ThemeController.isDark(this) ? Color.rgb(25, 40, 64) : Color.rgb(246, 249, 255); }
    private int selectedCard() { return ThemeController.isDark(this) ? Color.rgb(25, 54, 99) : Color.rgb(235, 242, 255); }
    private int textColor() { return ThemeController.isDark(this) ? Color.rgb(239, 244, 252) : Color.rgb(24, 35, 52); }
    private int muted() { return ThemeController.isDark(this) ? Color.rgb(169, 184, 207) : Color.rgb(102, 116, 138); }
    private int border() { return ThemeController.isDark(this) ? Color.rgb(46, 66, 96) : Color.rgb(222, 229, 240); }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
