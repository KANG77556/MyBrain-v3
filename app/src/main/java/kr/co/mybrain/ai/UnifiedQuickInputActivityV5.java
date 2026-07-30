package kr.co.mybrain.ai;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** 기존 통합 입력 기능에 일정 종료 시간과 실제 시간 구간 충돌 검사를 추가합니다. */
public class UnifiedQuickInputActivityV5 extends UnifiedQuickInputActivityV2 {
    private EditText rangeInput;
    private Button endTimeButton;
    private String manualEndTime = "";
    private boolean manualEndTimeSelected;
    private boolean rangeWatcherInstalled;
    private boolean confirmedConflict;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View root = findViewById(android.R.id.content);
        if (root != null) root.postDelayed(this::installTimeRangeUi, 220L);
    }

    @Override
    protected void onResume() {
        super.onResume();
        View root = findViewById(android.R.id.content);
        if (root != null) root.postDelayed(this::installTimeRangeUi, 220L);
    }

    private void installTimeRangeUi() {
        View root = findViewById(android.R.id.content);
        rangeInput = findInput(root);
        if (rangeInput == null) return;
        installRangeWatcher();

        KoreanTimeRangeParser.Range parsed = KoreanTimeRangeParser.parse(rangeInput.getText().toString());
        if (!manualEndTimeSelected) manualEndTime = parsed.isValid() ? parsed.endTime : "";

        if (endTimeButton == null || endTimeButton.getParent() == null) {
            ViewGroup parent = rangeInput.getParent() instanceof ViewGroup ? (ViewGroup) rangeInput.getParent() : null;
            if (parent instanceof LinearLayout) {
                endTimeButton = new Button(this);
                endTimeButton.setAllCaps(false);
                endTimeButton.setTextSize(13);
                endTimeButton.setContentDescription("일정 종료 시간 선택");
                endTimeButton.setOnClickListener(v -> pickEndTime());
                int index = ((LinearLayout) parent).indexOfChild(rangeInput);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
                params.setMargins(0, dp(7), 0, dp(7));
                ((LinearLayout) parent).addView(endTimeButton, Math.min(index + 1, parent.getChildCount()), params);
            }
        }
        refreshEndTimeLabel();
        connectSaveButton(root);
    }

    /** 사용자가 문장을 수정할 때 종료 시간 표시도 즉시 다시 분석합니다. */
    private void installRangeWatcher() {
        if (rangeWatcherInstalled || rangeInput == null) return;
        rangeWatcherInstalled = true;
        rangeInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (!manualEndTimeSelected) {
                    KoreanTimeRangeParser.Range parsed = KoreanTimeRangeParser.parse(s == null ? "" : s.toString());
                    manualEndTime = parsed.isValid() ? parsed.endTime : "";
                }
                refreshEndTimeLabel();
            }
        });
    }

    private void pickEndTime() {
        Calendar now = Calendar.getInstance();
        String value = currentEndTime();
        if (value.matches("\\d{2}:\\d{2}")) {
            try {
                now.set(Calendar.HOUR_OF_DAY, Integer.parseInt(value.substring(0, 2)));
                now.set(Calendar.MINUTE, Integer.parseInt(value.substring(3, 5)));
            } catch (Exception ignored) { }
        }
        new TimePickerDialog(this, (view, hour, minute) -> {
            manualEndTimeSelected = true;
            manualEndTime = String.format(Locale.KOREA, "%02d:%02d", hour, minute);
            refreshEndTimeLabel();
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();
    }

    private void refreshEndTimeLabel() {
        if (endTimeButton == null) return;
        KoreanTimeRangeParser.Range parsed = KoreanTimeRangeParser.parse(
                rangeInput == null ? "" : rangeInput.getText().toString());
        String start = parsed.isValid() ? parsed.startTime : QuickInputParser.parseSingle(
                rangeInput == null ? "" : rangeInput.getText().toString()).time;
        String end = currentEndTime();
        if (end.isEmpty()) {
            endTimeButton.setText("종료 시간 없음 · 눌러서 선택");
        } else if (!start.isEmpty()) {
            endTimeButton.setText("시간 " + start + " ~ " + end);
        } else {
            endTimeButton.setText("종료 " + end);
        }
    }

    private String currentEndTime() {
        if (manualEndTimeSelected) return manualEndTime;
        KoreanTimeRangeParser.Range parsed = KoreanTimeRangeParser.parse(
                rangeInput == null ? "" : rangeInput.getText().toString());
        return parsed.isValid() ? parsed.endTime : "";
    }

    private void connectSaveButton(View root) {
        Button save = findButton(root, "분석 결과로 저장");
        if (save == null || Boolean.TRUE.equals(save.getTag(0x710001))) return;
        save.setTag(0x710001, Boolean.TRUE);
        save.setOnTouchListener((view, event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP) return false;
            String raw = rangeInput == null ? "" : rangeInput.getText().toString().trim();
            AiAnalysisResult result = QuickInputParser.parseSingle(raw);
            KoreanTimeRangeParser.Range parsed = KoreanTimeRangeParser.parse(raw);
            String start = parsed.isValid() ? parsed.startTime : result.time;
            String end = currentEndTime();
            if (!confirmedConflict && "일정".equals(result.type) && !end.isEmpty()
                    && WorkItemStore.hasScheduleConflict(WorkItemStore.load(this), -1,
                    result.date, start, end)) {
                new AlertDialog.Builder(this)
                        .setTitle("시간이 겹치는 일정이 있습니다")
                        .setMessage(start + "부터 " + end + "까지 기존 일정과 겹칩니다. 그래도 저장할까요?")
                        .setNegativeButton("취소", null)
                        .setPositiveButton("저장", (dialog, which) -> {
                            confirmedConflict = true;
                            save.callOnClick();
                            scheduleEndTimeWrite(raw, start, end);
                        })
                        .show();
                return true;
            }
            scheduleEndTimeWrite(raw, start, end);
            return false;
        });
    }

    /** 같은 원문으로 생성된 날짜별 일정 모두에 종료 시간을 기록합니다. */
    private void scheduleEndTimeWrite(String raw, String start, String end) {
        if (end.isEmpty()) return;
        View root = findViewById(android.R.id.content);
        if (root == null) return;
        root.postDelayed(() -> {
            List<WorkItemRecord> items = WorkItemStore.load(this);
            int changed = 0;
            for (WorkItemRecord item : items) {
                if (!"일정".equals(item.type)) continue;
                if (!raw.equals(item.original)) continue;
                if (!start.isEmpty()) item.time = start;
                item.endTime = end;
                changed++;
            }
            if (changed > 0) {
                WorkItemStore.save(this, items);
                Toast.makeText(this, changed + "건에 종료 시간을 함께 저장했습니다.", Toast.LENGTH_SHORT).show();
            }
            confirmedConflict = false;
        }, 420L);
    }

    private EditText findInput(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            EditText found = findInput(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private Button findButton(View view, String text) {
        if (view instanceof Button) {
            CharSequence value = ((Button) view).getText();
            if (value != null && text.equals(value.toString().trim())) return (Button) view;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            Button found = findButton(group.getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
