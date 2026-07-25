package kr.co.mybrain.ai;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MyBrain AI 1.9.1 통합 입력 화면입니다.
 *
 * 기존 분석·저장·초안 복구 로직은 그대로 사용하고 화면 배치만 정리합니다.
 * 저장 버튼은 키보드가 열려도 보이도록 하단에 고정하고,
 * 분석 항목은 작은 화면에서도 잘리지 않도록 두 개씩 세 줄로 배치합니다.
 */
public class UnifiedQuickInputActivityV4 extends UnifiedQuickInputActivityV3 {

    private static final int REQUEST_VOICE_UI = 11601;
    private static final int REQUEST_DOCUMENT_UI = 11602;
    private static final int REQUEST_PRIVACY_UI = 11603;

    private static final int PRIMARY = Color.rgb(34, 96, 214);
    private static final int BORDER = Color.rgb(220, 228, 240);
    private static final int MUTED = Color.rgb(102, 116, 138);

    private static final String TAG_FIXED_ACTION = "mybrain_fixed_save_action";
    private static final String TAG_CHIP_LAYOUT = "mybrain_two_column_chips";

    private EditText inputField;
    private View rootContent;
    private boolean applying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootContent = findViewById(android.R.id.content);
        inputField = findInput(rootContent);
        installLayoutWatcher();
        applyUiCleanup();

        // 이전 화면의 지연 패치가 끝난 뒤에도 짧은 버튼 문구와 최신 동작을 유지합니다.
        if (rootContent != null) {
            rootContent.postDelayed(this::applyUiCleanup, 430L);
            rootContent.postDelayed(this::applyUiCleanup, 1_050L);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyUiCleanup();
    }

    /** 화면 내부 구조가 다시 만들어지면 즉시 최신 UI를 재적용합니다. */
    private void installLayoutWatcher() {
        if (rootContent == null) return;
        rootContent.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        applyUiCleanup();
                    }
                });
    }

    private void applyUiCleanup() {
        if (applying || rootContent == null) return;
        applying = true;
        try {
            inputField = findInput(rootContent);
            shortenAndConnectMediaButtons();
            moveSaveButtonToBottom();
            reflowAnalysisButtons();
            installScrollKeyboardDismiss();
            improveTouchTargets();
        } finally {
            applying = false;
        }
    }

    /** 음성·촬영·사진 버튼의 문구를 짧게 하고 최신 입력 흐름을 즉시 연결합니다. */
    private void shortenAndConnectMediaButtons() {
        for (Button button : findButtons(rootContent)) {
            String value = textOf(button);
            if (value.startsWith("🎤")) {
                button.setText("🎤 음성");
                connectTouch(button, () ->
                        startActivityForResult(new Intent(this, VoiceCaptureActivityV3.class), REQUEST_VOICE_UI));
                button.setContentDescription("여러 문장을 이어서 음성 입력");
            } else if (value.startsWith("📷")) {
                button.setText("📷 촬영");
                connectTouch(button, () -> launchDocument(DocumentCaptureActivity.MODE_CAMERA));
                button.setContentDescription("문서 촬영 후 품질 확인과 글자 인식");
            } else if (value.startsWith("🖼")) {
                button.setText("🖼 사진");
                connectTouch(button, () -> launchDocument(DocumentCaptureActivity.MODE_GALLERY));
                button.setContentDescription("사진 선택 후 품질 확인과 글자 인식");
            }
        }
    }

    private void launchDocument(String mode) {
        Intent intent = new Intent(this, DocumentCaptureActivityV2.class);
        intent.putExtra(DocumentCaptureActivity.EXTRA_MODE, mode);
        startActivityForResult(intent, REQUEST_DOCUMENT_UI);
    }

    /** 부모 화면이 나중에 클릭 동작을 바꿔도 실제 터치는 최신 동작이 우선되도록 합니다. */
    private void connectTouch(Button button, Runnable action) {
        button.setOnClickListener(v -> action.run());
        button.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                action.run();
                view.setPressed(false);
            } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
                view.setPressed(true);
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                view.setPressed(false);
            }
            return true;
        });
    }

    /** 저장 버튼을 스크롤 밖의 고정 하단 영역으로 이동합니다. */
    private void moveSaveButtonToBottom() {
        LinearLayout root = findRootLayout();
        if (root == null || findTagged(root, TAG_FIXED_ACTION) != null) return;

        Button save = findButtonByText("분석 결과로 저장");
        if (save == null || !(save.getParent() instanceof ViewGroup)) return;

        ViewGroup oldParent = (ViewGroup) save.getParent();
        oldParent.removeView(save);

        LinearLayout action = new LinearLayout(this);
        action.setTag(TAG_FIXED_ACTION);
        action.setOrientation(LinearLayout.VERTICAL);
        action.setPadding(dp(14), dp(8), dp(14), dp(10));
        action.setBackgroundColor(Color.WHITE);
        action.setElevation(dp(8));

        save.setTextSize(16);
        save.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        save.setMinimumHeight(0);
        save.setMinimumWidth(0);
        action.addView(save, new LinearLayout.LayoutParams(-1, dp(58)));

        Button undo = findButtonByText("실행 취소");
        View undoBar = undo != null && undo.getParent() instanceof View
                ? (View) undo.getParent() : null;
        int insertIndex = undoBar != null && undoBar.getParent() == root
                ? root.indexOfChild(undoBar) : root.getChildCount();
        root.addView(action, Math.max(0, insertIndex));
    }

    /** 기존 3개 고정 폭 두 줄을 2개씩 세 줄로 바꿉니다. */
    private void reflowAnalysisButtons() {
        Button detail = findButtonByText("상세 설정");
        if (detail == null || !(detail.getParent() instanceof ViewGroup)) return;
        ViewGroup secondRow = (ViewGroup) detail.getParent();
        if (!(secondRow.getParent() instanceof LinearLayout)) return;
        LinearLayout preview = (LinearLayout) secondRow.getParent();
        if (TAG_CHIP_LAYOUT.equals(preview.getTag())) return;

        Button type = null;
        Button date = null;
        Button time = null;
        Button reminder = null;
        Button repeat = null;

        List<Button> candidates = findButtons(preview);
        for (Button button : candidates) {
            String value = textOf(button);
            if (button == detail) continue;
            if (isType(value)) type = button;
            else if (isDate(value)) date = button;
            else if (isTime(value)) time = button;
            else if (isReminder(value)) reminder = button;
            else if (isRepeat(value)) repeat = button;
        }
        if (type == null || date == null || time == null || reminder == null || repeat == null) return;

        Set<ViewGroup> oldRows = new HashSet<>();
        oldRows.add((ViewGroup) type.getParent());
        oldRows.add((ViewGroup) date.getParent());
        oldRows.add((ViewGroup) time.getParent());
        oldRows.add((ViewGroup) reminder.getParent());
        oldRows.add((ViewGroup) repeat.getParent());
        oldRows.add((ViewGroup) detail.getParent());

        int insertIndex = preview.indexOfChild((View) type.getParent());
        if (insertIndex < 0) insertIndex = preview.getChildCount();

        detach(type);
        detach(date);
        detach(time);
        detach(reminder);
        detach(repeat);
        detach(detail);
        for (ViewGroup row : oldRows) {
            if (row.getParent() == preview) preview.removeView(row);
        }

        LinearLayout row1 = chipRow();
        addChip(row1, type);
        addChip(row1, date);
        LinearLayout row2 = chipRow();
        addChip(row2, time);
        addChip(row2, reminder);
        LinearLayout row3 = chipRow();
        addChip(row3, repeat);
        addChip(row3, detail);

        preview.addView(row1, insertIndex++);
        preview.addView(row2, insertIndex++);
        preview.addView(row3, insertIndex);
        preview.setTag(TAG_CHIP_LAYOUT);
    }

    private LinearLayout chipRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private void addChip(LinearLayout row, Button button) {
        button.setSingleLine(true);
        button.setTextSize(13);
        button.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        row.addView(button, params);
    }

    private void installScrollKeyboardDismiss() {
        for (View view : findViews(rootContent)) {
            if (!(view instanceof ScrollView)) continue;
            ScrollView scroll = (ScrollView) view;
            if (Boolean.TRUE.equals(scroll.getTag())) continue;
            scroll.setTag(Boolean.TRUE);
            scroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (Math.abs(scrollY - oldScrollY) > dp(3)) hideKeyboard();
            });
        }
    }

    /** 큰 글씨 설정에서도 모든 주요 조작 영역을 최소 48dp 이상 유지합니다. */
    private void improveTouchTargets() {
        for (Button button : findButtons(rootContent)) {
            ViewGroup.LayoutParams params = button.getLayoutParams();
            if (params != null && params.height > 0 && params.height < dp(48)) {
                params.height = dp(48);
                button.setLayoutParams(params);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_VOICE_UI) {
            if (resultCode == RESULT_OK && data != null) {
                String value = safe(data.getStringExtra(VoiceCaptureActivityV2.EXTRA_RESULT_TEXT)).trim();
                if (!value.isEmpty()) reviewOrAppend(value, "음성 입력");
            }
            return;
        }
        if (requestCode == REQUEST_DOCUMENT_UI) {
            if (resultCode == RESULT_OK && data != null) {
                String value = safe(data.getStringExtra(DocumentCaptureActivity.EXTRA_RESULT_TEXT)).trim();
                String source = safe(data.getStringExtra(DocumentCaptureActivity.EXTRA_RESULT_SOURCE)).trim();
                int score = data.getIntExtra(DocumentCaptureActivity.EXTRA_QUALITY_SCORE, -1);
                if (!value.isEmpty()) reviewOrAppend(value, source.isEmpty() ? "문서 OCR" : source);
                if (score >= 0) {
                    Toast.makeText(this, "OCR 품질 " + score + "점으로 처리했습니다.",
                            Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }
        if (requestCode == REQUEST_PRIVACY_UI) {
            if (resultCode == RESULT_OK && data != null) {
                appendText(safe(data.getStringExtra(PrivacyReviewActivity.EXTRA_RESULT_TEXT)));
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void reviewOrAppend(String raw, String source) {
        OcrPrivacyProcessor.ProcessedText processed = OcrPrivacyProcessor.process(raw, source);
        if (processed.maskedCount > 0) {
            Intent intent = new Intent(this, PrivacyReviewActivity.class);
            intent.putExtra(PrivacyReviewActivity.EXTRA_RAW_TEXT, raw);
            intent.putExtra(PrivacyReviewActivity.EXTRA_SOURCE, source);
            startActivityForResult(intent, REQUEST_PRIVACY_UI);
        } else {
            appendText(processed.storedText);
        }
    }

    private void appendText(String addition) {
        if (inputField == null) inputField = findInput(rootContent);
        if (inputField == null) return;
        String value = safe(addition).trim();
        if (value.isEmpty()) return;
        String current = inputField.getText().toString().trim();
        inputField.setText(current.isEmpty() ? value : current + "\n\n" + value);
        inputField.setSelection(inputField.length());
        inputField.clearFocus();
        hideKeyboard();
    }

    private LinearLayout findRootLayout() {
        View content = findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) content;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof LinearLayout) return (LinearLayout) child;
        }
        return null;
    }

    private View findTagged(View view, String tag) {
        if (tag.equals(view.getTag())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findTagged(group.getChildAt(i), tag);
            if (found != null) return found;
        }
        return null;
    }

    private Button findButtonByText(String text) {
        for (Button button : findButtons(rootContent)) {
            if (text.equals(textOf(button))) return button;
        }
        return null;
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

    private List<Button> findButtons(View root) {
        List<Button> result = new ArrayList<>();
        for (View view : findViews(root)) if (view instanceof Button) result.add((Button) view);
        return result;
    }

    private List<View> findViews(View root) {
        List<View> result = new ArrayList<>();
        collect(root, result);
        return result;
    }

    private void collect(View view, List<View> result) {
        if (view == null) return;
        result.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), result);
    }

    private void detach(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private boolean isType(String value) {
        return "메모".equals(value) || "할 일".equals(value) || "일정".equals(value);
    }

    private boolean isDate(String value) {
        return "날짜 없음".equals(value) || value.matches("\\d{1,2}월\\s*\\d{1,2}일");
    }

    private boolean isTime(String value) {
        return "시간 없음".equals(value) || value.matches("\\d{2}:\\d{2}");
    }

    private boolean isReminder(String value) {
        return value.contains("알림") || value.matches("\\d+(분|시간|일) 전");
    }

    private boolean isRepeat(String value) {
        return "반복 없음".equals(value) || "매일".equals(value) || "매주".equals(value)
                || "매월".equals(value) || "평일".equals(value);
    }

    private String textOf(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) return;
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        focused.clearFocus();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
