package dev.digitalrain.synthwave.feature.booking

import android.graphics.Matrix
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import dev.digitalrain.synthwave.core.model.MemberId

/**
 * What each member's camera frames in the performance video: the centre of the moving part, as
 * fractions of the frame's width and height, and how much of the frame's width the shot covers.
 */
internal data class CamFocus(val x: Float, val y: Float, val fieldWidth: Float)

// Every performance video uses the same stage layout, whichever member is featured.
internal fun camFocus(member: MemberId): CamFocus = when (member) {
    MemberId.FRONTMAN -> CamFocus(0.585f, 0.515f, 0.13f) // hair, face and mic
    MemberId.KEYTAR -> CamFocus(0.78f, 0.565f, 0.18f) // both hands on the keytar
    MemberId.KEYBOARDIST -> CamFocus(0.21f, 0.54f, 0.16f) // hands on the BORG keys
    MemberId.DRUMMER -> CamFocus(0.47f, 0.47f, 0.22f) // sticks on the pads
}

private const val VIDEO_ASPECT = 540f / 960f

/**
 * Transform for a TextureView of [viewWidth]×[viewHeight] that already stretches the whole video
 * to its bounds, so it instead shows a window [CamFocus.fieldWidth] of the frame wide, centred on
 * the focus point, at the video's own aspect ratio.
 */
internal fun camMatrixValues(viewWidth: Float, viewHeight: Float, focus: CamFocus): FloatArray {
    val scaleX = 1f / focus.fieldWidth
    // Same on-screen size per video pixel vertically as horizontally.
    val scaleY = scaleX * (viewWidth / viewHeight) / VIDEO_ASPECT
    val dx = viewWidth / 2 - focus.x * viewWidth * scaleX
    val dy = viewHeight / 2 - focus.y * viewHeight * scaleY
    return floatArrayOf(scaleX, 0f, dx, 0f, scaleY, dy, 0f, 0f, 1f)
}

/** Live close-up of the featured member, cut from the second decoder of the performance video. */
@Composable
internal fun MemberCam(player: ExoPlayer, member: MemberId, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = remember(context) { TextureView(context) }
    DisposableEffect(player, view) {
        player.setVideoTextureView(view)
        onDispose { player.clearVideoTextureView(view) }
    }
    AndroidView(
        factory = {
            view.apply {
                addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    (v as TextureView).setTransform(Matrix().apply {
                        setValues(camMatrixValues(v.width.toFloat(), v.height.toFloat(), camFocus(member)))
                    })
                }
            }
        },
        update = {
            if (it.width > 0) it.setTransform(Matrix().apply {
                setValues(camMatrixValues(it.width.toFloat(), it.height.toFloat(), camFocus(member)))
            })
        },
        modifier = modifier,
    )
}
