package kr.co.mybrain.v2.ui;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import kr.co.mybrain.v2.assistant.ParsedWorkItem;
import kr.co.mybrain.v2.data.WorkItemEntity;

/** AI 분석 결과를 저장 전에 직접 수정하는 대화상자입니다. */
public final class WorkItemEditorDialog extends DialogFragment {

    public interface Listener {
        void onConfirmed(ParsedWorkItem item);
    }

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy.MM.dd (E) HH:mm", Locale.KOREA);

    private final ParsedWorkItem item;
    private final Listener listener;
    private final ZoneId zoneId;

    private Spinner typeSpinner;
    private Spinner repeatSpinner;
    private Spinner prioritySpinner;
    private EditText titleInput;
    private CheckBox allDayCheck;
    private Button startButton;
    private Button endButton;

    public WorkItemEditorDialog(ParsedWorkItem item, Listener listener) {
        this.item = item;
        this.listener = listener;
        this.zoneId = ZoneId.systemDefault();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Context context = requireContext();
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(10), dp(20), dp(8));
        scroll.addView(root);

        root.addView(label("분류"));
        typeSpinner = spinner(new String[]{"일정", "할 일", "메모"});
        typeSpinner.setSelection(typeIndex(item.type));
        root.addView(typeSpinner);

        root.addView(label("제목"));
        titleInput = new EditText(context);
        titleInput.setText(item.title);
        titleInput.setSelectAllOnFocus(false);
        root.addView(titleInput, new LinearLayout.LayoutParams(-1, dp(56)));

        allDayCheck = new CheckBox(context);
        allDayCheck.setText("종일 일정");
        allDayCheck.setChecked(item.allDay);
        root.addView(allDayCheck);

        root.addView(label("시작"));
        startButton = timeButton(item.startAt, "시작 날짜·시간 없음");
        startButton.setOnClickListener(v -> chooseDateTime(true));
        root.addView(startButton);

        Button clearStart = smallButton("시작 시간 지우기");
        clearStart.setOnClickListener(v -> {
            item.startAt = null;
            startButton.setText("시작 날짜·시간 없음");
        });
        root.addView(clearStart);

        root.addView(label("종료"));
        endButton = timeButton(item.endAt, "종료 날짜·시간 없음");
        endButton.setOnClickListener(v -> chooseDateTime(false));
        root.addView(endButton);

        Button clearEnd = smallButton("종료 시간 지우기");
        clearEnd.setOnClickListener(v -> {
            item.endAt = null;
            endButton.setText("종료 날짜·시간 없음");
        });
        root.addView(clearEnd);

        root.addView(label("반복"));
        repeatSpinner = spinner(new String[]{"없음", "매일", "평일", "매주", "매월"});
        repeatSpinner.setSelection(repeatIndex(item.repeatRule));
        root.addView(repeatSpinner);

        root.addView(label("중요도"));
        prioritySpinner = spinner(new String[]{"낮음", "보통", "높음"});
        prioritySpinner.setSelection(priorityIndex(item.priority));
        root.addView(prioritySpinner);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("분석 결과 확인·수정")
                .setView(scroll)
                .setNegativeButton("취소", null)
                .setPositiveButton("적용", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> applyAndClose()));
        return dialog;
    }

    private void applyAndClose() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            titleInput.setError("제목을 입력하세요.");
            return;
        }
        if (item.startAt != null && item.endAt != null && item.endAt < item.startAt) {
            endButton.setError("종료 시간은 시작 시간보다 늦어야 합니다.");
            return;
        }

        item.type = new String[]{WorkItemEntity.TYPE_SCHEDULE, WorkItemEntity.TYPE_TASK, WorkItemEntity.TYPE_MEMO}
                [typeSpinner.getSelectedItemPosition()];
        item.title = title;
        item.allDay = allDayCheck.isChecked();
        item.repeatRule = new String[]{"NONE", "DAILY", "WEEKDAYS", "WEEKLY", "MONTHLY"}
                [repeatSpinner.getSelectedItemPosition()];
        item.priority = new String[]{"LOW", "NORMAL", "HIGH"}
                [prioritySpinner.getSelectedItemPosition()];

        listener.onConfirmed(item);
        dismiss();
    }

    private void chooseDateTime(boolean start) {
        Long currentMillis = start ? item.startAt : item.endAt;
        LocalDateTime initial = currentMillis == null
                ? LocalDateTime.now().plusMinutes(5)
                : Instant.ofEpochMilli(currentMillis).atZone(zoneId).toLocalDateTime();

        new DatePickerDialog(requireContext(), (datePicker, year, month, day) -> {
            LocalDate selectedDate = LocalDate.of(year, month + 1, day);
            new TimePickerDialog(requireContext(), (timePicker, hour, minute) -> {
                long millis = LocalDateTime.of(selectedDate, LocalTime.of(hour, minute))
                        .atZone(zoneId).toInstant().toEpochMilli();
                if (start) {
                    item.startAt = millis;
                    startButton.setText(format(millis));
                } else {
                    item.endAt = millis;
                    endButton.setText(format(millis));
                }
            }, initial.getHour(), initial.getMinute(), true).show();
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(requireContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, values);
        spinner.setAdapter(adapter);
        spinner.setMinimumHeight(dp(50));
        return spinner;
    }

    private TextView label(String value) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(14);
        view.setPadding(0, dp(12), 0, dp(4));
        return view;
    }

    private Button timeButton(Long millis, String emptyText) {
        Button button = new Button(requireContext());
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setText(millis == null ? emptyText : format(millis));
        button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return button;
    }

    private Button smallButton(String text) {
        Button button = new Button(requireContext());
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        return button;
    }

    private String format(long millis) {
        return Instant.ofEpochMilli(millis).atZone(zoneId).format(DISPLAY_FORMAT);
    }

    private int typeIndex(String type) {
        if (WorkItemEntity.TYPE_SCHEDULE.equals(type)) return 0;
        if (WorkItemEntity.TYPE_TASK.equals(type)) return 1;
        return 2;
    }

    private int repeatIndex(String repeat) {
        if ("DAILY".equals(repeat)) return 1;
        if ("WEEKDAYS".equals(repeat)) return 2;
        if ("WEEKLY".equals(repeat)) return 3;
        if ("MONTHLY".equals(repeat)) return 4;
        return 0;
    }

    private int priorityIndex(String priority) {
        if ("LOW".equals(priority)) return 0;
        if ("HIGH".equals(priority)) return 2;
        return 1;
    }

    private int dp(int value) {
        return Math.round(value * requireContext().getResources().getDisplayMetrics().density);
    }
}
