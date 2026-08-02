package com.seongho.brainassistant.widget

import com.seongho.brainassistant.core.model.CalendarItem
import com.seongho.brainassistant.core.model.DDayDisplay
import com.seongho.brainassistant.core.model.DDayItem
import com.seongho.brainassistant.core.model.TodaySnapshot
import com.seongho.brainassistant.core.model.WidgetConfig
import com.seongho.brainassistant.core.model.WidgetSizeClass
import com.seongho.brainassistant.core.model.WidgetType
import com.seongho.brainassistant.core.settings.SensitivePreviewMasker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * 홈 화면 위젯이 공통으로 사용하는 원본 데이터입니다.
 * Android/Glance 의존성을 포함하지 않아 JVM 단위 테스트가 가능합니다.
 */
data class WidgetSourceData(
    val today: LocalDate,
    val generatedAt: Instant,
    val snapshot: TodaySnapshot = TodaySnapshot(),
    val dDays: List<DDayItem> = emptyList(),
    val calendarItems: List<CalendarItem> = emptyList(),
    val recommendation: String? = null,
)

data class WidgetContentRow(
    val primaryText: String,
    val secondaryText: String? = null,
    val itemId: String? = null,
)

data class WidgetRenderContent(
    val widgetId: Int,
    val type: WidgetType,
    val title: String,
    val headline: String? = null,
    val rows: List<WidgetContentRow> = emptyList(),
    val emptyMessage: String? = null,
    val generatedAt: Instant,
    val isSensitiveContentMasked: Boolean,
)

/**
 * 일정·할 일·메모·D-Day 등의 도메인 데이터를 위젯용 읽기 전용 모델로 변환합니다.
 */
class WidgetContentFactory(
    private val masker: SensitivePreviewMasker,
) {
    fun create(
        config: WidgetConfig,
        source: WidgetSourceData,
    ): WidgetRenderContent {
        val limit = config.sizeClass.rowLimit()
        val content = when (config.widgetType) {
            WidgetType.TODAY_SUMMARY -> todaySummary(config, source, limit)
            WidgetType.SCHEDULE -> schedule(config, source, limit)
            WidgetType.TASK -> tasks(config, source, limit)
            WidgetType.DDAY -> dday(config, source, limit)
            WidgetType.NOTE -> notes(config, source, limit)
            WidgetType.QUICK_CAPTURE -> quickCapture(config, source)
            WidgetType.CALENDAR -> calendar(config, source, limit)
            WidgetType.AI_RECOMMENDATION -> recommendation(config, source)
        }
        return content.masked(config.maskSensitivePreview)
    }

    private fun todaySummary(
        config: WidgetConfig,
        source: WidgetSourceData,
        limit: Int,
    ): WidgetRenderContent {
        val rows = buildList {
            source.snapshot.events
                .sortedBy(CalendarItem::startAt)
                .firstOrNull()
                ?.let { event ->
                    add(
                        WidgetContentRow(
                            primaryText = event.title,
                            secondaryText = event.startAt.formatTimeSeoul(),
                            itemId = event.id,
                        ),
                    )
                }
            source.snapshot.tasks
                .sortedWith(compareBy({ it.dueAt ?: Instant.MAX }, { it.id }))
                .firstOrNull()
                ?.let { task ->
                    add(
                        WidgetContentRow(
                            primaryText = task.title,
                            secondaryText = task.dueAt?.formatDateTimeSeoul(),
                            itemId = task.id,
                        ),
                    )
                }
        }.take(limit)
        return base(
            config = config,
            source = source,
            title = "오늘 요약",
            headline = "일정 ${source.snapshot.events.size} · 할 일 ${source.snapshot.tasks.size}",
            rows = rows,
            emptyMessage = if (rows.isEmpty()) "오늘 등록된 항목이 없습니다." else null,
        )
    }

    private fun schedule(
        config: WidgetConfig,
        source: WidgetSourceData,
        limit: Int,
    ): WidgetRenderContent {
        val rows = source.snapshot.events
            .sortedWith(compareBy(CalendarItem::startAt, CalendarItem::id))
            .take(limit)
            .map { event ->
                WidgetContentRow(
                    primaryText = event.title,
                    secondaryText = event.startAt.formatTimeSeoul(),
                    itemId = event.id,
                )
            }
        return base(
            config = config,
            source = source,
            title = "오늘 일정",
            rows = rows,
            emptyMessage = if (rows.isEmpty()) "오늘 예정된 일정이 없습니다." else null,
        )
    }

    private fun tasks(
        config: WidgetConfig,
        source: WidgetSourceData,
        limit: Int,
    ): WidgetRenderContent {
        val rows = source.snapshot.tasks
            .sortedWith(compareBy({ it.dueAt ?: Instant.MAX }, { it.id }))
            .take(limit)
            .map { task ->
                WidgetContentRow(
                    primaryText = task.title,
                    secondaryText = task.dueAt?.formatDateTimeSeoul(),
                    itemId = task.id,
                )
            }
        return base(
            config = config,
            source = source,
            title = "할 일",
            rows = rows,
            emptyMessage = if (rows.isEmpty()) "남은 할 일이 없습니다." else null,
        )
    }

    private fun dday(
        config: WidgetConfig,
        source: WidgetSourceData,
        limit: Int,
    ): WidgetRenderContent {
        val items = source.dDays
            .sortedWith(
                compareByDescending<DDayItem> { it.isPinned }
                    .thenBy { abs(it.targetDate.toEpochDay() - source.today.toEpochDay()) }
                    .thenByDescending { it.importance }
                    .thenBy { it.title },
            )
            .take(limit)
        val representative = items.firstOrNull()
        return base(
            config = config,
            source = source,
            title = "D-Day",
            headline = representative?.let { DDayDisplay.between(source.today, it.targetDate).label() },
            rows = items.map { item ->
                WidgetContentRow(
                    primaryText = item.title,
                    secondaryText = item.targetDate.format(DATE_FORMATTER),
                    itemId = item.id,
                )
            },
            emptyMessage = if (items.isEmpty()) "등록된 D-Day가 없습니다." else null,
        )
    }

    private fun notes(
        config: WidgetConfig,
        source: WidgetSourceData,
        limit: Int,
    ): WidgetRenderContent {
        val rows = source.snapshot.notes
            .sortedWith(
                compareByDescending<com.seongho.brainassistant.core.model.NoteItem> { it.updatedAt }
                    .thenBy { it.id },
            )
            .take(limit)
            .map { note ->
                WidgetContentRow(
                    primaryText = note.title,
                    itemId = note.id,
                )
            }
        return base(
            config = config,
            source = source,
            title = "최근 메모",
            rows = rows,
            emptyMessage = if (rows.isEmpty()) "최근 메모가 없습니다." else null,
        )
    }

    private fun quickCapture(
        config: WidgetConfig,
        source: WidgetSourceData,
    ): WidgetRenderContent = base(
        config = config,
        source = source,
        title = "빠른 입력",
        headline = "무엇을 기록할까요?",
    )

    private fun calendar(
        config: WidgetConfig,
        source: WidgetSourceData,
        limit: Int,
    ): WidgetRenderContent {
        val rows = source.calendarItems
            .sortedWith(compareBy(CalendarItem::startAt, CalendarItem::id))
            .take(limit)
            .map { event ->
                WidgetContentRow(
                    primaryText = event.title,
                    secondaryText = event.startAt.formatDateTimeSeoul(),
                    itemId = event.id,
                )
            }
        return base(
            config = config,
            source = source,
            title = "캘린더",
            rows = rows,
            emptyMessage = if (rows.isEmpty()) "예정된 일정이 없습니다." else null,
        )
    }

    private fun recommendation(
        config: WidgetConfig,
        source: WidgetSourceData,
    ): WidgetRenderContent {
        val recommendation = source.recommendation?.trim().orEmpty()
        return base(
            config = config,
            source = source,
            title = "AI 추천",
            headline = recommendation.ifBlank { null },
            emptyMessage = if (recommendation.isBlank()) "추천할 작업이 없습니다." else null,
        )
    }

    private fun base(
        config: WidgetConfig,
        source: WidgetSourceData,
        title: String,
        headline: String? = null,
        rows: List<WidgetContentRow> = emptyList(),
        emptyMessage: String? = null,
    ) = WidgetRenderContent(
        widgetId = config.widgetId,
        type = config.widgetType,
        title = title,
        headline = headline,
        rows = rows,
        emptyMessage = emptyMessage,
        generatedAt = source.generatedAt,
        isSensitiveContentMasked = config.maskSensitivePreview,
    )

    private fun WidgetRenderContent.masked(enabled: Boolean): WidgetRenderContent = copy(
        title = masker.mask(title, enabled),
        headline = headline?.let { masker.mask(it, enabled) },
        rows = rows.map { row ->
            row.copy(
                primaryText = masker.mask(row.primaryText, enabled),
                secondaryText = row.secondaryText?.let { masker.mask(it, enabled) },
            )
        },
        emptyMessage = emptyMessage?.let { masker.mask(it, enabled) },
        isSensitiveContentMasked = enabled,
    )

    private fun WidgetSizeClass.rowLimit(): Int = when (this) {
        WidgetSizeClass.COMPACT -> 1
        WidgetSizeClass.MEDIUM -> 3
        WidgetSizeClass.EXPANDED -> 6
    }

    private fun DDayDisplay.label(): String = when (this) {
        is DDayDisplay.Before -> "D-$days"
        DDayDisplay.Today -> "D-Day"
        is DDayDisplay.After -> "D+$days"
    }

    private fun Instant.formatTimeSeoul(): String =
        atZone(SEOUL_ZONE).format(TIME_FORMATTER)

    private fun Instant.formatDateTimeSeoul(): String =
        atZone(SEOUL_ZONE).format(DATE_TIME_FORMATTER)

    private companion object {
        val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
        val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREAN)
    }
}
