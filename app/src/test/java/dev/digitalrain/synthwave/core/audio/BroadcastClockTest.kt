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
