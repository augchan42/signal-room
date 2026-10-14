package dev.digitalrain.synthwave.feature.booking

import dev.digitalrain.synthwave.core.model.PrepAction
import dev.digitalrain.synthwave.core.simulation.Highlight
import dev.digitalrain.synthwave.core.simulation.Swap
import dev.digitalrain.synthwave.core.simulation.TokenNote
import org.junit.Assert.assertEquals
import org.junit.Test

class RewardCopyTest {
    @Test fun headlines() {
        assertEquals("Good set — not invited this time.", headline(1))
        assertEquals("Invited back.", headline(2))
        assertEquals("Showstopper! Invited back.", headline(3))
    }

    @Test fun tipNamesTheSwapAndTruncatesTheGain() {
        assertEquals("Swap a Refine for a Rest next time: +2.15",
            tipText(Swap(PrepAction.REFINE, PrepAction.REST, 2.1549)))
        assertEquals("Your preparation was the best for this show — try another mix, member or stage.",
            tipText(null))
    }

    @Test fun goOnAirLabel() {
        assertEquals("Place 3 more tokens", goOnAirLabel(0))
        assertEquals("Place 1 more token", goOnAirLabel(2))
        assertEquals("Go on air", goOnAirLabel(3))
    }

    @Test fun notesAndHighlights() {
        assertEquals("Look & sound is already at max", noteText(TokenNote.AT_MAX))
        assertEquals("Too tired — another Rehearse hurts", noteText(TokenNote.TOO_TIRED))
        assertEquals("Your look and sound carried the show", positiveLine(Highlight.LOOK_AND_SOUND))
    }
}
