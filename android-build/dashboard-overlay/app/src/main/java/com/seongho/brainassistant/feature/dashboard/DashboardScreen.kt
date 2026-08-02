package com.seongho.brainassistant.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seongho.brainassistant.core.model.DDayDisplay
import com.seongho.brainassistant.core.model.DDayItem
import com.seongho.brainassistant.core.model.SyncState
import com.seongho.brainassistant.feature.capture.QuickCaptureBar
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onAction: (DashboardAction) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val todayLabel = remember(state.displayDate) {
        state.displayDate.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))
    }
    val visibleSections = visibleDashboardSections(state).toSet()

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it) }
    }

    BoxWithConstraints {
        val columns = if (maxWidth >= 600.dp) 2 else 1
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                "오늘의 브레인 비서",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                todayLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { onAction(DashboardAction.OpenDDay) }) {
                            Text("D-Day")
                        }
                        IconButton(onClick = { onAction(DashboardAction.OpenCalendar) }) {
                            Icon(Icons.Default.DateRange, contentDescription = "캘린더")
                        }
                        IconButton(onClick = { onAction(DashboardAction.OpenSettings) }) {
                            Icon(Icons.Default.Settings, contentDescription = "설정")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { CaptureDock(state, onAction) },
        ) { scaffoldPadding ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .testTag("dashboard-content"),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { SummaryCard(state) }

                if (DashboardSection.D_DAY in visibleSections) {
                    state.representativeDDay?.let { item ->
                        item { RepresentativeDDayCard(item, state.displayDate) }
                    }
                }

                if (DashboardSection.URGENT_TASKS in visibleSections) {
                    item {
                        SectionCard(
                            title = "긴급 할 일",
                            rows = state.urgentTasks.map { task ->
                                task.title + (task.dueAt?.let { " · ${it.formatDueSeoul()}" } ?: "")
                            },
                            emphasized = true,
                        )
                    }
                }

                item {
                    SectionCard(
                        title = "오늘 일정",
                        rows = state.events.map { event ->
                            buildString {
                                append(event.startAt.formatTimeSeoul())
                                append("  ")
                                append(event.title)
                                syncLabel(event.syncState)?.let { append(" · $it") }
                            }
                        },
                        emptyMessage = "오늘 예정된 일정이 없습니다.",
                    )
                }

                item {
                    SectionCard(
                        title = "추천 집중 작업",
                        rows = listOf(state.focusSuggestion),
                        emphasized = true,
                    )
                }

                if (DashboardSection.RECENT_NOTES in visibleSections) {
                    item { SectionCard("최근 메모", state.notes.map { it.title }) }
                }
            }
        }
    }
}

@Composable
private fun RepresentativeDDayCard(item: DDayItem, today: LocalDate) {
    val display = remember(today, item.targetDate) {
        when (val value = DDayDisplay.between(today, item.targetDate)) {
            is DDayDisplay.Before -> "D-${value.days}"
            DDayDisplay.Today -> "D-Day"
            is DDayDisplay.After -> "D+${value.days}"
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("representative-dday-card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("대표 D-Day", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(item.targetDate.format(DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)))
                }
                Text(display, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CaptureDock(state: DashboardUiState, onAction: (DashboardAction) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("quick-capture-dock"),
        ) {
            if (state.canUndo) {
                TextButton(
                    onClick = { onAction(DashboardAction.Undo) },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("방금 저장 실행 취소") }
            }
            QuickCaptureBar(
                text = state.inputText,
                enabled = !state.isSaving,
                onTextChange = { onAction(DashboardAction.ChangeInput(it)) },
                onSubmit = { onAction(DashboardAction.Submit) },
                onVoice = { onAction(DashboardAction.Voice) },
            )
        }
    }
}

@Composable
private fun SummaryCard(state: DashboardUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("오늘 요약", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SummaryMetric("일정", state.summary.eventCount.toString(), MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f).testTag("summary-metric-events"))
                SummaryMetric("할 일", state.summary.taskCount.toString(), MaterialTheme.colorScheme.secondaryContainer, Modifier.weight(1f).testTag("summary-metric-tasks"))
                SummaryMetric("긴급", state.urgentTasks.size.toString(), MaterialTheme.colorScheme.errorContainer, Modifier.weight(1f).testTag("summary-metric-urgent"))
                SummaryMetric("동기화", if (state.summary.pendingSyncCount == 0) "완료" else state.summary.pendingSyncCount.toString(), MaterialTheme.colorScheme.tertiaryContainer, Modifier.weight(1f).testTag("summary-metric-sync"))
            }
            if (state.summary.overdueCount > 0) {
                Text(
                    "마감이 지난 할 일 ${state.summary.overdueCount}건을 먼저 확인하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    containerColor: Color,
    modifier: Modifier,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = containerColor) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    rows: List<String>,
    emptyMessage: String? = null,
    emphasized: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (rows.isEmpty() && emptyMessage != null) {
                Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            rows.forEach { row ->
                Text(row, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
private val dashboardTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dashboardDueFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")

private fun java.time.Instant.formatTimeSeoul(): String = atZone(SEOUL_ZONE).format(dashboardTimeFormatter)
private fun java.time.Instant.formatDueSeoul(): String = atZone(SEOUL_ZONE).format(dashboardDueFormatter)

private fun syncLabel(state: SyncState): String? = when (state) {
    SyncState.PENDING -> "동기화 대기"
    SyncState.SYNCED -> null
    SyncState.CONFLICT -> "충돌 확인"
    SyncState.FAILED -> "동기화 실패"
    SyncState.LOCAL_ONLY -> "로컬"
}
