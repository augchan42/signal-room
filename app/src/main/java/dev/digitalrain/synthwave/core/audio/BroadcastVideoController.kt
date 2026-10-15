package dev.digitalrain.synthwave.core.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.digitalrain.synthwave.core.data.AssetCatalog
import dev.digitalrain.synthwave.core.data.AssetRecovery
import dev.digitalrain.synthwave.core.data.VisualPlayback
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.Selection
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

enum class VideoStatus { IDLE, LOADING, READY, FALLBACK }

data class VideoState(
    val conceptId: ConceptId? = null,
    val status: VideoStatus = VideoStatus.IDLE,
    val stillPath: String? = null,
    val loopPath: String? = null,
    /** True for the four-second concept loop, false for the 20-second performance of the mix. */
    val loops: Boolean = true,
)

/**
 * Muted video of the band: the mix's 20-second performance, played once, or the concept's
 * four-second loop if the performance cannot be opened. Audio position is authoritative; this
 * player never outputs sound.
 */
class BroadcastVideoController(
    context: Context,
    private val catalog: AssetCatalog,
) {
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
        volume = 0f
        repeatMode = Player.REPEAT_MODE_ONE
    }
    /** Second muted decoder of the same performance, shown zoomed on the featured member. */
    val camPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
        volume = 0f
        repeatMode = Player.REPEAT_MODE_OFF
    }
    private val mutableState = MutableStateFlow(VideoState())
    val state: StateFlow<VideoState> = mutableState.asStateFlow()
    private var pending: CancellableContinuation<Unit>? = null
    private var released = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                val waiting = pending
                pending = null
                if (waiting?.isActive == true) waiting.resume(Unit)
                if (mutableState.value.status == VideoStatus.LOADING) {
                    mutableState.value = mutableState.value.copy(status = VideoStatus.READY)
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            useMatchingStill()
        }
    }

    init { player.addListener(listener) }

    suspend fun load(selection: Selection) {
        check(!released)
        pending?.cancel(CancellationException("video load superseded"))
        pending = null
        player.stop()
        camPlayer.stop()
        camPlayer.clearMediaItems()
        val id = selection.concept
        val media = catalog.visualPlayback(selection)
        val video = when (media) {
            is VisualPlayback.Still -> {
                mutableState.value = VideoState(id, VideoStatus.FALLBACK, stillPath = media.path)
                null
            }
            is VisualPlayback.Performance -> media.path
            is VisualPlayback.Loop -> media.path
        }
        if (video == null) return
        val loops = media is VisualPlayback.Loop
        player.repeatMode = if (loops) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        mutableState.value = VideoState(id, VideoStatus.LOADING,
            stillPath = catalog.concept(id).stillPath, loopPath = video, loops = loops)
        suspendCancellableCoroutine<Unit> { continuation ->
            pending = continuation
            continuation.invokeOnCancellation {
                if (pending === continuation) pending = null
            }
            try {
                player.setMediaItem(MediaItem.fromUri("asset:///demo/$video"), 0)
                player.prepare()
                if (!loops) {
                    camPlayer.setMediaItem(MediaItem.fromUri("asset:///demo/$video"), 0)
                    camPlayer.prepare()
                }
            } catch (_: Exception) {
                useMatchingStill()
            }
        }
    }

    fun sync(audioPositionMs: Long, audioStatus: PlaybackStatus) {
        if (released || mutableState.value.status != VideoStatus.READY) return
        val loops = mutableState.value.loops
        val expected = BroadcastClock.visualPosition(audioPositionMs, loops)
        if (BroadcastClock.needsCorrection(audioPositionMs, player.currentPosition, loops)) {
            player.seekTo(expected)
        }
        if (audioStatus == PlaybackStatus.PLAYING) player.play() else player.pause()
        if (!loops && camPlayer.mediaItemCount > 0) {
            if (BroadcastClock.needsCorrection(audioPositionMs, camPlayer.currentPosition, false)) {
                camPlayer.seekTo(expected)
            }
            if (audioStatus == PlaybackStatus.PLAYING) camPlayer.play() else camPlayer.pause()
        }
    }

    fun onBackground() {
        player.pause()
        camPlayer.pause()
    }

    fun release() {
        if (released) return
        released = true
        pending?.cancel(CancellationException("video controller released"))
        pending = null
        player.removeListener(listener)
        player.release()
        camPlayer.release()
        mutableState.value = VideoState()
    }

    private fun useMatchingStill() {
        val current = mutableState.value
        val loop = current.loopPath ?: return
        val recovery = catalog.recoveryForDecoderError(loop)
        val still = (recovery as? AssetRecovery.UseStill)?.stillPath ?: return
        player.pause()
        camPlayer.stop()
        camPlayer.clearMediaItems()
        mutableState.value = current.copy(status = VideoStatus.FALLBACK, stillPath = still)
        val waiting = pending
        pending = null
        if (waiting?.isActive == true) waiting.resume(Unit)
    }
}
