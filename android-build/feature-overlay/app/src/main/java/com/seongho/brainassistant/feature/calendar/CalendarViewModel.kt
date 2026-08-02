package com.seongho.brainassistant.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seongho.brainassistant.core.model.CalendarItem
import com.seongho.brainassistant.data.BrainRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CalendarViewMode {
    MONTH,
    WEEK,
    AGENDA,
}

data class CalendarUiState(
    val anchorDate: LocalDate = LocalDate.now(CALENDAR_ZONE),
    val selectedDate: LocalDate = anchorDate,
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val events: List<CalendarItem> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
)

sealed interface CalendarAction {
    data object Back : CalendarAction
    data object Previous : CalendarAction
    data object Next : CalendarAction
    data object Today : CalendarAction
    data object Refresh : CalendarAction
    data class SelectMode(val mode: CalendarViewMode) : CalendarAction
    data class SelectDate(val date: LocalDate) : CalendarAction
}

internal fun monthGridDates(anchorDate: LocalDate): List<LocalDate> {
    val firstDay = anchorDate.withDayOfMonth(1)
    val gridStart = firstDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return List(42) { index -> gridStart.plusDays(index.toLong()) }
}

internal fun weekDates(anchorDate: LocalDate): List<LocalDate> {
    val weekStart = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return List(7) { index -> weekStart.plusDays(index.toLong()) }
}

internal fun moveCalendarAnchor(
    anchorDate: LocalDate,
    viewMode: CalendarViewMode,
    direction: Int,
): LocalDate = when (viewMode) {
    CalendarViewMode.MONTH -> anchorDate.plusMonths(direction.toLong())
    CalendarViewMode.WEEK -> anchorDate.plusWeeks(direction.toLong())
    CalendarViewMode.AGENDA -> anchorDate.plusDays(direction.toLong())
}

internal fun eventsForDate(
    date: LocalDate,
    events: List<CalendarItem>,
    zoneId: ZoneId = CALENDAR_ZONE,
): List<CalendarItem> {
    val start = date.atStartOfDay(zoneId).toInstant()
    val end = date.plusDays(1).atStartOfDay(zoneId).toInstant()
    return events
        .asSequence()
        .filter { item ->
            item.deletedAt == null && item.startAt < end && item.endAt > start
        }
        .sortedWith(compareBy<CalendarItem> { it.startAt }.thenBy { it.title })
        .toList()
}

class CalendarViewModel(
    private val repository: BrainRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = CALENDAR_ZONE,
) : ViewModel() {
    private val today: LocalDate = clock.instant().atZone(zoneId).toLocalDate()
    private val _state = MutableStateFlow(
        CalendarUiState(
            anchorDate = today,
            selectedDate = today,
        ),
    )
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun onAction(action: CalendarAction) {
        when (action) {
            CalendarAction.Back -> Unit
            CalendarAction.Previous -> move(-1)
            CalendarAction.Next -> move(1)
            CalendarAction.Today -> {
                _state.update { it.copy(anchorDate = today, selectedDate = today, message = null) }
                refresh()
            }
            CalendarAction.Refresh -> refresh()
            is CalendarAction.SelectMode -> {
                _state.update {
                    it.copy(
                        anchorDate = it.selectedDate,
                        viewMode = action.mode,
                        message = null,
                    )
                }
                refresh()
            }
            is CalendarAction.SelectDate -> {
                _state.update {
                    it.copy(
                        anchorDate = action.date,
                        selectedDate = action.date,
                        message = null,
                    )
                }
                refresh()
            }
        }
    }

    private fun move(direction: Int) {
        _state.update { current ->
            val moved = moveCalendarAnchor(current.anchorDate, current.viewMode, direction)
            current.copy(anchorDate = moved, selectedDate = moved, message = null)
        }
        refresh()
    }

    private fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, message = null) }
            val current = _state.value
            val range = calendarRange(current.anchorDate, current.viewMode, zoneId)
            runCatching {
                repository.listCalendars(range.start, range.end)
                    .filter { it.deletedAt == null }
                    .sortedBy { it.startAt }
            }.onSuccess { events ->
                _state.update { it.copy(events = events, isLoading = false) }
            }.onFailure {
                _state.update {
                    it.copy(
                        isLoading = false,
                        message = "일정을 불러오지 못했습니다.",
                    )
                }
            }
        }
    }
}

private data class CalendarRange(
    val start: Instant,
    val end: Instant,
)

private fun calendarRange(
    anchorDate: LocalDate,
    viewMode: CalendarViewMode,
    zoneId: ZoneId,
): CalendarRange {
    val dates = when (viewMode) {
        CalendarViewMode.MONTH -> monthGridDates(anchorDate)
        CalendarViewMode.WEEK -> weekDates(anchorDate)
        CalendarViewMode.AGENDA -> List(31) { anchorDate.plusDays(it.toLong()) }
    }
    return CalendarRange(
        start = dates.first().atStartOfDay(zoneId).toInstant(),
        end = dates.last().plusDays(1).atStartOfDay(zoneId).toInstant(),
    )
}

internal val CALENDAR_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
