package dev.digitalrain.synthwave.core.simulation

import dev.digitalrain.synthwave.core.model.BalanceRules
import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.model.BookingResult
import dev.digitalrain.synthwave.core.model.Contribution
import dev.digitalrain.synthwave.core.model.Dimension
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.model.Selection

fun effectiveFatigue(start: Int, prep: Preparation, rules: BalanceRules): Int {
    require(prep.rehearsals >= 0 && prep.refinements >= 0 && prep.rests >= 0) {
        "Preparation counts must be non-negative"
    }
    return (
        start + prep.rehearsals * rules.fatiguePerRehearsal - prep.rests * rules.recoveryPerRest
    ).coerceIn(0, 100)
}

fun scoreBooking(
    content: BookingContent,
    selection: Selection,
    prep: Preparation,
): BookingResult {
    require(prep.rehearsals >= 0 && prep.refinements >= 0 && prep.rests >= 0) {
        "Preparation counts must be non-negative"
    }
    require(prep.used == 3) { "Preparation must use exactly three slots" }

    val member = requireNotNull(content.members[selection.member])
    val arrangement = requireNotNull(content.arrangements[selection.arrangement])
    val effectiveFatigue = effectiveFatigue(member.fatigue, prep, content.balance)
    val refinementGain = content.balance.refinementGains.take(prep.refinements).sum()

    val coherence = dimension(
        listOf(
            Contribution(
                "concept-base",
                requireNotNull(arrangement.conceptCoherence[selection.concept]),
                "Arrangement and concept foundation",
            ),
            Contribution(
                "arrangement-suitability",
                requireNotNull(member.suitability[selection.arrangement]),
                "Featured member arrangement strength",
            ),
            Contribution(
                "concept-suitability",
                requireNotNull(member.conceptSuitability[selection.concept]),
                "Featured member concept strength",
            ),
            Contribution("refinement", refinementGain, "Preparation refinement"),
        ),
    )

    val rehearsalGain = content.balance.rehearsalGains.take(prep.rehearsals).sum()
    val execution = dimension(
        listOf(
            Contribution("arrangement-base", arrangement.baseExecution, "Arrangement execution baseline"),
            Contribution("skill", arrangement.skillWeight * member.skill, "Featured member skill"),
            Contribution(
                "fatigue",
                -arrangement.fatigueWeight * effectiveFatigue,
                "Featured member fatigue",
            ),
            Contribution(
                "coordination",
                arrangement.coordinationWeight * (coherence.value - 50.0),
                "Arrangement coordination from coherence",
            ),
            Contribution("rehearsal", rehearsalGain, "Preparation rehearsal"),
        ),
    )

    val fitValue = requireNotNull(arrangement.conceptFit[selection.concept])
    val briefFit = dimension(
        listOf(Contribution("show-fit", fitValue, "Late-night booking fit")),
    )
    val weights = content.balance.weights
    val invitationScore =
        weights[0] * coherence.value + weights[1] * execution.value + weights[2] * briefFit.value
    val response = audienceResponse(content, selection, coherence.value, execution.value)
    val fatigueAfter = content.members.mapValues { (id, profile) ->
        if (id == selection.member) effectiveFatigue else profile.fatigue
    }

    return BookingResult(
        coherence = coherence,
        execution = execution,
        briefFit = briefFit,
        invitationScore = invitationScore,
        invited = invitationScore >= content.balance.invitationThreshold,
        audienceAfter = response.mapValues { it.value.after },
        fatigueAfter = fatigueAfter,
    )
}

private fun dimension(contributions: List<Contribution>): Dimension {
    val raw = contributions.sumOf(Contribution::points)
    val bounded = raw.coerceIn(0.0, 100.0)
    val clamp = bounded - raw
    return Dimension(
        value = bounded,
        contributions = if (clamp == 0.0) {
            contributions
        } else {
            contributions + Contribution("clamp", clamp, "Bounded to the 0–100 scale")
        },
    )
}
