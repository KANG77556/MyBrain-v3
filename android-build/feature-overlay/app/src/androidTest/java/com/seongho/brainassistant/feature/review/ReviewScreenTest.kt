package com.seongho.brainassistant.feature.review

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seongho.brainassistant.core.model.ClarificationField
import com.seongho.brainassistant.core.model.ItemType
import com.seongho.brainassistant.core.model.ParsedItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReviewScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun showsOnlyUncertainFieldsAsWarnings() {
        composeRule.setContent {
            ReviewScreen(
                state = ReviewUiState(
                    inputId = "input",
                    items = listOf(ReviewItemUi.from(ParsedItem(type = ItemType.EVENT, title = "상담"))),
                    clarificationFields = setOf(ClarificationField.DATE),
                ),
                onAction = {},
            )
        }

        composeRule.onNodeWithText("날짜를 확인해 주세요").assertIsDisplayed()
        composeRule.onNodeWithText("시간을 확인해 주세요").assertDoesNotExist()
        composeRule.onNodeWithText("저장").assertIsNotEnabled()
        composeRule.onNodeWithText("취소").assertIsDisplayed()
    }

    @Test
    fun showsOriginalAndMultipleCardsAndAllowsItemRemoval() {
        val actions = mutableListOf<ReviewAction>()
        composeRule.setContent {
            ReviewScreen(
                state = ReviewUiState(
                    inputId = "input",
                    originalText = "내일 3시 회의하고 보고서 제출해",
                    items = listOf(
                        ReviewItemUi.from(
                            ParsedItem(
                                localId = "event",
                                type = ItemType.EVENT,
                                title = "회의",
                                startAt = java.time.Instant.parse("2026-08-02T06:00:00Z"),
                            ),
                        ),
                        ReviewItemUi.from(
                            ParsedItem(localId = "task", type = ItemType.TASK, title = "보고서 제출"),
                        ),
                    ),
                ),
                onAction = actions::add,
            )
        }

        composeRule.onNodeWithText("원문").assertIsDisplayed()
        composeRule.onNodeWithText("내일 3시 회의하고 보고서 제출해").assertIsDisplayed()
        composeRule.onNodeWithText("일정").assertIsDisplayed()
        composeRule.onNodeWithText("할 일").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("보고서 제출 삭제").performClick()
        assertEquals(ReviewAction.RemoveItem("task"), actions.single())
    }
}
