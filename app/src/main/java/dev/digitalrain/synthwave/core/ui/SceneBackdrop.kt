package dev.digitalrain.synthwave.core.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

@Composable
fun SceneBackdrop(
    assetPath: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val assets = LocalContext.current.assets
    val bitmap = remember(assetPath) {
        assets.open(assetPath).use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) {
                "Could not decode backdrop asset: $assetPath"
            }.asImageBitmap()
        }
    }
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
    )
}
