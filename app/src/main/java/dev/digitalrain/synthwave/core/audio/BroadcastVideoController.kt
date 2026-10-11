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
)

/** Muted four-second loop. Audio position is authoritative; this player never outputs sound. */
class BroadcastVideoController(
    context: Context,
    private val catalog: AssetCatalog,
) {
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
        volume = 0f
        repeatMode = Player.REPEAT_MODE_ONE
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

    suspend fun load(id: ConceptId) {
        check(!released)
        pending?.cancel(CancellationException("video load superseded"))
        pending = null
        player.stop()
        when (val media = catalog.visualPlayback(id)) {
            is VisualPlayback.Still -> {
                mutableState.value = VideoState(id, VideoStatus.FALLBACK, stillPath = media.path)
            }
            is VisualPlayback.Loop -> {
                mutableState.value = VideoState(id, VideoStatus.LOADING,
                    stillPath = catalog.concept(id).stillPath, loopPath = media.path)
                suspendCancellableCoroutine<Unit> { continuation ->
                    pending = continuation
                    continuation.invokeOnCancellation {
                        if (pending === continuation) pending = null
                    }
                    try {
                        player.setMediaItem(MediaItem.fromUri("asset:///demo/${media.path}"), 0)
                        player.prepare()
                    } catch (_: Exception) {
                        useMatchingStill()
                    }
                }
            }
        }
    }

    fun sync(audioPositionMs: Long, audioStatus: PlaybackStatus) {
        if (released || mutableState.value.status != VideoStatus.READY) return
        val expected = BroadcastClock.visualPosition(audioPositionMs)
        if (BroadcastClock.needsCorrection(audioPositionMs, player.currentPosition)) {
            player.seekTo(expected)
        }
        if (audioStatus == PlaybackStatus.PLAYING) player.play() else player.pause()
    }

    fun onBackground() { player.pause() }

    fun release() {
        if (released) return
        released = true
        pending?.cancel(CancellationException("video controller released"))
        pending = null
        player.removeListener(listener)
        player.release()
        mutableState.value = VideoState()
    }

    private fun useMatchingStill() {
        val current = mutableState.value
        val loop = current.loopPath ?: return
        val recovery = catalog.recoveryForDecoderError(loop)
        val still = (recovery as? AssetRecovery.UseStill)?.stillPath ?: return
        player.pause()
        mutableState.value = current.copy(status = VideoStatus.FALLBACK, stillPath = still)
        val waiting = pending
        pending = null
        if (waiting?.isActive == true) waiting.resume(Unit)
    }
}
