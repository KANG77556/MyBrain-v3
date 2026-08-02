package com.seongho.brainassistant.feature.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seongho.brainassistant.core.model.RecurrenceScope

data class RecurrenceScopeOption(val scope: RecurrenceScope, val label: String)

fun recurrenceScopeOptions(): List<RecurrenceScopeOption> = listOf(
    RecurrenceScopeOption(RecurrenceScope.THIS_OCCURRENCE, "이번 일정만"),
    RecurrenceScopeOption(RecurrenceScope.THIS_AND_FOLLOWING, "이후 일정"),
    RecurrenceScopeOption(RecurrenceScope.ALL_OCCURRENCES, "전체 반복 일정"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrenceScopeSheet(
    delete: Boolean,
    onScope: (RecurrenceScope) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = if (delete) "반복 일정 삭제 범위" else "반복 일정 수정 범위",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            recurrenceScopeOptions().forEach { option ->
                ListItem(
                    headlineContent = { Text(option.label) },
                    modifier = Modifier.clickable { onScope(option.scope) },
                )
            }
        }
    }
}
