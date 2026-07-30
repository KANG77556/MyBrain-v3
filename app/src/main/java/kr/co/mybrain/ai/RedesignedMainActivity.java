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

/** 목업의 메뉴 구조와 밝은·어두운 테마를 실제로 구현한 메인 화면입니다. */
public final class RedesignedMainActivity extends Activity {
    private static final String HOME = "홈";
    private static final String CALENDAR = "캘린더";
    private static final String SETTINGS = "설정";
    private static final String RECORDS = "기록";

    private static final int BLUE = Color.rgb(47, 116, 245);
    private static final int GREEN = Color.rgb(34, 197, 94);
    private static final int ORANGE = Color.rgb(245, 158, 11);
    private static final int RED = Color.rgb(239, 68, 68);
    private static final int PURPLE = Color.rgb(99, 102, 241);

    private final List<WorkItemRecord> items = new ArrayList<>();
    private final List<TextView> navTabs = new ArrayList<>();
    private FrameLayout host;
    private TextView headerTitle;
    private String page = HOME;
    private String selectedDate = LocalDate.now().toString();
    private String recordFilter = "전체";
    private String recordQuery = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeController.applyWindow(this);
        reload();
        buildShell();
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        reload();
        if (host != null) render();
    }

    @Override public void onBackPressed() {
        if (RECORDS.equals(page)) {
            page = HOME;
            render();
        } else {
            super.onBackPressed();
        }
    }

    private void reload() {
        items.clear();
        items.addAll(WorkItemStore.load(this));
    }

    private void buildShell() {
        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(bg());

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(bg());

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(6), dp(10), dp(6));
        header.setBackgroundColor(bg());
        headerTitle = label("MyBrain AI", 25, text(), true);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0, dp(58), 1f));
        Button menu = button("⋮", card(), BLUE, 24, 16);
        menu.setContentDescription("설정 열기");
        menu.setOnClickListener(v -> { page = SETTINGS; render(); });
        header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        shell.addView(header);

        host = new FrameLayout(this);
        shell.addView(host, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(5), dp(8), dp(5));
        nav.setBackground(round(card(), 24, border(), 1));
        addNav(nav, "⌂", HOME);
        addNav(nav, "▦", CALENDAR);
        addNav(nav, "⚙", SETTINGS);
        shell.addView(nav, new LinearLayout.LayoutParams(-1, dp(70)));
        outer.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        Button add = button("＋", BLUE, Color.WHITE, 31, 40);
        add.setContentDescription("새 기록 작성");
        add.setElevation(dp(8));
        add.setOnClickListener(v -> openInput("메모", ""));
        FrameLayout.LayoutParams plus = new FrameLayout.LayoutParams(dp(64), dp(64));
        plus.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        plus.setMargins(0, 0, 0, dp(49));
        outer.addView(add, plus);

        setContentView(outer);
        ThemeController.applyTree(this, outer);
    }

    private void addNav(LinearLayout nav, String icon, String name) {
        TextView tab = label(icon + "\n" + name, 12, muted(), true);
        tab.setTag(name);
        tab.setGravity(Gravity.CENTER);
        tab.setContentDescription(name + " 화면");
        tab.setOnClickListener(v -> { page = String.valueOf(v.getTag()); render(); });
        navTabs.add(tab);
        nav.addView(tab, new LinearLayout.LayoutParams(0, dp(58), 1f));
    }

    private void render() {
        if (host == null) return;
        host.removeAllViews();
        headerTitle.setText(HOME.equals(page) ? "MyBrain AI" : page);
        View content;
        if (CALENDAR.equals(page)) content = calendarPage();
        else if (SETTINGS.equals(page)) content = settingsPage();
        else if (RECORDS.equals(page)) content = recordsPage();
        else content = homePage();
        host.addView(content, new FrameLayout.LayoutParams(-1, -1));
        updateNav();
        ThemeController.applyTree(this, findViewById(android.R.id.content));
    }

    /** 오늘 요약 → 검색 → 빠른 작업 → 기록 분류 순서입니다. */
    private View homePage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = column(dp(16), dp(4), dp(16), dp(100));
        scroll.addView(body);

        EditText search = new EditText(this);
        search.setHint("기록 검색 (메모, 일정, 할 일, D-Day, 제출)");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(round(card(), 16, border(), 1));
        search.setOnEditorActionListener((v, actionId, event) -> {
            showRecords("전체", search.getText().toString());
            return true;
        });
        body.addView(search, heightMargin(54, 14));

        body.addView(section("오늘 요약", "더보기 ›", v -> showRecords("전체", "")));
        LinearLayout summary = column(dp(8), dp(9), dp(8), dp(9));
        summary.setBackground(round(card(), 18, border(), 1));
        LinearLayout metricRow = new LinearLayout(this);
        metricRow.setOrientation(LinearLayout.HORIZONTAL);
        metricRow.addView(metric("전체", items.size(), BLUE), weight(66));
        metricRow.addView(metric("지연", overdue(), ORANGE), weight(66));
        metricRow.addView(metric("미완료", incomplete(), GREEN), weight(66));
        metricRow.addView(metric("중요", important(), RED), weight(66));
        summary.addView(metricRow);
        body.addView(summary, matchMargin(16));

        body.addView(section("빠른 작업", "", null));
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.addView(tile("▤", "메모 작성", v -> openInput("메모", "")), weight(82));
        quick.addView(tile("☑", "할 일 추가", v -> openInput("할 일", "")), weight(82));
        quick.addView(tile("▦", "일정 추가", v -> openInput("일정", "")), weight(82));
        quick.addView(tile("⚑", "D-Day 추가", v -> openInput("할 일", "D-Day: ")), weight(82));
        body.addView(quick, matchMargin(18));

        body.addView(section("기록", "", null));
        body.addView(tileRow(
                tile("☷", "전체 기록", v -> showRecords("전체", "")),
                tile("▤", "메모", v -> showRecords("메모", "")),
                tile("☑", "할 일", v -> showRecords("할 일", ""))
        ), matchMargin(10));
        body.addView(tileRow(
                tile("▦", "일정(캘린더)", v -> { page = CALENDAR; render(); }),
                tile("⚑", "D-Day", v -> showRecords("D-Day", "")),
                tile("▧", "제출", v -> showRecords("제출", ""))
        ), matchMargin(10));
        return scroll;
    }

    private LinearLayout tileRow(View a, View b, View c) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(a, weight(92));
        row.addView(b, weight(92));
        row.addView(c, weight(92));
        return row;
    }

    private void showRecords(String filter, String query) {
        recordFilter = filter;
        recordQuery = query == null ? "" : query;
        page = RECORDS;
        render();
    }

    private View recordsPage() {
        LinearLayout root = column(dp(16), dp(4), dp(16), 0);
        TextView back = label("‹  " + recordFilter, 18, text(), true);
        back.setPadding(dp(2), dp(7), 0, dp(10));
        back.setOnClickListener(v -> { page = HOME; render(); });
        root.addView(back);

        EditText search = new EditText(this);
        search.setHint(recordFilter + " 검색");
        search.setText(recordQuery);
        search.setSingleLine(true);
        search.setPadding(dp(15), 0, dp(15), 0);
        search.setBackground(round(card(), 16, border(), 1));
        root.addView(search, heightMargin(52, 10));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = column(0, 0, 0, dp(100));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        Runnable refresh = () -> fillList(list, recordFilter, search.getText().toString());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { recordQuery = s.toString(); refresh.run(); }
        });
        refresh.run();
        return root;
    }

    private void fillList(LinearLayout list, String filter, String queryValue) {
        list.removeAllViews();
        String query = safe(queryValue).trim().toLowerCase(Locale.KOREA);
        int shown = 0;
        for (WorkItemRecord item : items) {
            if (!matches(item, filter)) continue;
            String text = (safe(item.title) + " " + safe(item.original)).toLowerCase(Locale.KOREA);
            if (!query.isEmpty() && !text.contains(query)) continue;
            list.addView(recordCard(item), matchMargin(8));
            shown++;
        }
        if (shown == 0) list.addView(empty("조건에 맞는 기록이 없습니다."));
    }

    private boolean matches(WorkItemRecord item, String filter) {
        if ("전체".equals(filter)) return true;
        String hay = (safe(item.title) + " " + safe(item.original)).toLowerCase(Locale.KOREA);
        if ("D-Day".equals(filter)) return hay.contains("d-day") || hay.contains("디데이");
        if ("제출".equals(filter)) return hay.contains("제출");
        return filter.equals(item.type);
    }

    private View recordCard(WorkItemRecord item) {
        LinearLayout box = column(dp(14), dp(12), dp(14), dp(12));
        box.setBackground(round(card(), 16, border(), 1));
        box.addView(label(safe(item.title).isEmpty() ? "제목 없음" : item.title, 16, text(), true));
        String meta = safe(item.type);
        if (!safe(item.date).isEmpty()) meta += " · " + item.date;
        if (!safe(item.time).isEmpty()) meta += " · " + item.time;
        if (item.completed) meta += " · 완료";
        TextView info = label(meta, 13, muted(), false);
        info.setPadding(0, dp(5), 0, 0);
        box.addView(info);
        box.setOnClickListener(v -> {
            int index = WorkItemStore.findBestIndex(items, item.title, item.date);
            Intent detail = new Intent(this, WorkItemDetailActivity.class);
            detail.putExtra(WorkItemDetailActivity.EXTRA_INDEX, index);
            startActivity(detail);
        });
        return box;
    }

    private View calendarPage() {
        LinearLayout root = column(dp(16), dp(4), dp(16), 0);
        EventCalendarView calendar = new EventCalendarView(this);
        calendar.setSelectedDate(selectedDate);
        calendar.setEventMarkerProvider(this::marker);
        calendar.setOnDateSelectedListener(value -> { selectedDate = value; render(); });
        root.addView(calendar, matchMargin(8));
        TextView date = label(koreanDate(parseDate(selectedDate)), 19, text(), true);
        date.setPadding(dp(2), dp(5), 0, dp(9));
        root.addView(date);

        ScrollView scroll = new ScrollView(this);
        LinearLayout agenda = column(0, 0, 0, dp(100));
        scroll.addView(agenda);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        int count = 0;
        LocalDate target = parseDate(selectedDate);
        for (WorkItemRecord item : items) {
            if (occurs(item, target)) {
                agenda.addView(recordCard(item), matchMargin(8));
                count++;
            }
        }
        if (count == 0) agenda.addView(empty("선택한 날짜의 일정이 없습니다."));
        return root;
    }

    private EventCalendarView.EventMarker marker(String date) {
        int count = 0;
        LocalDate target = parseDate(date);
        for (WorkItemRecord item : items) if (occurs(item, target)) count++;
        return new EventCalendarView.EventMarker(count, BLUE);
    }

    private View settingsPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = column(dp(16), dp(4), dp(16), dp(100));
        scroll.addView(body);
        body.addView(setting("화면 테마", themeLabel(), v -> showTheme()), matchMargin(9));
        body.addView(setting("AI 설정", "제공자·모델·연결 상태", v -> startActivity(new Intent(this, AiSettingsActivity.class))), matchMargin(9));
        body.addView(setting("데이터 및 동기화", "백업·복원", v -> startActivity(new Intent(this, BackupActivity.class))), matchMargin(9));
        body.addView(setting("기록 관리", "완료·삭제·일괄 관리", v -> startActivity(new Intent(this, WorkItemManagerActivity.class))), matchMargin(9));
        body.addView(setting("기기 진단", "권한·알람·배터리 확인", v -> startActivity(new Intent(this, DiagnosticsActivity.class))), matchMargin(9));
        body.addView(setting("앱 정보", "MyBrain AI 1.10.4", null), matchMargin(9));
        return scroll;
    }

    private View setting(String name, String detail, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(12), dp(12), dp(12));
        row.setBackground(round(card(), 16, border(), 1));
        LinearLayout words = column(0, 0, 0, 0);
        words.addView(label(name, 16, text(), true));
        TextView sub = label(detail, 13, muted(), false);
        sub.setPadding(0, dp(4), 0, 0);
        words.addView(sub);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(label("›", 25, muted(), false), new LinearLayout.LayoutParams(dp(32), -2));
        if (listener != null) row.setOnClickListener(listener);
        return row;
    }

    private void showTheme() {
        String[] modes = {"시스템 설정 따르기", "밝은 모드", "어두운 모드"};
        new AlertDialog.Builder(this)
                .setTitle("화면 테마")
                .setSingleChoiceItems(modes, ThemeController.selectedIndex(this), (dialog, which) -> {
                    ThemeController.setMode(this, ThemeController.modeForIndex(which));
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private String themeLabel() {
        int mode = ThemeController.selectedIndex(this);
        if (mode == 1) return "밝은 모드";
        if (mode == 2) return "어두운 모드";
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

    private View section(String title, String action, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label(title, 18, text(), true), new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView more = label(action, 13, BLUE, false);
        more.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (listener != null) more.setOnClickListener(listener);
        row.addView(more, new LinearLayout.LayoutParams(dp(82), dp(42)));
        return row;
    }

    private TextView metric(String name, int number, int color) {
        TextView view = label(number + "\n" + name, 13, color, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(soft(), 14, soft(), 0));
        return view;
    }

    private TextView tile(String icon, String name, View.OnClickListener listener) {
        TextView view = label(icon + "\n" + name, 13, text(), true);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(name);
        view.setBackground(round(card(), 15, border(), 1));
        view.setOnClickListener(listener);
        return view;
    }

    private TextView empty(String message) {
        TextView view = label(message, 15, muted(), false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(24), dp(12), dp(24));
        view.setBackground(round(card(), 16, border(), 1));
        return view;
    }

    private void updateNav() {
        for (TextView tab : navTabs) {
            boolean selected = page.equals(String.valueOf(tab.getTag()));
            tab.setTextColor(selected ? BLUE : muted());
            tab.setBackground(round(selected ? selectedCard() : card(), 17,
                    selected ? BLUE : card(), selected ? 1 : 0));
        }
    }

    private int overdue() {
        int count = 0;
        LocalDate today = LocalDate.now();
        for (WorkItemRecord item : items) {
            LocalDate date = parseDate(item.date);
            if (!item.completed && date != null && date.isBefore(today)) count++;
        }
        return count;
    }

    private int incomplete() {
        int count = 0;
        for (WorkItemRecord item : items) if (!item.completed && "할 일".equals(item.type)) count++;
        return count;
    }

    private int important() {
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

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String value, int fill, int foreground, int size, int radius) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(size);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setGravity(Gravity.CENTER);
        button.setBackground(round(fill, radius, fill, 0));
        return button;
    }

    private LinearLayout column(int left, int top, int right, int bottom) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(left, top, right, bottom);
        return layout;
    }

    private GradientDrawable round(int fill, int radius, int stroke, int width) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (width > 0) drawable.setStroke(dp(width), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams weight(int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(height), 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private LinearLayout.LayoutParams matchMargin(int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams heightMargin(int height, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(height));
        params.setMargins(0, 0, 0, dp(bottom));
        return params;
    }

    private String koreanDate(LocalDate date) {
        if (date == null) date = LocalDate.now();
        return date.getYear() + "년 " + date.getMonthValue() + "월 " + date.getDayOfMonth() + "일";
    }

    private LocalDate parseDate(String value) {
        try { return LocalDate.parse(safe(value), DateTimeFormatter.ISO_LOCAL_DATE); }
        catch (DateTimeParseException ignored) { return null; }
    }

    private String safe(String value) { return value == null ? "" : value; }
    private int bg() { return ThemeController.isDark(this) ? Color.rgb(10, 18, 32) : Color.rgb(248, 250, 253); }
    private int card() { return ThemeController.isDark(this) ? Color.rgb(20, 31, 49) : Color.WHITE; }
    private int soft() { return ThemeController.isDark(this) ? Color.rgb(25, 40, 64) : Color.rgb(246, 249, 255); }
    private int selectedCard() { return ThemeController.isDark(this) ? Color.rgb(25, 54, 99) : Color.rgb(235, 242, 255); }
    private int text() { return ThemeController.isDark(this) ? Color.rgb(239, 244, 252) : Color.rgb(24, 35, 52); }
    private int muted() { return ThemeController.isDark(this) ? Color.rgb(169, 184, 207) : Color.rgb(102, 116, 138); }
    private int border() { return ThemeController.isDark(this) ? Color.rgb(46, 66, 96) : Color.rgb(222, 229, 240); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
