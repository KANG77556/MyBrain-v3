package com.seongho.brainassistant.core.model

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelTest {
    @Test
    fun ddayDisplayUsesDateNotClockTime() {
        val target = LocalDate.of(2026, 8, 20)

        assertEquals(
            DDayDisplay.Before(3),
            DDayDisplay.between(LocalDate.of(2026, 8, 17), target),
        )
        assertEquals(DDayDisplay.Today, DDayDisplay.between(target, target))
        assertEquals(
            DDayDisplay.After(1),
            DDayDisplay.between(LocalDate.of(2026, 8, 21), target),
        )
    }

    @Test
    fun parsedItemUsesSafeMetadataDefaults() {
        val item = ParsedItem(type = ItemType.NOTE, title = "회의 메모")

        assertNull(item.targetDate)
        assertTrue(item.reminderOffsets.isEmpty())
        assertTrue(item.linkedLocalIds.isEmpty())
        assertNull(item.sourceStart)
        assertNull(item.sourceEnd)
    }

    @Test
    fun calendarRangeKeepsExclusiveEndBoundary() {
        val start = Instant.parse("2026-08-01T00:00:00Z")
        val endExclusive = Instant.parse("2026-09-01T00:00:00Z")

        assertEquals(CalendarRange(start, endExclusive), CalendarRange(start, endExclusive))
    }

    @Test
    fun widgetConfigMasksSensitivePreviewByDefault() {
        val config = WidgetConfig(
            widgetId = 7,
            widgetType = WidgetType.TODAY_SUMMARY,
            sizeClass = WidgetSizeClass.MEDIUM,
        )

        assertEquals(WidgetThemeMode.SYSTEM, config.themeMode)
        assertTrue(config.filters.isEmpty())
        assertTrue(config.maskSensitivePreview)
    }
}
