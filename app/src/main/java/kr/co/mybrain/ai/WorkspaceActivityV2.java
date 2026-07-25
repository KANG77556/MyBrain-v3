package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MyBrain AI 1.8.3 메인 작업 화면입니다.
 * 달력 화면을 중심으로 간격, 버튼, 카드와 하단 메뉴를 재설계했습니다.
 * 기존 SharedPreferences 저장 형식을 그대로 사용하여 이전 자료와 호환합니다.
 */
public class WorkspaceActivityV2 extends Activity {
    private static final String PREFS = "mybrain_data";
    private static final String KEY_ITEMS = "items";
    private static final String UI_PREFS = "mybrain_ui_settings";
    private static final String KEY_CALENDAR_MODE = "calendar_view_mode";

    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int PRIMARY_LIGHT = Color.rgb(232, 240, 255);
    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BORDER = Color.rgb(222, 229, 240);
    private static final int BACKGROUND = Color.rgb(247, 249, 253);
    private static final int TASK = Color.rgb(234, 120, 35);
    private static final int MEMO = Color.rgb(123, 86, 188);

    private static final String[] MENUS = {"홈", "할 일", "일정", "메모", "달력"};
    private static final String[] MENU_ICONS = {"⌂", "☑", "▣", "▤", "▦"};
    private static final String[] TYPES = {"메모", "할 일", "일정"};
    private static final String[] REMINDER_LABELS = {
            "알림 없음", "정각", "5분 전", "10분 전", "30분 전", "1시간 전"
    };
    private static final int[] REMINDER_VALUES = {-1, 0, 5, 10, 30, 60};
    private static final String[] REPEAT_LABELS = {"반복 없음", "매일", "매주", "매월", "평일"};
    private static final String[] REPEAT_VALUES = {"NONE", "DAILY", "WEEKLY", "MONTHLY", "WEEKDAYS"};

    private final List<Item> items = new ArrayList<>();
    private final List<TextView> menuViews = new ArrayList<>();
    private FrameLayout content;
    private LinearLayout bottomBar;
    private TextView headerTitle;
    private Button settingsButton;
    private String menu = "홈";
    private String selectedDate = formatDate(new Date());
    private String calendarMode = "WEEK";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        calendarMode = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                .getString(KEY_CALENDAR_MODE, "WEEK");
        loadItems();
        buildShell();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content == null) return;
        loadItems();
        render();
    }

    /** 앱 전체 골격: 상단 제목, 콘텐츠, 하단 5개 메뉴를 구성합니다. */
    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(8), dp(14), dp(6));
        header.setBackgroundColor(Color.WHITE);

        headerTitle = text("MyBrain AI", 25, TEXT);
        headerTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0, dp(58), 1f));

        settingsButton = button("⚙", Color.WHITE, PRIMARY, 16, 18);
        settingsButton.setContentDescription("설정 및 관리");
        settingsButton.setOnClickListener(v -> showManagementMenu());
        header.addView(settingsButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(dp(4), dp(5), dp(4), dp(4));
        bottomBar.setBackground(shape(Color.WHITE, 24, BORDER, 1));

        for (int i = 0; i < MENUS.length; i++) {
            final String value = MENUS[i];
            TextView tab = text(MENU_ICONS[i] + "\n" + value, 12, MUTED);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            tab.setTag(value);
            tab.setPadding(0, dp(4), 0, dp(2));
            tab.setOnClickListener(v -> {
                menu = String.valueOf(v.getTag());
                render();
            });
            menuViews.add(tab);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(72), 1f);
            params.setMargins(dp(2), 0, dp(2), 0);
            bottomBar.addView(tab, params);
        }
        root.addView(bottomBar);
        setContentView(root);
    }

    /** 선택한 메뉴에 맞는 화면을 다시 그립니다. */
    private void render() {
        content.removeAllViews();
        headerTitle.setText("홈".equals(menu) ? "MyBrain AI" : menu);
        View page;
        if ("홈".equals(menu)) page = buildHome();
        else if ("달력".equals(menu)) page = buildCalendar();
        else page = buildList(menu);
        content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        addFab();
        updateBottomMenu();
    }

    private View buildHome() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = page();
        scroll.addView(page);

        TextView today = text(displayDate(formatDate(new Date())), 15, MUTED);
        today.setPadding(0, 0, 0, dp(10));
        page.addView(today);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(metric("오늘 일정", countOnDate("일정", formatDate(new Date()))), weight(72));
        metrics.addView(metric("남은 할 일", countOpenTasks()), weight(72));
        metrics.addView(metric("메모", countType("메모")), weight(72));
        page.addView(metrics);

        page.addView(section("오늘 일정"));
        addPreview(page, itemsOnDate("일정", formatDate(new Date())), 3, "오늘 일정이 없습니다.");
        page.addView(section("우선 처리할 일"));
        addPreview(page, openTasks(), 3, "남은 할 일이 없습니다.");
        page.addView(section("최근 메모"));
        addPreview(page, itemsOfType("메모"), 3, "저장된 메모가 없습니다.");
        page.addView(spacer(96));
        return scroll;
    }

    private View buildList(String type) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(10), dp(16), 0);

        EditText search = new EditText(this);
        search.setHint(type + " 검색");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(shape(Color.WHITE, 16, BORDER, 1));
        page.addView(search, new LinearLayout.LayoutParams(-1, dp(50)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(12), 0, dp(96));
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Runnable refresh = () -> populateList(type, search.getText().toString(), list);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { refresh.run(); }
        });
        refresh.run();
        return page;
    }

    private void populateList(String type, String queryValue, LinearLayout list) {
        list.removeAllViews();
        String query = queryValue == null ? "" : queryValue.trim().toLowerCase(Locale.KOREA);
        int shown = 0;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (!type.equals(item.type)) continue;
            if (!query.isEmpty() && !item.title.toLowerCase(Locale.KOREA).contains(query)
                    && !item.original.toLowerCase(Locale.KOREA).contains(query)) continue;
            list.addView(itemCard(item, i, true), cardParams());
            shown++;
        }
        if (shown == 0) list.addView(empty("조건에 맞는 " + type + "이 없습니다."));
    }

    /** 월간·주간·일간 보기와 선택 날짜의 업무 목록을 표시합니다. */
    private View buildCalendar() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(10), dp(18), 0);

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setPadding(dp(4), dp(4), dp(4), dp(4));
        modes.setBackground(shape(Color.WHITE, 28, BORDER, 1));
        addMode(modes, "월간", "MONTH");
        addMode(modes, "주간", "WEEK");
        addMode(modes, "일간", "DAY");
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(-1, dp(58));
        modeParams.setMargins(0, 0, 0, dp(12));
        page.addView(modes, modeParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(96));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        if ("MONTH".equals(calendarMode)) {
            body.addView(monthNavigator());
            EventCalendarView calendar = new EventCalendarView(this);
            calendar.setSelectedDate(selectedDate);
            calendar.setEventMarkerProvider(this::marker);
            calendar.setOnDateSelectedListener(value -> {
                selectedDate = value;
                render();
            });
            LinearLayout.LayoutParams calendarParams = new LinearLayout.LayoutParams(-1, -2);
            calendarParams.setMargins(0, dp(4), 0, dp(6));
            body.addView(calendar, calendarParams);
        } else {
            body.addView(dateNavigator());
            if ("WEEK".equals(calendarMode)) addWeekChips(body);
        }

        TextView selectedTitle = text(displayDate(selectedDate), 21, TEXT);
        selectedTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        selectedTitle.setPadding(0, dp(18), 0, dp(10));
        body.addView(selectedTitle);
        addAgenda(body, selectedDate);
        return page;
    }

    private void addMode(LinearLayout row, String label, String mode) {
        boolean selected = mode.equals(calendarMode);
        Button tab = button(label, selected ? PRIMARY : Color.TRANSPARENT,
                selected ? Color.WHITE : TEXT, 24, 16);
        tab.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        tab.setOnClickListener(v -> {
            calendarMode = mode;
            getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_CALENDAR_MODE, mode).apply();
            render();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        row.addView(tab, params);
    }

    private View monthNavigator() {
        LinearLayout row = navigationRow();
        Button previous = navButton("‹");
        previous.setOnClickListener(v -> moveDate(-1, Calendar.MONTH));
        row.addView(previous, new LinearLayout.LayoutParams(dp(52), dp(48)));
        TextView label = text(monthLabel(selectedDate), 18, TEXT);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button today = todayButton();
        row.addView(today, new LinearLayout.LayoutParams(dp(66), dp(44)));
        Button next = navButton("›");
        next.setOnClickListener(v -> moveDate(1, Calendar.MONTH));
        row.addView(next, new LinearLayout.LayoutParams(dp(52), dp(48)));
        return row;
    }

    private View dateNavigator() {
        LinearLayout row = navigationRow();
        Button previous = navButton("‹");
        previous.setOnClickListener(v -> moveDate(-1,
                "WEEK".equals(calendarMode) ? Calendar.WEEK_OF_YEAR : Calendar.DAY_OF_MONTH));
        row.addView(previous, new LinearLayout.LayoutParams(dp(52), dp(48)));

        TextView label = text("WEEK".equals(calendarMode)
                ? weekLabel(selectedDate) : displayDate(selectedDate), 18, TEXT);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button today = todayButton();
        row.addView(today, new LinearLayout.LayoutParams(dp(66), dp(44)));
        Button next = navButton("›");
        next.setOnClickListener(v -> moveDate(1,
                "WEEK".equals(calendarMode) ? Calendar.WEEK_OF_YEAR : Calendar.DAY_OF_MONTH));
        row.addView(next, new LinearLayout.LayoutParams(dp(52), dp(48)));
        return row;
    }

    private LinearLayout navigationRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(8));
        return row;
    }

    private Button navButton(String label) {
        Button value = button(label, Color.WHITE, TEXT, 16, 24);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return value;
    }

    private Button todayButton() {
        Button today = button("오늘", PRIMARY_LIGHT, PRIMARY, 16, 15);
        today.setOnClickListener(v -> {
            selectedDate = formatDate(new Date());
            render();
        });
        return today;
    }

    private void moveDate(int amount, int field) {
        Calendar value = parseCalendar(selectedDate);
        value.add(field, amount);
        selectedDate = formatDate(value.getTime());
        render();
    }

    /** 주간 보기의 7개 날짜를 같은 크기의 카드로 표시합니다. */
    private void addWeekChips(LinearLayout body) {
        Calendar monday = startOfWeek(selectedDate);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(2), 0, dp(4));

        for (int i = 0; i < 7; i++) {
            Calendar day = (Calendar) monday.clone();
            day.add(Calendar.DAY_OF_MONTH, i);
            String date = formatDate(day.getTime());
            String weekday = new SimpleDateFormat("E", Locale.KOREA).format(day.getTime());
            String monthDay = new SimpleDateFormat("M/d", Locale.KOREA).format(day.getTime());
            int count = countOnDate(null, date);
            boolean selected = date.equals(selectedDate);

            TextView chip = text(weekday + "\n" + monthDay + "\n" + (count > 0 ? count : ""),
                    12, selected ? Color.WHITE : TEXT);
            chip.setGravity(Gravity.CENTER);
            chip.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            chip.setBackground(shape(selected ? PRIMARY : Color.WHITE, 16,
                    selected ? PRIMARY : BORDER, 1));
            chip.setOnClickListener(v -> {
                selectedDate = date;
                render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(92), 1f);
            params.setMargins(dp(2), dp(3), dp(2), dp(3));
            row.addView(chip, params);
        }
        body.addView(row);
    }

    private void addAgenda(LinearLayout body, String date) {
        List<IndexedItem> values = itemsOnDate(null, date);
        Collections.sort(values, Comparator.comparing(v -> v.item.time == null ? "" : v.item.time));
        if (values.isEmpty()) {
            body.addView(empty("이 날짜의 일정과 할 일이 없습니다."));
            return;
        }
        for (IndexedItem value : values) {
            body.addView(itemCard(value.item, value.index, false), cardParams());
        }
    }

    /** 일정·할 일·메모 공통 카드입니다. */
    private View itemCard(Item item, int index, boolean showComplete) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setGravity(Gravity.CENTER_VERTICAL);
        outer.setPadding(dp(14), dp(14), dp(14), dp(14));
        outer.setBackground(shape(Color.WHITE, 20, BORDER, 1));
        outer.setElevation(dp(2));
        outer.setOnClickListener(v -> showDetail(item, index));

        View stripe = new View(this);
        stripe.setBackground(shape(item.completed ? Color.LTGRAY : itemColor(item), 3,
                item.completed ? Color.LTGRAY : itemColor(item), 0));
        outer.addView(stripe, new LinearLayout.LayoutParams(dp(6), dp(72)));

        LinearLayout textArea = new LinearLayout(this);
        textArea.setOrientation(LinearLayout.VERTICAL);
        textArea.setPadding(dp(14), 0, dp(8), 0);

        TextView name = text((item.completed ? "✓ " : "")
                + (item.title.isEmpty() ? "제목 없음" : item.title), 18,
                item.completed ? Color.GRAY : TEXT);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textArea.addView(name);

        String info = join(item.date, item.time);
        if (!info.isEmpty()) {
            TextView dateTime = text("◷  " + info, 14, MUTED);
            dateTime.setPadding(0, dp(6), 0, 0);
            textArea.addView(dateTime);
        }
        if (!item.original.isEmpty() && !item.original.equals(item.title)) {
            String preview = item.original.length() > 58
                    ? item.original.substring(0, 58) + "…" : item.original;
            TextView note = text(preview, 14, MUTED);
            note.setPadding(0, dp(5), 0, 0);
            textArea.addView(note);
        }
        outer.addView(textArea, new LinearLayout.LayoutParams(0, -2, 1f));

        if (showComplete && "할 일".equals(item.type)) {
            Button complete = button(item.completed ? "✓" : "○",
                    item.completed ? Color.rgb(232, 236, 242) : Color.rgb(255, 245, 230),
                    item.completed ? Color.GRAY : TASK, 14, 20);
            complete.setOnClickListener(v -> {
                item.completed = !item.completed;
                saveItems();
                render();
            });
            outer.addView(complete, new LinearLayout.LayoutParams(dp(46), dp(46)));
        }
        return outer;
    }

    private void showDetail(Item item, int index) {
        StringBuilder message = new StringBuilder();
        message.append(item.type).append("\n\n");
        if (!item.date.isEmpty()) message.append("날짜  ").append(displayDate(item.date)).append("\n");
        if (!item.time.isEmpty()) message.append("시간  ").append(item.time).append("\n");
        message.append("알림  ").append(REMINDER_LABELS[reminderIndex(item.reminderMinutes)]).append("\n");
        message.append("반복  ").append(REPEAT_LABELS[repeatIndex(item.repeatType)]).append("\n");
        if (!item.original.isEmpty()) message.append("\n").append(item.original);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(item.title.isEmpty() ? "제목 없음" : item.title)
                .setMessage(message.toString())
                .setNegativeButton("닫기", null)
                .setNeutralButton("삭제", (d, w) -> confirmDelete(index))
                .setPositiveButton("수정", (d, w) -> showEditor(item.copy(), index))
                .create();
        dialog.show();
    }

    private void showEditor(Item draft, int target) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), 0, dp(18), 0);

        Spinner type = spinner(TYPES, typeIndex(draft.type));
        EditText title = field("제목", draft.title, true);
        EditText date = field("날짜: 2026-07-25", draft.date, true);
        EditText time = field("시간: 14:00", draft.time, true);
        EditText contentText = field("내용", draft.original, false);
        contentText.setMinLines(4);
        Spinner reminder = spinner(REMINDER_LABELS, reminderIndex(draft.reminderMinutes));
        Spinner repeat = spinner(REPEAT_LABELS, repeatIndex(draft.repeatType));
        EditText repeatEnd = field("반복 종료일: 2026-12-31", draft.repeatEndDate, true);

        form.addView(labeled("종류", type));
        form.addView(title);
        form.addView(date);
        form.addView(time);
        form.addView(labeled("알림", reminder));
        form.addView(labeled("반복", repeat));
        form.addView(repeatEnd);
        form.addView(contentText);

        new AlertDialog.Builder(this)
                .setTitle(target < 0 ? "새 항목" : "항목 수정")
                .setView(form)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> {
                    draft.type = String.valueOf(type.getSelectedItem());
                    draft.title = title.getText().toString().trim();
                    draft.date = date.getText().toString().trim();
                    draft.time = time.getText().toString().trim();
                    draft.original = contentText.getText().toString().trim();
                    draft.reminderMinutes = REMINDER_VALUES[reminder.getSelectedItemPosition()];
                    draft.repeatType = REPEAT_VALUES[repeat.getSelectedItemPosition()];
                    draft.repeatEndDate = repeatEnd.getText().toString().trim();
                    if (draft.title.isEmpty()) {
                        Toast.makeText(this, "제목을 입력하세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (target < 0) items.add(0, draft); else items.set(target, draft);
                    saveItems();
                    render();
                }).show();
    }

    private void addFab() {
        Button add = button("＋", PRIMARY, Color.WHITE, 32, 30);
        add.setContentDescription("새 항목 추가");
        add.setElevation(dp(7));
        add.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("새 항목 추가")
                .setItems(new String[]{"직접 입력", "AI 메시지 분석", "기존 전체 입력 화면"},
                        (dialog, which) -> {
                            if (which == 0) showEditor(new Item(defaultType()), -1);
                            else if (which == 1) startActivity(new Intent(this, AiInputActivity.class));
                            else startActivity(new Intent(this, WorkspaceActivity.class));
                        }).show());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(68), dp(68));
        params.gravity = Gravity.END | Gravity.BOTTOM;
        params.setMargins(0, 0, dp(20), dp(22));
        content.addView(add, params);
    }

    private void showManagementMenu() {
        new AlertDialog.Builder(this)
                .setTitle("설정 및 관리")
                .setItems(new String[]{"AI 설정", "백업·복원", "기존 화면", "오늘로 이동"},
                        (dialog, which) -> {
                            if (which == 0) startActivity(new Intent(this, AiSettingsActivity.class));
                            else if (which == 1) startActivity(new Intent(this, BackupActivity.class));
                            else if (which == 2) startActivity(new Intent(this, WorkspaceActivity.class));
                            else {
                                selectedDate = formatDate(new Date());
                                menu = "달력";
                                render();
                            }
                        }).show();
    }

    private void confirmDelete(int index) {
        new AlertDialog.Builder(this)
                .setTitle("항목 삭제")
                .setMessage("이 항목을 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> {
                    if (index >= 0 && index < items.size()) items.remove(index);
                    saveItems();
                    render();
                }).show();
    }

    private void updateBottomMenu() {
        for (TextView value : menuViews) {
            boolean selected = menu.equals(String.valueOf(value.getTag()));
            value.setTextColor(selected ? PRIMARY : MUTED);
            value.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            value.setBackground(shape(selected ? PRIMARY_LIGHT : Color.WHITE,
                    18, selected ? Color.rgb(195, 213, 247) : Color.WHITE, selected ? 1 : 0));
        }
    }

    private void addPreview(LinearLayout parent, List<IndexedItem> values, int max, String emptyText) {
        if (values.isEmpty()) {
            parent.addView(empty(emptyText));
            return;
        }
        for (int i = 0; i < Math.min(max, values.size()); i++) {
            IndexedItem value = values.get(i);
            parent.addView(itemCard(value.item, value.index, false), cardParams());
        }
    }

    private List<IndexedItem> itemsOfType(String type) {
        List<IndexedItem> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (type.equals(items.get(i).type)) result.add(new IndexedItem(items.get(i), i));
        }
        return result;
    }

    private List<IndexedItem> itemsOnDate(String type, String date) {
        List<IndexedItem> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (type != null && !type.equals(item.type)) continue;
            if (!("일정".equals(item.type) || "할 일".equals(item.type))) continue;
            if (item.completed || !occurs(item, date)) continue;
            result.add(new IndexedItem(item, i));
        }
        return result;
    }

    private List<IndexedItem> openTasks() {
        List<IndexedItem> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if ("할 일".equals(item.type) && !item.completed) result.add(new IndexedItem(item, i));
        }
        Collections.sort(result, Comparator.comparing(v -> v.item.date == null ? "" : v.item.date));
        return result;
    }

    private EventCalendarView.EventMarker marker(String date) {
        int count = 0;
        int color = PRIMARY;
        for (Item item : items) {
            if (!("일정".equals(item.type) || "할 일".equals(item.type))) continue;
            if (item.completed || !occurs(item, date)) continue;
            if (count == 0) color = itemColor(item);
            count++;
        }
        return new EventCalendarView.EventMarker(count, color);
    }

    private boolean occurs(Item item, String targetDate) {
        if (item.date.equals(targetDate)) return true;
        if (item.date.isEmpty() || targetDate.isEmpty() || "NONE".equals(item.repeatType)) return false;
        long start = parseDate(item.date);
        long target = parseDate(targetDate);
        if (start < 0 || target < start) return false;
        if (!item.repeatEndDate.isEmpty()) {
            long end = parseDate(item.repeatEndDate);
            if (end >= 0 && target > end) return false;
        }
        Calendar s = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        s.setTimeInMillis(start);
        t.setTimeInMillis(target);
        switch (item.repeatType) {
            case "DAILY": return true;
            case "WEEKLY": return s.get(Calendar.DAY_OF_WEEK) == t.get(Calendar.DAY_OF_WEEK);
            case "MONTHLY": return s.get(Calendar.DAY_OF_MONTH) == t.get(Calendar.DAY_OF_MONTH);
            case "WEEKDAYS":
                int day = t.get(Calendar.DAY_OF_WEEK);
                return day != Calendar.SATURDAY && day != Calendar.SUNDAY;
            default: return false;
        }
    }

    private int countOnDate(String type, String date) {
        return itemsOnDate(type, date).size();
    }

    private int countOpenTasks() {
        int count = 0;
        for (Item item : items) if ("할 일".equals(item.type) && !item.completed) count++;
        return count;
    }

    private int countType(String type) {
        int count = 0;
        for (Item item : items) if (type.equals(item.type)) count++;
        return count;
    }

    private String defaultType() {
        if ("할 일".equals(menu)) return "할 일";
        if ("일정".equals(menu) || "달력".equals(menu)) return "일정";
        return "메모";
    }

    private int itemColor(Item item) {
        if ("할 일".equals(item.type)) return TASK;
        if ("메모".equals(item.type)) return MEMO;
        return PRIMARY;
    }

    private TextView metric(String label, int value) {
        TextView view = text(label + "\n" + value, 14, TEXT);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(dp(5), dp(14), dp(5), dp(14));
        view.setBackground(shape(Color.WHITE, 18, BORDER, 1));
        return view;
    }

    private TextView section(String value) {
        TextView view = text(value, 18, TEXT);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(18), 0, dp(9));
        return view;
    }

    private TextView empty(String value) {
        TextView view = text(value, 15, MUTED);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(28), dp(12), dp(28));
        view.setBackground(shape(Color.WHITE, 18, BORDER, 1));
        return view;
    }

    private LinearLayout page() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(12), dp(18), dp(18));
        return page;
    }

    private View labeled(String label, View control) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(label, 13, MUTED);
        name.setPadding(0, dp(8), 0, dp(2));
        holder.addView(name);
        holder.addView(control);
        return holder;
    }

    private Spinner spinner(String[] labels, int selection) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selection);
        return spinner;
    }

    private EditText field(String hint, String value, boolean single) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value == null ? "" : value);
        field.setSingleLine(single);
        field.setTextSize(15);
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        field.setBackground(shape(Color.WHITE, 14, BORDER, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(7), 0, 0);
        field.setLayoutParams(params);
        return field;
    }

    private Button button(String label, int background, int color, int radius, int textSize) {
        Button value = new Button(this);
        value.setText(label);
        value.setTextSize(textSize);
        value.setTextColor(color);
        value.setAllCaps(false);
        value.setPadding(dp(4), 0, dp(4), 0);
        value.setBackground(shape(background, radius, background == Color.TRANSPARENT ? Color.TRANSPARENT : BORDER,
                background == Color.TRANSPARENT ? 0 : 1));
        return value;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable shape(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable value = new GradientDrawable();
        value.setColor(fill);
        value.setCornerRadius(dp(radius));
        if (strokeWidth > 0) value.setStroke(dp(strokeWidth), stroke);
        return value;
    }

    private LinearLayout.LayoutParams weight(int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(height), 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(11));
        return params;
    }

    private View spacer(int height) {
        View value = new View(this);
        value.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return value;
    }

    private Calendar startOfWeek(String date) {
        Calendar value = parseCalendar(date);
        value.add(Calendar.DAY_OF_MONTH, -((value.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        return value;
    }

    private String weekLabel(String date) {
        Calendar start = startOfWeek(date);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 6);
        SimpleDateFormat format = new SimpleDateFormat("M월 d일", Locale.KOREA);
        return format.format(start.getTime()) + " ~ " + format.format(end.getTime());
    }

    private String monthLabel(String date) {
        Calendar value = parseCalendar(date);
        return String.format(Locale.KOREA, "%d년 %d월",
                value.get(Calendar.YEAR), value.get(Calendar.MONTH) + 1);
    }

    private Calendar parseCalendar(String value) {
        Calendar result = Calendar.getInstance();
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(value);
            if (parsed != null) result.setTime(parsed);
        } catch (Exception ignored) {
            // 잘못된 날짜는 현재 날짜를 사용합니다.
        }
        return result;
    }

    private long parseDate(String value) {
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(value);
            return parsed == null ? -1L : parsed.getTime();
        } catch (Exception e) {
            return -1L;
        }
    }

    private String join(String date, String time) {
        if (date == null || date.isEmpty()) return time == null ? "" : time;
        if (time == null || time.isEmpty()) return displayDate(date);
        return displayDate(date) + " · " + time;
    }

    private String displayDate(String date) {
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(date);
            return parsed == null ? date
                    : new SimpleDateFormat("M월 d일 E요일", Locale.KOREA).format(parsed);
        } catch (Exception e) {
            return date;
        }
    }

    private static String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(date);
    }

    private int typeIndex(String value) {
        return "할 일".equals(value) ? 1 : ("일정".equals(value) ? 2 : 0);
    }

    private int reminderIndex(int value) {
        for (int i = 0; i < REMINDER_VALUES.length; i++) if (REMINDER_VALUES[i] == value) return i;
        return 1;
    }

    private int repeatIndex(String value) {
        for (int i = 0; i < REPEAT_VALUES.length; i++) if (REPEAT_VALUES[i].equals(value)) return i;
        return 0;
    }

    private void saveItems() {
        StringBuilder output = new StringBuilder();
        for (Item item : items) {
            if (output.length() > 0) output.append("\n");
            output.append(escape(item.type)).append("\t")
                    .append(escape(item.title)).append("\t")
                    .append(escape(item.date)).append("\t")
                    .append(escape(item.time)).append("\t")
                    .append(escape(item.original)).append("\t")
                    .append(item.completed ? "1" : "0").append("\t")
                    .append(item.reminderMinutes).append("\t")
                    .append(item.repeatType).append("\t")
                    .append(escape(item.repeatEndDate)).append("\t")
                    .append(item.colorValue == null ? "DEFAULT" : item.colorValue);
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_ITEMS, output.toString()).apply();
        AlarmScheduler.rescheduleAll(this);
    }

    private void loadItems() {
        items.clear();
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        String stored = preferences.getString(KEY_ITEMS, "");
        if (stored == null || stored.isEmpty()) return;
        for (String line : stored.split("\n")) {
            String[] values = line.split("\t", -1);
            if (values.length < 5) continue;
            Item item = new Item();
            item.type = unescape(values[0]);
            item.title = unescape(values[1]);
            item.date = unescape(values[2]);
            item.time = unescape(values[3]);
            item.original = unescape(values[4]);
            item.completed = values.length >= 6 && "1".equals(values[5]);
            item.reminderMinutes = parseReminder(values.length >= 7 ? values[6] : "0");
            item.repeatType = normalizeRepeat(values.length >= 8 ? values[7] : "NONE");
            item.repeatEndDate = values.length >= 9 ? unescape(values[8]) : "";
            item.colorValue = values.length >= 10 ? values[9] : "DEFAULT";
            items.add(item);
        }
    }

    private int parseReminder(String value) {
        try {
            int parsed = Integer.parseInt(value);
            for (int allowed : REMINDER_VALUES) if (allowed == parsed) return parsed;
        } catch (Exception ignored) {
            // 손상된 값은 정각 알림으로 복구합니다.
        }
        return 0;
    }

    private String normalizeRepeat(String value) {
        for (String allowed : REPEAT_VALUES) if (allowed.equals(value)) return value;
        return "NONE";
    }

    private String escape(String value) {
        return (value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n");
    }

    private String unescape(String value) {
        return value.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class IndexedItem {
        final Item item;
        final int index;
        IndexedItem(Item item, int index) {
            this.item = item;
            this.index = index;
        }
    }

    private static final class Item {
        String type = "메모";
        String title = "";
        String date = "";
        String time = "";
        String original = "";
        boolean completed;
        int reminderMinutes;
        String repeatType = "NONE";
        String repeatEndDate = "";
        String colorValue = "DEFAULT";

        Item() { }
        Item(String type) { this.type = type; }

        Item copy() {
            Item value = new Item(type);
            value.title = title;
            value.date = date;
            value.time = time;
            value.original = original;
            value.completed = completed;
            value.reminderMinutes = reminderMinutes;
            value.repeatType = repeatType;
            value.repeatEndDate = repeatEndDate;
            value.colorValue = colorValue;
            return value;
        }
    }
}
