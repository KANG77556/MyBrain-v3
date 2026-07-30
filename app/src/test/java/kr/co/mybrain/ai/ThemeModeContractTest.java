package kr.co.mybrain.ai;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** 테마 선택 인덱스가 저장 값으로 정확히 변환되는지 확인합니다. */
public class ThemeModeContractTest {
    @Test public void themeIndexMapsToExpectedMode() {
        assertEquals(ThemeController.MODE_SYSTEM, ThemeController.modeForIndex(0));
        assertEquals(ThemeController.MODE_LIGHT, ThemeController.modeForIndex(1));
        assertEquals(ThemeController.MODE_DARK, ThemeController.modeForIndex(2));
    }
}
