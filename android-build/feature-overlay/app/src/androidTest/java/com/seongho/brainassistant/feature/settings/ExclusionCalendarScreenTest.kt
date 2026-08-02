package com.seongho.brainassistant.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExclusionCalendarScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sourceAndCandidateSelectionsAreVisibleAndSaveable() {
        val actions = mutableListOf<ExclusionCalendarAction>()
        compose.setContent {
            ExclusionCalendarScreen(
                state = ExclusionCalendarUiState(
                    sources = listOf(
                        ExclusionSourceUi("holiday", "대한민국의 공휴일", "공휴일", true),
                        ExclusionSourceUi("school", "학교 학사일정", "학교", false),
                    ),
                    candidates = listOf(
                        ExclusionCandidateUi(
                            sourceId = "school",
                            remoteEventId = "vacation",
                            title = "여름방학",
                            date = LocalDate.of(2026, 8, 3),
                            categoryLabel = "방학",
                            approved = false,
                        ),
                    ),
                ),
                onAction = actions::add,
            )
        }

        compose.onNodeWithText("제외 캘린더").assertIsDisplayed()
        compose.onNodeWithText("대한민국의 공휴일").assertIsDisplayed()
        compose.onNodeWithText("여름방학").assertIsDisplayed()
        compose.onNodeWithContentDescription("학교 학사일정 선택").performClick()
        compose.onNodeWithContentDescription("여름방학 승인").performClick()
        compose.onNodeWithText("저장").performClick()

        assertTrue(actions.contains(ExclusionCalendarAction.ToggleSource("school", true)))
        assertTrue(actions.contains(ExclusionCalendarAction.ToggleCandidate("school", "vacation", LocalDate.of(2026, 8, 3), true)))
        assertTrue(actions.contains(ExclusionCalendarAction.Save))
    }
}
