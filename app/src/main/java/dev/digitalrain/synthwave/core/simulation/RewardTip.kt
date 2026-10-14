package dev.digitalrain.synthwave.core.simulation

import dev.digitalrain.synthwave.core.model.BookingContent
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
