package com.rork.whispercell.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ControlRoomColors = darkColorScheme(
    primary = Color(0xFF39D7C8),
    onPrimary = Color(0xFF03100F),
    primaryContainer = Color(0xFF103D3A),
    onPrimaryContainer = Color(0xFFC9FFF8),
    secondary = Color(0xFFFFB85C),
    onSecondary = Color(0xFF1E1200),
    secondaryContainer = Color(0xFF3D2505),
    onSecondaryContainer = Color(0xFFFFDCB3),
    tertiary = Color(0xFF9DA7FF),
    onTertiary = Color(0xFF090E2E),
    background = Color(0xFF05080C),
    onBackground = Color(0xFFE9F2F2),
    surface = Color(0xFF0A1017),
    onSurface = Color(0xFFE9F2F2),
    surfaceVariant = Color(0xFF121B24),
    onSurfaceVariant = Color(0xFFB5C7C9),
    outline = Color(0xFF28434A),
    outlineVariant = Color(0xFF172B32),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF270000)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ControlRoomColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
