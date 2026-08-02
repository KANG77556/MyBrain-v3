package com.seongho.brainassistant.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = { onAction(SettingsAction.Back) }) {
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
            item {
                SettingCard("아침 브리핑", state.briefingTime) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            onAction(SettingsAction.SetBriefingTime((state.briefingHour + 23) % 24, state.briefingMinute))
                        }) { Text("-1시간") }
                        Button(onClick = {
                            onAction(SettingsAction.SetBriefingTime((state.briefingHour + 1) % 24, state.briefingMinute))
                        }) { Text("+1시간") }
                    }
                }
            }
            item {
                SettingCard("방해 금지 시간", "%02d:00~%02d:00".format(state.quietStart, state.quietEnd)) {
                    Text("시작과 종료가 같으면 방해 금지를 사용하지 않습니다.")
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("민감정보 미리보기 가리기") },
                    supportingContent = { Text("학생 이름과 전화번호 일부를 가립니다.") },
                    trailingContent = {
                        Switch(
                            checked = state.maskSensitivePreview,
                            onCheckedChange = { onAction(SettingsAction.SetMasking(it)) },
                        )
                    },
                )
            }
            item { Text("Google Calendar 연결 상태: ${state.calendarStatusLabel}") }
            item {
                ListItem(
                    headlineContent = { Text("휴지통") },
                    supportingContent = { Text("삭제한 기록은 30일 동안 복구할 수 있습니다.") },
                    leadingContent = {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onAction(SettingsAction.OpenTrash) },
                )
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, value: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title)
        Text(value)
        content()
    }
}
