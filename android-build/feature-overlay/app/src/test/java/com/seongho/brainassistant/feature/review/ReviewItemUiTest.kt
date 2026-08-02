package com.seongho.brainassistant.feature.review

import com.seongho.brainassistant.core.model.ItemType
import com.seongho.brainassistant.core.model.ParsedItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewItemUiTest {
    @Test
    fun ddayTargetDateAndBatchMetadataSurviveReviewRoundTrip() {
        val original = ParsedItem(
            localId = "dday",
            batchId = "batch-1",
            batchIndex = 1,
            type = ItemType.D_DAY,
            title = "개학",
            targetDate = LocalDate.of(2026, 8, 20),
            priority = 3,
            reminderOffsets = setOf(7, 3, 1, 0),
            linkedLocalIds = setOf("task-1"),
            sourceStart = 12,
            sourceEnd = 25,
        )

        val ui = ReviewItemUi.from(original)
        val restored = ui.toParsedItem()

        assertEquals(original.targetDate, restored.targetDate)
        assertEquals(original.batchId, restored.batchId)
        assertEquals(original.batchIndex, restored.batchIndex)
        assertEquals(original.reminderOffsets, restored.reminderOffsets)
        assertEquals(original.linkedLocalIds, restored.linkedLocalIds)
        assertEquals(original.sourceStart, restored.sourceStart)
        assertEquals(original.sourceEnd, restored.sourceEnd)
        assertTrue(ReviewUiState(items = listOf(ui)).isValid)
    }
}
