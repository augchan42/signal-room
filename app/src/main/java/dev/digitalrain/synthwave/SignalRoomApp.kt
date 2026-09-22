package dev.digitalrain.synthwave

import androidx.compose.runtime.Composable
import dev.digitalrain.synthwave.core.ui.SignalRoomTheme
import dev.digitalrain.synthwave.feature.booking.BookingScreen

@Composable
fun SignalRoomApp() {
    SignalRoomTheme {
        BookingScreen()
    }
}
