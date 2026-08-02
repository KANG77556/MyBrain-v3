package com.seongho.brainassistant.feature.capture

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun QuickCaptureBar(
    text: String,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .testTag("quick-capture-input"),
        placeholder = { Text("무엇을 기록할까요?") },
        enabled = enabled,
        maxLines = 3,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
        ),
        trailingIcon = {
            Row {
                IconButton(onClick = onVoice, enabled = enabled) {
                    Icon(Icons.Default.Mic, contentDescription = "음성 입력")
                }
                IconButton(onClick = onSubmit, enabled = enabled && text.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "저장")
                }
            }
        },
    )
}
