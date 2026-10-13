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
        assertTrue(BroadcastClock.needsCorrection(4051L, 0L))
    }
}
