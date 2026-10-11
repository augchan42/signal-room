package dev.digitalrain.synthwave.core.audio

import dev.digitalrain.synthwave.core.model.AudioAsset
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AudioEngine {
    interface Listener {
        fun onEnded()
        fun onError()
        fun onInterruption()
    }

    var listener: Listener?
    val positionMs: Long
    var volume: Float
    suspend fun load(path: String, atMs: Long)
    suspend fun fadeTo(target: Float, durationMs: Long)
    fun cancelPendingLoad()
    fun play(): Boolean
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun release()
}

enum class PlaybackStatus { IDLE, LOADING, PLAYING, PAUSED, ENDED, ERROR }

data class PlaybackState(
    val assetId: String? = null,
    val positionMs: Long = 0,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val errorAssetId: String? = null,
    val muted: Boolean = false,
)

/** Owns one audible engine. The latest selection wins even if an earlier load completes late. */
class PreviewController(private val engine: AudioEngine) : AudioEngine.Listener {
    private val commands = Mutex()
    private val generation = AtomicLong()
    private val mutableState = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    private var closed = false
    private var resumeAllowed = true

    init { engine.listener = this }

    suspend fun select(asset: AudioAsset) {
        check(!closed)
        val token = generation.incrementAndGet()
        val before = mutableState.value
        val position = when (before.status) {
            PlaybackStatus.IDLE, PlaybackStatus.ERROR -> 0L
            PlaybackStatus.ENDED -> 20_000L
            else -> engine.positionMs.coerceIn(0L, 20_000L)
        }
        if (before.status == PlaybackStatus.LOADING) engine.cancelPendingLoad()
        commands.withLock {
            if (token != generation.get()) return@withLock
            val shouldResume = before.status == PlaybackStatus.PLAYING
            mutableState.value = before.copy(assetId = asset.id, positionMs = position,
                status = PlaybackStatus.LOADING, errorAssetId = null)
            try {
                if (shouldResume) engine.fadeTo(0f, 50L)
                if (token != generation.get()) return@withLock
                engine.stop()
                engine.load(asset.path, position)
                if (token != generation.get()) return@withLock
                var status = when {
                    before.status == PlaybackStatus.PLAYING && resumeAllowed -> PlaybackStatus.PLAYING
                    before.status == PlaybackStatus.ENDED -> PlaybackStatus.ENDED
                    else -> PlaybackStatus.PAUSED
                }
                if (status == PlaybackStatus.PLAYING) {
                    if (engine.play()) {
                        engine.fadeTo(if (mutableState.value.muted) 0f else 1f, 50L)
                    } else {
                        status = PlaybackStatus.PAUSED
                    }
                } else {
                    engine.volume = if (mutableState.value.muted) 0f else 1f
                }
                mutableState.value = mutableState.value.copy(positionMs = position, status = status)
            } catch (error: CancellationException) {
                if (token == generation.get()) throw error
            } catch (_: Exception) {
                if (token == generation.get()) fail(asset.id)
            }
        }
    }

    suspend fun startBroadcast(asset: AudioAsset, atMs: Long = 0L, autoPlay: Boolean = true) {
        check(!closed)
        val position = atMs.coerceIn(0L, 20_000L)
        val token = generation.incrementAndGet()
        engine.cancelPendingLoad()
        commands.withLock {
            if (token != generation.get()) return@withLock
            if (autoPlay) resumeAllowed = true
            engine.stop()
            mutableState.value = mutableState.value.copy(assetId = asset.id, positionMs = position,
                status = PlaybackStatus.LOADING, errorAssetId = null)
            try {
                engine.load(asset.path, position)
                if (token != generation.get()) return@withLock
                engine.volume = if (mutableState.value.muted) 0f else 1f
                if (resumeAllowed && autoPlay) {
                    mutableState.value = mutableState.value.copy(status = if (engine.play()) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED)
                } else {
                    mutableState.value = mutableState.value.copy(status = PlaybackStatus.PAUSED)
                }
            } catch (error: CancellationException) {
                if (token == generation.get()) throw error
            } catch (_: Exception) {
                if (token == generation.get()) fail(asset.id)
            }
        }
    }

    fun play() {
        if (closed || mutableState.value.status !in setOf(PlaybackStatus.PAUSED, PlaybackStatus.PLAYING)) return
        resumeAllowed = true
        mutableState.value = mutableState.value.copy(status = if (engine.play()) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED)
    }

    fun pause() {
        if (closed || mutableState.value.status != PlaybackStatus.PLAYING) return
        engine.pause()
        mutableState.value = mutableState.value.copy(positionMs = engine.positionMs, status = PlaybackStatus.PAUSED)
    }

    fun replay() {
        if (closed || mutableState.value.assetId == null || mutableState.value.status == PlaybackStatus.ERROR) return
        resumeAllowed = true
        engine.seekTo(0)
        mutableState.value = mutableState.value.copy(positionMs = 0,
            status = if (engine.play()) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED)
    }

    fun seekTo(positionMs: Long) {
        if (closed || mutableState.value.assetId == null || mutableState.value.status == PlaybackStatus.ERROR) return
        val bounded = positionMs.coerceIn(0L, 20_000L)
        engine.seekTo(bounded)
        mutableState.value = mutableState.value.copy(positionMs = bounded)
    }

    fun reset() {
        if (closed) return
        generation.incrementAndGet()
        engine.cancelPendingLoad()
        engine.stop()
        resumeAllowed = true
        mutableState.value = PlaybackState(muted = mutableState.value.muted)
    }

    fun setMuted(muted: Boolean) {
        if (closed) return
        engine.volume = if (muted) 0f else 1f
        mutableState.value = mutableState.value.copy(muted = muted)
    }

    fun onBackground() {
        if (closed) return
        resumeAllowed = false
        if (mutableState.value.status in setOf(PlaybackStatus.PLAYING, PlaybackStatus.LOADING)) {
            engine.pause()
            mutableState.value = mutableState.value.copy(status = PlaybackStatus.PAUSED)
        }
    }

    fun refresh() {
        if (closed || mutableState.value.status !in setOf(PlaybackStatus.PLAYING, PlaybackStatus.PAUSED)) return
        val position = engine.positionMs.coerceIn(0L, 20_000L)
        if (position >= 20_000L) {
            engine.pause()
            mutableState.value = mutableState.value.copy(positionMs = 20_000L, status = PlaybackStatus.ENDED)
        } else {
            mutableState.value = mutableState.value.copy(positionMs = position)
        }
    }

    override fun onEnded() {
        if (closed || mutableState.value.assetId == null) return
        mutableState.value = mutableState.value.copy(positionMs = 20_000L, status = PlaybackStatus.ENDED)
    }

    override fun onError() {
        if (closed) return
        mutableState.value.assetId?.let(::fail)
    }

    override fun onInterruption() = onBackground()

    fun close() {
        if (closed) return
        closed = true
        generation.incrementAndGet()
        engine.cancelPendingLoad()
        engine.listener = null
        engine.stop()
        engine.release()
        mutableState.value = PlaybackState(muted = mutableState.value.muted)
    }

    private fun fail(assetId: String) {
        engine.pause()
        mutableState.value = mutableState.value.copy(assetId = assetId, status = PlaybackStatus.ERROR,
            errorAssetId = assetId)
    }
}
