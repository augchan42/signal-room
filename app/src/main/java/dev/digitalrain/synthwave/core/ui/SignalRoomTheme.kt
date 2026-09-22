package dev.digitalrain.synthwave.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SignalRoomColors = darkColorScheme(
    primary = Color(0xFF54DCEC),
    secondary = Color(0xFFFF7868),
    background = Color(0xFF08111D),
    surface = Color(0xFF0E1B2A),
    onBackground = Color(0xFFE8E1D2),
    onSurface = Color(0xFFE8E1D2),
)

@Composable
fun SignalRoomTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SignalRoomColors, content = content)
}
