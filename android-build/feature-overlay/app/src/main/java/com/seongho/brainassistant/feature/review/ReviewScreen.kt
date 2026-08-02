package com.seongho.brainassistant.feature.review

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.seongho.brainassistant.core.model.ClarificationField
import com.seongho.brainassistant.core.model.ItemType

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
                ) { Text(if (state.isSaving) "저장 중" else "전체 저장") }
            }
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (maxWidth >= 600.dp) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OriginalTextCard(
                        originalText = state.originalText,
                        modifier = Modifier.weight(0.4f),
                    )
                    ReviewItemList(
                        state = state,
                        onAction = onAction,
                        includeOriginal = false,
                        modifier = Modifier.weight(0.6f),
                    )
                }
            } else {
                ReviewItemList(
                    state = state,
                    onAction = onAction,
                    includeOriginal = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ReviewItemList(
    state: ReviewUiState,
    onAction: (ReviewAction) -> Unit,
    includeOriginal: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (includeOriginal && state.originalText.isNotBlank()) {
            item { OriginalTextCard(state.originalText) }
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
        items(state.items, key = ReviewItemUi::localId) { item ->
            ReviewItemCard(item, onAction)
        }
    }
}

@Composable
private fun OriginalTextCard(
    originalText: String,
    modifier: Modifier = Modifier,
) {
    if (originalText.isBlank()) return
    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("원문", style = MaterialTheme.typography.labelLarge)
            Text(originalText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WarningText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
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

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ItemType.entries.forEach { type ->
                    OutlinedButton(
                        onClick = { onAction(ReviewAction.ChangeType(item.localId, type)) },
                        enabled = item.type != type,
                    ) {
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
                supportingText = if (item.title.isBlank()) {
                    { Text("제목을 입력해 주세요.") }
                } else null,
            )

            when (item.type) {
                ItemType.EVENT -> {
                    OutlinedTextField(
                        value = item.startAt,
                        onValueChange = { onAction(ReviewAction.ChangeStartAt(item.localId, it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("시작 시각(ISO 형식)") },
                        isError = item.startAt.parseInstantForReview() == null,
                        supportingText = if (item.startAt.parseInstantForReview() == null) {
                            { Text("날짜와 시간을 확인해 주세요.") }
                        } else null,
                    )
                    OutlinedTextField(
                        value = item.endAt,
                        onValueChange = { onAction(ReviewAction.ChangeEndAt(item.localId, it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("종료 시각(선택)") },
                    )
                }

                ItemType.TASK -> OutlinedTextField(
                    value = item.dueAt,
                    onValueChange = { onAction(ReviewAction.ChangeDueAt(item.localId, it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("마감 시각(선택)") },
                )

                ItemType.D_DAY -> OutlinedTextField(
                    value = item.targetDate,
                    onValueChange = { onAction(ReviewAction.ChangeTargetDate(item.localId, it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("목표일(YYYY-MM-DD)") },
                    singleLine = true,
                    isError = item.targetDate.parseLocalDateForReview() == null &&
                        item.dueAt.parseInstantForReview() == null,
                    supportingText = if (
                        item.targetDate.parseLocalDateForReview() == null &&
                        item.dueAt.parseInstantForReview() == null
                    ) {
                        { Text("D-Day 목표일을 입력해 주세요.") }
                    } else null,
                )

                ItemType.NOTE -> OutlinedTextField(
                    value = item.body,
                    onValueChange = { value ->
                        onAction(ReviewAction.ChangeBody(item.localId, value))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("메모 내용") },
                    minLines = 2,
                )
            }

            Text("우선순위", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { priority ->
                    OutlinedButton(
                        onClick = { onAction(ReviewAction.ChangePriority(item.localId, priority)) },
                        enabled = item.priority != priority,
                    ) {
                        Text(priorityLabel(priority))
                    }
                }
            }
        }
    }
}

private fun String.parseInstantForReview(): java.time.Instant? =
    trim().takeIf(String::isNotBlank)?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }

private fun String.parseLocalDateForReview(): java.time.LocalDate? =
    trim().takeIf(String::isNotBlank)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }

private fun typeLabel(type: ItemType): String = when (type) {
    ItemType.NOTE -> "메모"
    ItemType.TASK -> "할 일"
    ItemType.EVENT -> "일정"
    ItemType.D_DAY -> "D-Day"
}

private fun priorityLabel(priority: Int): String = when (priority) {
    1 -> "낮음"
    2 -> "보통"
    else -> "높음"
}
