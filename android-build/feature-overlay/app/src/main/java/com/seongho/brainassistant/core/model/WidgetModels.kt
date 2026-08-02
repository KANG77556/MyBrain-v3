package com.seongho.brainassistant.core.model

import java.time.Instant

enum class WidgetType {
    TODAY_SUMMARY,
    SCHEDULE,
    TASK,
    DDAY,
    NOTE,
    QUICK_CAPTURE,
    CALENDAR,
    AI_RECOMMENDATION,
}

enum class WidgetSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

enum class WidgetThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class WidgetConfig(
    val widgetId: Int,
    val widgetType: WidgetType,
    val sizeClass: WidgetSizeClass,
    val calendarId: String? = null,
    val filters: Set<String> = emptySet(),
    val themeMode: WidgetThemeMode = WidgetThemeMode.SYSTEM,
    val maskSensitivePreview: Boolean = true,
)

data class WidgetSnapshot(
    val widgetId: Int,
    val type: WidgetType,
    val payloadJson: String,
    val generatedAt: Instant,
)
