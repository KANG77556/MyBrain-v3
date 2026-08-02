package com.seongho.brainassistant.core.settings

import org.junit.Assert.assertEquals
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
}
