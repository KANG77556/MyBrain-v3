package com.seongho.brainassistant.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.seongho.brainassistant.feature.dashboard.DashboardScreen
import com.seongho.brainassistant.feature.dashboard.DashboardSummary
import com.seongho.brainassistant.feature.dashboard.DashboardUiState
import org.junit.Rule
import org.junit.Test

class OfflineRecoveryE2ETest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun pendingSyncIsVisibleWhileOffline() {
        composeRule.setContent {
            DashboardScreen(
                state = DashboardUiState(summary = DashboardSummary(pendingSyncCount = 1)),
                onAction = {},
            )
        }

        composeRule.onNodeWithTag("summary-metric-sync").assertIsDisplayed()
        composeRule.onNodeWithText("1").assertIsDisplayed()
        composeRule.onNodeWithText("동기화").assertIsDisplayed()
    }
}
