package dev.digitalrain.synthwave.core.simulation

import dev.digitalrain.synthwave.core.model.AudienceRules
import dev.digitalrain.synthwave.core.model.AudienceState
import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.model.Selection
import dev.digitalrain.synthwave.core.model.TraitVector
import kotlin.math.abs
import kotlin.math.min

data class SegmentResponse(
    val fit: Double,
    val quality: Double,
    val before: AudienceState,
    val after: AudienceState,
    val awarenessDelta: Double,
    val affinityDelta: Double,
    val adopted: Boolean,
)

fun audienceResponse(
    content: BookingContent,
    selection: Selection,
    coherence: Double,
    execution: Double,
): Map<String, SegmentResponse> {
    val arrangement = requireNotNull(content.arrangements[selection.arrangement])
    val concept = requireNotNull(content.concepts[selection.concept])
    val selectedTraits = TraitVector(
        energy = arrangement.energy,
        texture = arrangement.texture,
        brightness = concept.brightness,
    )
    val quality = (coherence + execution) / 2.0
    val rules = content.balance.audience

    return content.segments.mapValues { (_, segment) ->
        val fit = 100.0 - listOf(
            abs(selectedTraits.energy - segment.preferences.energy),
            abs(selectedTraits.texture - segment.preferences.texture),
            abs(selectedTraits.brightness - segment.preferences.brightness),
        ).average()
        val requestedAwareness = min(
            rules.maxAwarenessGain,
            (rules.awarenessFitCoefficient * fit + rules.awarenessQualityCoefficient * quality) *
                (1.0 + segment.initial.affinity / 100.0),
        )
        val requestedAffinity =
            rules.affinityQualityCoefficient * (quality - 50.0) +
                rules.affinityFitCoefficient * (fit - 50.0)
        val after = AudienceState(
            awareness = (segment.initial.awareness + requestedAwareness).coerceIn(0.0, 100.0),
            affinity = (segment.initial.affinity + requestedAffinity).coerceIn(0.0, 100.0),
        )
        val awarenessDelta = after.awareness - segment.initial.awareness
        val affinityDelta = after.affinity - segment.initial.affinity
        SegmentResponse(
            fit = fit,
            quality = quality,
            before = segment.initial,
            after = after,
            awarenessDelta = awarenessDelta,
            affinityDelta = affinityDelta,
            adopted = isAdopted(awarenessDelta, affinityDelta, after.affinity, rules),
        )
    }
}

fun isAdopted(
    actualAwarenessGain: Double,
    actualAffinityGain: Double,
    finalAffinity: Double,
    rules: AudienceRules,
): Boolean = actualAffinityGain >= rules.adoptionAffinityGain &&
    finalAffinity >= rules.adoptionFinalAffinity &&
    actualAwarenessGain >= rules.adoptionAwarenessGain
