package dev.digitalrain.synthwave.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ArrangementId { SPARSE, DRIVING, LAYERED }

@Serializable
enum class MemberId { FRONTMAN, KEYBOARDIST, KEYTAR, DRUMMER }

@Serializable
enum class ConceptId { MONOCHROME, WARM, ELECTRIC }

@Serializable
enum class PrepAction { REHEARSE, REFINE, REST }

@Serializable
data class Selection(
    val arrangement: ArrangementId,
    val member: MemberId,
    val concept: ConceptId,
)

@Serializable
data class Preparation(
    val rehearsals: Int,
    val refinements: Int,
    val rests: Int,
    val researched: Boolean,
) {
    val used: Int
        get() = rehearsals + refinements + rests + if (researched) 1 else 0
}

@Serializable
data class AudienceState(val awareness: Double, val affinity: Double)

@Serializable
data class Contribution(val id: String, val points: Double, val explanation: String)

@Serializable
data class Dimension(val value: Double, val contributions: List<Contribution>)

@Serializable
data class BookingResult(
    val coherence: Dimension,
    val execution: Dimension,
    val briefFit: Dimension,
    val invitationScore: Double,
    val invited: Boolean,
    val audienceAfter: Map<String, AudienceState>,
    val fatigueAfter: Map<MemberId, Int>,
)

@Serializable
data class TraitVector(
    val energy: Double,
    val texture: Double,
    val brightness: Double,
)

@Serializable
data class MemberProfile(
    val displayName: String,
    val strength: String,
    val skill: Double,
    val fatigue: Int,
    val suitability: Map<ArrangementId, Double>,
    val conceptSuitability: Map<ConceptId, Double>,
)

@Serializable
data class ArrangementRules(
    val displayName: String,
    val riskDescription: String,
    val baseExecution: Double,
    val skillWeight: Double,
    val fatigueWeight: Double,
    val coordinationWeight: Double,
    val conceptCoherence: Map<ConceptId, Double>,
    val conceptFit: Map<ConceptId, Double>,
    val energy: Double,
    val texture: Double,
)

@Serializable
data class ConceptProfile(
    val displayName: String,
    val brightness: Double,
)

@Serializable
data class SegmentProfile(
    val displayName: String,
    val initial: AudienceState,
    val preferences: TraitVector,
)

@Serializable
data class AudienceRules(
    val maxAwarenessGain: Double,
    val awarenessFitCoefficient: Double,
    val awarenessQualityCoefficient: Double,
    val affinityQualityCoefficient: Double,
    val affinityFitCoefficient: Double,
    val adoptionAffinityGain: Double,
    val adoptionFinalAffinity: Double,
    val adoptionAwarenessGain: Double,
)

@Serializable
data class BalanceRules(
    val weights: List<Double>,
    val invitationThreshold: Double,
    val rehearsalGains: List<Double>,
    val refinementGains: List<Double>,
    val fatiguePerRehearsal: Int,
    val recoveryPerRest: Int,
    val audience: AudienceRules,
)

@Serializable
data class BookingContent(
    val contentVersion: Int,
    val title: String,
    val brief: List<String>,
    val members: Map<MemberId, MemberProfile>,
    val arrangements: Map<ArrangementId, ArrangementRules>,
    val concepts: Map<ConceptId, ConceptProfile>,
    val segments: Map<String, SegmentProfile>,
    val balance: BalanceRules,
    val researchReport: List<String>,
    val mixDescriptions: Map<String, String>,
)
