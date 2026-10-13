package dev.digitalrain.synthwave.core.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.digitalrain.synthwave.core.data.loadAndroidAssetCatalog
import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Selection
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaPlaybackTest {
    @Test
    fun bundledAudioLoadsAndPausedSwitchKeepsPosition() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = loadAndroidAssetCatalog(context.assets)
        val sparse = catalog.audio(Selection(ArrangementId.SPARSE, MemberId.FRONTMAN, ConceptId.MONOCHROME))
        val driving = catalog.audio(Selection(ArrangementId.DRIVING, MemberId.FRONTMAN, ConceptId.MONOCHROME))
        withContext(Dispatchers.Main) {
            val engine = Media3AudioEngine(context)
            val controller = PreviewController(engine)
            try {
                withTimeout(10_000) {
                    controller.select(sparse)
                    assertEquals(PlaybackStatus.PAUSED, controller.state.value.status)
                    engine.seekTo(9250)
                    controller.select(driving)
                    assertEquals("driving-frontman", controller.state.value.assetId)
                    assertTrue(abs(engine.positionMs - 9250) <= 50)
                    controller.play()
                    assertEquals(PlaybackStatus.PLAYING, controller.state.value.status)
                    controller.onBackground()
                    assertEquals(PlaybackStatus.PAUSED, controller.state.value.status)
                }
            } finally {
                controller.close()
            }
        }
    }

    @Test
    fun bundledVideoFollowsAudioClockAndRemainsSilent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = loadAndroidAssetCatalog(context.assets)
        withContext(Dispatchers.Main) {
            val video = BroadcastVideoController(context, catalog)
            try {
                withTimeout(10_000) {
                    video.load(Selection(ArrangementId.DRIVING, MemberId.KEYTAR, ConceptId.WARM))
                    assertEquals(VideoStatus.READY, video.state.value.status)
                    assertEquals(false, video.state.value.loops)
                    assertEquals(0f, video.player.volume)
                    video.sync(9250, PlaybackStatus.PAUSED)
                    assertTrue(abs(video.player.currentPosition - 9250) <= 50)
                    video.sync(9250, PlaybackStatus.PLAYING)
                    assertTrue(video.player.playWhenReady)
                    video.onBackground()
                    assertTrue(!video.player.playWhenReady)
                }
            } finally {
                video.release()
            }
        }
    }
}
