package com.seongho.brainassistant.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSaveNavigationPolicyTest {
    @Test
    fun savingReviewReturnsToDashboardWithoutRemovingItFromBackStack() {
        assertEquals(
            ReviewSaveNavigation(destination = "dashboard", popUpTo = "dashboard", inclusive = false, launchSingleTop = true),
            reviewSaveNavigation(),
        )
    }
}
