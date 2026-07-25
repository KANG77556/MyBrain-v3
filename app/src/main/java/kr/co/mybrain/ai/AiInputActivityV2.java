package kr.co.mybrain.ai;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

/**
 * MyBrain AI 1.8.6 AI 메시지 분석 화면입니다.
 *
 * 기존 분석 기능은 그대로 사용하고 화면이 만들어진 뒤 입력창 높이와 버튼 순서만
 * 정리합니다. 가장 자주 사용하는 자동 추천 분석 버튼을 입력창 바로 아래에
 * 배치해 스크롤 없이 실행할 수 있게 합니다.
 */
public class AiInputActivityV2 extends AiInputActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View content = findViewById(android.R.id.content);
        if (content != null) content.post(this::rearrangePrimaryAction);
    }

    /** 입력창과 분석 버튼을 찾아 첫 화면의 행동 순서를 재배치합니다. */
    private void rearrangePrimaryAction() {
        View content = findViewById(android.R.id.content);
        EditText input = findFirstEditText(content);
        Button analyze = findAnalyzeButton(content);
        if (input == null || analyze == null) return;
        if (!(input.getParent() instanceof LinearLayout) || input.getParent() != analyze.getParent()) return;

        LinearLayout root = (LinearLayout) input.getParent();
        int inputIndex = root.indexOfChild(input);
        int analyzeIndex = root.indexOfChild(analyze);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(180));
        inputParams.setMargins(0, 0, 0, dp(10));
        input.setMinLines(6);
        input.setLayoutParams(inputParams);

        LinearLayout.LayoutParams analyzeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        analyzeParams.setMargins(0, 0, 0, dp(10));

        if (analyzeIndex != inputIndex + 1) {
            root.removeView(analyze);
            inputIndex = root.indexOfChild(input);
            root.addView(analyze, inputIndex + 1, analyzeParams);
        } else {
            analyze.setLayoutParams(analyzeParams);
        }
        analyze.setContentDescription("입력한 메시지를 자동 추천 방식으로 분석");
    }

    private EditText findFirstEditText(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            EditText found = findFirstEditText(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private Button findAnalyzeButton(View view) {
        if (view instanceof Button) {
            Button button = (Button) view;
            String text = button.getText() == null ? "" : button.getText().toString();
            if (text.contains("분석") && !text.contains("설정")) return button;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            Button found = findAnalyzeButton(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
