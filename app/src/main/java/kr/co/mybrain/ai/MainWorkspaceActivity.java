package kr.co.mybrain.ai;

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
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * MyBrain AI 1.9.5 통합 메인 화면입니다.
 * 이전 WorkspaceActivityV2~V9의 지연 패치 없이 홈·목록·달력을 처음부터 직접 구성합니다.
 */
public class MainWorkspaceActivity extends android.app.Activity {

    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int PRIMARY_LIGHT = Color.rgb(235, 242, 255);
    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BORDER = Color.rgb(220, 228, 240);
    private static final int BACKGROUND = Color.rgb(247, 249, 253);
    private static final int TASK = Color.rgb(234, 120, 35);

    private static final String[] MENUS = {"홈", "할 일", "일정", "메모", "달력"};
    private static final String[] ICONS = {"⌂", "☑", "▣", "▤", "▦"};

    private final List<WorkItemRecord> items = new ArrayList<>();
    private final List<TextView> tabs = new ArrayList<>();

    private FrameLayout content;
    private TextView headerTitle;
    private String menu = "홈";
    private String selectedDate = LocalDate.now().toString();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadItems();
        buildShell();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadItems();
        render();
    }

    /** 저장된 일정·할 일·메모를 공통 저장소에서 불러옵니다. */
    private void loadItems() {
        items.clear();
        items.addAll(WorkItemStore.load(this));
    }

    /** 상단 제목, 본문, 하단 메뉴를 한 번만 생성합니다. */
    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(8), dp(12), dp(6));
        header.setBackgroundColor(Color.WHITE);

        headerTitle = text("MyBrain AI", 24, TEXT);
        headerTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0, dp(58), 1f));

        Button settings = button("⚙", Color.WHITE, PRIMARY, 17);
        settings.setContentDescription("설정 및 관리");
        settings.setOnClickListener(v -> showManagementMenu());
        header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(dp(4), dp(4), dp(4), dp(4));
        bottom.setBackground(shape(Color.WHITE, 22, BORDER, 1));

        for (int i = 0; i < MENUS.length; i++) {
            String target = MENUS[i];
            TextView tab = text(ICONS[i] + "\n" + target, 12, MUTED);
            tab.setGravity(Gravity.CENTER);
            tab.setTag(target);
            tab.setContentDescription(target + " 화면");
            tab.setOnClickListener(v -> {
                menu = String.valueOf(v.getTag());
                render();
            });
            tabs.add(tab);
            bottom.addView(tab, new LinearLayout.LayoutParams(0, dp(68), 1f));
        }
        root.addView(bottom);
        setContentView(root);
    }

    /** 선택된 하단 메뉴에 맞는 화면을 즉시 구성합니다. */
    private void render() {
        if (content == null) return;
        content.removeAllViews();
        headerTitle.setText("홈".equals(menu) ? "MyBrain AI" : menu);

        View page;
        if ("홈".equals(menu)) page = buildHome();
        else if ("달력".equals(menu)) page = buildCalendar();
        else page = buildList(menu);

        content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        addFloatingButton();
        updateTabs();
    }

    /** 바로 기록, 다음 일정, 남은 할 일만 보이는 간단 홈입니다. */
    private View buildHome() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = page();
        scroll.addView(page);

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(Gravity.CENTER_VERTICAL);
        quick.setPadding(dp(14), dp(12), dp(10), dp(12));
        quick.setBackground(shape(Color.WHITE, 18, BORDER, 1));

        TextView prompt = text("무엇을 기록할까요?", 16, MUTED);
        prompt.setGravity(Gravity.CENTER_VERTICAL);
        prompt.setContentDescription("바로 기록 열기");
        prompt.setOnClickListener(v -> openUnifiedInput(null));
        quick.addView(prompt, new LinearLayout.LayoutParams(0, dp(52), 1f));

        Button voice = button("🎤", PRIMARY, Color.WHITE, 19);
        voice.setContentDescription("음성으로 바로 기록");
        voice.setOnClickListener(v -> startActivityForResult(
                new Intent(this, VoiceCaptureActivityV3.class), 2401));
        quick.addView(voice, new LinearLayout.LayoutParams(dp(54), dp(52)));
        page.addView(quick, matchWithBottom(12));

        TextView date = text(koreanDate(LocalDate.now()), 14, MUTED);
        date.setPadding(dp(2), 0, 0, dp(10));
        page.addView(date);

        List<WorkItemRecord> todaySchedules = todaySchedules();
        List<WorkItemRecord> openTasks = openTasks();

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.addView(metric("오늘 일정", todaySchedules.size()), weightedHeight(68));
        summary.addView(metric("남은 할 일", openTasks.size()), weightedHeight(68));
        page.addView(summary, matchWithBottom(14));

        page.addView(section("다음 일정"));
        WorkItemRecord nearest = nearestSchedule(todaySchedules);
        if (nearest == null) {
            page.addView(emptyCard("오늘 일정이 없습니다."), matchWithBottom(8));
        } else {
            View card = compactCard(nearest, false);
            card.setOnClickListener(v -> {
                selectedDate = LocalDate.now().toString();
                menu = "달력";
                render();
            });
            page.addView(card, matchWithBottom(8));
        }

        page.addView(section("남은 할 일"));
        if (openTasks.isEmpty()) {
            page.addView(emptyCard("남은 할 일이 없습니다."), matchWithBottom(8));
        } else {
            int count = Math.min(2, openTasks.size());
            for (int i = 0; i < count; i++) {
                page.addView(compactCard(openTasks.get(i), true), matchWithBottom(7));
            }
            if (openTasks.size() > count) {
                TextView more = text("+ " + (openTasks.size() - count) + "개 더 보기", 14, PRIMARY);
                more.setGravity(Gravity.CENTER);
                more.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                more.setPadding(dp(12), dp(12), dp(12), dp(12));
                more.setOnClickListener(v -> {
                    menu = "할 일";
                    render();
                });
                page.addView(more);
            }
        }

        Button all = button("오늘 전체 보기", PRIMARY_LIGHT, PRIMARY, 15);
        all.setOnClickListener(v -> {
            selectedDate = LocalDate.now().toString();
            menu = "달력";
            render();
        });
        page.addView(all, new LinearLayout.LayoutParams(-1, dp(52)));
        page.addView(spacer(88));
        return scroll;
    }

    /** 할 일·일정·메모 전체 목록과 검색창입니다. */
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
        String query = safe(queryValue).trim().toLowerCase(Locale.KOREA);
        int shown = 0;
        for (WorkItemRecord item : items) {
            if (!type.equals(item.type)) continue;
            String haystack = (safe(item.title) + " " + safe(item.original)).toLowerCase(Locale.KOREA);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            list.addView(compactCard(item, "할 일".equals(type)), matchWithBottom(8));
            shown++;
        }
        if (shown == 0) list.addView(emptyCard("조건에 맞는 " + type + "이 없습니다."));
    }

    /** 선택 날짜의 일정과 할 일을 보여주는 간단 달력 화면입니다. */
    private View buildCalendar() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(10), dp(16), 0);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);

        Button previous = button("‹", Color.WHITE, PRIMARY, 24);
        previous.setOnClickListener(v -> moveDate(-1));
        nav.addView(previous, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text(koreanDate(parseDate(selectedDate)), 18, TEXT);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nav.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button today = button("오늘", PRIMARY_LIGHT, PRIMARY, 14);
        today.setOnClickListener(v -> {
            selectedDate = LocalDate.now().toString();
            render();
        });
        nav.addView(today, new LinearLayout.LayoutParams(dp(66), dp(44)));

        Button next = button("›", Color.WHITE, PRIMARY, 24);
        next.setOnClickListener(v -> moveDate(1));
        nav.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));
        page.addView(nav, matchWithBottom(10));

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(96));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        int shown = 0;
        LocalDate date = parseDate(selectedDate);
        for (WorkItemRecord item : items) {
            if (item.completed) continue;
            if (!("일정".equals(item.type) || "할 일".equals(item.type))) continue;
            if (!occurs(item, date)) continue;
            body.addView(compactCard(item, "할 일".equals(item.type)), matchWithBottom(8));
            shown++;
        }
        if (shown == 0) body.addView(emptyCard("이 날짜에는 일정과 할 일이 없습니다."));
        return page;
    }

    /** 카드 터치 시 읽기 화면으로 이동하고 할 일 완료 버튼을 제공합니다. */
    private View compactCard(WorkItemRecord item, boolean showComplete) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(13), dp(10), dp(13));
        card.setBackground(shape(Color.WHITE, 17, BORDER, 1));

        LinearLayout textArea = new LinearLayout(this);
        textArea.setOrientation(LinearLayout.VERTICAL);

        TextView title = text(item.title.isEmpty() ? "제목 없음" : item.title, 16, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        textArea.addView(title);

        String info = join(displayDate(item.date), item.time);
        if (!info.isEmpty()) {
            TextView dateTime = text(info, 13, MUTED);
            dateTime.setPadding(0, dp(5), 0, 0);
            textArea.addView(dateTime);
        }
        card.addView(textArea, new LinearLayout.LayoutParams(0, -2, 1f));

        card.setOnClickListener(v -> {
            int index = WorkItemStore.findBestIndex(items, item.title, item.date);
            Intent intent = new Intent(this, WorkItemDetailActivity.class);
            intent.putExtra(WorkItemDetailActivity.EXTRA_INDEX, index);
            startActivity(intent);
        });

        if (showComplete && "할 일".equals(item.type)) {
            Button complete = button(item.completed ? "✓" : "○",
                    item.completed ? Color.rgb(232, 236, 242) : Color.rgb(255, 245, 230),
                    item.completed ? Color.GRAY : TASK, 20);
            complete.setContentDescription(item.completed ? "완료 취소" : "할 일 완료");
            complete.setOnClickListener(v -> {
                item.completed = !item.completed;
                WorkItemStore.save(this, items);
                loadItems();
                render();
            });
            card.addView(complete, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        return card;
    }

    private void addFloatingButton() {
        Button add = button("＋", PRIMARY, Color.WHITE, 30);
        add.setContentDescription("빠른 입력, 길게 누르면 여러 항목 관리");
        add.setElevation(dp(7));
        add.setOnClickListener(v -> openUnifiedInput(null));
        add.setOnLongClickListener(v -> {
            startActivity(new Intent(this, WorkItemManagerActivity.class));
            return true;
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(64), dp(64));
        params.gravity = Gravity.END | Gravity.BOTTOM;
        params.setMargins(0, 0, dp(18), dp(18));
        content.addView(add, params);
    }

    private void openUnifiedInput(String prefill) {
        Intent intent = new Intent(this, UnifiedQuickInputActivityV4.class);
        if ("할 일".equals(menu)) intent.putExtra(UnifiedQuickInputActivity.EXTRA_DEFAULT_TYPE, "할 일");
        if ("일정".equals(menu) || "달력".equals(menu)) intent.putExtra(UnifiedQuickInputActivity.EXTRA_DEFAULT_TYPE, "일정");
        if ("메모".equals(menu)) intent.putExtra(UnifiedQuickInputActivity.EXTRA_DEFAULT_TYPE, "메모");
        if ("달력".equals(menu)) intent.putExtra(UnifiedQuickInputActivity.EXTRA_DEFAULT_DATE, selectedDate);
        if (prefill != null && !prefill.trim().isEmpty()) {
            intent.putExtra(UnifiedQuickInputActivityV2.EXTRA_PREFILL_TEXT, prefill);
            intent.putExtra(UnifiedQuickInputActivityV2.EXTRA_PREFILL_SOURCE, "음성 입력");
        }
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 2401 || resultCode != RESULT_OK || data == null) return;
        String value = safe(data.getStringExtra(VoiceCaptureActivityV2.EXTRA_RESULT_TEXT)).trim();
        if (!value.isEmpty()) openUnifiedInput(value);
    }

    private void showManagementMenu() {
        new AlertDialog.Builder(this)
                .setTitle("설정 및 관리")
                .setItems(new String[]{"AI 설정", "백업·복원", "여러 항목 관리", "오늘로 이동"},
                        (dialog, which) -> {
                            if (which == 0) startActivity(new Intent(this, AiSettingsActivity.class));
                            else if (which == 1) startActivity(new Intent(this, BackupActivity.class));
                            else if (which == 2) startActivity(new Intent(this, WorkItemManagerActivity.class));
                            else {
                                selectedDate = LocalDate.now().toString();
                                menu = "달력";
                                render();
                            }
                        }).show();
    }

    private List<WorkItemRecord> todaySchedules() {
        LocalDate today = LocalDate.now();
        List<WorkItemRecord> result = new ArrayList<>();
        for (WorkItemRecord item : items) {
            if (!item.completed && "일정".equals(item.type) && occurs(item, today)) result.add(item);
        }
        result.sort(Comparator.comparing(value -> parseTime(value.time),
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    private List<WorkItemRecord> openTasks() {
        List<WorkItemRecord> result = new ArrayList<>();
        for (WorkItemRecord item : items) {
            if ("할 일".equals(item.type) && !item.completed) result.add(item);
        }
        result.sort(Comparator.comparing(value -> safe(value.date)));
        return result;
    }

    private WorkItemRecord nearestSchedule(List<WorkItemRecord> schedules) {
        if (schedules.isEmpty()) return null;
        LocalTime now = LocalTime.now();
        for (WorkItemRecord item : schedules) {
            LocalTime time = parseTime(item.time);
            if (time != null && !time.isBefore(now)) return item;
        }
        return schedules.get(0);
    }

    private boolean occurs(WorkItemRecord item, LocalDate target) {
        LocalDate start = parseDate(item.date);
        if (start == null || target == null || target.isBefore(start)) return false;
        LocalDate end = parseDate(item.repeatEndDate);
        if (end != null && target.isAfter(end)) return false;
        if (target.equals(start)) return true;

        String repeat = safe(item.repeatType);
        switch (repeat) {
            case "DAILY": return true;
            case "WEEKLY": return start.getDayOfWeek() == target.getDayOfWeek();
            case "MONTHLY": return start.getDayOfMonth() == target.getDayOfMonth();
            case "WEEKDAYS":
                DayOfWeek day = target.getDayOfWeek();
                return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
            default: return false;
        }
    }

    private void moveDate(int days) {
        LocalDate value = parseDate(selectedDate);
        selectedDate = (value == null ? LocalDate.now() : value).plusDays(days).toString();
        render();
    }

    private void updateTabs() {
        for (TextView tab : tabs) {
            boolean selected = menu.equals(String.valueOf(tab.getTag()));
            tab.setTextColor(selected ? PRIMARY : MUTED);
            tab.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            tab.setBackground(shape(selected ? PRIMARY_LIGHT : Color.WHITE,
                    17, selected ? Color.rgb(195, 213, 247) : Color.WHITE, selected ? 1 : 0));
        }
    }

    private TextView metric(String label, int value) {
        TextView view = text(label + "\n" + value, 14, TEXT);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setBackground(shape(Color.WHITE, 17, BORDER, 1));
        return view;
    }

    private TextView section(String value) {
        TextView view = text(value, 18, TEXT);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(dp(1), dp(8), 0, dp(8));
        return view;
    }

    private TextView emptyCard(String value) {
        TextView view = text(value, 15, MUTED);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(24), dp(12), dp(24));
        view.setBackground(shape(Color.WHITE, 17, BORDER, 1));
        return view;
    }

    private LinearLayout page() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(10), dp(16), dp(18));
        return page;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String value, int background, int foreground, int size) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(size);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setBackground(shape(background, 15, background, 0));
        return button;
    }

    private GradientDrawable shape(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWithBottom(int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams weightedHeight(int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(height), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private View spacer(int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return view;
    }

    private String koreanDate(LocalDate date) {
        if (date == null) date = LocalDate.now();
        return date.getYear() + "년 " + date.getMonthValue() + "월 " + date.getDayOfMonth() + "일";
    }

    private String displayDate(String date) {
        LocalDate value = parseDate(date);
        if (value == null) return safe(date);
        return value.getMonthValue() + "월 " + value.getDayOfMonth() + "일";
    }

    private LocalDate parseDate(String value) {
        try { return LocalDate.parse(safe(value), DateTimeFormatter.ISO_LOCAL_DATE); }
        catch (DateTimeParseException ignored) { return null; }
    }

    private LocalTime parseTime(String value) {
        try { return LocalTime.parse(safe(value), DateTimeFormatter.ofPattern("H:mm")); }
        catch (DateTimeParseException ignored) { return null; }
    }

    private String join(String first, String second) {
        String a = safe(first).trim();
        String b = safe(second).trim();
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a + " · " + b;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
