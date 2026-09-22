package dev.digitalrain.synthwave.core.data

import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import kotlin.math.abs

fun validateContent(content: BookingContent): List<String> = buildList {
    if (content.contentVersion < 1) add("contentVersion must be positive")
    if (content.title.isBlank()) add("title must not be blank")
    if (content.brief.isEmpty() || content.brief.any(String::isBlank)) add("brief must contain non-blank copy")

    requireExactKeys("members", content.members.keys, MemberId.entries.toSet())
    requireExactKeys("arrangements", content.arrangements.keys, ArrangementId.entries.toSet())
    requireExactKeys("concepts", content.concepts.keys, ConceptId.entries.toSet())
    if (content.segments.size != 5 || content.segments.keys.any(String::isBlank)) {
        add("segments must contain five unique non-blank IDs")
    }

    content.members.forEach { (id, member) ->
        range("$id skill", member.skill)
        range("$id fatigue", member.fatigue.toDouble())
        signedRange("$id suitability", member.suitability.values)
        signedRange("$id conceptSuitability", member.conceptSuitability.values)
        requireExactKeys("$id suitability", member.suitability.keys, ArrangementId.entries.toSet())
        requireExactKeys("$id conceptSuitability", member.conceptSuitability.keys, ConceptId.entries.toSet())
        if (member.displayName.isBlank() || member.strength.isBlank()) add("$id copy must not be blank")
    }

    content.arrangements.forEach { (id, arrangement) ->
        requireExactKeys("$id conceptCoherence", arrangement.conceptCoherence.keys, ConceptId.entries.toSet())
        requireExactKeys("$id conceptFit", arrangement.conceptFit.keys, ConceptId.entries.toSet())
        arrangement.conceptCoherence.values.forEach { range("$id conceptCoherence", it) }
        arrangement.conceptFit.values.forEach { range("$id conceptFit", it) }
        range("$id energy", arrangement.energy)
        range("$id texture", arrangement.texture)
        if (arrangement.displayName.isBlank() || arrangement.riskDescription.isBlank()) {
            add("$id arrangement copy must not be blank")
        }
    }

    content.concepts.forEach { (id, concept) ->
        range("$id brightness", concept.brightness)
        if (concept.displayName.isBlank()) add("$id displayName must not be blank")
    }

    content.segments.forEach { (id, segment) ->
        range("$id initial awareness", segment.initial.awareness)
        range("$id initial affinity", segment.initial.affinity)
        range("$id energy preference", segment.preferences.energy)
        range("$id texture preference", segment.preferences.texture)
        range("$id brightness preference", segment.preferences.brightness)
        if (segment.displayName.isBlank()) add("$id displayName must not be blank")
    }

    val balance = content.balance
    if (balance.weights.size != 3 || balance.weights.any { !it.isFinite() || it < 0.0 } ||
        abs(balance.weights.sum() - 1.0) > 1e-9
    ) {
        add("weights must contain three non-negative values summing to one")
    }
    range("invitationThreshold", balance.invitationThreshold)
    diminishing("rehearsalGains", balance.rehearsalGains)
    diminishing("refinementGains", balance.refinementGains)
    if (balance.fatiguePerRehearsal < 0 || balance.recoveryPerRest < 0) {
        add("preparation fatigue increments must be non-negative")
    }
    val audienceNumbers = listOf(
        balance.audience.maxAwarenessGain,
        balance.audience.awarenessFitCoefficient,
        balance.audience.awarenessQualityCoefficient,
        balance.audience.affinityQualityCoefficient,
        balance.audience.affinityFitCoefficient,
        balance.audience.adoptionAffinityGain,
        balance.audience.adoptionFinalAffinity,
        balance.audience.adoptionAwarenessGain,
    )
    if (audienceNumbers.any { it < 0.0 }) add("audience coefficients and thresholds must be non-negative")

    if (content.researchReport.isEmpty() || content.researchReport.any(String::isBlank)) {
        add("researchReport must contain non-blank copy")
    }
    val expectedMixIds = ArrangementId.entries.flatMap { arrangement ->
        MemberId.entries.map { member -> "${arrangement.name.lowercase()}-${member.name.lowercase()}" }
    }.toSet()
    requireExactKeys("mixDescriptions", content.mixDescriptions.keys, expectedMixIds)
    content.mixDescriptions.forEach { (id, description) ->
        if (description.isBlank()) add("$id mix description must not be blank")
    }
}

private fun MutableList<String>.range(label: String, value: Double) {
    if (!value.isFinite() || value !in 0.0..100.0) add("$label must be in 0..100")
}

private fun MutableList<String>.signedRange(label: String, values: Collection<Double>) {
    if (values.any { !it.isFinite() || it !in -100.0..100.0 }) add("$label must be in -100..100")
}

private fun MutableList<String>.diminishing(label: String, gains: List<Double>) {
    if (gains.isEmpty() || gains.any { it < 0.0 } || gains.zipWithNext().any { (a, b) -> a < b }) {
        add("$label must be non-negative and ordered from largest to smallest")
    }
}

private fun <T> MutableList<String>.requireExactKeys(label: String, actual: Set<T>, expected: Set<T>) {
    if (actual != expected) add("$label must contain exactly $expected")
}
