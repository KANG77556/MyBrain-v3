package kr.co.mybrain.v2.ui;

import android.content.Context;
import android.content.SharedPreferences;

/** 글자 크기와 한 손 조작 등 화면 접근성 설정을 기기에 저장합니다. */
public final class UiPreferences {
    private static final String FILE = "ui_accessibility_settings";
    private static final String KEY_TEXT_SCALE = "text_scale_percent";
    private static final String KEY_HIGH_CONTRAST = "high_contrast";
    private static final String KEY_LARGE_TOUCH = "large_touch_targets";
    private static final String KEY_ONE_HAND = "one_hand_mode";
    private static final String KEY_REDUCE_MOTION = "reduce_motion";

    public int textScalePercent = 100;
    public boolean highContrast;
    public boolean largeTouchTargets = true;
    public boolean oneHandMode = true;
    public boolean reduceMotion = true;

    private UiPreferences() {}

    public static UiPreferences load(Context context) {
        SharedPreferences values = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        UiPreferences settings = new UiPreferences();
        settings.textScalePercent = normalizeScale(values.getInt(KEY_TEXT_SCALE, 100));
        settings.highContrast = values.getBoolean(KEY_HIGH_CONTRAST, false);
        settings.largeTouchTargets = values.getBoolean(KEY_LARGE_TOUCH, true);
        settings.oneHandMode = values.getBoolean(KEY_ONE_HAND, true);
        settings.reduceMotion = values.getBoolean(KEY_REDUCE_MOTION, true);
        return settings;
    }

    public void save(Context context) {
        textScalePercent = normalizeScale(textScalePercent);
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_TEXT_SCALE, textScalePercent)
                .putBoolean(KEY_HIGH_CONTRAST, highContrast)
                .putBoolean(KEY_LARGE_TOUCH, largeTouchTargets)
                .putBoolean(KEY_ONE_HAND, oneHandMode)
                .putBoolean(KEY_REDUCE_MOTION, reduceMotion)
                .apply();
    }

    public static void reset(Context context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply();
    }

    public float textScale() {
        return textScalePercent / 100f;
    }

    public String textSizeLabel() {
        if (textScalePercent >= 130) return "매우 크게";
        if (textScalePercent >= 115) return "크게";
        return "기본";
    }

    public String summary() {
        return "글자 " + textSizeLabel()
                + " · 큰 터치 영역 " + (largeTouchTargets ? "사용" : "미사용")
                + " · 한 손 조작 " + (oneHandMode ? "사용" : "미사용")
                + " · 고대비 " + (highContrast ? "사용" : "미사용");
    }

    private static int normalizeScale(int value) {
        if (value >= 123) return 130;
        if (value >= 108) return 115;
        return 100;
    }
}