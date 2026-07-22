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
import android.widget.CalendarView;
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
 * MyBrain AI v1.2 메인 화면입니다.
 * 메시지를 일정·할 일·메모로 분류하고 항목별 알림 시간을 관리합니다.
 */
public class MainActivity extends Activity {
    private static final String PREFS = "mybrain_data";
    private static final String KEY_ITEMS = "items";

    /** 화면에 표시할 알림 선택 문구입니다. */
    private static final String[] REMINDER_LABELS = {
            "알림 없음", "정각", "5분 전", "10분 전", "30분 전", "1시간 전"
    };

    /** 문구와 같은 순서로 저장되는 실제 사전 알림 시간(분)입니다. */
    private static final int[] REMINDER_MINUTES = {-1, 0, 5, 10, 30, 60};

    private final List<Item> items = new ArrayList<>();
    private LinearLayout listArea;
    private TextView summary;
    private EditText searchInput;
    private CalendarView calendarView;
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

        TextView title = text("MyBrain AI", 28, Color.rgb(20, 45, 80));
        root.addView(title, fullWrap());

        TextView version = text("v1.2 · 일정·할 일·메모·알림 통합 관리", 14, Color.DKGRAY);
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
        String[] tabNames = {"전체", "일정", "할 일", "메모", "달력"};
        for (String name : tabNames) {
            Button tab = new Button(this);
            tab.setText(name);
            tab.setTextSize(12);
            tab.setAllCaps(false);
            tab.setOnClickListener(v -> selectTab(name));
            tabs.addView(tab, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        root.addView(tabs, fullWrap());

        calendarView = new CalendarView(this);
        calendarView.setVisibility(View.GONE);
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedDate = String.format(Locale.KOREA, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            refreshList();
        });
        root.addView(calendarView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        listArea = new LinearLayout(this);
        listArea.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listArea, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        refreshList();
    }

    /** 상단 탭을 변경하고 목록을 다시 그립니다. */
    private void selectTab(String tab) {
        selectedTab = tab;
        if ("달력".equals(tab)) {
            calendarView.setVisibility(View.VISIBLE);
            selectedDate = formatDate(new Date(calendarView.getDate()));
        } else {
            calendarView.setVisibility(View.GONE);
            selectedDate = "";
        }
        refreshList();
    }

    /** 공유하거나 직접 입력한 메시지를 분석하는 창입니다. */
    private void showInputDialog(String preset) {
        EditText input = new EditText(this);
        input.setMinLines(6);
        input.setGravity(Gravity.TOP);
        input.setHint("예: 내일 오후 2시 교무실에서 회의가 있습니다.");
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

    /** 분석 결과를 사용자가 확인한 뒤 저장합니다. */
    private void analyzeAndConfirm(String raw) {
        String value = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) {
            Toast.makeText(this, "분석할 메시지를 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        Analysis result = analyze(value);
        showItemEditor(null, result.type, result.title, result.date, result.time, value, 0);
    }

    /** 새 항목과 기존 항목을 같은 화면에서 저장·수정합니다. */
    private void showItemEditor(Item target, String typeValue, String titleValue,
                                String dateValue, String timeValue, String originalValue,
                                int reminderMinutesValue) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), 0, dp(18), 0);

        EditText type = field("분류: 일정 / 할 일 / 메모", typeValue);
        EditText itemTitle = field("제목", titleValue);
        EditText date = field("날짜: 2026-07-22", dateValue);
        EditText time = field("시간: 14:00", timeValue);
        EditText original = field("원문", originalValue);

        TextView reminderTitle = text("알림 시간", 14, Color.DKGRAY);
        reminderTitle.setPadding(0, dp(8), 0, 0);
        Spinner reminderSpinner = new Spinner(this);
        ArrayAdapter<String> reminderAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                REMINDER_LABELS
        );
        reminderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reminderSpinner.setAdapter(reminderAdapter);
        reminderSpinner.setSelection(reminderIndex(reminderMinutesValue));

        form.addView(type);
        form.addView(itemTitle);
        form.addView(date);
        form.addView(time);
        form.addView(reminderTitle);
        form.addView(reminderSpinner, fullWrap());
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
                    if (target == null) items.add(0, item);
                    saveItems();
                    refreshList();
                })
                .show();
    }

    private EditText field(String hint, String value) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value == null ? "" : value);
        field.setSingleLine(true);
        return field;
    }

    /** 기기 내부에서 동작하는 간단한 규칙 기반 분석기입니다. */
    private Analysis analyze(String text) {
        Analysis result = new Analysis();
        result.date = extractDate(text);
        result.time = extractTime(text);
        String[] taskWords = {"제출", "작성", "확인", "준비", "보내", "전달", "신청", "완료", "처리", "연락"};
        String[] scheduleWords = {"회의", "연수", "수업", "상담", "면담", "행사", "출장", "약속", "모임", "방문"};
        boolean task = containsAny(text, taskWords) || text.contains("까지") || text.contains("마감");
        boolean schedule = containsAny(text, scheduleWords) || (!result.date.isEmpty() && !result.time.isEmpty());
        result.type = task ? "할 일" : (schedule ? "일정" : "메모");
        String title = text.replaceAll("[.!?]", "").trim();
        result.title = title.length() > 40 ? title.substring(0, 40) + "…" : title;
        return result;
    }

    private String extractDate(String text) {
        Calendar cal = Calendar.getInstance();
        if (text.contains("모레")) cal.add(Calendar.DAY_OF_MONTH, 2);
        else if (text.contains("내일")) cal.add(Calendar.DAY_OF_MONTH, 1);
        else if (text.contains("오늘")) return formatDate(cal.getTime());
        else {
            Matcher full = Pattern.compile("(20\\d{2})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일").matcher(text);
            if (full.find()) return String.format(Locale.KOREA, "%s-%02d-%02d", full.group(1), Integer.parseInt(full.group(2)), Integer.parseInt(full.group(3)));
            Matcher shortDate = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일").matcher(text);
            if (shortDate.find()) return String.format(Locale.KOREA, "%04d-%02d-%02d", cal.get(Calendar.YEAR), Integer.parseInt(shortDate.group(1)), Integer.parseInt(shortDate.group(2)));
            return "";
        }
        return formatDate(cal.getTime());
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

    /** 현재 탭, 검색어, 달력 날짜에 맞는 항목만 표시합니다. */
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
        if ("달력".equals(selectedTab) && !selectedDate.equals(item.date)) return false;
        if (query.isEmpty()) return true;
        return item.title.toLowerCase(Locale.KOREA).contains(query)
                || item.original.toLowerCase(Locale.KOREA).contains(query);
    }

    /** 한 개 항목의 카드와 수정·완료·삭제 버튼을 만듭니다. */
    private View createCard(Item item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(Color.WHITE);

        String done = item.completed ? "✓ " : "";
        TextView header = text(done + "[" + item.type + "] " + item.title, 17,
                item.completed ? Color.GRAY : Color.rgb(20, 45, 80));
        card.addView(header, fullWrap());

        String info = joinInfo(item.date, item.time);
        if (!info.isEmpty()) {
            TextView meta = text(info, 14, Color.rgb(29, 99, 216));
            meta.setPadding(0, dp(5), 0, dp(2));
            card.addView(meta, fullWrap());
        }

        if (("일정".equals(item.type) || "할 일".equals(item.type)) && !item.time.isEmpty()) {
            TextView reminder = text("알림: " + reminderLabel(item.reminderMinutes), 13, Color.GRAY);
            reminder.setPadding(0, 0, 0, dp(5));
            card.addView(reminder, fullWrap());
        }

        TextView original = text(item.original, 14, Color.DKGRAY);
        card.addView(original, fullWrap());

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button edit = smallButton("수정");
        edit.setOnClickListener(v -> showItemEditor(
                item,
                item.type,
                item.title,
                item.date,
                item.time,
                item.original,
                item.reminderMinutes
        ));
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

    /**
     * 기존 데이터와 호환되는 탭 구분 저장 형식입니다.
     * 일곱 번째 값에 사전 알림 시간(분)을 저장합니다.
     */
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
                    .append(item.reminderMinutes);
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
            items.add(item);
        }
    }

    private int parseReminderMinutes(String value) {
        try {
            int minutes = Integer.parseInt(value);
            for (int allowed : REMINDER_MINUTES) {
                if (allowed == minutes) return minutes;
            }
        } catch (NumberFormatException ignored) {
            // 손상된 값은 기존 버전과 같은 정각 알림으로 복구합니다.
        }
        return 0;
    }

    private int reminderIndex(int minutes) {
        for (int i = 0; i < REMINDER_MINUTES.length; i++) {
            if (REMINDER_MINUTES[i] == minutes) return i;
        }
        return 1;
    }

    private String reminderLabel(int minutes) {
        return REMINDER_LABELS[reminderIndex(minutes)];
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
    }

    private static class Item {
        String type = "메모";
        String title = "";
        String date = "";
        String time = "";
        String original = "";
        boolean completed = false;
        int reminderMinutes = 0;
    }
}
