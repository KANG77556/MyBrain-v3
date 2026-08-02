package com.seongho.brainassistant.feature.dday

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seongho.brainassistant.core.model.DDayCategory
import com.seongho.brainassistant.core.model.DDayDisplay
import com.seongho.brainassistant.core.model.DDayItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DDayScreen(
    state: DDayUiState,
    onAction: (DDayAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("D-Day") },
                navigationIcon = {
                    IconButton(onClick = { onAction(DDayAction.Back) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAction(DDayAction.Add) }) {
                Icon(Icons.Default.Add, contentDescription = "D-Day 추가")
            }
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val columns = if (maxWidth >= 600.dp) 2 else 1
            if (state.items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("등록된 D-Day가 없습니다.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "오른쪽 아래 + 버튼으로 시험·제출·행사·기념일을 추가하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.items, key = DDayItem::id) { item ->
                        DDayCard(
                            item = item,
                            today = state.today,
                            onEdit = { onAction(DDayAction.Edit(item.id)) },
                            onDelete = { onAction(DDayAction.Delete(item.id)) },
                        )
                    }
                }
            }
        }
        if (state.editorVisible) {
            DDayEditorDialog(state = state, onAction = onAction)
        }
    }
}

@Composable
private fun DDayCard(
    item: DDayItem,
    today: java.time.LocalDate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val display = DDayDisplay.between(today, item.targetDate)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isPinned) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isPinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "대표 D-Day",
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    ddayLabel(display),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "${item.targetDate} · ${categoryLabel(item.category)} · 중요도 ${item.importance}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "알림 ${item.reminderOffsets.sortedDescending().joinToString { if (it == 0) "당일" else "D-$it" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "${item.title} 수정")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "${item.title} 삭제")
                }
            }
        }
    }
}

@Composable
private fun DDayEditorDialog(
    state: DDayUiState,
    onAction: (DDayAction) -> Unit,
) {
    val editor = state.editor
    val validDate = runCatching { java.time.LocalDate.parse(editor.targetDate.trim()) }.isSuccess
    AlertDialog(
        onDismissRequest = { onAction(DDayAction.CloseEditor) },
        title = { Text(if (editor.editingId == null) "D-Day 추가" else "D-Day 수정") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = editor.title,
                    onValueChange = { onAction(DDayAction.ChangeTitle(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("제목") },
                    singleLine = true,
                    isError = editor.title.isBlank(),
                )
                OutlinedTextField(
                    value = editor.targetDate,
                    onValueChange = { onAction(DDayAction.ChangeTargetDate(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("목표일 YYYY-MM-DD") },
                    singleLine = true,
                    isError = editor.targetDate.isNotBlank() && !validDate,
                )
                Text("분류", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DDayCategory.entries.forEach { category ->
                        OutlinedButton(
                            onClick = { onAction(DDayAction.ChangeCategory(category)) },
                            enabled = editor.category != category,
                        ) { Text(categoryLabel(category)) }
                    }
                }
                Text("중요도", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { importance ->
                        OutlinedButton(
                            onClick = { onAction(DDayAction.ChangeImportance(importance)) },
                            enabled = editor.importance != importance,
                        ) { Text(importance.toString()) }
                    }
                }
                ToggleRow("대표 D-Day로 고정", editor.isPinned) {
                    onAction(DDayAction.TogglePinned)
                }
                ToggleRow("지난 날짜도 표시", editor.showElapsedDays) {
                    onAction(DDayAction.ToggleShowElapsed)
                }
                OutlinedTextField(
                    value = editor.archiveAfterDays.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let { onAction(DDayAction.ChangeArchiveAfterDays(it)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("지난 뒤 보관 일수") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = editor.recurrenceRule,
                    onValueChange = { onAction(DDayAction.ChangeRecurrenceRule(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("반복 규칙(선택, 예: YEARLY)") },
                    singleLine = true,
                )
                Text("알림", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(30, 14, 7, 3, 1, 0).forEach { days ->
                        OutlinedButton(
                            onClick = { onAction(DDayAction.ToggleReminder(days)) },
                            enabled = days !in editor.reminderOffsets,
                        ) { Text(if (days == 0) "당일" else "D-$days") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAction(DDayAction.Save) },
                enabled = !state.isSaving && editor.title.isNotBlank() && validDate,
            ) { Text(if (state.isSaving) "저장 중" else "저장") }
        },
        dismissButton = {
            TextButton(onClick = { onAction(DDayAction.CloseEditor) }) { Text("취소") }
        },
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

private fun ddayLabel(display: DDayDisplay): String = when (display) {
    is DDayDisplay.Before -> "D-${display.days}"
    DDayDisplay.Today -> "D-Day"
    is DDayDisplay.After -> "D+${display.days}"
}

private fun categoryLabel(category: DDayCategory): String = when (category) {
    DDayCategory.DEADLINE -> "마감"
    DDayCategory.EXAM -> "시험"
    DDayCategory.EVENT -> "행사"
    DDayCategory.HEALTH -> "건강"
    DDayCategory.TRAVEL -> "여행"
    DDayCategory.ANNIVERSARY -> "기념일"
    DDayCategory.CUSTOM -> "기타"
}
