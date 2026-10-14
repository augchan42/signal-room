package dev.digitalrain.synthwave

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountInTest {
    @Test fun freshFirstViewingCountsIn() = assertTrue(shouldCountIn(false, false, 0L))

    @Test fun restoredRunDoesNotCountIn() = assertFalse(shouldCountIn(true, false, 0L))

    @Test fun completedViewingDoesNotCountIn() = assertFalse(shouldCountIn(false, true, 0L))

    @Test fun resumedPositionDoesNotCountIn() = assertFalse(shouldCountIn(false, false, 1_000L))
}
