package com.seongho.brainassistant.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSaveNavigationPolicyTest {
    @Test
    fun savingReviewRemovesCurrentReviewAndShowsDashboard() {
        assertEquals(
            ReviewSaveNavigation(destination = "dashboard", popUpTo = "review/{inputId}", inclusive = true, launchSingleTop = true),
            reviewSaveNavigation(),
        )
    }
}
