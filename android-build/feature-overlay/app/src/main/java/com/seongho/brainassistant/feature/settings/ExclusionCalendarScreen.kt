package com.seongho.brainassistant.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate

data class ExclusionSourceUi(
    val id: String,
    val title: String,
    val kindLabel: String,
    val enabled: Boolean,
)

data class ExclusionCandidateUi(
    val sourceId: String,
    val remoteEventId: String,
    val title: String,
    val date: LocalDate,
    val categoryLabel: String,
    val approved: Boolean,
)

data class ExclusionCalendarUiState(
    val sources: List<ExclusionSourceUi> = emptyList(),
    val candidates: List<ExclusionCandidateUi> = emptyList(),
    val isSaving: Boolean = false,
)

sealed interface ExclusionCalendarAction {
    data class ToggleSource(val sourceId: String, val enabled: Boolean) : ExclusionCalendarAction
    data class ToggleCandidate(
        val sourceId: String,
        val remoteEventId: String,
        val date: LocalDate,
        val approved: Boolean,
    ) : ExclusionCalendarAction
    data object Save : ExclusionCalendarAction
    data object Back : ExclusionCalendarAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExclusionCalendarScreen(
    state: ExclusionCalendarUiState,
    onAction: (ExclusionCalendarAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("제외 캘린더") },
                navigationIcon = {
                    IconButton(onClick = { onAction(ExclusionCalendarAction.Back) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("사용할 캘린더") }
            items(state.sources.size, key = { state.sources[it].id }) { index ->
                val source = state.sources[index]
                ListItem(
                    headlineContent = { Text(source.title) },
                    supportingContent = { Text(source.kindLabel) },
                    trailingContent = {
                        Checkbox(
                            checked = source.enabled,
                            onCheckedChange = { onAction(ExclusionCalendarAction.ToggleSource(source.id, it)) },
                            modifier = Modifier.semantics { contentDescription = "${source.title} 선택" },
                        )
                    },
                )
            }
            if (state.candidates.isNotEmpty()) item { Text("학교 일정 제외 후보") }
            items(
                count = state.candidates.size,
                key = { index ->
                    val item = state.candidates[index]
                    "${item.sourceId}:${item.remoteEventId}:${item.date}"
                },
            ) { index ->
                val item = state.candidates[index]
                ListItem(
                    headlineContent = { Text(item.title) },
                    supportingContent = { Text("${item.categoryLabel} · ${item.date}") },
                    trailingContent = {
                        Checkbox(
                            checked = item.approved,
                            onCheckedChange = {
                                onAction(
                                    ExclusionCalendarAction.ToggleCandidate(
                                        item.sourceId,
                                        item.remoteEventId,
                                        item.date,
                                        it,
                                    ),
                                )
                            },
                            modifier = Modifier.semantics { contentDescription = "${item.title} 승인" },
                        )
                    },
                )
            }
            item {
                Button(
                    onClick = { onAction(ExclusionCalendarAction.Save) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("저장") }
            }
        }
    }
}
