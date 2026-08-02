package com.seongho.brainassistant.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.seongho.brainassistant.core.model.ClarificationField
import com.seongho.brainassistant.core.model.ItemType
import com.seongho.brainassistant.core.model.RecurrenceDraft
import com.seongho.brainassistant.core.model.RecurrenceEnd

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    state: ReviewUiState,
    onAction: (ReviewAction) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("내용 확인") }) },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onAction(ReviewAction.Cancel) },
                    modifier = Modifier.weight(1f),
                ) { Text("취소") }
                Button(
                    onClick = { onAction(ReviewAction.Save) },
                    enabled = state.isValid && !state.isSaving,
                    modifier = Modifier.weight(1f),
                ) { Text(if (state.isSaving) "저장 중" else "저장") }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.originalText.isNotBlank()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("원문", style = MaterialTheme.typography.labelLarge)
                            Text(state.originalText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (ClarificationField.DATE in state.clarificationFields) {
                item { WarningText("날짜를 확인해 주세요") }
            }
            if (ClarificationField.TIME in state.clarificationFields) {
                item { WarningText("시간을 확인해 주세요") }
            }
            if (ClarificationField.TITLE in state.clarificationFields) {
                item { WarningText("제목을 확인해 주세요") }
            }
            state.conflictMessage?.let { message -> item { WarningText(message) } }
            state.message?.let { message -> item { WarningText(message) } }
            items(state.recurrences, key = RecurrenceDraft::localId) { recurrence ->
                RecurrenceReviewCard(recurrence)
            }
            items(state.items, key = ReviewItemUi::localId) { item ->
                ReviewItemCard(item, onAction)
            }
        }
    }
}

@Composable
private fun RecurrenceReviewCard(recurrence: RecurrenceDraft) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("반복 일정", style = MaterialTheme.typography.labelLarge)
            Text(recurrence.title, style = MaterialTheme.typography.titleMedium)
            Text("${recurrence.startDate} ${recurrence.startTime} · ${recurrence.durationMinutes / 60}시간")
            Text(recurrence.rule.summary())
            Text(when (val end = recurrence.rule.end) {
                is RecurrenceEnd.Until -> "종료일: ${end.date}"
                is RecurrenceEnd.Count -> "${end.occurrences}회"
                RecurrenceEnd.Never -> "종료일 없음"
            })
        }
    }
}

private fun com.seongho.brainassistant.core.model.RecurrenceRule.summary(): String = when (frequency) {
    com.seongho.brainassistant.core.model.RecurrenceFrequency.DAILY -> "매일"
    com.seongho.brainassistant.core.model.RecurrenceFrequency.WEEKLY -> "매주 ${weekdays.sortedBy { it.value }.joinToString(",") { it.name.take(2) }}"
    com.seongho.brainassistant.core.model.RecurrenceFrequency.MONTHLY -> "매월"
    com.seongho.brainassistant.core.model.RecurrenceFrequency.YEARLY -> "매년"
}
@Composable
private fun WarningText(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ReviewItemCard(item: ReviewItemUi, onAction: (ReviewAction) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(typeLabel(item.type), style = MaterialTheme.typography.labelLarge)
                TextButton(
                    onClick = { onAction(ReviewAction.RemoveItem(item.localId)) },
                    modifier = Modifier.semantics { contentDescription = "${item.title} 삭제" },
                ) { Text("삭제") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ItemType.entries.forEach { type ->
                    OutlinedButton(onClick = { onAction(ReviewAction.ChangeType(item.localId, type)) }) {
                        Text(typeLabel(type))
                    }
                }
            }
            OutlinedTextField(
                value = item.title,
                onValueChange = { onAction(ReviewAction.ChangeTitle(item.localId, it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("제목") },
                singleLine = true,
                isError = item.title.isBlank(),
            )
            when (item.type) {
                ItemType.EVENT -> {
                    OutlinedTextField(
                        value = item.startAt,
                        onValueChange = { onAction(ReviewAction.ChangeStartAt(item.localId, it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("시작 시각(ISO 형식)") },
                        isError = item.startAt.parseInstantForReview() == null,
                    )
                    OutlinedTextField(
                        value = item.endAt,
                        onValueChange = { onAction(ReviewAction.ChangeEndAt(item.localId, it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("종료 시각(선택)") },
                    )
                }
                ItemType.TASK, ItemType.D_DAY -> OutlinedTextField(
                    value = item.dueAt,
                    onValueChange = { onAction(ReviewAction.ChangeDueAt(item.localId, it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (item.type == ItemType.D_DAY) "목표 시각(ISO 형식)" else "마감 시각(선택)") },
                    isError = item.type == ItemType.D_DAY && item.dueAt.parseInstantForReview() == null,
                )
                ItemType.NOTE -> Unit
            }
            OutlinedTextField(
                value = item.priority.toString(),
                onValueChange = { value -> value.toIntOrNull()?.let { onAction(ReviewAction.ChangePriority(item.localId, it)) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("우선순위(1~3)") },
                singleLine = true,
            )
        }
    }
}

private fun String.parseInstantForReview(): java.time.Instant? =
    trim().takeIf(String::isNotBlank)?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }

private fun typeLabel(type: ItemType): String = when (type) {
    ItemType.NOTE -> "메모"
    ItemType.TASK -> "할 일"
    ItemType.EVENT -> "일정"
    ItemType.D_DAY -> "D-Day"
}
