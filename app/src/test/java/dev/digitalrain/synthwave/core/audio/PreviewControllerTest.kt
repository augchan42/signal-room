package dev.digitalrain.synthwave.core.audio

import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.AudioAsset
import dev.digitalrain.synthwave.core.model.MemberId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewControllerTest {
    @Test
    fun switchingWhilePlayingPreservesMusicalPosition() = runBlocking {
        val engine = FakeAudioEngine()
        val controller = PreviewController(engine)
        controller.select(asset("sparse-frontman"))
        controller.play()
        engine.positionMs = 9250

        controller.select(asset("driving-frontman"))

        assertEquals("driving-frontman", controller.state.value.assetId)
        assertEquals(9250, engine.loads.last().second)
        assertEquals(PlaybackStatus.PLAYING, controller.state.value.status)
        assertEquals(listOf(0f to 50L, 1f to 50L), engine.fades)
        controller.close()
    }

    @Test
    fun pausedAndEndedSelectionsDoNotRestart() = runBlocking {
        val engine = FakeAudioEngine()
        val controller = PreviewController(engine)
        controller.select(asset("sparse-frontman"))
        controller.select(asset("driving-frontman"))
        assertEquals(PlaybackStatus.PAUSED, controller.state.value.status)
        assertEquals(0, engine.playCalls)

        controller.play()
        engine.positionMs = 20_000
        controller.refresh()
        controller.select(asset("layered-frontman"))
        assertEquals(PlaybackStatus.ENDED, controller.state.value.status)
        assertEquals(20_000, engine.loads.last().second)
        assertEquals(1, engine.playCalls)
        controller.replay()
        assertEquals(PlaybackStatus.PLAYING, controller.state.value.status)
        assertEquals(0, engine.positionMs)
        controller.close()
    }

    @Test
    fun latestRapidSelectionWinsWithoutStalePlayback() = runBlocking {
        val engine = FakeAudioEngine()
        engine.blockNextLoad = true
        val controller = PreviewController(engine)
        val first = async { controller.select(asset("sparse-frontman")) }
        yield()
        val second = async { controller.select(asset("driving-keytar")) }
        first.await()
        second.await()

        assertEquals("driving-keytar", controller.state.value.assetId)
        assertEquals(PlaybackStatus.PAUSED, controller.state.value.status)
        assertTrue(engine.cancelCalls > 0)
        assertEquals(0, engine.playCalls)
        controller.close()
    }

    @Test
    fun backgroundPausesAndRequiresExplicitResume() = runBlocking {
        val engine = FakeAudioEngine()
        val controller = PreviewController(engine)
        controller.select(asset("sparse-frontman"))
        controller.play()
        controller.onBackground()
        assertEquals(PlaybackStatus.PAUSED, controller.state.value.status)
        assertFalse(engine.playing)
        controller.select(asset("driving-frontman"))
        assertFalse(engine.playing)
        controller.close()
    }

    @Test
    fun backgroundDuringLoadingCannotAutoResume() = runBlocking {
        val engine = FakeAudioEngine()
        val controller = PreviewController(engine)
        controller.select(asset("sparse-frontman"))
        controller.play()
        engine.blockNextLoad = true
        val switching = async { controller.select(asset("driving-frontman")) }
        yield()
        controller.onBackground()
        engine.completePendingLoad()
        switching.await()

        assertEquals(PlaybackStatus.PAUSED, controller.state.value.status)
        assertFalse(engine.playing)
        controller.close()
    }

    @Test
    fun decoderFailureReportsStableAssetId() = runBlocking {
        val engine = FakeAudioEngine()
        engine.failPath = "audio/driving-frontman.m4a"
        val controller = PreviewController(engine)
        controller.select(asset("driving-frontman"))
        assertEquals(PlaybackStatus.ERROR, controller.state.value.status)
        assertEquals("driving-frontman", controller.state.value.errorAssetId)
        assertFalse(engine.playing)
        controller.close()
    }

    @Test
    fun broadcastStartsAtZeroAndMuteDoesNotStopClock() = runBlocking {
        val engine = FakeAudioEngine()
        val controller = PreviewController(engine)
        controller.select(asset("sparse-frontman"))
        controller.play()
        engine.positionMs = 9250
        controller.setMuted(true)
        controller.startBroadcast(asset("layered-keytar"))
        assertEquals(0, engine.loads.last().second)
        assertEquals(PlaybackStatus.PLAYING, controller.state.value.status)
        assertEquals(0f, engine.volume)
        assertTrue(engine.playing)
        controller.close()
    }

    @Test
    fun restoredBroadcastLoadsCheckpointButWaitsForExplicitResume() = runBlocking {
        val engine = FakeAudioEngine()
        val controller = PreviewController(engine)
        controller.startBroadcast(asset("layered-keyboardist"), atMs = 8_250, autoPlay = false)
        assertEquals(8_250, engine.loads.last().second)
        assertEquals(PlaybackStatus.PAUSED, controller.state.value.status)
        assertFalse(engine.playing)
        controller.play()
        assertTrue(engine.playing)
        controller.close()
    }

    @Test
    fun explicitBroadcastAfterEarlierBackgroundStartsPlaying() = runBlocking {
        val engine = FakeAudioEngine()
        val controller = PreviewController(engine)
        controller.select(asset("sparse-frontman"))
        controller.onBackground()

        controller.startBroadcast(asset("sparse-frontman"), autoPlay = true)

        assertEquals(PlaybackStatus.PLAYING, controller.state.value.status)
        assertTrue(engine.playing)
        controller.close()
    }

    @Test
    fun backgroundDuringBroadcastLoadStillRequiresExplicitResume() = runBlocking {
        val engine = FakeAudioEngine()
        engine.blockNextLoad = true
        val controller = PreviewController(engine)
        val loading = async { controller.startBroadcast(asset("sparse-frontman"), autoPlay = true) }
        yield()

        controller.onBackground()
        engine.completePendingLoad()
        loading.await()

        assertEquals(PlaybackStatus.PAUSED, controller.state.value.status)
        assertFalse(engine.playing)
        controller.close()
    }

    private fun asset(id: String): AudioAsset {
        val arrangement = ArrangementId.valueOf(id.substringBefore('-').uppercase())
        val member = MemberId.valueOf(id.substringAfter('-').uppercase())
        return AudioAsset(id, "audio/$id.m4a", arrangement, member, "fixture")
    }

    private class FakeAudioEngine : AudioEngine {
        override var listener: AudioEngine.Listener? = null
        override var positionMs: Long = 0
        override var volume: Float = 1f
        var playing = false
        var playCalls = 0
        var cancelCalls = 0
        var blockNextLoad = false
        var failPath: String? = null
        val loads = mutableListOf<Pair<String, Long>>()
        val fades = mutableListOf<Pair<Float, Long>>()
        private var pending: CompletableDeferred<Unit>? = null

        override suspend fun load(path: String, atMs: Long) {
            loads += path to atMs
            positionMs = atMs
            if (blockNextLoad) {
                blockNextLoad = false
                pending = CompletableDeferred()
                pending!!.await()
            }
            if (path == failPath) throw IllegalStateException("decoder failed")
        }

        override suspend fun fadeTo(target: Float, durationMs: Long) {
            fades += target to durationMs
            volume = target
        }

        override fun cancelPendingLoad() {
            cancelCalls++
            pending?.completeExceptionally(kotlinx.coroutines.CancellationException("superseded"))
            pending = null
        }

        fun completePendingLoad() {
            pending?.complete(Unit)
            pending = null
        }

        override fun play(): Boolean {
            playing = true
            playCalls++
            return true
        }

        override fun pause() { playing = false }
        override fun stop() { playing = false }
        override fun seekTo(positionMs: Long) { this.positionMs = positionMs }
        override fun release() { playing = false }
    }
}
