package dev.digitalrain.synthwave.core.stage

import org.junit.Assert.assertEquals
import org.junit.Test

class StageClockTest {
    private var now = 1_000L
    private val clock = StageClock { now }

    @Test fun carriesThePositionForwardWhilePlaying() {
        clock.update(9_250, playing = true)
        now += 30
        assertEquals(9_280, clock.positionMs())
    }

    @Test fun holdsThePositionWhilePaused() {
        clock.update(9_250, playing = false)
        now += 500
        assertEquals(9_250, clock.positionMs())
    }

    @Test fun stopsCarryingForwardWhenSamplesStop() {
        clock.update(9_250, playing = true)
        now += 5_000
        assertEquals(9_450, clock.positionMs())
    }
}
