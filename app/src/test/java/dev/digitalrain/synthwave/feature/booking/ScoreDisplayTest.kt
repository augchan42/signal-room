package dev.digitalrain.synthwave.feature.booking

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreDisplayTest {
    @Test fun neverRoundsFailureUpToThreshold() {
        assertEquals("64.96", displayScore(64.96))
        assertEquals("64.99", displayScore(64.999))
        assertEquals("65.00", displayScore(65.0))
    }
}
