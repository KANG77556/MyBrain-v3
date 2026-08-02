package com.seongho.brainassistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEAFF),
    onPrimaryContainer = Color(0xFF0B315F),
    secondary = Color(0xFF49627A),
    secondaryContainer = Color(0xFFDDEAF5),
    background = Color(0xFFF5F8FC),
    onBackground = Color(0xFF17202A),
    surface = Color.White,
    onSurface = Color(0xFF17202A),
    surfaceVariant = Color(0xFFE8EEF7),
    onSurfaceVariant = Color(0xFF52606D),
    outlineVariant = Color(0xFFC8D2DF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF083258),
    primaryContainer = Color(0xFF173B5D),
    onPrimaryContainer = Color(0xFFDCEAFF),
    secondary = Color(0xFFB6C9DD),
    secondaryContainer = Color(0xFF2B4054),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6EDF6),
    surface = Color(0xFF121B2B),
    onSurface = Color(0xFFE6EDF6),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFFB8C4D4),
    outlineVariant = Color(0xFF3C4B60),
)

@Composable
fun BrainAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
