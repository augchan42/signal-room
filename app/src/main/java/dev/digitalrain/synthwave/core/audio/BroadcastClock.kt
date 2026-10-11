package dev.digitalrain.synthwave.core.audio

object BroadcastClock {
    private const val LOOP_MS = 4_000L
    private const val PERFORMANCE_MS = 20_000L

    fun visualPosition(audioPositionMs: Long): Long =
        if (audioPositionMs <= 0) 0 else audioPositionMs % LOOP_MS

    fun isComplete(audioPositionMs: Long): Boolean = audioPositionMs >= PERFORMANCE_MS

    fun driftMs(audioPositionMs: Long, videoPositionMs: Long): Long {
        val expected = visualPosition(audioPositionMs)
        val actual = ((videoPositionMs % LOOP_MS) + LOOP_MS) % LOOP_MS
        val difference = kotlin.math.abs(expected - actual)
        return minOf(difference, LOOP_MS - difference)
    }

    fun needsCorrection(audioPositionMs: Long, videoPositionMs: Long): Boolean =
        driftMs(audioPositionMs, videoPositionMs) > 50L
}
