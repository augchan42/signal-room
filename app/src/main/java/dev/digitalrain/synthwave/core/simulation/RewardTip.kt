package dev.digitalrain.synthwave.core.simulation

import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.model.BookingResult
import dev.digitalrain.synthwave.core.model.PrepAction
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.model.Selection

const val THREE_STAR_SCORE = 75.0

fun starsFor(score: Double): Int = when {
    score >= THREE_STAR_SCORE -> 3
    score >= 65.0 -> 2
    else -> 1
}

enum class Readiness { LOW, GOOD, MAX }

fun readiness(value: Double): Readiness = when {
    value >= 100.0 -> Readiness.MAX
    value >= 60.0 -> Readiness.GOOD
    else -> Readiness.LOW
}

fun fitDots(content: BookingContent, selection: Selection): Int {
    val fit = requireNotNull(content.arrangements[selection.arrangement]?.conceptFit?.get(selection.concept))
    return when {
        fit >= 70.0 -> 3
        fit >= 50.0 -> 2
        else -> 1
    }
}

/** The ten ways to spend three slots without Research. */
fun allPlans(): List<Preparation> = (0..3).flatMap { rehearsals ->
    (0..3 - rehearsals).map { refinements ->
        Preparation(rehearsals, refinements, 3 - rehearsals - refinements, false)
    }
}

fun canBeInvited(content: BookingContent, selection: Selection): Boolean =
    allPlans().any { scoreBooking(content, selection, it).invited }

enum class TokenNote { AT_MAX, FULLY_RESTED, TOO_TIRED, LOWERS_SHOW, HARDLY_HELPS }

/** Why adding one token of [kind] now would add little or nothing; null when it helps. */
fun tokenNote(content: BookingContent, selection: Selection, prep: Preparation, kind: PrepAction): TokenNote? {
    if (prep.used >= 3) return null
    val now = projectBooking(content, selection, prep)
    if (kind == PrepAction.REFINE && now.coherence.value >= 100.0) return TokenNote.AT_MAX
    if (kind == PrepAction.REST && now.fatigueAfter.getValue(selection.member) == 0) return TokenNote.FULLY_RESTED
    val gain = projectScore(content, selection, prep.adjusted(kind, 1)) - now.invitationScore
    return when {
        gain < 0.0 && kind == PrepAction.REHEARSE -> TokenNote.TOO_TIRED
        gain < 0.0 -> TokenNote.LOWERS_SHOW
        gain < 0.5 -> TokenNote.HARDLY_HELPS
        else -> null
    }
}

data class Swap(val from: PrepAction, val to: PrepAction, val gain: Double)

/** The single token move that most improves a full plan, if it gains at least one point. */
fun bestSwap(content: BookingContent, selection: Selection, prep: Preparation): Swap? {
    val base = scoreBooking(content, selection, prep).invitationScore
    return PrepAction.entries.filter { prep.count(it) > 0 }.flatMap { from ->
        PrepAction.entries.filter { it != from }.map { to ->
            Swap(from, to, projectScore(content, selection, prep.adjusted(from, -1).adjusted(to, 1)) - base)
        }
    }.maxByOrNull { it.gain }?.takeIf { it.gain >= 1.0 }
}

enum class Highlight { LOOK_AND_SOUND, PLAYING, SLOT_FIT }

fun highlight(content: BookingContent, result: BookingResult): Highlight {
    val weights = content.balance.weights
    return listOf(
        Highlight.LOOK_AND_SOUND to weights[0] * result.coherence.value,
        Highlight.PLAYING to weights[1] * result.execution.value,
        Highlight.SLOT_FIT to weights[2] * result.briefFit.value,
    ).maxBy { it.second }.first
}
