package com.seongho.brainassistant.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seongho.brainassistant.core.model.CalendarItem
import com.seongho.brainassistant.core.model.SyncState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onAction: (CalendarAction) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("캘린더", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { onAction(CalendarAction.Back) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            CalendarPeriodHeader(state = state, onAction = onAction)
            CalendarModeSelector(state.viewMode, onAction)
            state.message?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isTablet = maxWidth >= 600.dp
                if (isTablet) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CalendarPane(
                            state = state,
                            onAction = onAction,
                            modifier = Modifier.weight(1.15f),
                        )
                        AgendaPane(
                            state = state,
                            modifier = Modifier
                                .weight(0.85f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CalendarPane(
                            state = state,
                            onAction = onAction,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AgendaPane(
                            state = state,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarPeriodHeader(
    state: CalendarUiState,
    onAction: (CalendarAction) -> Unit,
) {
    val title = remember(state.anchorDate, state.viewMode) {
        when (state.viewMode) {
            CalendarViewMode.MONTH -> state.anchorDate.format(MONTH_TITLE_FORMATTER)
            CalendarViewMode.WEEK -> {
                val dates = weekDates(state.anchorDate)
                "${dates.first().format(SHORT_DATE_FORMATTER)} - ${dates.last().format(SHORT_DATE_FORMATTER)}"
            }
            CalendarViewMode.AGENDA -> state.anchorDate.format(FULL_DATE_FORMATTER)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onAction(CalendarAction.Previous) }) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "이전 기간")
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        IconButton(onClick = { onAction(CalendarAction.Next) }) {
            Icon(Icons.Default.ChevronRight, contentDescription = "다음 기간")
        }
        TextButton(onClick = { onAction(CalendarAction.Today) }) {
            Text("오늘")
        }
    }
}

@Composable
private fun CalendarModeSelector(
    selectedMode: CalendarViewMode,
    onAction: (CalendarAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CalendarModeChip("월간", CalendarViewMode.MONTH, selectedMode, onAction, Modifier.weight(1f))
        CalendarModeChip("주간", CalendarViewMode.WEEK, selectedMode, onAction, Modifier.weight(1f))
        CalendarModeChip("목록", CalendarViewMode.AGENDA, selectedMode, onAction, Modifier.weight(1f))
    }
}

@Composable
private fun CalendarModeChip(
    label: String,
    mode: CalendarViewMode,
    selectedMode: CalendarViewMode,
    onAction: (CalendarAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selectedMode == mode,
        onClick = { onAction(CalendarAction.SelectMode(mode)) },
        label = {
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun CalendarPane(
    state: CalendarUiState,
    onAction: (CalendarAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        when (state.viewMode) {
            CalendarViewMode.MONTH -> MonthCalendar(state, onAction)
            CalendarViewMode.WEEK -> WeekCalendar(state, onAction)
            CalendarViewMode.AGENDA -> AgendaOverview(state)
        }
    }
}

@Composable
private fun MonthCalendar(
    state: CalendarUiState,
    onAction: (CalendarAction) -> Unit,
) {
    val dates = remember(state.anchorDate) { monthGridDates(state.anchorDate) }
    val eventDates = remember(state.events) {
        state.events
            .filter { it.deletedAt == null }
            .map { it.startAt.atZone(CALENDAR_ZONE).toLocalDate() }
            .toSet()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp)
            .testTag("calendar-month-grid"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_LABELS.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        dates.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                week.forEach { date ->
                    DateCell(
                        date = date,
                        isCurrentMonth = date.month == state.anchorDate.month,
                        isSelected = date == state.selectedDate,
                        hasEvent = date in eventDates,
                        onClick = { onAction(CalendarAction.SelectDate(date)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekCalendar(
    state: CalendarUiState,
    onAction: (CalendarAction) -> Unit,
) {
    val dates = remember(state.anchorDate) { weekDates(state.anchorDate) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            dates.forEach { date ->
                DateCell(
                    date = date,
                    isCurrentMonth = true,
                    isSelected = date == state.selectedDate,
                    hasEvent = eventsForDate(date, state.events).isNotEmpty(),
                    onClick = { onAction(CalendarAction.SelectDate(date)) },
                    modifier = Modifier.weight(1f),
                    showWeekday = true,
                )
            }
        }
    }
}

@Composable
private fun DateCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    hasEvent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showWeekday: Boolean = false,
) {
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        date == LocalDate.now(CALENDAR_ZONE) -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        !isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier
            .aspectRatio(if (showWeekday) 0.72f else 1f)
            .clickable(onClick = onClick),
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showWeekday) {
                Text(
                    text = date.format(WEEKDAY_FORMATTER),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                )
            }
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
            )
            if (hasEvent) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(5.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            } else {
                Spacer(Modifier.height(7.dp))
            }
        }
    }
}

@Composable
private fun AgendaPane(
    state: CalendarUiState,
    modifier: Modifier = Modifier,
) {
    if (state.viewMode == CalendarViewMode.AGENDA) {
        AgendaOverview(state, modifier)
        return
    }
    val selectedEvents = remember(state.selectedDate, state.events) {
        eventsForDate(state.selectedDate, state.events)
    }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = state.selectedDate.format(FULL_DATE_FORMATTER),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (selectedEvents.isEmpty()) {
                Text(
                    text = "선택한 날짜에 일정이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                selectedEvents.forEach { event -> EventRow(event) }
            }
        }
    }
}

@Composable
private fun AgendaOverview(
    state: CalendarUiState,
    modifier: Modifier = Modifier,
) {
    val events = remember(state.events) {
        state.events.filter { it.deletedAt == null }.sortedBy { it.startAt }
    }
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "일정 목록",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (events.isEmpty()) {
            Text(
                text = "표시할 일정이 없습니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            events.forEach { event ->
                Text(
                    text = event.startAt.atZone(CALENDAR_ZONE).toLocalDate().format(FULL_DATE_FORMATTER),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                EventRow(event)
            }
        }
    }
}

@Composable
private fun EventRow(event: CalendarItem) {
    val timeLabel = remember(event.startAt, event.endAt) {
        val start = event.startAt.atZone(CALENDAR_ZONE)
        val end = event.endAt.atZone(CALENDAR_ZONE)
        "${start.format(TIME_FORMATTER)} - ${end.format(TIME_FORMATTER)}"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            syncLabel(event.syncState)?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

private fun syncLabel(state: SyncState): String? = when (state) {
    SyncState.PENDING -> "동기화 대기"
    SyncState.SYNCED -> null
    SyncState.CONFLICT -> "충돌 확인"
    SyncState.FAILED -> "동기화 실패"
    SyncState.LOCAL_ONLY -> "로컬"
}

private val WEEKDAY_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")
private val MONTH_TITLE_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
private val FULL_DATE_FORMATTER = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
private val SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)
private val WEEKDAY_FORMATTER = DateTimeFormatter.ofPattern("E", Locale.KOREAN)
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
