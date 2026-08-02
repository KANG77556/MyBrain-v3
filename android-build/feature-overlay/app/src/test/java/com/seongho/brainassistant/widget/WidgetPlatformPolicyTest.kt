package com.seongho.brainassistant.widget

import com.seongho.brainassistant.core.model.WidgetSizeClass
import com.seongho.brainassistant.core.model.WidgetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPlatformPolicyTest {
    @Test
    fun resolvesLauncherSizesToThreeStableClasses() {
        assertEquals(WidgetSizeClass.COMPACT, WidgetLayoutPolicy.resolve(110, 60))
        assertEquals(WidgetSizeClass.MEDIUM, WidgetLayoutPolicy.resolve(250, 110))
        assertEquals(WidgetSizeClass.EXPANDED, WidgetLayoutPolicy.resolve(250, 250))

        assertEquals(WidgetSizeClass.COMPACT, WidgetLayoutPolicy.resolve(0, 0))
        assertEquals(WidgetSizeClass.MEDIUM, WidgetLayoutPolicy.resolve(600, 140))
    }

    @Test
    fun everyWidgetTypeHasAnExplicitAppDestination() {
        val destinations = WidgetType.entries.associateWith(WidgetDestinationPolicy::forType)

        assertEquals(WidgetType.entries.toSet(), destinations.keys)
        assertTrue(destinations.values.all { it.route.isNotBlank() })
        assertEquals(WidgetDestination.DASHBOARD, destinations.getValue(WidgetType.TODAY_SUMMARY))
        assertEquals(WidgetDestination.CALENDAR, destinations.getValue(WidgetType.SCHEDULE))
        assertEquals(WidgetDestination.TASKS, destinations.getValue(WidgetType.TASK))
        assertEquals(WidgetDestination.DDAY, destinations.getValue(WidgetType.DDAY))
        assertEquals(WidgetDestination.NOTES, destinations.getValue(WidgetType.NOTE))
        assertEquals(WidgetDestination.QUICK_CAPTURE, destinations.getValue(WidgetType.QUICK_CAPTURE))
        assertEquals(WidgetDestination.CALENDAR, destinations.getValue(WidgetType.CALENDAR))
        assertEquals(WidgetDestination.RECOMMENDATION, destinations.getValue(WidgetType.AI_RECOMMENDATION))
    }

    @Test
    fun refreshPolicyUsesAndroidMinimumPeriodicInterval() {
        assertEquals(15L, WidgetRefreshPolicy.PERIODIC_INTERVAL_MINUTES)
        assertEquals("brain-widget-periodic-refresh", WidgetRefreshPolicy.UNIQUE_PERIODIC_WORK_NAME)
        assertEquals("brain-widget-immediate-refresh", WidgetRefreshPolicy.UNIQUE_IMMEDIATE_WORK_NAME)
    }
}
