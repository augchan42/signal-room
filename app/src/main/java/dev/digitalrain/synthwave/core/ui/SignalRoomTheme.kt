package dev.digitalrain.synthwave.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import dev.digitalrain.synthwave.R

private val SignalRoomColors = darkColorScheme(
    primary = Color(0xFF54DCEC),
    secondary = Color(0xFFFF7868),
    background = Color(0xFF08111D),
    surface = Color(0xFF0E1B2A),
    onBackground = Color(0xFFE8E1D2),
    onSurface = Color(0xFFE8E1D2),
)

val SignalRoomDisplayFont = FontFamily(Font(R.font.oxanium_variable))

private val DefaultTypography = Typography()

private val SignalRoomTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(fontFamily = SignalRoomDisplayFont),
    displayMedium = DefaultTypography.displayMedium.copy(fontFamily = SignalRoomDisplayFont),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontFamily = SignalRoomDisplayFont),
    titleLarge = DefaultTypography.titleLarge.copy(fontFamily = SignalRoomDisplayFont),
    titleMedium = DefaultTypography.titleMedium.copy(fontFamily = SignalRoomDisplayFont),
    labelLarge = DefaultTypography.labelLarge.copy(fontFamily = SignalRoomDisplayFont),
    labelMedium = DefaultTypography.labelMedium.copy(fontFamily = SignalRoomDisplayFont),
)

@Composable
fun SignalRoomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SignalRoomColors,
        typography = SignalRoomTypography,
        content = content,
    )
}
