package dev.digitalrain.synthwave.feature.booking

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.digitalrain.synthwave.core.stage.FilamentStage

/** The live stage in a SurfaceView; Compose overlays (timer, inset, count-in) draw above it. */
@Composable
internal fun LiveStageView(stage: FilamentStage, modifier: Modifier = Modifier) {
    AndroidView(factory = { context ->
        SurfaceView(context).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) = Unit
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    stage.attachMain(holder.surface, width, height)
                }
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    stage.detachMain()
                }
            })
        }
    }, modifier = modifier)
}

/** The featured member's fancam from the same scene, in a TextureView so the inset can clip it. */
@Composable
internal fun LiveMemberCam(stage: FilamentStage, modifier: Modifier = Modifier) {
    AndroidView(factory = { context ->
        TextureView(context).apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                private var surface: Surface? = null

                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                    surface = Surface(texture).also { stage.attachCam(it, width, height) }
                }

                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                    surface?.let { stage.attachCam(it, width, height) }
                }

                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                    stage.detachCam()
                    surface?.release()
                    surface = null
                    return true
                }

                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
            }
        }
    }, modifier = modifier)
}
