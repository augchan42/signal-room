package dev.digitalrain.synthwave.core.audio

object BroadcastClock {
    private const val LOOP_MS = 4_000L
    private const val PERFORMANCE_MS = 20_000L

    /** Where the video should be: the four-second loop repeats, the 20-second performance plays once. */
    fun visualPosition(audioPositionMs: Long, loop: Boolean = true): Long = when {
        audioPositionMs <= 0 -> 0
        loop -> audioPositionMs % LOOP_MS
        else -> minOf(audioPositionMs, PERFORMANCE_MS)
    }

    fun isComplete(audioPositionMs: Long): Boolean = audioPositionMs >= PERFORMANCE_MS

    fun driftMs(audioPositionMs: Long, videoPositionMs: Long, loop: Boolean = true): Long {
        val expected = visualPosition(audioPositionMs, loop)
        if (!loop) return kotlin.math.abs(expected - videoPositionMs)
        val actual = ((videoPositionMs % LOOP_MS) + LOOP_MS) % LOOP_MS
        val difference = kotlin.math.abs(expected - actual)
        return minOf(difference, LOOP_MS - difference)
    }

    fun needsCorrection(audioPositionMs: Long, videoPositionMs: Long, loop: Boolean = true): Boolean =
        driftMs(audioPositionMs, videoPositionMs, loop) > 50L
}
