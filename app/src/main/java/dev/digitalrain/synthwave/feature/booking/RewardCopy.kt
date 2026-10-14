package dev.digitalrain.synthwave.feature.booking

import dev.digitalrain.synthwave.core.model.PrepAction
import dev.digitalrain.synthwave.core.simulation.Highlight
import dev.digitalrain.synthwave.core.simulation.Readiness
import dev.digitalrain.synthwave.core.simulation.Swap
import dev.digitalrain.synthwave.core.simulation.TokenNote

const val INTRO_LINE =
    "You're the producer. Pick a performance, spend three preparation tokens, then go on air to earn a return booking."
const val HARD_SELL = "This mix and stage are a hard sell for the late-night slot."

fun headline(stars: Int): String = when (stars) {
    3 -> "Showstopper! Invited back."
    2 -> "Invited back."
    else -> "Good set — not invited this time."
}

fun positiveLine(highlight: Highlight): String = when (highlight) {
    Highlight.LOOK_AND_SOUND -> "Your look and sound carried the show"
    Highlight.PLAYING -> "The band played tight"
    Highlight.SLOT_FIT -> "A perfect fit for the late-night slot"
}

fun actionLabel(kind: PrepAction): String = when (kind) {
    PrepAction.REHEARSE -> "Rehearse"
    PrepAction.REFINE -> "Refine"
    PrepAction.REST -> "Rest"
}

fun actionEffect(kind: PrepAction): String = when (kind) {
    PrepAction.REHEARSE -> "Tighter playing, more tired"
    PrepAction.REFINE -> "The look and sound fit better"
    PrepAction.REST -> "Less tired"
}

fun tipText(swap: Swap?): String = if (swap == null) {
    "Your preparation was the best for this show — try another mix, member or stage."
} else {
    "Swap a ${actionLabel(swap.from)} for a ${actionLabel(swap.to)} next time: +${displayScore(swap.gain)}"
}

fun noteText(note: TokenNote): String = when (note) {
    TokenNote.AT_MAX -> "Look & sound is already at max"
    TokenNote.FULLY_RESTED -> "Already fully rested"
    TokenNote.TOO_TIRED -> "Too tired — another Rehearse hurts"
    TokenNote.LOWERS_SHOW -> "This would lower the show"
    TokenNote.HARDLY_HELPS -> "Hardly helps now"
}

fun readinessText(level: Readiness): String = when (level) {
    Readiness.LOW -> "Low"
    Readiness.GOOD -> "Good"
    Readiness.MAX -> "Max"
}

fun goOnAirLabel(used: Int): String {
    val left = 3 - used
    return if (left <= 0) "Go on air" else "Place $left more token${if (left == 1) "" else "s"}"
}
