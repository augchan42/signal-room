package dev.digitalrain.synthwave.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.digitalrain.synthwave.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val SPLASH_HOLD_MS = 2_400L

/** Opening title card over the studio stage. Fades in, holds, then calls [onDone]; a tap skips it. */
@Composable
fun SplashScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val done by rememberUpdatedState(onDone)
    val title = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { title.animateTo(1f, tween(700)) }
        launch { delay(400); tagline.animateTo(1f, tween(600)) }
        delay(SPLASH_HOLD_MS)
        done()
    }
    Box(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("splash")
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done() },
    ) {
        Image(
            painter = painterResource(R.drawable.splash_stage),
            contentDescription = "A synthwave band on a broadcast stage, seen from the producer's desk",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0xCC08111D), 0.35f to Color(0x0008111D)),
            ),
        )
        Column(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(top = 96.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "SIGNAL ROOM",
                modifier = Modifier.graphicsLayer {
                    alpha = title.value
                    translationY = (1f - title.value) * 24.dp.toPx()
                },
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = SignalRoomDisplayFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 46.sp,
                    letterSpacing = 6.sp,
                    color = MaterialTheme.colorScheme.primary,
                    shadow = Shadow(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), blurRadius = 28f),
                ),
            )
            Text(
                "LATE-NIGHT BROADCAST",
                modifier = Modifier.graphicsLayer { alpha = tagline.value },
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = SignalRoomDisplayFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    letterSpacing = 5.sp,
                    color = MaterialTheme.colorScheme.secondary,
                ),
            )
        }
    }
}
