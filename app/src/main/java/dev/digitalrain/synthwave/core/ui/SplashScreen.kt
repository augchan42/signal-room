package dev.digitalrain.synthwave.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.digitalrain.synthwave.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val SPLASH_HOLD_MS = 2_400L

/** Opening title card over the studio stage. Fades in, holds, then calls [onDone]; a tap skips it. */
@Composable
fun SplashScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val done by rememberUpdatedState(onDone)
    val streak = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { streak.animateTo(1f, tween(600)) }
        launch { delay(250); title.animateTo(1f, tween(700)) }
        launch { delay(650); tagline.animateTo(1f, tween(600)) }
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
        // The streak image is 3:1; its cyan line sits at 69% height and the coral glow at 86%.
        Box(Modifier.fillMaxWidth().safeDrawingPadding().padding(top = 72.dp).aspectRatio(3f)) {
            val streakImage = ImageBitmap.imageResource(R.drawable.splash_title_streak)
            // The streak image has a transparent, feathered ground, so only the light lands on the stage.
            Canvas(Modifier.fillMaxSize()) {
                scale(scaleX = 0.2f + 0.8f * streak.value, scaleY = 1f) {
                    drawImage(
                        streakImage,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        alpha = streak.value,
                    )
                }
            }
            Image(
                painter = painterResource(R.drawable.ic_on_air_lamp),
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-28).dp).size(80.dp)
                    .graphicsLayer { alpha = streak.value },
            )
            // Fade by colour, not graphicsLayer alpha: a layer would clip the glow to the text bounds.
            Text(
                "SIGNAL ROOM",
                modifier = Modifier.align(BiasAlignment(0f, -0.12f))
                    .offset { IntOffset(0, ((1f - title.value) * 16.dp.toPx()).roundToInt()) },
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = TextStyle(
                    fontFamily = SignalRoomDisplayFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 40.sp,
                    letterSpacing = 4.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = title.value),
                    shadow = Shadow(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f * title.value), blurRadius = 28f),
                ),
            )
            Text(
                "LATE-NIGHT BROADCAST",
                modifier = Modifier.align(BiasAlignment(0f, 0.74f)),
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = TextStyle(
                    fontFamily = SignalRoomDisplayFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    letterSpacing = 5.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = tagline.value),
                ),
            )
        }
    }
}
