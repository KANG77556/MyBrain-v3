package kr.co.mybrain.v2.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.Button;

/** 선택형 탭과 필터 버튼의 상태를 모든 화면에서 동일하게 표시합니다. */
public final class UiSelection {
    private UiSelection() {}

    public static Button button(Context context, String label) {
        Button button = AppUi.compactButton(context, label);
        button.setMinHeight(AppUi.dp(context, 44));
        return button;
    }

    public static void apply(Context context, Button button, boolean selected) {
        button.setTextColor(selected ? Color.WHITE : AppUi.TEXT);
        button.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setBackground(AppUi.round(
                context,
                selected ? AppUi.PRIMARY : AppUi.SURFACE,
                12,
                selected ? 0 : AppUi.BORDER));
        button.setContentDescription(button.getText() + (selected ? ", 선택됨" : ""));
    }
}