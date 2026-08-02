package com.seongho.brainassistant.widget

import com.seongho.brainassistant.core.model.WidgetSizeClass
import com.seongho.brainassistant.core.model.WidgetType

/**
 * 런처가 전달한 위젯의 최소 너비·높이를 세 가지 공통 크기로 변환합니다.
 */
object WidgetLayoutPolicy {
    fun resolve(widthDp: Int, heightDp: Int): WidgetSizeClass = when {
        widthDp <= 0 || heightDp <= 0 -> WidgetSizeClass.COMPACT
        widthDp < 180 || heightDp < 90 -> WidgetSizeClass.COMPACT
        widthDp < 250 || heightDp < 180 -> WidgetSizeClass.MEDIUM
        else -> WidgetSizeClass.EXPANDED
    }
}

/**
 * 홈 화면 위젯을 눌렀을 때 앱에서 열 위치입니다.
 * route는 이후 Android Intent의 deep-link 값으로 사용합니다.
 */
enum class WidgetDestination(val route: String) {
    DASHBOARD("dashboard"),
    CALENDAR("calendar"),
    TASKS("dashboard?section=tasks"),
    DDAY("dday"),
    NOTES("dashboard?section=notes"),
    QUICK_CAPTURE("dashboard?focus=capture"),
    RECOMMENDATION("dashboard?section=recommendation"),
}

object WidgetDestinationPolicy {
    fun forType(type: WidgetType): WidgetDestination = when (type) {
        WidgetType.TODAY_SUMMARY -> WidgetDestination.DASHBOARD
        WidgetType.SCHEDULE -> WidgetDestination.CALENDAR
        WidgetType.TASK -> WidgetDestination.TASKS
        WidgetType.DDAY -> WidgetDestination.DDAY
        WidgetType.NOTE -> WidgetDestination.NOTES
        WidgetType.QUICK_CAPTURE -> WidgetDestination.QUICK_CAPTURE
        WidgetType.CALENDAR -> WidgetDestination.CALENDAR
        WidgetType.AI_RECOMMENDATION -> WidgetDestination.RECOMMENDATION
    }
}

object WidgetRefreshPolicy {
    const val PERIODIC_INTERVAL_MINUTES: Long = 15L
    const val UNIQUE_PERIODIC_WORK_NAME: String = "brain-widget-periodic-refresh"
    const val UNIQUE_IMMEDIATE_WORK_NAME: String = "brain-widget-immediate-refresh"
}
