package com.seongho.brainassistant.core.database

import com.seongho.brainassistant.core.model.DDayCategory
import com.seongho.brainassistant.core.model.DDayItem
import com.seongho.brainassistant.core.model.ItemStatus
import com.seongho.brainassistant.core.model.WidgetConfig
import com.seongho.brainassistant.core.model.WidgetSizeClass
import com.seongho.brainassistant.core.model.WidgetSnapshot
import com.seongho.brainassistant.core.model.WidgetThemeMode
import com.seongho.brainassistant.core.model.WidgetType
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtendedStorageMapperTest {
    @Test
    fun ddayRoundTripPreservesStorageFields() {
        val item = DDayItem(
            id = "dday-1",
            inputId = "input-1",
            transactionId = "tx-1",
            title = "교육청 보고서 제출",
            targetDate = LocalDate.of(2026, 8, 20),
            category = DDayCategory.DEADLINE,
            importance = 4,
            isPinned = true,
            showElapsedDays = false,
            archiveAfterDays = 14,
            recurrenceRule = "FREQ=YEARLY",
            linkedTaskId = "task-1",
            linkedCalendarId = "calendar-1",
            reminderOffsets = setOf(14, 7, 1, 0),
            status = ItemStatus.DELETED,
            deletedAt = Instant.parse("2026-08-01T02:00:00Z"),
            updatedAt = Instant.parse("2026-08-01T03:00:00Z"),
        )

        assertEquals(item, item.toEntity().toDomain())
    }

    @Test
    fun widgetConfigRoundTripUsesStableFilterOrdering() {
        val config = WidgetConfig(
            widgetId = 42,
            widgetType = WidgetType.CALENDAR,
            sizeClass = WidgetSizeClass.EXPANDED,
            calendarId = "primary",
            filters = setOf("DDAY", "TASK", "EVENT"),
            themeMode = WidgetThemeMode.DARK,
            maskSensitivePreview = false,
        )
        val updatedAt = Instant.parse("2026-08-01T04:00:00Z")

        val entity = config.toEntity(updatedAt)

        assertEquals("DDAY,EVENT,TASK", entity.filtersCsv)
        assertEquals(config, entity.toDomain())
    }

    @Test
    fun widgetSnapshotRoundTripPreservesWidgetIdentity() {
        val snapshot = WidgetSnapshot(
            widgetId = 42,
            type = WidgetType.TODAY_SUMMARY,
            payloadJson = "{\"taskCount\":3}",
            generatedAt = Instant.parse("2026-08-01T05:00:00Z"),
        )

        assertEquals(snapshot, snapshot.toEntity().toDomain())
    }
}
