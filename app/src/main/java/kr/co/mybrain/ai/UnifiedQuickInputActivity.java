package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MyBrain AI 1.8.8 통합 빠른 입력 화면입니다.
 * 텍스트·음성·문서 촬영·사진 OCR을 한 입력창에서 이어서 사용하고,
 * 분석 결과를 칩 형태로 확인한 뒤 한 번에 저장합니다.
 */
public class UnifiedQuickInputActivity extends Activity {
    public static final String EXTRA_DEFAULT_TYPE = "unified_default_type";
    public static final String EXTRA_DEFAULT_DATE = "unified_default_date";
    public static final String EXTRA_START_MODE = "unified_start_mode";

    private static final int REQUEST_CAPTURE = 3201;
    private static final String DRAFT_PREFS = "mybrain_unified_input_draft";
    private static final String[] TYPES = {"메모", "할 일", "일정"};
    private static final int[] REMINDERS = {-1, 0, 5, 10, 30, 60, 1440};
    private static final String[] REPEATS = {"NONE", "DAILY", "WEEKLY", "MONTHLY", "WEEKDAYS"};

    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int PRIMARY_LIGHT = Color.rgb(235, 242, 255);
    private static final int TEXT = Color.rgb(28, 38, 52);
    private static final int MUTED = Color.rgb(102, 116, 138);
    private static final int BORDER = Color.rgb(220, 228, 240);
    private static final int BACKGROUND = Color.rgb(247, 249, 253);
    private static final int SUCCESS = Color.rgb(35, 135, 86);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable previewRunnable = this::updatePreview;
    private final Runnable finishRunnable = this::finishAfterSave;

    private LinearLayout root;
    private EditText input;
    private TextView previewTitle;
    private TextView previewDetail;
    private Button typeChip;
    private Button dateChip;
    private Button timeChip;
    private Button reminderChip;
    private Button repeatChip;
    private Button saveButton;
    private LinearLayout undoBar;
    private TextView undoMessage;

    private String contextType = "";
    private String contextDate = "";
    private String manualType;
    private String manualDate;
    private String manualTime;
    private String manualRepeat;
    private Integer manualReminder;
    private boolean structureEdited;
    private boolean binding;
    private List<WorkItemRecord> undoSnapshot;
    private boolean savePendingUndo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        contextType = safe(getIntent().getStringExtra(EXTRA_DEFAULT_TYPE));
        contextDate = safe(getIntent().getStringExtra(EXTRA_DEFAULT_DATE));
        buildScreen();
        restoreDraft();
        updatePreview();

        String startMode = safe(getIntent().getStringExtra(EXTRA_START_MODE));
        if (!startMode.isEmpty()) {
            root.post(() -> launchCapture(startMode));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!savePendingUndo) saveDraft();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(previewRunnable);
        handler.removeCallbacks(finishRunnable);
        super.onDestroy();
    }

    private void buildScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.setBackgroundColor(Color.WHITE);

        Button close = button("‹", Color.WHITE, TEXT);
        close.setTextSize(25);
        close.setContentDescription("닫기");
        close.setOnClickListener(v -> handleClose());
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text("빠른 입력", 22, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(14), dp(16), dp(34));
        scroll.addView(body);

        TextView guide = text("무엇을 기록할까요?", 19, TEXT);
        guide.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        body.addView(guide);

        TextView hint = text("자연스럽게 입력하면 종류·날짜·시간을 자동으로 정리합니다.", 13, MUTED);
        hint.setPadding(0, dp(5), 0, dp(10));
        body.addView(hint);

        input = new EditText(this);
        input.setHint("예: 내일 오후 3시 교무회의 30분 전에 알려줘");
        input.setTextSize(17);
        input.setTextColor(TEXT);
        input.setHintTextColor(Color.rgb(145, 156, 174));
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(4);
        input.setMaxLines(10);
        input.setPadding(dp(16), dp(14), dp(16), dp(14));
        input.setBackground(rounded(Color.WHITE, 18, BORDER, 1));
        body.addView(input, new LinearLayout.LayoutParams(-1, dp(154)));

        LinearLayout mediaRow = new LinearLayout(this);
        mediaRow.setOrientation(LinearLayout.HORIZONTAL);
        mediaRow.setPadding(0, dp(9), 0, dp(10));
        mediaRow.addView(mediaButton("🎤 음성", QuickInputActivity.MODE_VOICE), weighted(dp(46)));
        mediaRow.addView(mediaButton("📷 촬영", QuickInputActivity.MODE_CAMERA), weighted(dp(46)));
        mediaRow.addView(mediaButton("🖼 사진", QuickInputActivity.MODE_GALLERY), weighted(dp(46)));
        body.addView(mediaRow);

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(14), dp(12), dp(14), dp(12));
        preview.setBackground(rounded(Color.WHITE, 18, BORDER, 1));

        previewTitle = text("입력하면 자동 분석 결과가 표시됩니다.", 16, TEXT);
        previewTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        preview.addView(previewTitle);

        previewDetail = text("종류와 날짜가 맞는지만 확인한 뒤 저장하세요.", 13, MUTED);
        previewDetail.setPadding(0, dp(4), 0, dp(9));
        preview.addView(previewDetail);

        LinearLayout firstChips = new LinearLayout(this);
        firstChips.setOrientation(LinearLayout.HORIZONTAL);
        typeChip = chip("메모");
        typeChip.setOnClickListener(v -> cycleType());
        firstChips.addView(typeChip, weighted(dp(44)));
        dateChip = chip("날짜 없음");
        dateChip.setOnClickListener(v -> pickDate());
        dateChip.setOnLongClickListener(v -> {
            manualDate = "";
            structureEdited = true;
            updatePreview();
            return true;
        });
        firstChips.addView(dateChip, weighted(dp(44)));
        timeChip = chip("시간 없음");
        timeChip.setOnClickListener(v -> pickTime());
        timeChip.setOnLongClickListener(v -> {
            manualTime = "";
            structureEdited = true;
            updatePreview();
            return true;
        });
        firstChips.addView(timeChip, weighted(dp(44)));
        preview.addView(firstChips);

        LinearLayout secondChips = new LinearLayout(this);
        secondChips.setOrientation(LinearLayout.HORIZONTAL);
        reminderChip = chip("알림 없음");
        reminderChip.setOnClickListener(v -> cycleReminder());
        secondChips.addView(reminderChip, weighted(dp(44)));
        repeatChip = chip("반복 없음");
        repeatChip.setOnClickListener(v -> cycleRepeat());
        secondChips.addView(repeatChip, weighted(dp(44)));
        Button detail = chip("상세 설정");
        detail.setOnClickListener(v -> openDetailedEditor());
        secondChips.addView(detail, weighted(dp(44)));
        preview.addView(secondChips);

        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, -2);
        previewParams.setMargins(0, 0, 0, dp(12));
        body.addView(preview, previewParams);

        saveButton = button("분석 결과로 저장", PRIMARY, Color.WHITE);
        saveButton.setTextSize(16);
        saveButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        saveButton.setOnClickListener(v -> requestSave());
        body.addView(saveButton, new LinearLayout.LayoutParams(-1, dp(56)));

        TextView help = text("날짜가 없는 메모는 바로 저장할 수 있습니다. 날짜·시간 칩을 길게 누르면 값을 지울 수 있습니다.", 12, MUTED);
        help.setPadding(dp(2), dp(10), dp(2), 0);
        body.addView(help);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        buildUndoBar();
        setContentView(root);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (binding) return;
                saveDraft();
                handler.removeCallbacks(previewRunnable);
                handler.postDelayed(previewRunnable, 180L);
            }
        });
    }

    private void buildUndoBar() {
        undoBar = new LinearLayout(this);
        undoBar.setOrientation(LinearLayout.HORIZONTAL);
        undoBar.setGravity(Gravity.CENTER_VERTICAL);
        undoBar.setPadding(dp(14), dp(10), dp(10), dp(10));
        undoBar.setBackgroundColor(Color.rgb(36, 47, 64));
        undoBar.setVisibility(View.GONE);

        undoMessage = text("저장했습니다.", 14, Color.WHITE);
        undoBar.addView(undoMessage, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button undo = button("실행 취소", Color.TRANSPARENT, Color.rgb(143, 190, 255));
        undo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        undo.setOnClickListener(v -> undoSave());
        undoBar.addView(undo, new LinearLayout.LayoutParams(dp(104), dp(44)));
        root.addView(undoBar);
    }

    private Button mediaButton(String label, String mode) {
        Button value = button(label, PRIMARY_LIGHT, PRIMARY);
        value.setTextSize(13);
        value.setOnClickListener(v -> launchCapture(mode));
        return value;
    }

    private void launchCapture(String mode) {
        Intent intent = new Intent(this, QuickInputActivity.class);
        intent.putExtra(QuickInputActivity.EXTRA_MODE, mode);
        intent.putExtra(QuickInputActivity.EXTRA_RETURN_RESULT, true);
        startActivityForResult(intent, REQUEST_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTURE || resultCode != RESULT_OK || data == null) return;
        String captured = safe(data.getStringExtra(QuickInputActivity.EXTRA_RESULT_TEXT)).trim();
        if (captured.isEmpty()) return;
        String current = input.getText().toString().trim();
        input.setText(current.isEmpty() ? captured : current + "\n" + captured);
        input.setSelection(input.length());
        input.requestFocus();
        updatePreview();
    }

    private void updatePreview() {
        String raw = input.getText().toString().trim();
        AiAnalysisResult result = resolvedResult(raw);
        List<AiAnalysisResult> range = raw.isEmpty() ? new ArrayList<>()
                : KoreanScheduleRangeParser.parse(raw, new Date());

        int count = !structureEdited && range.size() > 1 ? range.size() : 1;
        if (raw.isEmpty()) {
            previewTitle.setText("입력하면 자동 분석 결과가 표시됩니다.");
            previewDetail.setText("텍스트·음성·사진을 한 입력창에서 함께 사용할 수 있습니다.");
            saveButton.setEnabled(false);
            saveButton.setAlpha(0.55f);
        } else {
            previewTitle.setText(count > 1 ? result.type + " " + count + "건으로 정리" : result.title);
            previewDetail.setText(summary(result, count));
            saveButton.setEnabled(true);
            saveButton.setAlpha(1f);
        }

        typeChip.setText(emptyDefault(result.type, "메모"));
        dateChip.setText(result.date.isEmpty() ? "날짜 없음" : displayDate(result.date));
        timeChip.setText(result.time.isEmpty() ? "시간 없음" : result.time);
        reminderChip.setText(reminderLabel(resolvedReminder(raw)));
        repeatChip.setText(repeatLabel(emptyDefault(result.repeatType, "NONE")));
    }

    private AiAnalysisResult resolvedResult(String raw) {
        AiAnalysisResult result = QuickInputParser.parseSingle(raw);
        if (manualType != null) result.type = manualType;
        else if (!contextType.isEmpty() && (raw.isEmpty() || "메모".equals(result.type))) result.type = contextType;

        if (manualDate != null) result.date = manualDate;
        else if (result.date.isEmpty() && !contextDate.isEmpty()) result.date = contextDate;

        if (manualTime != null) result.time = manualTime;
        if (manualRepeat != null) result.repeatType = manualRepeat;
        if (result.title == null || result.title.trim().isEmpty()) result.title = "새 기록";
        result.content = raw;
        return result;
    }

    private int resolvedReminder(String raw) {
        if (manualReminder != null) return manualReminder;
        String value = safe(raw).replaceAll("\\s+", "");
        if (value.contains("1일전") || value.contains("하루전")) return 1440;
        if (value.contains("1시간전")) return 60;
        if (value.contains("30분전")) return 30;
        if (value.contains("10분전")) return 10;
        if (value.contains("5분전")) return 5;
        if (value.contains("정각") || value.contains("시작할때")) return 0;
        return -1;
    }

    private String summary(AiAnalysisResult result, int count) {
        StringBuilder value = new StringBuilder();
        if (!result.date.isEmpty()) value.append(displayDate(result.date));
        if (!result.time.isEmpty()) {
            if (value.length() > 0) value.append(" · ");
            value.append(result.time);
        }
        if (!"NONE".equals(result.repeatType)) {
            if (value.length() > 0) value.append(" · ");
            value.append(repeatLabel(result.repeatType));
        }
        if (count > 1) {
            if (value.length() > 0) value.append(" · ");
            value.append(count).append("건");
        }
        if (value.length() == 0) value.append("날짜 없는 ").append(result.type);
        return value.toString();
    }

    private void cycleType() {
        AiAnalysisResult current = resolvedResult(input.getText().toString().trim());
        int index = indexOf(TYPES, current.type);
        manualType = TYPES[(index + 1) % TYPES.length];
        structureEdited = true;
        updatePreview();
        saveDraft();
    }

    private void pickDate() {
        Calendar calendar = parseDate(resolvedResult(input.getText().toString()).date);
        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, day, 0, 0, 0);
            manualDate = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(selected.getTime());
            structureEdited = true;
            updatePreview();
            saveDraft();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickTime() {
        AiAnalysisResult result = resolvedResult(input.getText().toString());
        Calendar calendar = Calendar.getInstance();
        if (result.time != null && result.time.matches("\\d{2}:\\d{2}")) {
            try {
                calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(result.time.substring(0, 2)));
                calendar.set(Calendar.MINUTE, Integer.parseInt(result.time.substring(3, 5)));
            } catch (Exception ignored) { }
        }
        new TimePickerDialog(this, (view, hour, minute) -> {
            manualTime = String.format(Locale.KOREA, "%02d:%02d", hour, minute);
            structureEdited = true;
            updatePreview();
            saveDraft();
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
    }

    private void cycleReminder() {
        int current = resolvedReminder(input.getText().toString());
        int index = indexOf(REMINDERS, current);
        manualReminder = REMINDERS[(index + 1) % REMINDERS.length];
        updatePreview();
        saveDraft();
    }

    private void cycleRepeat() {
        String current = resolvedResult(input.getText().toString()).repeatType;
        int index = indexOf(REPEATS, current);
        manualRepeat = REPEATS[(index + 1) % REPEATS.length];
        structureEdited = true;
        updatePreview();
        saveDraft();
    }

    private void openDetailedEditor() {
        String raw = input.getText().toString().trim();
        if (raw.isEmpty()) {
            input.setError("내용을 입력하세요.");
            input.requestFocus();
            return;
        }
        clearDraft();
        QuickInputPrefill.openEditor(this, resolvedResult(raw));
    }

    private void requestSave() {
        hideKeyboard();
        String raw = input.getText().toString().trim();
        if (raw.isEmpty()) {
            input.setError("내용을 입력하세요.");
            input.requestFocus();
            return;
        }

        List<AiAnalysisResult> range = KoreanScheduleRangeParser.parse(raw, new Date());
        List<AiAnalysisResult> results = new ArrayList<>();
        if (!structureEdited && range.size() > 1) results.addAll(range);
        else results.add(resolvedResult(raw));

        List<WorkItemRecord> existing = WorkItemStore.load(this);
        int conflicts = countConflicts(existing, results);
        if (conflicts > 0) {
            new AlertDialog.Builder(this)
                    .setTitle("같은 시간의 일정이 있습니다")
                    .setMessage("기존 일정과 시간이 겹치는 항목이 " + conflicts + "건 있습니다. 그래도 저장할까요?")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("저장", (dialog, which) -> commit(results))
                    .show();
        } else {
            commit(results);
        }
    }

    private int countConflicts(List<WorkItemRecord> existing, List<AiAnalysisResult> results) {
        int count = 0;
        for (AiAnalysisResult result : results) {
            if ("일정".equals(result.type)
                    && WorkItemStore.hasScheduleConflict(existing, -1, result.date, result.time)) count++;
        }
        return count;
    }

    private void commit(List<AiAnalysisResult> results) {
        List<WorkItemRecord> items = WorkItemStore.load(this);
        undoSnapshot = copyItems(items);
        int reminder = resolvedReminder(input.getText().toString());

        for (int index = results.size() - 1; index >= 0; index--) {
            AiAnalysisResult result = results.get(index);
            WorkItemRecord item = new WorkItemRecord();
            item.type = emptyDefault(result.type, "메모");
            item.title = emptyDefault(result.title, "새 기록");
            item.date = safe(result.date);
            item.time = safe(result.time);
            item.original = input.getText().toString().trim();
            item.completed = false;
            item.reminderMinutes = reminder;
            item.repeatType = emptyDefault(result.repeatType, "NONE");
            items.add(0, item);
        }

        WorkItemStore.save(this, items);
        clearDraft();
        savePendingUndo = true;
        input.setEnabled(false);
        saveButton.setEnabled(false);
        saveButton.setAlpha(0.55f);
        undoMessage.setText(results.size() > 1
                ? results.size() + "건을 저장했습니다."
                : "저장했습니다.");
        undoBar.setVisibility(View.VISIBLE);
        handler.removeCallbacks(finishRunnable);
        handler.postDelayed(finishRunnable, 4200L);
    }

    private void undoSave() {
        handler.removeCallbacks(finishRunnable);
        if (undoSnapshot != null) WorkItemStore.save(this, undoSnapshot);
        savePendingUndo = false;
        undoBar.setVisibility(View.GONE);
        input.setEnabled(true);
        saveButton.setEnabled(true);
        saveButton.setAlpha(1f);
        Toast.makeText(this, "저장을 취소했습니다.", Toast.LENGTH_SHORT).show();
    }

    private void finishAfterSave() {
        if (!savePendingUndo) return;
        setResult(RESULT_OK);
        finish();
    }

    private void handleClose() {
        if (savePendingUndo) {
            finishAfterSave();
            return;
        }
        String raw = input.getText().toString().trim();
        if (raw.isEmpty()) {
            clearDraft();
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("작성 내용을 보관할까요?")
                .setMessage("다음에 빠른 입력을 열면 현재 내용을 이어서 작성할 수 있습니다.")
                .setNegativeButton("버리기", (dialog, which) -> {
                    clearDraft();
                    finish();
                })
                .setPositiveButton("임시 저장", (dialog, which) -> {
                    saveDraft();
                    finish();
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        handleClose();
    }

    private void restoreDraft() {
        binding = true;
        SharedPreferences draft = getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE);
        String text = draft.getString("text", "");
        if (text != null && !text.isEmpty()) {
            input.setText(text);
            input.setSelection(input.length());
            manualType = nullIfMissing(draft, "type");
            manualDate = nullIfMissing(draft, "date");
            manualTime = nullIfMissing(draft, "time");
            manualRepeat = nullIfMissing(draft, "repeat");
            if (draft.contains("reminder")) manualReminder = draft.getInt("reminder", -1);
            structureEdited = draft.getBoolean("structure_edited", false);
            Toast.makeText(this, "작성 중이던 내용을 복구했습니다.", Toast.LENGTH_SHORT).show();
        }
        binding = false;
    }

    private void saveDraft() {
        if (binding || savePendingUndo || input == null) return;
        getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE).edit()
                .putString("text", input.getText().toString())
                .putString("type", manualType == null ? "__NULL__" : manualType)
                .putString("date", manualDate == null ? "__NULL__" : manualDate)
                .putString("time", manualTime == null ? "__NULL__" : manualTime)
                .putString("repeat", manualRepeat == null ? "__NULL__" : manualRepeat)
                .putInt("reminder", manualReminder == null ? Integer.MIN_VALUE : manualReminder)
                .putBoolean("structure_edited", structureEdited)
                .apply();
    }

    private String nullIfMissing(SharedPreferences draft, String key) {
        String value = draft.getString(key, "__NULL__");
        return "__NULL__".equals(value) ? null : value;
    }

    private void clearDraft() {
        getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE).edit().clear().apply();
    }

    private List<WorkItemRecord> copyItems(List<WorkItemRecord> source) {
        List<WorkItemRecord> result = new ArrayList<>();
        for (WorkItemRecord item : source) result.add(item.copy());
        return result;
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) return;
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        focused.clearFocus();
    }

    private Button chip(String label) {
        Button value = button(label, PRIMARY_LIGHT, PRIMARY);
        value.setTextSize(12);
        value.setSingleLine(true);
        return value;
    }

    private Button button(String label, int background, int color) {
        Button value = new Button(this);
        value.setText(label);
        value.setTextColor(color);
        value.setTextSize(14);
        value.setAllCaps(false);
        value.setGravity(Gravity.CENTER);
        value.setMinimumHeight(0);
        value.setMinimumWidth(0);
        value.setPadding(dp(8), 0, dp(8), 0);
        value.setBackground(rounded(background, 14, background == Color.TRANSPARENT ? Color.TRANSPARENT : BORDER, background == Color.TRANSPARENT ? 0 : 1));
        return value;
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    private LinearLayout.LayoutParams weighted(int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, height, 1f);
        params.setMargins(dp(3), dp(2), dp(3), dp(2));
        return params;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private Calendar parseDate(String value) {
        Calendar calendar = Calendar.getInstance();
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) return calendar;
        try {
            calendar.set(Integer.parseInt(value.substring(0, 4)),
                    Integer.parseInt(value.substring(5, 7)) - 1,
                    Integer.parseInt(value.substring(8, 10)));
        } catch (Exception ignored) { }
        return calendar;
    }

    private String displayDate(String date) {
        if (date == null || date.length() < 10) return safe(date);
        try {
            return Integer.parseInt(date.substring(5, 7)) + "월 "
                    + Integer.parseInt(date.substring(8, 10)) + "일";
        } catch (Exception ignored) {
            return date;
        }
    }

    private String reminderLabel(int minutes) {
        if (minutes < 0) return "알림 없음";
        if (minutes == 0) return "정각 알림";
        if (minutes == 1440) return "1일 전";
        if (minutes == 60) return "1시간 전";
        return minutes + "분 전";
    }

    private String repeatLabel(String repeat) {
        if ("DAILY".equals(repeat)) return "매일";
        if ("WEEKLY".equals(repeat)) return "매주";
        if ("MONTHLY".equals(repeat)) return "매월";
        if ("WEEKDAYS".equals(repeat)) return "평일";
        return "반복 없음";
    }

    private int indexOf(String[] values, String target) {
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(target)) return index;
        }
        return 0;
    }

    private int indexOf(int[] values, int target) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == target) return index;
        }
        return 0;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String emptyDefault(String value, String fallback) {
        String safe = safe(value).trim();
        return safe.isEmpty() ? fallback : safe;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
