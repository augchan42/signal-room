package dev.digitalrain.synthwave.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastClockTest {
    @Test
    fun visualClockUsesAudioPosition() {
        assertEquals(1250L, BroadcastClock.visualPosition(9250L))
        assertEquals(0L, BroadcastClock.visualPosition(20_000L))
        assertEquals(0L, BroadcastClock.visualPosition(-1L))
    }

    @Test
    fun performancePlaysOnceAlongsideTheAudio() {
        assertEquals(9250L, BroadcastClock.visualPosition(9250L, loop = false))
        assertEquals(20_000L, BroadcastClock.visualPosition(25_000L, loop = false))
        assertEquals(0L, BroadcastClock.visualPosition(-1L, loop = false))
        assertEquals(4000L, BroadcastClock.driftMs(9250L, 5250L, loop = false))
        assertFalse(BroadcastClock.needsCorrection(9250L, 9210L, loop = false))
        assertTrue(BroadcastClock.needsCorrection(9250L, 1250L, loop = false))
    }

    @Test
    fun completionIsSeparateFromLoopPosition() {
        assertFalse(BroadcastClock.isComplete(19_999L))
        assertTrue(BroadcastClock.isComplete(20_000L))
    }

    @Test
    fun driftUsesCircularFourSecondDistance() {
        assertEquals(25L, BroadcastClock.driftMs(4025L, 0L))
        assertEquals(25L, BroadcastClock.driftMs(3975L, 0L))
        assertFalse(BroadcastClock.needsCorrection(4025L, 0L))
        assertTrue(BroadcastClock.needsCorrection(4251L, 0L))
    }

    @Test
    fun smallDriftIsLeftAlone() {
        // A seek in a video with keyframes 10 s apart takes longer than 50 ms, so correcting small
        // drift starts a seek loop that keeps the decoders flushing for the whole show.
        assertFalse(BroadcastClock.needsCorrection(9250L, 9130L, loop = false))
        assertFalse(BroadcastClock.needsCorrection(9250L, 9000L, loop = false))
        assertTrue(BroadcastClock.needsCorrection(9250L, 8999L, loop = false))
    }

    @Test
    fun noNewSeekWhileTheLastOneIsSettling() {
        assertFalse(BroadcastClock.needsCorrection(9250L, 1250L, loop = false, seeking = true))
        assertTrue(BroadcastClock.needsCorrection(9250L, 1250L, loop = false, seeking = false))
    }
}
