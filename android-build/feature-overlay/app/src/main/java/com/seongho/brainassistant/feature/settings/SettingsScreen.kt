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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seongho.brainassistant.core.settings.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("설정") }, navigationIcon = {
            IconButton(onClick = { onAction(SettingsAction.Back) }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
            }
        })
    }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingCard("화면 모드", themeLabel(state.themeMode)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            TextButton(onClick = { onAction(SettingsAction.SetThemeMode(mode)) }) { Text(themeLabel(mode)) }
                        }
                    }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("로그인 유지") },
                    supportingContent = { Text("다음 실행 때 Google 계정을 다시 선택하지 않습니다.") },
                    trailingContent = { Switch(state.keepSignedIn, { onAction(SettingsAction.SetKeepSignedIn(it)) }) },
                )
            }
            item {
                SettingCard("아침 브리핑", state.briefingTime) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAction(SettingsAction.SetBriefingTime((state.briefingHour + 23) % 24, state.briefingMinute)) }) { Text("-1시간") }
                        Button(onClick = { onAction(SettingsAction.SetBriefingTime((state.briefingHour + 1) % 24, state.briefingMinute)) }) { Text("+1시간") }
                    }
                }
            }
            item { Text("Google Calendar 연결 상태: ${state.calendarStatusLabel}") }
            item { Button(onClick = { onAction(SettingsAction.SyncNow) }, modifier = Modifier.fillMaxWidth()) { Text("지금 동기화") } }
            item {
                ListItem(
                    headlineContent = { Text("공휴일·학교 제외 일정") },
                    supportingContent = { Text("반복 수업에서 제외할 캘린더와 학교 일정을 선택합니다.") },
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable { onAction(SettingsAction.OpenExclusions) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("휴지통") },
                    supportingContent = { Text("삭제한 기록은 30일 동안 복구할 수 있습니다.") },
                    leadingContent = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable { onAction(SettingsAction.OpenTrash) },
                )
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, value: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title)
        Text(value)
        content()
    }
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "시스템 설정"
    ThemeMode.LIGHT -> "밝게"
    ThemeMode.DARK -> "어둡게"
}
