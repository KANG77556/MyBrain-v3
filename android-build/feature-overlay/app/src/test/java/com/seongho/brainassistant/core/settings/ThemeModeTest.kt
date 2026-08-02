package com.seongho.brainassistant.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun invalidStoredModeFallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("unexpected"))
    }

    @Test
    fun storedDarkModeIsRestored() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("DARK"))
    }

    @Test fun lightModeOverridesDarkSystem() = assertFalse(ThemeMode.LIGHT.resolveDarkTheme(systemDark = true))
    @Test fun darkModeOverridesLightSystem() = assertTrue(ThemeMode.DARK.resolveDarkTheme(systemDark = false))
    @Test fun systemModeUsesSystemSetting() = assertTrue(ThemeMode.SYSTEM.resolveDarkTheme(systemDark = true))
}
