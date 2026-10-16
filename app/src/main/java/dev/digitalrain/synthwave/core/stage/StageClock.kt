package dev.digitalrain.synthwave.core.stage

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicReference

/**
 * The show position for the live stage, from the app's audio samples (every 50 ms) carried forward
 * between them while the audio plays. One sample object, so a reader on the render thread never
 * pairs a new position with an old time.
 */
class StageClock(private val now: () -> Long = SystemClock::elapsedRealtime) {
    private class Sample(val positionMs: Long, val atMs: Long, val playing: Boolean)

    private val sample = AtomicReference(Sample(0, 0, false))

    fun update(positionMs: Long, playing: Boolean) {
        sample.set(Sample(positionMs, now(), playing))
    }

    fun positionMs(): Long {
        val current = sample.get()
        if (!current.playing) return current.positionMs
        // If samples stop arriving, stop after a few samples' worth rather than run ahead of the audio.
        return current.positionMs + (now() - current.atMs).coerceIn(0, MAX_CARRY_MS)
    }

    private companion object {
        const val MAX_CARRY_MS = 200L
    }
}
