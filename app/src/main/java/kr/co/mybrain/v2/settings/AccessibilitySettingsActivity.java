package kr.co.mybrain.v2.settings;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import kr.co.mybrain.v2.ui.AppUi;
import kr.co.mybrain.v2.ui.UiConsistencyController;
import kr.co.mybrain.v2.ui.UiPreferences;

/** 글자 크기, 고대비, 큰 터치 영역과 한 손 조작을 설정합니다. */
public class AccessibilitySettingsActivity extends AppCompatActivity {
    private UiPreferences preferences;
    private RadioButton normalText;
    private RadioButton largeText;
    private RadioButton extraLargeText;
    private Switch highContrast;
    private Switch largeTouchTargets;
    private Switch oneHandMode;
    private Switch reduceMotion;
    private TextView preview;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = UiPreferences.load(this);
        setContentView(buildScreen());
        bindValues();
        refreshPreview();
    }

    private View buildScreen() {
        AppUi.Screen screen = AppUi.screen(this);
        LinearLayout root = screen.root;

        Button back = AppUi.secondaryButton(this, "←  설정으로");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, AppUi.dp(this, 52)));

        root.addView(AppUi.title(this, "화면·접근성"));
        root.addView(AppUi.subtitle(this, "글자와 버튼을 보기 편하게 조정합니다. 변경 내용은 모든 화면에 적용됩니다."));

        LinearLayout textCard = AppUi.card(this);
        root.addView(textCard, AppUi.cardParams(this));
        textCard.addView(AppUi.sectionTitle(this, "글자 크기"));
        textCard.addView(AppUi.body(this, "휴대폰의 시스템 글자 크기와 함께 적용됩니다."));

        RadioGroup textGroup = new RadioGroup(this);
        textGroup.setOrientation(RadioGroup.VERTICAL);
        normalText = radio("기본 · 한 화면에 더 많이 표시");
        largeText = radio("크게 · 읽기 편한 권장 크기");
        extraLargeText = radio("매우 크게 · 가까이 보기 어려울 때");
        textGroup.addView(normalText, rowParams());
        textGroup.addView(largeText, rowParams());
        textGroup.addView(extraLargeText, rowParams());
        textGroup.setOnCheckedChangeListener((group, checkedId) -> refreshPreview());
        textCard.addView(textGroup);

        LinearLayout controlCard = AppUi.card(this);
        root.addView(controlCard, AppUi.cardParams(this));
        controlCard.addView(AppUi.sectionTitle(this, "조작과 표시"));
        largeTouchTargets = option("큰 터치 영역 사용", "버튼과 선택 항목을 최소 56dp로 표시합니다.");
        oneHandMode = option("한 손 조작 여백 사용", "화면 아래쪽에 여유 공간을 두어 주요 버튼을 누르기 쉽게 합니다.");
        highContrast = option("고대비 글자 사용", "일반 글자와 설명 문구를 더 진하게 표시합니다.");
        reduceMotion = option("움직임 줄이기", "화면 전환 중 불필요한 버튼·스크롤 애니메이션을 줄입니다.");
        controlCard.addView(largeTouchTargets, rowParams());
        controlCard.addView(oneHandMode, rowParams());
        controlCard.addView(highContrast, rowParams());
        controlCard.addView(reduceMotion, rowParams());

        LinearLayout previewCard = AppUi.card(this);
        root.addView(previewCard, AppUi.cardParams(this));
        previewCard.addView(AppUi.sectionTitle(this, "미리 보기"));
        preview = AppUi.body(this, "일정과 할 일을 쉽고 빠르게 확인합니다.\n버튼과 설명 문구도 함께 조정됩니다.");
        preview.setPadding(0, AppUi.dp(this, 4), 0, AppUi.dp(this, 10));
        previewCard.addView(preview);
        Button sample = AppUi.primaryButton(this, "예시 버튼");
        sample.setEnabled(false);
        sample.setAlpha(.8f);
        previewCard.addView(sample, AppUi.actionParams(this));

        Button save = AppUi.primaryButton(this, "화면 설정 저장");
        save.setOnClickListener(v -> saveSettings());
        root.addView(save, AppUi.actionParams(this));

        Button reset = AppUi.secondaryButton(this, "기본값으로 되돌리기");
        reset.setOnClickListener(v -> resetSettings());
        root.addView(reset, AppUi.actionParams(this));

        TextView note = AppUi.body(this, "TalkBack 사용 시 제목은 제목으로, 상태 변화는 자동 안내되도록 설정됩니다.");
        note.setGravity(Gravity.CENTER);
        note.setPadding(AppUi.dp(this, 8), AppUi.dp(this, 14), AppUi.dp(this, 8), 0);
        root.addView(note);
        return screen.scroll;
    }

    private void bindValues() {
        if (preferences.textScalePercent >= 130) extraLargeText.setChecked(true);
        else if (preferences.textScalePercent >= 115) largeText.setChecked(true);
        else normalText.setChecked(true);
        highContrast.setChecked(preferences.highContrast);
        largeTouchTargets.setChecked(preferences.largeTouchTargets);
        oneHandMode.setChecked(preferences.oneHandMode);
        reduceMotion.setChecked(preferences.reduceMotion);
    }

    private void captureValues() {
        preferences.textScalePercent = extraLargeText.isChecked() ? 130
                : largeText.isChecked() ? 115 : 100;
        preferences.highContrast = highContrast.isChecked();
        preferences.largeTouchTargets = largeTouchTargets.isChecked();
        preferences.oneHandMode = oneHandMode.isChecked();
        preferences.reduceMotion = reduceMotion.isChecked();
    }

    private void saveSettings() {
        captureValues();
        preferences.save(this);
        UiConsistencyController.apply(this);
        refreshPreview();
        Toast.makeText(this, "화면 설정을 저장했습니다.", Toast.LENGTH_SHORT).show();
        View content = findViewById(android.R.id.content);
        if (content != null) content.announceForAccessibility("화면 설정을 저장했습니다.");
    }

    private void resetSettings() {
        UiPreferences.reset(this);
        preferences = UiPreferences.load(this);
        bindValues();
        UiConsistencyController.apply(this);
        refreshPreview();
        Toast.makeText(this, "기본 화면 설정으로 되돌렸습니다.", Toast.LENGTH_SHORT).show();
    }

    private void refreshPreview() {
        if (preview == null) return;
        int scale = extraLargeText != null && extraLargeText.isChecked() ? 130
                : largeText != null && largeText.isChecked() ? 115 : 100;
        preview.setText("현재 선택: 글자 " + (scale == 130 ? "매우 크게" : scale == 115 ? "크게" : "기본")
                + "\n일정과 할 일을 쉽고 빠르게 확인합니다.");
    }

    private RadioButton radio(String value) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(value);
        button.setTextSize(16);
        button.setTextColor(AppUi.TEXT);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(AppUi.dp(this, 4), 0, AppUi.dp(this, 4), 0);
        button.setMinHeight(AppUi.dp(this, 56));
        return button;
    }

    private Switch option(String title, String description) {
        Switch value = new Switch(this);
        value.setText(title + "\n" + description);
        value.setTextSize(15);
        value.setTextColor(AppUi.TEXT);
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setPadding(AppUi.dp(this, 4), AppUi.dp(this, 5), AppUi.dp(this, 4), AppUi.dp(this, 5));
        value.setMinHeight(AppUi.dp(this, 64));
        return value;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, AppUi.dp(this, 5), 0, 0);
        return params;
    }
}