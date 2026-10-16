package dev.digitalrain.synthwave.core.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

/** One Media3 player for all audible preview and broadcast mixes. Call on the main thread. */
class Media3AudioEngine(context: Context) : AudioEngine {
    private val appContext = context.applicationContext
    private val player = ExoPlayer.Builder(appContext).build()
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var pending: CancellableContinuation<Unit>? = null
    private var released = false
    override var listener: AudioEngine.Listener? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change != AudioManager.AUDIOFOCUS_GAIN) {
            player.pause()
            listener?.onInterruption()
        }
    }
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build())
        .setOnAudioFocusChangeListener(focusListener, Handler(Looper.getMainLooper()))
        .build()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                player.pause()
                abandonFocus()
                listener?.onInterruption()
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                val waiting = pending
                pending = null
                if (waiting?.isActive == true) waiting.resume(Unit)
            }
            if (playbackState == Player.STATE_ENDED) {
                abandonFocus()
                listener?.onEnded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val waiting = pending
            pending = null
            if (waiting?.isActive == true) waiting.resumeWithException(error)
            else listener?.onError()
            abandonFocus()
        }
    }

    init {
        player.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            false,
        )
        player.addListener(playerListener)
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(noisyReceiver, filter)
        }
    }

    override val positionMs: Long get() = player.currentPosition.coerceAtLeast(0)

    override var volume: Float
        get() = player.volume
        set(value) { player.volume = value.coerceIn(0f, 1f) }

    override suspend fun load(path: String, atMs: Long) {
        check(!released)
        cancelPendingLoad()
        suspendCancellableCoroutine<Unit> { continuation ->
            pending = continuation
            continuation.invokeOnCancellation {
                if (pending === continuation) pending = null
            }
            try {
                player.stop()
                // A track that played to its end keeps playWhenReady; loading never starts playback.
                player.playWhenReady = false
                player.setMediaItem(MediaItem.fromUri("asset:///demo/$path"), atMs.coerceAtLeast(0))
                player.prepare()
            } catch (error: Exception) {
                pending = null
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }

    override suspend fun fadeTo(target: Float, durationMs: Long) {
        val start = volume
        if (durationMs <= 0) {
            volume = target
            return
        }
        val steps = 5
        repeat(steps) { index ->
            delay(durationMs / steps)
            volume = start + (target - start) * (index + 1) / steps
        }
    }

    override fun cancelPendingLoad() {
        pending?.cancel(CancellationException("media load superseded"))
        pending = null
        player.stop()
    }

    override fun play(): Boolean {
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            listener?.onInterruption()
            return false
        }
        player.play()
        return true
    }

    override fun pause() {
        player.pause()
        abandonFocus()
    }

    override fun stop() {
        player.stop()
        abandonFocus()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0))
    }

    override fun release() {
        if (released) return
        released = true
        cancelPendingLoad()
        appContext.unregisterReceiver(noisyReceiver)
        player.removeListener(playerListener)
        abandonFocus()
        player.release()
    }

    private fun abandonFocus() {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}
