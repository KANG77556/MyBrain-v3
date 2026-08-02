package com.seongho.brainassistant.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

fun ThemeMode.resolveDarkTheme(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

data class UserSettings(
    val briefingHour: Int = 7,
    val briefingMinute: Int = 30,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 7,
    val maskSensitivePreview: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val keepSignedIn: Boolean = true,
)

interface UserSettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun setBriefingTime(hour: Int, minute: Int)
    suspend fun setQuietHours(startHour: Int, endHour: Int)
    suspend fun setMaskSensitivePreview(enabled: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setKeepSignedIn(enabled: Boolean)
}

private val Context.userSettingsDataStore by preferencesDataStore(name = "user_settings")

class DataStoreUserSettingsRepository(context: Context) : UserSettingsRepository {
    private val dataStore = context.applicationContext.userSettingsDataStore

    override val settings: Flow<UserSettings> = dataStore.data.map { values ->
        UserSettings(
            briefingHour = values[BRIEFING_HOUR] ?: 7,
            briefingMinute = values[BRIEFING_MINUTE] ?: 30,
            quietStartHour = values[QUIET_START] ?: 22,
            quietEndHour = values[QUIET_END] ?: 7,
            maskSensitivePreview = values[MASK_PREVIEW] ?: true,
            themeMode = ThemeMode.fromStorage(values[THEME_MODE]),
            keepSignedIn = values[KEEP_SIGNED_IN] ?: true,
        )
    }

    override suspend fun setBriefingTime(hour: Int, minute: Int) {
        require(hour in 0..23 && minute in 0..59)
        dataStore.edit { values ->
            values[BRIEFING_HOUR] = hour
            values[BRIEFING_MINUTE] = minute
        }
    }

    override suspend fun setQuietHours(startHour: Int, endHour: Int) {
        require(startHour in 0..23 && endHour in 0..23)
        dataStore.edit { values ->
            values[QUIET_START] = startHour
            values[QUIET_END] = endHour
        }
    }

    override suspend fun setMaskSensitivePreview(enabled: Boolean) {
        dataStore.edit { it[MASK_PREVIEW] = enabled }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    override suspend fun setKeepSignedIn(enabled: Boolean) {
        dataStore.edit { it[KEEP_SIGNED_IN] = enabled }
    }

    private companion object {
        val BRIEFING_HOUR = intPreferencesKey("briefing_hour")
        val BRIEFING_MINUTE = intPreferencesKey("briefing_minute")
        val QUIET_START = intPreferencesKey("quiet_start_hour")
        val QUIET_END = intPreferencesKey("quiet_end_hour")
        val MASK_PREVIEW = booleanPreferencesKey("mask_sensitive_preview")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val KEEP_SIGNED_IN = booleanPreferencesKey("keep_signed_in")
    }
}

class SensitivePreviewMasker {
    private val phone = Regex("(01[016789])-?([0-9]{3,4})-?([0-9]{4})")
    private val koreanNameBeforeStudent = Regex("([가-힣])([가-힣]{1,2})(?= 학생)")

    fun mask(text: String, enabled: Boolean): String {
        if (!enabled) return text
        return text
            .replace(phone) { "${it.groupValues[1]}-****-${it.groupValues[3]}" }
            .replace(koreanNameBeforeStudent) { "${it.groupValues[1]}**" }
    }
}
