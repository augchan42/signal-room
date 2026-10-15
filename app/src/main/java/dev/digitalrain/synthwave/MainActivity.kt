package dev.digitalrain.synthwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.digitalrain.synthwave.core.ui.SignalRoomTheme
import dev.digitalrain.synthwave.core.ui.SplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // The splash plays once per launch; rotation and process restore skip it.
            var showSplash by rememberSaveable { mutableStateOf(true) }
            Box {
                SignalRoomApp()
                AnimatedVisibility(showSplash, enter = EnterTransition.None, exit = fadeOut(tween(450))) {
                    SignalRoomTheme { SplashScreen(onDone = { showSplash = false }) }
                }
            }
        }
    }
}
