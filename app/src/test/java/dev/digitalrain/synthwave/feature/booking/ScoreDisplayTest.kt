package dev.digitalrain.synthwave.feature.booking

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreDisplayTest {
    @Test fun neverRoundsFailureUpToThreshold() {
        assertEquals("64.96", displayScore(64.96))
        assertEquals("64.99", displayScore(64.999))
        assertEquals("65.00", displayScore(65.0))
    }

    @Test fun timeLeftRoundsUpToWholeSeconds() {
        assertEquals("0:20", timeLeft(0))
        assertEquals("0:01", timeLeft(19_000))
        assertEquals("0:01", timeLeft(19_001))
        assertEquals("0:00", timeLeft(20_000))
        assertEquals("0:20", timeLeft(-5))
    }
}
