package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 새 업무 입력과 기존 업무 수정을 담당하는 전체 화면입니다.
 * 날짜·시간 선택, 초안 자동 보존, 일정 중복 경고를 지원합니다.
 */
public class WorkItemEditorActivity extends Activity {
    public static final String EXTRA_INDEX = "item_index";
    public static final String EXTRA_TYPE = "default_type";
    public static final String EXTRA_DATE = "default_date";

    private static final String DRAFT_PREFS = "mybrain_editor_draft";
    private static final String[] TYPES = {"메모", "할 일", "일정"};
    private static final String[] REMINDER_LABELS = {
            "알림 없음", "정각", "5분 전", "10분 전", "30분 전", "1시간 전"
    };
    private static final int[] REMINDER_VALUES = {-1, 0, 5, 10, 30, 60};
    private static final String[] REPEAT_LABELS = {"반복 없음", "매일", "매주", "매월", "평일"};
    private static final String[] REPEAT_VALUES = {"NONE", "DAILY", "WEEKLY", "MONTHLY", "WEEKDAYS"};

    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BORDER = Color.rgb(220, 228, 240);
    private static final int BACKGROUND = Color.rgb(247, 249, 253);

    private List<WorkItemRecord> items;
    private WorkItemRecord draft;
    private WorkItemRecord original;
    private int targetIndex = -1;
    private boolean dirty;
    private boolean binding = true;

    private Spinner typeSpinner;
    private EditText titleField;
    private Button dateButton;
    private Button timeButton;
    private EditText contentField;
    private Spinner reminderSpinner;
    private Spinner repeatSpinner;
    private Button repeatEndButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        items = WorkItemStore.load(this);
        targetIndex = getIntent().getIntExtra(EXTRA_INDEX, -1);
        prepareDraft();
        buildScreen();
        bindDraft();
        binding = false;
    }

    private void prepareDraft() {
        if (targetIndex >= 0 && targetIndex < items.size()) {
            original = items.get(targetIndex).copy();
            draft = original.copy();
            return;
        }

        draft = new WorkItemRecord();
        draft.type = safe(getIntent().getStringExtra(EXTRA_TYPE));
        if (draft.type.isEmpty()) draft.type = "메모";
        draft.date = safe(getIntent().getStringExtra(EXTRA_DATE));

        SharedPreferences saved = getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE);
        if (saved.getBoolean("exists", false)) {
            draft.type = saved.getString("type", draft.type);
            draft.title = saved.getString("title", "");
            draft.date = saved.getString("date", draft.date);
            draft.time = saved.getString("time", "");
            draft.original = saved.getString("content", "");
            draft.reminderMinutes = saved.getInt("reminder", 0);
            draft.repeatType = saved.getString("repeat", "NONE");
            draft.repeatEndDate = saved.getString("repeat_end", "");
            Toast.makeText(this, "작성 중이던 내용을 복구했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.setBackgroundColor(Color.WHITE);

        Button back = button("‹", Color.WHITE, TEXT);
        back.setTextSize(25);
        back.setOnClickListener(v -> handleBack());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text(targetIndex < 0 ? "새 항목" : "항목 수정", 22, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button save = button("저장", PRIMARY, Color.WHITE);
        save.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        save.setOnClickListener(v -> requestSave());
        header.addView(save, new LinearLayout.LayoutParams(dp(72), dp(46)));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(14), dp(18), dp(40));
        scroll.addView(form);

        typeSpinner = spinner(TYPES);
        form.addView(labeled("종류", typeSpinner));

        titleField = field("제목", true);
        form.addView(labeled("제목", titleField));

        LinearLayout dateTime = new LinearLayout(this);
        dateTime.setOrientation(LinearLayout.HORIZONTAL);
        dateButton = choiceButton("날짜 선택");
        dateButton.setOnClickListener(v -> showDatePicker(false));
        dateTime.addView(dateButton, weighted());
        timeButton = choiceButton("시간 선택");
        timeButton.setOnClickListener(v -> showTimePicker());
        dateTime.addView(timeButton, weighted());
        form.addView(labeled("날짜와 시간", dateTime));

        contentField = field("기록할 내용을 입력하세요.", false);
        contentField.setMinLines(7);
        contentField.setGravity(Gravity.TOP);
        form.addView(labeled("내용", contentField));

        reminderSpinner = spinner(REMINDER_LABELS);
        form.addView(labeled("알림", reminderSpinner));

        repeatSpinner = spinner(REPEAT_LABELS);
        form.addView(labeled("반복", repeatSpinner));

        repeatEndButton = choiceButton("반복 종료일 없음");
        repeatEndButton.setOnClickListener(v -> showDatePicker(true));
        form.addView(labeled("반복 종료", repeatEndButton));

        TextView help = text("입력 내용은 자동으로 임시 저장됩니다. 저장하지 않고 나가도 다음 작성 때 복구할 수 있습니다.", 13, MUTED);
        help.setPadding(dp(2), dp(18), dp(2), 0);
        form.addView(help);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        installChangeListeners();
    }

    private void bindDraft() {
        typeSpinner.setSelection(indexOf(TYPES, draft.type, 0));
        titleField.setText(draft.title);
        contentField.setText(draft.original);
        reminderSpinner.setSelection(indexOf(REMINDER_VALUES, draft.reminderMinutes, 1));
        repeatSpinner.setSelection(indexOf(REPEAT_VALUES, draft.repeatType, 0));
        updateDateButtons();
    }

    private void installChangeListeners() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { markChanged(); }
        };
        titleField.addTextChangedListener(watcher);
        contentField.addTextChangedListener(watcher);

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                markChanged();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        };
        typeSpinner.setOnItemSelectedListener(listener);
        reminderSpinner.setOnItemSelectedListener(listener);
        repeatSpinner.setOnItemSelectedListener(listener);
    }

    private void markChanged() {
        if (binding) return;
        dirty = true;
        readForm();
        saveDraftLocally();
    }

    private void readForm() {
        draft.type = String.valueOf(typeSpinner.getSelectedItem());
        draft.title = titleField.getText().toString().trim();
        draft.original = contentField.getText().toString().trim();
        draft.reminderMinutes = REMINDER_VALUES[reminderSpinner.getSelectedItemPosition()];
        draft.repeatType = REPEAT_VALUES[repeatSpinner.getSelectedItemPosition()];
        if ("NONE".equals(draft.repeatType)) draft.repeatEndDate = "";
    }

    private void requestSave() {
        readForm();
        if (draft.title.isEmpty()) {
            titleField.setError("제목을 입력하세요.");
            titleField.requestFocus();
            return;
        }
        if (("일정".equals(draft.type) || "할 일".equals(draft.type)) && draft.date.isEmpty()) {
            Toast.makeText(this, "날짜를 선택하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (WorkItemStore.hasScheduleConflict(items, targetIndex, draft.date, draft.time)
                && "일정".equals(draft.type)) {
            new AlertDialog.Builder(this)
                    .setTitle("같은 시간의 일정이 있습니다")
                    .setMessage("동일한 날짜와 시간에 다른 일정이 있습니다. 그래도 저장할까요?")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("저장", (dialog, which) -> saveWithRepeatScope())
                    .show();
            return;
        }
        saveWithRepeatScope();
    }

    private void saveWithRepeatScope() {
        if (targetIndex >= 0 && original != null && !"NONE".equals(original.repeatType)) {
            new AlertDialog.Builder(this)
                    .setTitle("반복 일정 수정")
                    .setItems(new String[]{"전체 반복 일정 수정", "새 단일 일정으로 저장"},
                            (dialog, which) -> {
                                if (which == 0) commit(false);
                                else commit(true);
                            })
                    .setNegativeButton("취소", null)
                    .show();
        } else {
            commit(false);
        }
    }

    private void commit(boolean asSingleCopy) {
        if (asSingleCopy) {
            WorkItemRecord single = draft.copy();
            single.repeatType = "NONE";
            single.repeatEndDate = "";
            items.add(0, single);
        } else if (targetIndex >= 0 && targetIndex < items.size()) {
            items.set(targetIndex, draft.copy());
        } else {
            items.add(0, draft.copy());
        }
        WorkItemStore.save(this, items);
        clearSavedDraft();
        dirty = false;
        setResult(RESULT_OK);
        finish();
    }

    private void showDatePicker(boolean repeatEnd) {
        Calendar calendar = parseDate(repeatEnd ? draft.repeatEndDate : draft.date);
        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, day, 0, 0, 0);
            String value = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(selected.getTime());
            if (repeatEnd) draft.repeatEndDate = value;
            else draft.date = value;
            dirty = true;
            updateDateButtons();
            saveDraftLocally();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker() {
        Calendar calendar = Calendar.getInstance();
        if (draft.time != null && draft.time.matches("\\d{2}:\\d{2}")) {
            try {
                calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(draft.time.substring(0, 2)));
                calendar.set(Calendar.MINUTE, Integer.parseInt(draft.time.substring(3, 5)));
            } catch (Exception ignored) { }
        }
        new TimePickerDialog(this, (view, hour, minute) -> {
            draft.time = String.format(Locale.KOREA, "%02d:%02d", hour, minute);
            dirty = true;
            updateDateButtons();
            saveDraftLocally();
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
    }

    private void updateDateButtons() {
        dateButton.setText(draft.date == null || draft.date.isEmpty() ? "날짜 선택" : draft.date);
        timeButton.setText(draft.time == null || draft.time.isEmpty() ? "시간 없음" : draft.time);
        repeatEndButton.setText(draft.repeatEndDate == null || draft.repeatEndDate.isEmpty()
                ? "반복 종료일 없음" : draft.repeatEndDate);
    }

    private void saveDraftLocally() {
        if (targetIndex >= 0) return;
        getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE).edit()
                .putBoolean("exists", true)
                .putString("type", draft.type)
                .putString("title", draft.title)
                .putString("date", draft.date)
                .putString("time", draft.time)
                .putString("content", draft.original)
                .putInt("reminder", draft.reminderMinutes)
                .putString("repeat", draft.repeatType)
                .putString("repeat_end", draft.repeatEndDate)
                .apply();
    }

    private void clearSavedDraft() {
        getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE).edit().clear().apply();
    }

    private void handleBack() {
        if (!dirty) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("작성 중인 내용")
                .setMessage("저장하지 않고 나갈까요? 새 항목은 임시 보관됩니다.")
                .setNegativeButton("계속 작성", null)
                .setNeutralButton("초안 삭제", (dialog, which) -> {
                    clearSavedDraft();
                    dirty = false;
                    finish();
                })
                .setPositiveButton("나가기", (dialog, which) -> {
                    readForm();
                    saveDraftLocally();
                    dirty = false;
                    finish();
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private View labeled(String label, View control) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(label, 13, MUTED);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setPadding(0, dp(12), 0, dp(5));
        holder.addView(name);
        holder.addView(control);
        return holder;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackground(shape(Color.WHITE, 14, BORDER, 1));
        spinner.setPadding(dp(10), 0, dp(10), 0);
        spinner.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(52)));
        return spinner;
    }

    private EditText field(String hint, boolean singleLine) {
        EditText value = new EditText(this);
        value.setHint(hint);
        value.setSingleLine(singleLine);
        value.setTextSize(16);
        value.setTextColor(TEXT);
        value.setPadding(dp(14), dp(10), dp(14), dp(10));
        value.setBackground(shape(Color.WHITE, 14, BORDER, 1));
        value.setLayoutParams(new LinearLayout.LayoutParams(-1, singleLine ? dp(54) : -2));
        return value;
    }

    private Button choiceButton(String label) {
        Button value = button(label, Color.WHITE, TEXT);
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setPadding(dp(14), 0, dp(10), 0);
        return value;
    }

    private Button button(String label, int background, int color) {
        Button value = new Button(this);
        value.setText(label);
        value.setTextSize(15);
        value.setTextColor(color);
        value.setAllCaps(false);
        value.setMinimumWidth(0);
        value.setMinimumHeight(0);
        value.setBackground(shape(background, 14, BORDER, 1));
        return value;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable shape(int fill, int radius, int stroke, int width) {
        GradientDrawable value = new GradientDrawable();
        value.setColor(fill);
        value.setCornerRadius(dp(radius));
        if (width > 0) value.setStroke(dp(width), stroke);
        return value;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams value = new LinearLayout.LayoutParams(0, dp(52), 1f);
        value.setMargins(dp(2), 0, dp(2), 0);
        return value;
    }

    private Calendar parseDate(String value) {
        Calendar calendar = Calendar.getInstance();
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(value);
            if (parsed != null) calendar.setTime(parsed);
        } catch (Exception ignored) { }
        return calendar;
    }

    private int indexOf(String[] values, String target, int fallback) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(target)) return i;
        return fallback;
    }

    private int indexOf(int[] values, int target, int fallback) {
        for (int i = 0; i < values.length; i++) if (values[i] == target) return i;
        return fallback;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
