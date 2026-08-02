package com.seongho.brainassistant.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSaveNavigationPolicyTest {
    @Test
    fun savingReviewAlwaysAddsDashboardWithoutPoppingTheOnlyDestination() {
        assertEquals(
            ReviewSaveNavigation(destination = "dashboard", launchSingleTop = true),
            reviewSaveNavigation(),
        )
    }
}
