package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MyBrain AI 메인 화면입니다.
 * 일정·할 일·메모·알림·반복 일정과 날짜별 표시 달력을 관리합니다.
 */
public class MainActivity extends Activity {
    private static final String PREFS = "mybrain_data";
    private static final String KEY_ITEMS = "items";

    private static final String[] REMINDER_LABELS = {
            "알림 없음", "정각", "5분 전", "10분 전", "30분 전", "1시간 전"
    };
    private static final int[] REMINDER_MINUTES = {-1, 0, 5, 10, 30, 60};

    private static final String[] REPEAT_LABELS = {
            "반복 없음", "매일", "매주", "매월", "평일"
    };
    private static final String[] REPEAT_VALUES = {
            "NONE", "DAILY", "WEEKLY", "MONTHLY", "WEEKDAYS"
    };

    private final List<Item> items = new ArrayList<>();
    private LinearLayout listArea;
    private TextView summary;
    private EditText searchInput;
    private EventCalendarView calendarView;
    private String selectedTab = "전체";
    private String selectedDate = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadItems();
        buildScreen();
        receiveSharedText(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        receiveSharedText(intent);
    }

    /** 앱의 전체 화면을 코드로 구성합니다. */
    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.setBackgroundColor(Color.rgb(247, 249, 252));

        root.addView(text("MyBrain AI", 28, Color.rgb(20, 45, 80)), fullWrap());
        TextView version = text("v1.3.2 · 일정 표시 달력·알림·위젯", 14, Color.DKGRAY);
        version.setPadding(0, dp(2), 0, dp(10));
        root.addView(version, fullWrap());

        summary = text("", 16, Color.rgb(29, 99, 216));
        summary.setPadding(dp(12), dp(12), dp(12), dp(12));
        summary.setBackgroundColor(Color.WHITE);
        root.addView(summary, fullWrap());

        Button addButton = new Button(this);
        addButton.setText("+ 메시지 분석 및 추가");
        addButton.setAllCaps(false);
        addButton.setOnClickListener(v -> showInputDialog(""));
        LinearLayout.LayoutParams addParams = fullWrap();
        addParams.setMargins(0, dp(10), 0, dp(6));
        root.addView(addButton, addParams);

        searchInput = new EditText(this);
        searchInput.setHint("제목 또는 원문 검색");
        searchInput.setSingleLine(true);
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            refreshList();
            return true;
        });
        root.addView(searchInput, fullWrap());

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        for (String name : new String[]{"전체", "일정", "할 일", "메모", "달력"}) {
            Button tab = new Button(this);
            tab.setText(name);
            tab.setTextSize(12);
            tab.setAllCaps(false);
            tab.setOnClickListener(v -> selectTab(name));
            tabs.addView(tab, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        root.addView(tabs, fullWrap());

        calendarView = new EventCalendarView(this);
        calendarView.setVisibility(View.GONE);
        calendarView.setEventCountProvider(this::countEventsOnDate);
        calendarView.setOnDateSelectedListener(date -> {
            selectedDate = date;
            refreshList();
        });
        root.addView(calendarView, fullWrap());

        ScrollView scroll = new ScrollView(this);
        listArea = new LinearLayout(this);
        listArea.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listArea, fullWrap());
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        refreshList();
    }

    private void selectTab(String tab) {
        selectedTab = tab;
        if ("달력".equals(tab)) {
            calendarView.setVisibility(View.VISIBLE);
            if (selectedDate.isEmpty()) selectedDate = formatDate(new Date());
            calendarView.setSelectedDate(selectedDate);
        } else {
            calendarView.setVisibility(View.GONE);
            selectedDate = "";
        }
        refreshList();
    }

    private void showInputDialog(String preset) {
        EditText input = new EditText(this);
        input.setMinLines(6);
        input.setGravity(Gravity.TOP);
        input.setHint("예: 매주 월요일 오후 2시 교무실 회의");
        input.setText(preset);
        input.setSelection(input.getText().length());

        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(18), 0, dp(18), 0);
        holder.addView(input, fullWrap());

        new AlertDialog.Builder(this)
                .setTitle("메시지 분석")
                .setView(holder)
                .setNegativeButton("취소", null)
                .setPositiveButton("분석", (dialog, which) -> analyzeAndConfirm(input.getText().toString()))
                .show();
    }

    private void analyzeAndConfirm(String raw) {
        String value = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) {
            Toast.makeText(this, "분석할 메시지를 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        Analysis result = analyze(value);
        showItemEditor(null, result.type, result.title, result.date, result.time,
                value, 0, result.repeatType, "");
    }

    /** 새 항목과 기존 항목을 같은 화면에서 저장·수정합니다. */
    private void showItemEditor(Item target, String typeValue, String titleValue,
                                String dateValue, String timeValue, String originalValue,
                                int reminderMinutesValue, String repeatTypeValue,
                                String repeatEndDateValue) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), 0, dp(18), 0);

        EditText type = field("분류: 일정 / 할 일 / 메모", typeValue);
        EditText itemTitle = field("제목", titleValue);
        EditText date = field("시작 날짜: 2026-07-22", dateValue);
        EditText time = field("시간: 14:00", timeValue);
        TextView reminderTitle = text("알림 시간", 14, Color.DKGRAY);
        Spinner reminderSpinner = spinner(REMINDER_LABELS, reminderIndex(reminderMinutesValue));
        TextView repeatTitle = text("반복 설정", 14, Color.DKGRAY);
        repeatTitle.setPadding(0, dp(8), 0, 0);
        Spinner repeatSpinner = spinner(REPEAT_LABELS, repeatIndex(repeatTypeValue));
        EditText repeatEndDate = field("반복 종료일: 2026-12-31 (비워두면 계속)", repeatEndDateValue);
        EditText original = field("원문", originalValue);

        form.addView(type);
        form.addView(itemTitle);
        form.addView(date);
        form.addView(time);
        form.addView(reminderTitle);
        form.addView(reminderSpinner, fullWrap());
        form.addView(repeatTitle);
        form.addView(repeatSpinner, fullWrap());
        form.addView(repeatEndDate);
        form.addView(original);

        new AlertDialog.Builder(this)
                .setTitle(target == null ? "분석 결과 확인" : "항목 수정")
                .setView(form)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> {
                    Item item = target == null ? new Item() : target;
                    item.type = normalizeType(type.getText().toString());
                    item.title = itemTitle.getText().toString().trim();
                    item.date = date.getText().toString().trim();
                    item.time = time.getText().toString().trim();
                    item.original = original.getText().toString().trim();
                    item.reminderMinutes = REMINDER_MINUTES[reminderSpinner.getSelectedItemPosition()];
                    item.repeatType = REPEAT_VALUES[repeatSpinner.getSelectedItemPosition()];
                    item.repeatEndDate = repeatEndDate.getText().toString().trim();
                    if (target == null) items.add(0, item);
                    saveItems();
                    refreshList();
                })
                .show();
    }

    private Spinner spinner(String[] labels, int selection) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selection);
        return spinner;
    }

    private EditText field(String hint, String value) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value == null ? "" : value);
        field.setSingleLine(true);
        return field;
    }

    /** 기기 내부에서 동작하는 규칙 기반 분석기입니다. */
    private Analysis analyze(String text) {
        Analysis result = new Analysis();
        result.date = extractDate(text);
        result.time = extractTime(text);
        result.repeatType = extractRepeatType(text);

        String[] taskWords = {"제출", "작성", "확인", "준비", "보내", "전달", "신청", "완료", "처리", "연락"};
        String[] scheduleWords = {"회의", "연수", "수업", "상담", "면담", "행사", "출장", "약속", "모임", "방문"};
        boolean task = containsAny(text, taskWords) || text.contains("까지") || text.contains("마감");
        boolean schedule = containsAny(text, scheduleWords) || (!result.date.isEmpty() && !result.time.isEmpty());
        result.type = task ? "할 일" : (schedule ? "일정" : "메모");
        String title = text.replaceAll("[.!?]", "").trim();
        result.title = title.length() > 40 ? title.substring(0, 40) + "…" : title;
        return result;
    }

    private String extractRepeatType(String text) {
        if (text.contains("평일마다") || text.contains("매주 평일")) return "WEEKDAYS";
        if (text.contains("매일")) return "DAILY";
        if (text.contains("매주")) return "WEEKLY";
        if (text.contains("매월") || text.contains("매달")) return "MONTHLY";
        return "NONE";
    }

    private String extractDate(String text) {
        Calendar cal = Calendar.getInstance();
        if (text.contains("모레")) cal.add(Calendar.DAY_OF_MONTH, 2);
        else if (text.contains("내일")) cal.add(Calendar.DAY_OF_MONTH, 1);
        else if (text.contains("오늘")) return formatDate(cal.getTime());
        else {
            Matcher full = Pattern.compile("(20\\d{2})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일").matcher(text);
            if (full.find()) return String.format(Locale.KOREA, "%s-%02d-%02d",
                    full.group(1), Integer.parseInt(full.group(2)), Integer.parseInt(full.group(3)));
            Matcher shortDate = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일").matcher(text);
            if (shortDate.find()) return String.format(Locale.KOREA, "%04d-%02d-%02d",
                    cal.get(Calendar.YEAR), Integer.parseInt(shortDate.group(1)), Integer.parseInt(shortDate.group(2)));
            String weekday = extractWeekdayDate(text, cal);
            if (!weekday.isEmpty()) return weekday;
            return "";
        }
        return formatDate(cal.getTime());
    }

    /** 매주 월요일 같은 표현의 첫 시작일을 계산합니다. */
    private String extractWeekdayDate(String text, Calendar now) {
        String[] names = {"일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일"};
        for (int i = 0; i < names.length; i++) {
            if (!text.contains(names[i])) continue;
            int target = Calendar.SUNDAY + i;
            int add = (target - now.get(Calendar.DAY_OF_WEEK) + 7) % 7;
            if (add == 0 && !text.contains("오늘")) add = 7;
            now.add(Calendar.DAY_OF_MONTH, add);
            return formatDate(now.getTime());
        }
        return "";
    }

    private String extractTime(String text) {
        Matcher matcher = Pattern.compile("(오전|오후)?\\s*(\\d{1,2})시(?:\\s*(\\d{1,2})분)?").matcher(text);
        if (!matcher.find()) return "";
        int hour = Integer.parseInt(matcher.group(2));
        int minute = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        if ("오후".equals(matcher.group(1)) && hour < 12) hour += 12;
        if ("오전".equals(matcher.group(1)) && hour == 12) hour = 0;
        return String.format(Locale.KOREA, "%02d:%02d", hour, minute);
    }

    private void refreshList() {
        if (listArea == null) return;
        listArea.removeAllViews();
        int schedules = 0, tasks = 0, memos = 0, completed = 0;
        for (Item item : items) {
            if ("일정".equals(item.type)) schedules++;
            else if ("할 일".equals(item.type)) {
                tasks++;
                if (item.completed) completed++;
            } else memos++;
        }
        summary.setText("일정 " + schedules + " · 할 일 " + tasks + "(완료 " + completed + ") · 메모 " + memos);
        if (calendarView != null) calendarView.refreshEvents();

        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.KOREA);
        int shown = 0;
        for (Item item : items) {
            if (!matches(item, query)) continue;
            shown++;
            listArea.addView(createCard(item), cardParams());
        }
        if (shown == 0) {
            TextView empty = text("조건에 맞는 항목이 없습니다.", 16, Color.DKGRAY);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(38), dp(12), dp(38));
            listArea.addView(empty, fullWrap());
        }
    }

    private boolean matches(Item item, String query) {
        if (!"전체".equals(selectedTab) && !"달력".equals(selectedTab) && !selectedTab.equals(item.type)) return false;
        if ("달력".equals(selectedTab) && !occursOnDate(item, selectedDate)) return false;
        if (query.isEmpty()) return true;
        return item.title.toLowerCase(Locale.KOREA).contains(query)
                || item.original.toLowerCase(Locale.KOREA).contains(query);
    }

    /** 달력에 표시할 일정과 미완료 할 일의 개수를 계산합니다. */
    private int countEventsOnDate(String targetDate) {
        int count = 0;
        for (Item item : items) {
            if (!("일정".equals(item.type) || "할 일".equals(item.type))) continue;
            if ("할 일".equals(item.type) && item.completed) continue;
            if (occursOnDate(item, targetDate)) count++;
        }
        return count;
    }

    /** 달력에서 선택한 날짜에 반복 일정이 발생하는지 판단합니다. */
    private boolean occursOnDate(Item item, String targetDate) {
        if (item.date.equals(targetDate)) return true;
        if ("NONE".equals(item.repeatType) || item.date.isEmpty() || targetDate.isEmpty()) return false;
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

    private View createCard(Item item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(Color.WHITE);

        String done = item.completed ? "✓ " : "";
        card.addView(text(done + "[" + item.type + "] " + item.title, 17,
                item.completed ? Color.GRAY : Color.rgb(20, 45, 80)), fullWrap());
        String info = joinInfo(item.date, item.time);
        if (!info.isEmpty()) card.addView(text(info, 14, Color.rgb(29, 99, 216)), fullWrap());
        if (!"NONE".equals(item.repeatType)) {
            String end = item.repeatEndDate.isEmpty() ? "계속" : item.repeatEndDate + "까지";
            card.addView(text("반복: " + repeatLabel(item.repeatType) + " · " + end, 13, Color.GRAY), fullWrap());
        }
        if (("일정".equals(item.type) || "할 일".equals(item.type)) && !item.time.isEmpty()) {
            card.addView(text("알림: " + reminderLabel(item.reminderMinutes), 13, Color.GRAY), fullWrap());
        }
        card.addView(text(item.original, 14, Color.DKGRAY), fullWrap());

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button edit = smallButton("수정");
        edit.setOnClickListener(v -> showItemEditor(item, item.type, item.title, item.date,
                item.time, item.original, item.reminderMinutes, item.repeatType, item.repeatEndDate));
        buttons.addView(edit, new LinearLayout.LayoutParams(0, dp(46), 1f));

        if ("할 일".equals(item.type)) {
            Button complete = smallButton(item.completed ? "완료 취소" : "완료");
            complete.setOnClickListener(v -> {
                item.completed = !item.completed;
                saveItems();
                refreshList();
            });
            buttons.addView(complete, new LinearLayout.LayoutParams(0, dp(46), 1f));
        }

        Button delete = smallButton("삭제");
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("항목 삭제")
                .setMessage("이 항목을 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (d, w) -> {
                    items.remove(item);
                    saveItems();
                    refreshList();
                }).show());
        buttons.addView(delete, new LinearLayout.LayoutParams(0, dp(46), 1f));
        card.addView(buttons, fullWrap());
        return card;
    }

    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = fullWrap();
        params.setMargins(0, 0, 0, dp(10));
        return params;
    }

    private void receiveSharedText(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null && !shared.trim().isEmpty()) showInputDialog(shared);
        }
    }

    /** 기존 저장 형식을 유지해 업데이트 설치 시 자료가 보존됩니다. */
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
                    .append(escape(item.repeatEndDate));
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_ITEMS, output.toString()).apply();
    }

    private void loadItems() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String stored = prefs.getString(KEY_ITEMS, "");
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
            item.reminderMinutes = parseReminderMinutes(values.length >= 7 ? values[6] : "0");
            item.repeatType = normalizeRepeat(values.length >= 8 ? values[7] : "NONE");
            item.repeatEndDate = values.length >= 9 ? unescape(values[8]) : "";
            items.add(item);
        }
    }

    private int parseReminderMinutes(String value) {
        try {
            int minutes = Integer.parseInt(value);
            for (int allowed : REMINDER_MINUTES) if (allowed == minutes) return minutes;
        } catch (NumberFormatException ignored) {
            // 손상된 값은 정각 알림으로 복구합니다.
        }
        return 0;
    }

    private String normalizeRepeat(String value) {
        for (String allowed : REPEAT_VALUES) if (allowed.equals(value)) return value;
        return "NONE";
    }

    private int reminderIndex(int minutes) {
        for (int i = 0; i < REMINDER_MINUTES.length; i++) if (REMINDER_MINUTES[i] == minutes) return i;
        return 1;
    }

    private int repeatIndex(String value) {
        for (int i = 0; i < REPEAT_VALUES.length; i++) if (REPEAT_VALUES[i].equals(value)) return i;
        return 0;
    }

    private String reminderLabel(int minutes) { return REMINDER_LABELS[reminderIndex(minutes)]; }
    private String repeatLabel(String value) { return REPEAT_LABELS[repeatIndex(value)]; }

    private long parseDate(String value) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
            format.setLenient(false);
            Date parsed = format.parse(value);
            return parsed == null ? -1L : parsed.getTime();
        } catch (Exception e) {
            return -1L;
        }
    }

    private String escape(String value) {
        return (value == null ? "" : value).replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
    }

    private boolean containsAny(String text, String[] words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }

    private String normalizeType(String value) {
        String text = value == null ? "" : value.trim();
        if (text.contains("할")) return "할 일";
        if (text.contains("일정")) return "일정";
        return "메모";
    }

    private String joinInfo(String date, String time) {
        if (date == null || date.isEmpty()) return time == null ? "" : time;
        if (time == null || time.isEmpty()) return date;
        return date + " · " + time;
    }

    private String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(date);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class Analysis {
        String type = "메모";
        String title = "";
        String date = "";
        String time = "";
        String repeatType = "NONE";
    }

    private static class Item {
        String type = "메모";
        String title = "";
        String date = "";
        String time = "";
        String original = "";
        boolean completed = false;
        int reminderMinutes = 0;
        String repeatType = "NONE";
        String repeatEndDate = "";
    }
}
