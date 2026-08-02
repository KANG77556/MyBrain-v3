package com.seongho.brainassistant.widget

import com.seongho.brainassistant.core.model.CalendarItem
import com.seongho.brainassistant.core.model.DDayCategory
import com.seongho.brainassistant.core.model.DDayItem
import com.seongho.brainassistant.core.model.NoteItem
import com.seongho.brainassistant.core.model.TaskItem
import com.seongho.brainassistant.core.model.TodaySnapshot
import com.seongho.brainassistant.core.model.WidgetConfig
import com.seongho.brainassistant.core.model.WidgetSizeClass
import com.seongho.brainassistant.core.model.WidgetType
import com.seongho.brainassistant.core.settings.SensitivePreviewMasker
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetContentFactoryTest {
    private val today = LocalDate.of(2026, 8, 2)
    private val factory = WidgetContentFactory(SensitivePreviewMasker())

    @Test
    fun createsContentForAllEightWidgetTypes() {
        val source = populatedSource()

        val results = WidgetType.entries.map { type ->
            factory.create(
                config = WidgetConfig(
                    widgetId = type.ordinal + 1,
                    widgetType = type,
                    sizeClass = WidgetSizeClass.MEDIUM,
                ),
                source = source,
            )
        }

        assertEquals(8, results.size)
        assertEquals(WidgetType.entries, results.map { it.type })
        assertTrue(results.all { it.title.isNotBlank() })
        assertTrue(results.all { it.generatedAt == source.generatedAt })
    }

    @Test
    fun sizeClassLimitsVisibleRowsDeterministically() {
        val source = populatedSource(taskCount = 8)

        val compact = factory.create(config(WidgetType.TASK, WidgetSizeClass.COMPACT), source)
        val medium = factory.create(config(WidgetType.TASK, WidgetSizeClass.MEDIUM), source)
        val expanded = factory.create(config(WidgetType.TASK, WidgetSizeClass.EXPANDED), source)

        assertEquals(1, compact.rows.size)
        assertEquals(3, medium.rows.size)
        assertEquals(6, expanded.rows.size)
        assertEquals("할 일 1", compact.rows.single().primaryText)
        assertEquals((1..6).map { "할 일 $it" }, expanded.rows.map { it.primaryText })
    }

    @Test
    fun sensitivePreviewIsMaskedByDefaultAndCanBeDisabled() {
        val source = WidgetSourceData(
            today = today,
            generatedAt = Instant.parse("2026-08-02T01:00:00Z"),
            snapshot = TodaySnapshot(
                notes = listOf(
                    NoteItem(
                        id = "note",
                        inputId = "input",
                        transactionId = "tx",
                        title = "김민수 학생 010-1234-5678 상담",
                        body = "김민수 학생 010-1234-5678 상담",
                    ),
                ),
            ),
        )

        val masked = factory.create(config(WidgetType.NOTE), source)
        val visible = factory.create(
            config(WidgetType.NOTE).copy(maskSensitivePreview = false),
            source,
        )

        assertEquals("김** 학생 010-****-5678 상담", masked.rows.single().primaryText)
        assertEquals("김민수 학생 010-1234-5678 상담", visible.rows.single().primaryText)
        assertTrue(masked.isSensitiveContentMasked)
        assertFalse(visible.isSensitiveContentMasked)
    }

    @Test
    fun ddayHeadlineUsesDateBoundaryInsteadOfClockTime() {
        val source = WidgetSourceData(
            today = today,
            generatedAt = Instant.parse("2026-08-02T23:59:59Z"),
            dDays = listOf(
                DDayItem(
                    id = "opening",
                    inputId = "input",
                    transactionId = "tx",
                    title = "개학",
                    targetDate = LocalDate.of(2026, 8, 5),
                    category = DDayCategory.EVENT,
                    isPinned = true,
                ),
            ),
        )

        val content = factory.create(config(WidgetType.DDAY), source)

        assertEquals("D-3", content.headline)
        assertEquals("개학", content.rows.single().primaryText)
    }

    @Test
    fun emptyInteractiveWidgetsExposeUsefulKoreanPrompts() {
        val source = WidgetSourceData(
            today = today,
            generatedAt = Instant.parse("2026-08-02T01:00:00Z"),
        )

        val quickCapture = factory.create(config(WidgetType.QUICK_CAPTURE), source)
        val calendar = factory.create(config(WidgetType.CALENDAR), source)
        val recommendation = factory.create(config(WidgetType.AI_RECOMMENDATION), source)

        assertEquals("무엇을 기록할까요?", quickCapture.headline)
        assertEquals("예정된 일정이 없습니다.", calendar.emptyMessage)
        assertEquals("추천할 작업이 없습니다.", recommendation.emptyMessage)
    }

    private fun config(
        type: WidgetType,
        size: WidgetSizeClass = WidgetSizeClass.MEDIUM,
    ) = WidgetConfig(
        widgetId = type.ordinal + 10,
        widgetType = type,
        sizeClass = size,
    )

    private fun populatedSource(taskCount: Int = 2): WidgetSourceData {
        val tasks = (1..taskCount).map { index ->
            TaskItem(
                id = "task-$index",
                inputId = "input-$index",
                transactionId = "tx-$index",
                title = "할 일 $index",
                dueAt = Instant.parse("2026-08-02T${(10 + index).toString().padStart(2, '0')}:00:00Z"),
                priority = 3 - (index % 3),
                estimatedMinutes = 20,
            )
        }
        val event = CalendarItem(
            id = "event",
            inputId = "input-event",
            transactionId = "tx-event",
            title = "교무회의",
            startAt = Instant.parse("2026-08-02T01:00:00Z"),
            endAt = Instant.parse("2026-08-02T02:00:00Z"),
        )
        val note = NoteItem(
            id = "note",
            inputId = "input-note",
            transactionId = "tx-note",
            title = "준비물 확인",
            body = "준비물 확인",
        )
        val dDay = DDayItem(
            id = "dday",
            inputId = "input-dday",
            transactionId = "tx-dday",
            title = "개학",
            targetDate = LocalDate.of(2026, 8, 20),
            category = DDayCategory.EVENT,
            isPinned = true,
        )
        return WidgetSourceData(
            today = today,
            generatedAt = Instant.parse("2026-08-02T01:00:00Z"),
            snapshot = TodaySnapshot(tasks = tasks, events = listOf(event), notes = listOf(note)),
            dDays = listOf(dDay),
            calendarItems = listOf(event),
            recommendation = "보고서 제출부터 처리하세요.",
        )
    }
}
