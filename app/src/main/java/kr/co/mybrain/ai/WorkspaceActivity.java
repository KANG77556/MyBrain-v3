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
import android.view.ViewGroup;
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
 * 홈·할 일·일정·메모·달력을 분리한 기본 작업 화면입니다.
 * 기존 SharedPreferences 저장 형식을 그대로 사용하여 이전 자료와 호환합니다.
 */
public class WorkspaceActivity extends Activity {
    private static final String PREFS = "mybrain_data";
    private static final String KEY_ITEMS = "items";
    private static final String UI_PREFS = "mybrain_ui_settings";
    private static final String KEY_CALENDAR_MODE = "calendar_view_mode";

    private static final int PRIMARY = Color.rgb(35, 92, 190);
    private static final int TEXT = Color.rgb(31, 42, 55);
    private static final int MUTED = Color.rgb(104, 116, 132);
    private static final int BACKGROUND = Color.rgb(244, 247, 251);
    private static final int SCHEDULE = Color.rgb(37, 99, 235);
    private static final int TASK = Color.rgb(234, 120, 35);
    private static final int MEMO = Color.rgb(123, 86, 188);

    private static final String[] MENUS = {"홈", "할 일", "일정", "메모", "달력"};
    private static final String[] TYPES = {"메모", "할 일", "일정"};
    private static final String[] REMINDER_LABELS = {
            "알림 없음", "정각", "5분 전", "10분 전", "30분 전", "1시간 전"
    };
    private static final int[] REMINDER_VALUES = {-1, 0, 5, 10, 30, 60};
    private static final String[] REPEAT_LABELS = {"반복 없음", "매일", "매주", "매월", "평일"};
    private static final String[] REPEAT_VALUES = {"NONE", "DAILY", "WEEKLY", "MONTHLY", "WEEKDAYS"};
    private static final String[] COLOR_LABELS = {"기본", "파랑", "빨강", "주황", "초록", "보라", "회색"};
    private static final String[] COLOR_VALUES = {"DEFAULT", "BLUE", "RED", "ORANGE", "GREEN", "PURPLE", "GRAY"};

    private final List<Item> items = new ArrayList<>();
    private final List<Button> menuButtons = new ArrayList<>();
    private FrameLayout content;
    private LinearLayout bottomBar;
    private TextView title;
    private Button settings;
    private String menu = "홈";
    private String selectedDate = formatDate(new Date());
    private String calendarMode = "MONTH";
    private int detailIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        calendarMode = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                .getString(KEY_CALENDAR_MODE, "MONTH");
        loadItems();
        buildShell();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content == null) return;
        loadItems();
        if (detailIndex >= items.size()) detailIndex = -1;
        render();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(8), dp(10), dp(6));
        top.setBackgroundColor(Color.WHITE);
        title = text("MyBrain AI", 22, Color.rgb(18, 48, 89));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1f));
        settings = button("⚙", Color.WHITE, PRIMARY);
        settings.setTextSize(20);
        settings.setContentDescription("설정 및 관리");
        settings.setOnClickListener(v -> showManagementMenu());
        top.addView(settings, new LinearLayout.LayoutParams(dp(52), dp(44)));
        root.addView(top);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(dp(6), dp(4), dp(6), dp(6));
        bottomBar.setBackgroundColor(Color.WHITE);
        for (String value : MENUS) {
            Button tab = new Button(this);
            tab.setText(value);
            tab.setTextSize(12);
            tab.setAllCaps(false);
            tab.setPadding(0, 0, 0, 0);
            tab.setTag(value);
            tab.setOnClickListener(v -> {
                menu = String.valueOf(v.getTag());
                detailIndex = -1;
                render();
            });
            menuButtons.add(tab);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(54), 1f);
            params.setMargins(dp(2), 0, dp(2), 0);
            bottomBar.addView(tab, params);
        }
        root.addView(bottomBar);
        setContentView(root);
    }

    private void render() {
        content.removeAllViews();
        boolean detail = detailIndex >= 0 && detailIndex < items.size();
        bottomBar.setVisibility(detail ? View.GONE : View.VISIBLE);
        settings.setVisibility(detail ? View.GONE : View.VISIBLE);
        if (detail) {
            title.setText("상세 보기");
            content.addView(buildDetail(items.get(detailIndex)));
            return;
        }

        title.setText("홈".equals(menu) ? "MyBrain AI" : menu);
        View page;
        if ("홈".equals(menu)) page = buildHome();
        else if ("달력".equals(menu)) page = buildCalendar();
        else page = buildList(menu);
        content.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        addFab();
        updateMenuStyles();
    }

    private View buildHome() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = page();
        scroll.addView(page);

        TextView date = text(displayDate(selectedDate), 15, MUTED);
        date.setPadding(0, 0, 0, dp(10));
        page.addView(date);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(metric("오늘 일정", countOnDate("일정", selectedDate)), weight());
        metrics.addView(metric("남은 할 일", countOpenTasks()), weight());
        metrics.addView(metric("메모", countType("메모")), weight());
        page.addView(metrics);

        page.addView(section("빠른 실행"));
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        Button manual = action("✍ 직접 입력", PRIMARY, Color.WHITE);
        manual.setOnClickListener(v -> showEditor(new Item(defaultType()), -1));
        quick.addView(manual, weight());
        Button ai = action("✨ AI 분석", Color.rgb(231, 239, 255), PRIMARY);
        ai.setOnClickListener(v -> startActivity(new Intent(this, AiInputActivity.class)));
        quick.addView(ai, weight());
        page.addView(quick);

        page.addView(section("오늘 일정"));
        addPreview(page, itemsOnDate("일정", selectedDate), 3, "오늘 일정이 없습니다.");
        page.addView(section("우선 처리할 일"));
        addPreview(page, openTasks(), 3, "남은 할 일이 없습니다.");
        page.addView(section("최근 메모"));
        addPreview(page, itemsOfType("메모"), 3, "저장된 메모가 없습니다.");
        page.addView(spacer(82));
        return scroll;
    }

    private View buildList(String type) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(10), dp(14), 0);

        EditText search = new EditText(this);
        search.setHint(type + " 검색");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(round(Color.WHITE, 14, Color.rgb(210, 219, 232), 1));
        page.addView(search, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(10), 0, dp(84));
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

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
            list.addView(card(item, i, true), cardParams());
            shown++;
        }
        if (shown == 0) list.addView(empty("조건에 맞는 " + type + "이 없습니다."));
    }

    private View buildCalendar() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12), dp(8), dp(12), 0);

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        addMode(modes, "월간", "MONTH");
        addMode(modes, "주간", "WEEK");
        addMode(modes, "일간", "DAY");
        page.addView(modes);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        if ("MONTH".equals(calendarMode)) {
            EventCalendarView calendar = new EventCalendarView(this);
            calendar.setSelectedDate(selectedDate);
            calendar.setEventMarkerProvider(this::marker);
            calendar.setOnDateSelectedListener(value -> {
                selectedDate = value;
                render();
            });
            body.addView(calendar);
        } else {
            body.addView(dateNavigator());
            if ("WEEK".equals(calendarMode)) addWeek(body);
        }
        body.addView(section(displayDate(selectedDate)));
        addAgenda(body, selectedDate);
        body.addView(spacer(82));
        return page;
    }

    private void addMode(LinearLayout row, String label, String mode) {
        boolean selected = mode.equals(calendarMode);
        Button value = button(label, selected ? PRIMARY : Color.WHITE,
                selected ? Color.WHITE : TEXT);
        value.setOnClickListener(v -> {
            calendarMode = mode;
            getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_CALENDAR_MODE, mode).apply();
            render();
        });
        row.addView(value, weight());
    }

    private View dateNavigator() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Button previous = button("‹", Color.WHITE, PRIMARY);
        previous.setTextSize(22);
        previous.setOnClickListener(v -> moveDate(-1));
        row.addView(previous, new LinearLayout.LayoutParams(dp(52), dp(46)));
        TextView label = text("WEEK".equals(calendarMode)
                ? weekLabel(selectedDate) : displayDate(selectedDate), 16, TEXT);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(46), 1f));
        Button today = button("오늘", Color.rgb(231, 239, 255), PRIMARY);
        today.setOnClickListener(v -> {
            selectedDate = formatDate(new Date());
            render();
        });
        row.addView(today, new LinearLayout.LayoutParams(dp(58), dp(42)));
        Button next = button("›", Color.WHITE, PRIMARY);
        next.setTextSize(22);
        next.setOnClickListener(v -> moveDate(1));
        row.addView(next, new LinearLayout.LayoutParams(dp(52), dp(46)));
        return row;
    }

    private void moveDate(int direction) {
        Calendar value = parseCalendar(selectedDate);
        value.add(Calendar.DAY_OF_MONTH, direction * ("WEEK".equals(calendarMode) ? 7 : 1));
        selectedDate = formatDate(value.getTime());
        render();
    }

    /** 모바일에서는 7일 요약과 선택 날짜의 시간순 목록을 함께 보여줍니다. */
    private void addWeek(LinearLayout body) {
        Calendar monday = startOfWeek(selectedDate);
        LinearLayout days = new LinearLayout(this);
        days.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 7; i++) {
            Calendar day = (Calendar) monday.clone();
            day.add(Calendar.DAY_OF_MONTH, i);
            String date = formatDate(day.getTime());
            String label = new SimpleDateFormat("E\nM/d", Locale.KOREA).format(day.getTime());
            int count = countOnDate(null, date);
            if (count > 0) label += "\n" + count;
            boolean selected = date.equals(selectedDate);
            Button button = button(label, selected ? PRIMARY : Color.WHITE,
                    selected ? Color.WHITE : TEXT);
            button.setTextSize(11);
            button.setOnClickListener(v -> {
                selectedDate = date;
                render();
            });
            days.addView(button, weight());
        }
        body.addView(days);
    }

    private void addAgenda(LinearLayout body, String date) {
        List<IndexedItem> values = itemsOnDate(null, date);
        Collections.sort(values, Comparator.comparing(v -> v.item.time == null ? "" : v.item.time));
        if (values.isEmpty()) {
            body.addView(empty("이 날짜의 일정과 할 일이 없습니다."));
            return;
        }
        for (IndexedItem value : values) body.addView(card(value.item, value.index, false), cardParams());
    }

    private View buildDetail(Item item) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = page();
        scroll.addView(page);

        LinearLayout actions = new LinearLayout(this);
        Button back = button("← 목록", Color.WHITE, PRIMARY);
        back.setOnClickListener(v -> { detailIndex = -1; render(); });
        actions.addView(back, weight());
        Button edit = button("수정", Color.rgb(231, 239, 255), PRIMARY);
        edit.setOnClickListener(v -> showEditor(item.copy(), detailIndex));
        actions.addView(edit, weight());
        Button delete = button("삭제", Color.rgb(255, 239, 239), Color.rgb(190, 52, 52));
        delete.setOnClickListener(v -> confirmDelete(detailIndex));
        actions.addView(delete, weight());
        page.addView(actions);

        TextView type = text(item.type, 14, itemColor(item));
        type.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        type.setPadding(0, dp(20), 0, dp(4));
        page.addView(type);
        TextView name = text(item.title.isEmpty() ? "제목 없음" : item.title, 25, TEXT);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setPadding(0, 0, 0, dp(12));
        page.addView(name);
        detailRow(page, "날짜", item.date.isEmpty() ? "없음" : displayDate(item.date));
        detailRow(page, "시간", item.time.isEmpty() ? "없음" : item.time);
        detailRow(page, "알림", REMINDER_LABELS[reminderIndex(item.reminderMinutes)]);
        detailRow(page, "반복", REPEAT_LABELS[repeatIndex(item.repeatType)]);
        detailRow(page, "상태", item.completed ? "완료" : "진행 중");
        page.addView(section("내용"));
        TextView contentText = text(item.original.isEmpty() ? "내용이 없습니다." : item.original, 16, TEXT);
        contentText.setPadding(dp(14), dp(14), dp(14), dp(14));
        contentText.setBackground(round(Color.WHITE, 14, Color.rgb(222, 228, 238), 1));
        page.addView(contentText);
        if ("할 일".equals(item.type)) {
            Button complete = action(item.completed ? "완료 취소" : "완료 처리",
                    item.completed ? Color.rgb(239, 244, 251) : TASK,
                    item.completed ? TEXT : Color.WHITE);
            complete.setOnClickListener(v -> {
                item.completed = !item.completed;
                saveItems();
                render();
            });
            LinearLayout.LayoutParams params = full();
            params.setMargins(0, dp(14), 0, 0);
            page.addView(complete, params);
        }
        return scroll;
    }

    private void showEditor(Item draft, int target) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), 0, dp(18), 0);
        Spinner type = spinner(TYPES, typeIndex(draft.type));
        EditText name = field("제목", draft.title, true);
        EditText date = field("날짜: 2026-07-25", draft.date, true);
        EditText time = field("시간: 14:00", draft.time, true);
        EditText memo = field("내용", draft.original, false);
        memo.setMinLines(4);
        Spinner reminder = spinner(REMINDER_LABELS, reminderIndex(draft.reminderMinutes));
        Spinner repeat = spinner(REPEAT_LABELS, repeatIndex(draft.repeatType));
        EditText repeatEnd = field("반복 종료일: 2026-12-31", draft.repeatEndDate, true);
        Spinner color = spinner(COLOR_LABELS, colorIndex(draft.colorValue));
        form.addView(labeled("종류", type));
        form.addView(name); form.addView(date); form.addView(time);
        form.addView(labeled("알림", reminder));
        form.addView(labeled("반복", repeat));
        form.addView(repeatEnd);
        form.addView(labeled("색상", color));
        form.addView(memo);

        new AlertDialog.Builder(this)
                .setTitle(target < 0 ? "새 항목" : "항목 수정")
                .setView(form)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> {
                    draft.type = String.valueOf(type.getSelectedItem());
                    draft.title = name.getText().toString().trim();
                    draft.date = date.getText().toString().trim();
                    draft.time = time.getText().toString().trim();
                    draft.original = memo.getText().toString().trim();
                    draft.reminderMinutes = REMINDER_VALUES[reminder.getSelectedItemPosition()];
                    draft.repeatType = REPEAT_VALUES[repeat.getSelectedItemPosition()];
                    draft.repeatEndDate = repeatEnd.getText().toString().trim();
                    draft.colorValue = COLOR_VALUES[color.getSelectedItemPosition()];
                    if (draft.title.isEmpty()) {
                        Toast.makeText(this, "제목을 입력하세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (target < 0) items.add(0, draft); else items.set(target, draft);
                    detailIndex = target < 0 ? 0 : target;
                    saveItems();
                    render();
                }).show();
    }

    private void addFab() {
        Button add = new Button(this);
        add.setText("＋");
        add.setTextSize(28);
        add.setTextColor(Color.WHITE);
        add.setAllCaps(false);
        add.setBackground(round(PRIMARY, 30, PRIMARY, 0));
        add.setContentDescription("새 항목 추가");
        add.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("새 항목 추가")
                .setItems(new String[]{"직접 입력", "AI 메시지 분석", "기존 통합 입력"},
                        (dialog, which) -> {
                            if (which == 0) showEditor(new Item(defaultType()), -1);
                            else if (which == 1) startActivity(new Intent(this, AiInputActivity.class));
                            else startActivity(new Intent(this, IntegratedMainActivity.class));
                        }).show());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(60), dp(60));
        params.gravity = Gravity.END | Gravity.BOTTOM;
        params.setMargins(0, 0, dp(18), dp(18));
        content.addView(add, params);
    }

    private void showManagementMenu() {
        new AlertDialog.Builder(this)
                .setTitle("설정 및 관리")
                .setItems(new String[]{"AI 설정", "백업·복원", "기존 통합 관리", "오늘로 이동"},
                        (dialog, which) -> {
                            if (which == 0) startActivity(new Intent(this, AiSettingsActivity.class));
                            else if (which == 1) startActivity(new Intent(this, BackupActivity.class));
                            else if (which == 2) startActivity(new Intent(this, IntegratedMainActivity.class));
                            else {
                                selectedDate = formatDate(new Date());
                                menu = "홈";
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
                    detailIndex = -1;
                    saveItems();
                    render();
                }).show();
    }

    private View card(Item item, int index, boolean taskButton) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setGravity(Gravity.CENTER_VERTICAL);
        outer.setPadding(dp(10), dp(10), dp(10), dp(10));
        outer.setBackground(round(Color.WHITE, 14, Color.rgb(222, 228, 238), 1));
        outer.setOnClickListener(v -> { detailIndex = index; render(); });
        View stripe = new View(this);
        stripe.setBackgroundColor(item.completed ? Color.LTGRAY : itemColor(item));
        outer.addView(stripe, new LinearLayout.LayoutParams(dp(5), dp(54)));
        if (taskButton && "할 일".equals(item.type)) {
            Button complete = button(item.completed ? "✓" : "○",
                    item.completed ? Color.rgb(226, 232, 240) : Color.rgb(255, 245, 230),
                    item.completed ? Color.GRAY : TASK);
            complete.setTextSize(20);
            complete.setOnClickListener(v -> {
                item.completed = !item.completed;
                saveItems();
                render();
            });
            outer.addView(complete, new LinearLayout.LayoutParams(dp(48), dp(52)));
        }
        LinearLayout textArea = new LinearLayout(this);
        textArea.setOrientation(LinearLayout.VERTICAL);
        textArea.setPadding(dp(12), 0, 0, 0);
        TextView name = text((item.completed ? "✓ " : "")
                + (item.title.isEmpty() ? "제목 없음" : item.title), 16,
                item.completed ? Color.GRAY : TEXT);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textArea.addView(name);
        String info = join(item.date, item.time);
        if (!info.isEmpty()) textArea.addView(text(info, 13, MUTED));
        if (!item.original.isEmpty() && !item.original.equals(item.title)) {
            String preview = item.original.length() > 54 ? item.original.substring(0, 54) + "…" : item.original;
            textArea.addView(text(preview, 13, MUTED));
        }
        outer.addView(textArea, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return outer;
    }

    private void addPreview(LinearLayout parent, List<IndexedItem> values, int max, String emptyText) {
        if (values.isEmpty()) { parent.addView(empty(emptyText)); return; }
        for (int i = 0; i < Math.min(max, values.size()); i++) {
            IndexedItem value = values.get(i);
            parent.addView(card(value.item, value.index, false), cardParams());
        }
    }

    private List<IndexedItem> itemsOfType(String type) {
        List<IndexedItem> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) if (type.equals(items.get(i).type))
            result.add(new IndexedItem(items.get(i), i));
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
        int count = 0, color = SCHEDULE;
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
        long start = parseDate(item.date), target = parseDate(targetDate);
        if (start < 0 || target < start) return false;
        if (!item.repeatEndDate.isEmpty()) {
            long end = parseDate(item.repeatEndDate);
            if (end >= 0 && target > end) return false;
        }
        Calendar s = Calendar.getInstance(), t = Calendar.getInstance();
        s.setTimeInMillis(start); t.setTimeInMillis(target);
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

    private int countOnDate(String type, String date) { return itemsOnDate(type, date).size(); }
    private int countOpenTasks() { int c = 0; for (Item i : items) if ("할 일".equals(i.type) && !i.completed) c++; return c; }
    private int countType(String type) { int c = 0; for (Item i : items) if (type.equals(i.type)) c++; return c; }

    private String defaultType() {
        if ("할 일".equals(menu)) return "할 일";
        if ("일정".equals(menu) || "달력".equals(menu)) return "일정";
        return "메모";
    }

    private int itemColor(Item item) {
        switch (normalizeColor(item.colorValue)) {
            case "BLUE": return SCHEDULE;
            case "RED": return Color.rgb(220, 60, 60);
            case "ORANGE": return TASK;
            case "GREEN": return Color.rgb(34, 150, 90);
            case "PURPLE": return MEMO;
            case "GRAY": return Color.rgb(110, 118, 130);
            default: return "일정".equals(item.type) ? SCHEDULE : ("할 일".equals(item.type) ? TASK : MEMO);
        }
    }

    private void updateMenuStyles() {
        for (Button value : menuButtons) {
            boolean selected = menu.equals(String.valueOf(value.getTag()));
            value.setTextColor(selected ? PRIMARY : MUTED);
            value.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            value.setBackground(round(selected ? Color.rgb(231, 239, 255) : Color.WHITE,
                    14, selected ? Color.rgb(196, 213, 244) : Color.WHITE, selected ? 1 : 0));
        }
    }

    private void detailRow(LinearLayout page, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(5), 0, dp(5));
        row.addView(text(label, 14, MUTED), new LinearLayout.LayoutParams(dp(84), -2));
        row.addView(text(value, 15, TEXT), new LinearLayout.LayoutParams(0, -2, 1f));
        page.addView(row);
    }

    private View labeled(String label, View control) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(label, 13, MUTED);
        name.setPadding(0, dp(8), 0, dp(2));
        holder.addView(name); holder.addView(control);
        return holder;
    }

    private Spinner spinner(String[] labels, int selection) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter); spinner.setSelection(selection);
        return spinner;
    }

    private EditText field(String hint, String value, boolean single) {
        EditText field = new EditText(this);
        field.setHint(hint); field.setText(value == null ? "" : value);
        field.setSingleLine(single); field.setTextSize(15);
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        field.setBackground(round(Color.WHITE, 12, Color.rgb(210, 219, 232), 1));
        LinearLayout.LayoutParams params = full(); params.setMargins(0, dp(6), 0, 0);
        field.setLayoutParams(params);
        return field;
    }

    private LinearLayout page() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(12), dp(16), dp(18));
        return page;
    }

    private TextView metric(String label, int value) {
        TextView view = text(label + "\n" + value, 14, TEXT);
        view.setGravity(Gravity.CENTER); view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(dp(4), dp(14), dp(4), dp(14));
        view.setBackground(round(Color.WHITE, 14, Color.rgb(222, 228, 238), 1));
        return view;
    }

    private TextView section(String value) {
        TextView view = text(value, 17, TEXT);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(18), 0, dp(8));
        return view;
    }

    private TextView empty(String value) {
        TextView view = text(value, 15, MUTED);
        view.setGravity(Gravity.CENTER); view.setPadding(dp(12), dp(26), dp(12), dp(26));
        view.setBackground(round(Color.WHITE, 14, Color.rgb(226, 232, 240), 1));
        return view;
    }

    private Button button(String label, int background, int color) {
        Button value = new Button(this);
        value.setText(label); value.setTextSize(13); value.setTextColor(color);
        value.setAllCaps(false); value.setPadding(dp(4), 0, dp(4), 0);
        value.setBackground(round(background, 12, Color.rgb(218, 226, 237), 1));
        return value;
    }

    private Button action(String label, int background, int color) {
        Button value = button(label, background, color);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return value;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        return view;
    }

    private View spacer(int height) { View value = new View(this); value.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height))); return value; }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(50), 1f); p.setMargins(dp(3), dp(3), dp(3), dp(3)); return p; }
    private LinearLayout.LayoutParams cardParams() { LinearLayout.LayoutParams p = full(); p.setMargins(0, 0, 0, dp(9)); return p; }

    private GradientDrawable round(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable value = new GradientDrawable();
        value.setColor(fill); value.setCornerRadius(dp(radius));
        if (strokeWidth > 0) value.setStroke(dp(strokeWidth), stroke);
        return value;
    }

    private Calendar startOfWeek(String date) {
        Calendar value = parseCalendar(date);
        value.add(Calendar.DAY_OF_MONTH, -((value.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        return value;
    }

    private String weekLabel(String date) {
        Calendar start = startOfWeek(date); Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 6);
        SimpleDateFormat f = new SimpleDateFormat("M월 d일", Locale.KOREA);
        return f.format(start.getTime()) + " ~ " + f.format(end.getTime());
    }

    private Calendar parseCalendar(String value) {
        Calendar result = Calendar.getInstance();
        try { Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(value); if (parsed != null) result.setTime(parsed); }
        catch (Exception ignored) { }
        return result;
    }

    private long parseDate(String value) {
        try { Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(value); return parsed == null ? -1L : parsed.getTime(); }
        catch (Exception e) { return -1L; }
    }

    private String join(String date, String time) {
        if (date == null || date.isEmpty()) return time == null ? "" : time;
        if (time == null || time.isEmpty()) return displayDate(date);
        return displayDate(date) + " · " + time;
    }

    private String displayDate(String date) {
        try { Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(date);
            return parsed == null ? date : new SimpleDateFormat("M월 d일 E요일", Locale.KOREA).format(parsed); }
        catch (Exception e) { return date; }
    }

    private static String formatDate(Date date) { return new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(date); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private int typeIndex(String value) { return "할 일".equals(value) ? 1 : ("일정".equals(value) ? 2 : 0); }
    private int reminderIndex(int value) { for (int i = 0; i < REMINDER_VALUES.length; i++) if (REMINDER_VALUES[i] == value) return i; return 1; }
    private int repeatIndex(String value) { for (int i = 0; i < REPEAT_VALUES.length; i++) if (REPEAT_VALUES[i].equals(value)) return i; return 0; }
    private int colorIndex(String value) { for (int i = 0; i < COLOR_VALUES.length; i++) if (COLOR_VALUES[i].equals(normalizeColor(value))) return i; return 0; }

    private void saveItems() {
        StringBuilder output = new StringBuilder();
        for (Item item : items) {
            if (output.length() > 0) output.append("\n");
            output.append(escape(item.type)).append("\t").append(escape(item.title)).append("\t")
                    .append(escape(item.date)).append("\t").append(escape(item.time)).append("\t")
                    .append(escape(item.original)).append("\t").append(item.completed ? "1" : "0").append("\t")
                    .append(item.reminderMinutes).append("\t").append(item.repeatType).append("\t")
                    .append(escape(item.repeatEndDate)).append("\t").append(normalizeColor(item.colorValue));
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_ITEMS, output.toString()).apply();
        AlarmScheduler.rescheduleAll(this);
    }

    private void loadItems() {
        items.clear();
        String stored = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_ITEMS, "");
        if (stored == null || stored.isEmpty()) return;
        for (String line : stored.split("\n")) {
            String[] v = line.split("\t", -1); if (v.length < 5) continue;
            Item item = new Item(); item.type = unescape(v[0]); item.title = unescape(v[1]);
            item.date = unescape(v[2]); item.time = unescape(v[3]); item.original = unescape(v[4]);
            item.completed = v.length >= 6 && "1".equals(v[5]);
            item.reminderMinutes = parseReminder(v.length >= 7 ? v[6] : "0");
            item.repeatType = normalizeRepeat(v.length >= 8 ? v[7] : "NONE");
            item.repeatEndDate = v.length >= 9 ? unescape(v[8]) : "";
            item.colorValue = normalizeColor(v.length >= 10 ? v[9] : "DEFAULT");
            items.add(item);
        }
    }

    private int parseReminder(String value) { try { int v = Integer.parseInt(value); for (int a : REMINDER_VALUES) if (a == v) return v; } catch (Exception ignored) { } return 0; }
    private String normalizeRepeat(String value) { for (String a : REPEAT_VALUES) if (a.equals(value)) return value; return "NONE"; }
    private String normalizeColor(String value) { for (String a : COLOR_VALUES) if (a.equals(value)) return value; return "DEFAULT"; }
    private String escape(String value) { return (value == null ? "" : value).replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n"); }
    private String unescape(String value) { return value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\"); }

    private static final class IndexedItem {
        final Item item; final int index;
        IndexedItem(Item item, int index) { this.item = item; this.index = index; }
    }

    private static final class Item {
        String type = "메모", title = "", date = "", time = "", original = "";
        boolean completed;
        int reminderMinutes;
        String repeatType = "NONE", repeatEndDate = "", colorValue = "DEFAULT";
        Item() { }
        Item(String type) { this.type = type; }
        Item copy() {
            Item v = new Item(type); v.title = title; v.date = date; v.time = time; v.original = original;
            v.completed = completed; v.reminderMinutes = reminderMinutes; v.repeatType = repeatType;
            v.repeatEndDate = repeatEndDate; v.colorValue = colorValue; return v;
        }
    }
}
