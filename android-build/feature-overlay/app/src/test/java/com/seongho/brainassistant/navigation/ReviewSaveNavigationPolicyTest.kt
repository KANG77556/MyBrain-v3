package com.seongho.brainassistant.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewSaveNavigationPolicyTest {
    @Test
    fun savingReviewAlwaysAddsDashboardWithoutPoppingTheOnlyDestination() {
        assertEquals(
            ReviewSaveNavigation(destination = "dashboard", launchSingleTop = true),
            reviewSaveNavigation(),
        )
    }

    @Test
    fun emptyNavigationStateRecoversToDashboard() {
        assertEquals("dashboard", dashboardRecoveryDestination(null))
        assertNull(dashboardRecoveryDestination("review/input"))
    }
}
