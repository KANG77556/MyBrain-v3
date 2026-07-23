package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
 * 일정·할 일·메모·알림·반복 일정·달력·항목별 색상을 관리합니다.
 */
public class MainActivity extends Activity {
    private static final String PREFS = "mybrain_data";
    private static final String KEY_ITEMS = "items";

    private static final int COLOR_PRIMARY = Color.rgb(35, 92, 190);
    private static final int COLOR_TEXT = Color.rgb(31, 42, 55);
    private static final int COLOR_MUTED = Color.rgb(104, 116, 132);
    private static final int COLOR_BACKGROUND = Color.rgb(244, 247, 251);
    private static final int COLOR_SCHEDULE = Color.rgb(37, 99, 235);
    private static final int COLOR_TASK = Color.rgb(234, 120, 35);
    private static final int COLOR_MEMO = Color.rgb(123, 86, 188);

    private static final String[] REMINDER_LABELS = {
            "알림 없음", "정각", "5분 전", "10분 전", "30분 전", "1시간 전"
    };
    private static final int[] REMINDER_MINUTES = {-1, 0, 5, 10, 30, 60};
    private static final String[] REPEAT_LABELS = {"반복 없음", "매일", "매주", "매월", "평일"};
    private static final String[] REPEAT_VALUES = {"NONE", "DAILY", "WEEKLY", "MONTHLY", "WEEKDAYS"};
    private static final String[] COLOR_LABELS = {"기본 색상", "파랑", "빨강", "주황", "초록", "보라", "회색"};
    private static final String[] COLOR_VALUES = {"DEFAULT", "BLUE", "RED", "ORANGE", "GREEN", "PURPLE", "GRAY"};

    private final List<Item> items = new ArrayList<>();
    private final List<Button> tabButtons = new ArrayList<>();
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
        root.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.setBackgroundColor(COLOR_BACKGROUND);

        TextView title = text("MyBrain AI", 28, Color.rgb(18, 48, 89));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, fullWrap());

        TextView version = text("v1.5.2 · 날짜·시간 내용 자동 정리", 13, COLOR_MUTED);
        version.setPadding(0, dp(2), 0, dp(10));
        root.addView(version, fullWrap());

        summary = text("", 15, COLOR_PRIMARY);
        summary.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        summary.setPadding(dp(14), dp(13), dp(14), dp(13));
        summary.setBackground(rounded(Color.WHITE, 16, Color.rgb(226, 232, 240), 1));
        root.addView(summary, fullWrap());

        Button addButton = actionButton("＋ 메시지 분석 및 추가", COLOR_PRIMARY, Color.WHITE);
        addButton.setOnClickListener(v -> showInputDialog(""));
        LinearLayout.LayoutParams addParams = fullWrap();
        addParams.setMargins(0, dp(10), 0, dp(8));
        root.addView(addButton, addParams);

        searchInput = new EditText(this);
        searchInput.setHint("제목 또는 메모 내용 검색");
        searchInput.setTextSize(16);
        searchInput.setSingleLine(true);
        searchInput.setPadding(dp(14), 0, dp(14), 0);
        searchInput.setBackground(rounded(Color.WHITE, 14, Color.rgb(210, 219, 232), 1));
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            refreshList();
            return true;
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        searchParams.setMargins(0, 0, 0, dp(10));
        root.addView(searchInput, searchParams);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabButtons.clear();
        for (String name : new String[]{"전체", "일정", "할 일", "메모", "달력"}) {
            Button tab = new Button(this);
            tab.setText(name);
            tab.setTextSize(12);
            tab.setAllCaps(false);
            tab.setPadding(0, 0, 0, 0);
            tab.setTag(name);
            tab.setOnClickListener(v -> selectTab(name));
            tabButtons.add(tab);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
            params.setMargins(dp(2), 0, dp(2), 0);
            tabs.addView(tab, params);
        }
        root.addView(tabs, fullWrap());
        updateTabStyles();

        calendarView = new EventCalendarView(this);
        calendarView.setVisibility(View.GONE);
        calendarView.setEventMarkerProvider(this::eventMarkerOnDate);
        calendarView.setOnDateSelectedListener(date -> {
            selectedDate = date;
            refreshList();
        });
        LinearLayout.LayoutParams calendarParams = fullWrap();
        calendarParams.setMargins(0, dp(8), 0, dp(8));
        root.addView(calendarView, calendarParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listArea = new LinearLayout(this);
        listArea.setOrientation(LinearLayout.VERTICAL);
        listArea.setPadding(0, dp(10), 0, dp(20));
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
        updateTabStyles();
        refreshList();
    }

    private void updateTabStyles() {
        for (Button button : tabButtons) {
            boolean selected = selectedTab.equals(String.valueOf(button.getTag()));
            button.setTextColor(selected ? Color.WHITE : COLOR_TEXT);
            button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            button.setBackground(rounded(selected ? COLOR_PRIMARY : Color.WHITE,
                    12, selected ? COLOR_PRIMARY : Color.rgb(218, 226, 237), 1));
        }
    }

    private void showInputDialog(String preset) {
        EditText input = new EditText(this);
        input.setMinLines(6);
        input.setGravity(Gravity.TOP);
        input.setHint("예: 다음 주 월요일 오후 2시 교무실 회의");
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
                result.content, 0, result.repeatType, "", "DEFAULT");
    }

    /** 새 항목과 기존 항목을 같은 화면에서 저장·수정합니다. */
    private void showItemEditor(Item target, String typeValue, String titleValue,
                                String dateValue, String timeValue, String originalValue,
                                int reminderMinutesValue, String repeatTypeValue,
                                String repeatEndDateValue, String colorValue) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), 0, dp(18), 0);

        EditText type = field("분류: 일정 / 할 일 / 메모", typeValue);
        EditText itemTitle = field("제목", titleValue);
        EditText date = field("시작 날짜: 2026-07-22", dateValue);
        EditText time = field("시간: 14:00", timeValue);
        TextView reminderTitle = text("알림 시간", 14, COLOR_MUTED);
        Spinner reminderSpinner = spinner(REMINDER_LABELS, reminderIndex(reminderMinutesValue));
        TextView repeatTitle = text("반복 설정", 14, COLOR_MUTED);
        repeatTitle.setPadding(0, dp(8), 0, 0);
        Spinner repeatSpinner = spinner(REPEAT_LABELS, repeatIndex(repeatTypeValue));
        EditText repeatEndDate = field("반복 종료일: 2026-12-31 (비워두면 계속)", repeatEndDateValue);
        TextView colorTitle = text("항목 색상", 14, COLOR_MUTED);
        colorTitle.setPadding(0, dp(8), 0, 0);
        Spinner colorSpinner = spinner(COLOR_LABELS, colorIndex(colorValue));
        EditText original = field("메모 내용", originalValue);

        form.addView(type);
        form.addView(itemTitle);
        form.addView(date);
        form.addView(time);
        form.addView(reminderTitle);
        form.addView(reminderSpinner, fullWrap());
        form.addView(repeatTitle);
        form.addView(repeatSpinner, fullWrap());
        form.addView(repeatEndDate);
        form.addView(colorTitle);
        form.addView(colorSpinner, fullWrap());
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
                    item.colorValue = COLOR_VALUES[colorSpinner.getSelectedItemPosition()];
                    if (target == null) items.add(0, item);
                    saveItems();
                    refreshList();
                }).show();
    }

    private Spinner spinner(String[] labels, int selection) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
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

        String[] taskWords = {"제출", "작성", "확인", "준비", "보내", "전달", "신청", "완료", "처리", "연락", "하기"};
        String[] scheduleWords = {"회의", "연수", "수업", "상담", "면담", "행사", "출장", "약속", "모임", "방문", "예약"};
        boolean task = containsAny(text, taskWords) || text.contains("까지") || text.contains("마감");
        boolean schedule = containsAny(text, scheduleWords) || (!result.date.isEmpty() && !result.time.isEmpty());
        result.type = task ? "할 일" : (schedule ? "일정" : "메모");

        // 날짜와 시간이 별도 필드로 인식된 경우 메모 내용에서는 해당 문구를 제거합니다.
        String cleaned = cleanRecognizedDateTime(text, !result.date.isEmpty(), !result.time.isEmpty());
        if (cleaned.isEmpty()) cleaned = defaultTitle(result.type);
        result.content = cleaned;
        result.title = cleaned.length() > 40 ? cleaned.substring(0, 40) + "…" : cleaned;
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
        Calendar now = Calendar.getInstance();
        if (text.contains("모레")) {
            now.add(Calendar.DAY_OF_MONTH, 2);
            return formatDate(now.getTime());
        }
        if (text.contains("내일")) {
            now.add(Calendar.DAY_OF_MONTH, 1);
            return formatDate(now.getTime());
        }
        if (text.contains("오늘")) return formatDate(now.getTime());

        Matcher iso = Pattern.compile("(20\\d{2})\\s*[-./]\\s*(\\d{1,2})\\s*[-./]\\s*(\\d{1,2})").matcher(text);
        if (iso.find()) return validDate(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3)));

        Matcher full = Pattern.compile("(20\\d{2})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일").matcher(text);
        if (full.find()) return validDate(Integer.parseInt(full.group(1)), Integer.parseInt(full.group(2)), Integer.parseInt(full.group(3)));

        Matcher shortDate = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일").matcher(text);
        if (shortDate.find()) return validDate(now.get(Calendar.YEAR), Integer.parseInt(shortDate.group(1)), Integer.parseInt(shortDate.group(2)));

        return extractWeekdayDate(text, now);
    }

    /** 이번 주·다음 주·다다음 주와 일반 요일 표현을 날짜로 변환합니다. */
    private String extractWeekdayDate(String text, Calendar now) {
        String[] names = {"월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"};
        int[] calendarDays = {
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        };
        for (int i = 0; i < names.length; i++) {
            if (!text.contains(names[i])) continue;
            Calendar target = (Calendar) now.clone();
            int weekOffset = -1;
            if (text.matches(".*다다음\\s*주.*")) weekOffset = 2;
            else if (text.matches(".*다음\\s*주.*")) weekOffset = 1;
            else if (text.matches(".*이번\\s*주.*")) weekOffset = 0;

            if (weekOffset >= 0) {
                int daysFromMonday = (target.get(Calendar.DAY_OF_WEEK) + 5) % 7;
                target.add(Calendar.DAY_OF_MONTH, -daysFromMonday + weekOffset * 7 + i);
            } else {
                int add = (calendarDays[i] - target.get(Calendar.DAY_OF_WEEK) + 7) % 7;
                if (add == 0) add = 7;
                target.add(Calendar.DAY_OF_MONTH, add);
            }
            return formatDate(target.getTime());
        }
        return "";
    }

    private String extractTime(String text) {
        if (text.contains("정오")) return "12:00";
        if (text.contains("자정")) return "00:00";

        Matcher colon = Pattern.compile("(오전|오후)?\\s*(\\d{1,2}):(\\d{2})").matcher(text);
        if (colon.find()) {
            int hour = Integer.parseInt(colon.group(2));
            int minute = Integer.parseInt(colon.group(3));
            hour = convertHour(hour, colon.group(1));
            return validTime(hour, minute);
        }

        Matcher korean = Pattern.compile("(오전|오후|아침|낮|저녁|밤|새벽)?\\s*(\\d{1,2})시(?:\\s*(?:(\\d{1,2})분|(반)))?").matcher(text);
        if (!korean.find()) return "";
        int hour = Integer.parseInt(korean.group(2));
        int minute = korean.group(3) == null ? (korean.group(4) == null ? 0 : 30) : Integer.parseInt(korean.group(3));
        hour = convertHour(hour, korean.group(1));
        return validTime(hour, minute);
    }

    private int convertHour(int hour, String period) {
        if (period == null || period.isEmpty()) return hour;
        if ("오후".equals(period) || "낮".equals(period) || "저녁".equals(period) || "밤".equals(period)) {
            if (hour < 12) hour += 12;
        } else if (hour == 12) {
            hour = 0;
        }
        return hour;
    }

    /** 인식에 성공한 날짜·시간 표현만 제목과 메모 내용에서 제거합니다. */
    private String cleanRecognizedDateTime(String text, boolean hasDate, boolean hasTime) {
        String cleaned = text == null ? "" : text;
        if (hasDate) {
            cleaned = remove(cleaned, "20\\d{2}년\\s*\\d{1,2}월\\s*\\d{1,2}일(?:까지|부터|에)?");
            cleaned = remove(cleaned, "20\\d{2}\\s*[-./]\\s*\\d{1,2}\\s*[-./]\\s*\\d{1,2}(?:까지|부터|에)?");
            cleaned = remove(cleaned, "\\d{1,2}월\\s*\\d{1,2}일(?:까지|부터|에)?");
            cleaned = remove(cleaned, "(?:오늘|내일|모레)(?:까지|부터|에)?");
            cleaned = remove(cleaned, "(?:(?:이번|다음|다다음)\\s*주\\s*)?[월화수목금토일]요일(?:까지|부터|에)?");
        }
        if (hasTime) {
            cleaned = remove(cleaned, "(?:오전|오후|아침|낮|저녁|밤|새벽)\\s*\\d{1,2}\\s*시(?:\\s*(?:\\d{1,2}\\s*분|반))?(?:까지|부터|에)?");
            cleaned = remove(cleaned, "(?:오전|오후)?\\s*\\d{1,2}:\\d{2}(?:까지|부터|에)?");
            cleaned = remove(cleaned, "\\d{1,2}\\s*시(?:\\s*(?:\\d{1,2}\\s*분|반))?(?:까지|부터|에)?");
            cleaned = remove(cleaned, "(?:정오|자정)(?:까지|부터|에)?");
        }
        cleaned = cleaned.replaceAll("[.!?]+$", "")
                .replaceAll("\\s*[,;|·]+\\s*", " ")
                .replaceAll("(^|\\s)[-–—]+(?=\\s|$)", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.replaceAll("^[,;:·\\-–—\\s]+|[,;:·\\-–—\\s]+$", "").trim();
    }

    private String remove(String value, String regex) {
        return value.replaceAll(regex, " ");
    }

    private String validDate(int year, int month, int day) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
            format.setLenient(false);
            Date parsed = format.parse(String.format(Locale.KOREA, "%04d-%02d-%02d", year, month, day));
            return parsed == null ? "" : format.format(parsed);
        } catch (Exception e) {
            return "";
        }
    }

    private String validTime(int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return "";
        return String.format(Locale.KOREA, "%02d:%02d", hour, minute);
    }

    private String defaultTitle(String type) {
        if ("일정".equals(type)) return "일정";
        if ("할 일".equals(type)) return "할 일";
        return "메모";
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
        summary.setText("일정 " + schedules + "  ·  할 일 " + tasks + " (완료 " + completed + ")  ·  메모 " + memos);
        if (calendarView != null) calendarView.refreshEvents();

        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.KOREA);
        int shown = 0;
        for (Item item : items) {
            if (!matches(item, query)) continue;
            shown++;
            listArea.addView(createCard(item), cardParams());
        }
        if (shown == 0) {
            TextView empty = text("조건에 맞는 항목이 없습니다.", 16, COLOR_MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(42), dp(12), dp(42));
            empty.setBackground(rounded(Color.WHITE, 16, Color.rgb(226, 232, 240), 1));
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

    /** 해당 날짜의 일정 개수와 첫 번째 항목 색상을 달력에 전달합니다. */
    private EventCalendarView.EventMarker eventMarkerOnDate(String targetDate) {
        int count = 0;
        int markerColor = COLOR_SCHEDULE;
        boolean colorSet = false;
        for (Item item : items) {
            if (!("일정".equals(item.type) || "할 일".equals(item.type))) continue;
            if ("할 일".equals(item.type) && item.completed) continue;
            if (!occursOnDate(item, targetDate)) continue;
            count++;
            if (!colorSet) {
                markerColor = itemColor(item);
                colorSet = true;
            }
        }
        return new EventCalendarView.EventMarker(count, markerColor);
    }

    private boolean occursOnDate(Item item, String targetDate) {
        if (item.date.equals(targetDate)) return true;
        if ("NONE".equals(item.repeatType) || item.date.isEmpty() || targetDate.isEmpty()) return false;
        long start = parseDate(item.date), target = parseDate(targetDate);
        if (start < 0 || target < start) return false;
        if (!item.repeatEndDate.isEmpty()) {
            long end = parseDate(item.repeatEndDate);
            if (end >= 0 && target > end) return false;
        }
        Calendar s = Calendar.getInstance(), t = Calendar.getInstance();
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
        int itemColor = itemColor(item);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setBackground(rounded(Color.WHITE, 16, Color.rgb(222, 228, 238), 1));

        View stripe = new View(this);
        stripe.setBackgroundColor(item.completed ? Color.LTGRAY : itemColor);
        outer.addView(stripe, new LinearLayout.LayoutParams(dp(6), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(12), dp(11));

        TextView badge = text(item.type, 12, item.completed ? Color.GRAY : itemColor);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(badge, fullWrap());

        String done = item.completed ? "✓ " : "";
        TextView cardTitle = text(done + (item.title.isEmpty() ? "제목 없음" : item.title), 17,
                item.completed ? Color.GRAY : COLOR_TEXT);
        cardTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        cardTitle.setPadding(0, dp(3), 0, dp(4));
        card.addView(cardTitle, fullWrap());

        String info = joinInfo(item.date, item.time);
        if (!info.isEmpty()) card.addView(text(info, 14, item.completed ? Color.GRAY : itemColor), fullWrap());
        if (!"NONE".equals(item.repeatType)) {
            String end = item.repeatEndDate.isEmpty() ? "계속" : item.repeatEndDate + "까지";
            card.addView(text("반복 · " + repeatLabel(item.repeatType) + " · " + end, 13, COLOR_MUTED), fullWrap());
        }
        if (("일정".equals(item.type) || "할 일".equals(item.type)) && !item.time.isEmpty()) {
            card.addView(text("알림 · " + reminderLabel(item.reminderMinutes), 13, COLOR_MUTED), fullWrap());
        }
        card.addView(text("색상 · " + colorLabel(item.colorValue), 13, COLOR_MUTED), fullWrap());
        if (!item.original.isEmpty() && !item.original.equals(item.title)) {
            TextView original = text(item.original, 13, COLOR_MUTED);
            original.setPadding(0, dp(5), 0, 0);
            card.addView(original, fullWrap());
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(8), 0, 0);
        Button edit = smallButton("수정", Color.rgb(239, 244, 251), COLOR_TEXT);
        edit.setOnClickListener(v -> showItemEditor(item, item.type, item.title, item.date,
                item.time, item.original, item.reminderMinutes, item.repeatType,
                item.repeatEndDate, item.colorValue));
        buttons.addView(edit, buttonParams());

        if ("할 일".equals(item.type)) {
            Button complete = smallButton(item.completed ? "완료 취소" : "완료", Color.rgb(255, 245, 230), COLOR_TASK);
            complete.setOnClickListener(v -> {
                item.completed = !item.completed;
                saveItems();
                refreshList();
            });
            buttons.addView(complete, buttonParams());
        }

        Button delete = smallButton("삭제", Color.rgb(255, 239, 239), Color.rgb(190, 52, 52));
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("항목 삭제")
                .setMessage("이 항목을 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (d, w) -> {
                    items.remove(item);
                    saveItems();
                    refreshList();
                }).show());
        buttons.addView(delete, buttonParams());
        card.addView(buttons, fullWrap());
        outer.addView(card, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return outer;
    }

    private int itemColor(Item item) {
        switch (normalizeColor(item.colorValue)) {
            case "BLUE": return Color.rgb(37, 99, 235);
            case "RED": return Color.rgb(220, 60, 60);
            case "ORANGE": return Color.rgb(234, 120, 35);
            case "GREEN": return Color.rgb(34, 150, 90);
            case "PURPLE": return Color.rgb(123, 86, 188);
            case "GRAY": return Color.rgb(110, 118, 130);
            default: return typeColor(item.type);
        }
    }

    private int typeColor(String type) {
        if ("일정".equals(type)) return COLOR_SCHEDULE;
        if ("할 일".equals(type)) return COLOR_TASK;
        return COLOR_MEMO;
    }

    private Button actionButton(String label, int background, int textColor) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(textColor);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackground(rounded(background, 14, background, 0));
        return button;
    }

    private Button smallButton(String label, int background, int textColor) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(textColor);
        button.setAllCaps(false);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setBackground(rounded(background, 10, background, 0));
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private GradientDrawable rounded(int fillColor, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fillColor);
        shape.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) shape.setStroke(dp(strokeDp), strokeColor);
        return shape;
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

    /** 기존 9개 열 뒤에 색상 열만 추가해 구버전 데이터와 호환합니다. */
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
                    .append(normalizeColor(item.colorValue));
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
            item.colorValue = normalizeColor(values.length >= 10 ? values[9] : "DEFAULT");
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

    private String normalizeColor(String value) {
        for (String allowed : COLOR_VALUES) if (allowed.equals(value)) return value;
        return "DEFAULT";
    }

    private int reminderIndex(int minutes) {
        for (int i = 0; i < REMINDER_MINUTES.length; i++) if (REMINDER_MINUTES[i] == minutes) return i;
        return 1;
    }

    private int repeatIndex(String value) {
        for (int i = 0; i < REPEAT_VALUES.length; i++) if (REPEAT_VALUES[i].equals(value)) return i;
        return 0;
    }

    private int colorIndex(String value) {
        for (int i = 0; i < COLOR_VALUES.length; i++) if (COLOR_VALUES[i].equals(normalizeColor(value))) return i;
        return 0;
    }

    private String reminderLabel(int minutes) { return REMINDER_LABELS[reminderIndex(minutes)]; }
    private String repeatLabel(String value) { return REPEAT_LABELS[repeatIndex(value)]; }
    private String colorLabel(String value) { return COLOR_LABELS[colorIndex(value)]; }

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
        String content = "";
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
        String colorValue = "DEFAULT";
    }
}
