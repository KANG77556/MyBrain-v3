package com.seongho.brainassistant.feature.dashboard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DashboardCalendarEntryTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun calendarActionIsAvailableFromDashboardAppBar() {
        var lastAction: DashboardAction? = null
        composeRule.setContent {
            DashboardScreen(
                state = DashboardUiState(),
                onAction = { lastAction = it },
            )
        }

        composeRule.onNodeWithContentDescription("캘린더").performClick()

        assertEquals(DashboardAction.OpenCalendar, lastAction)
    }
}
