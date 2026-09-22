package dev.digitalrain.synthwave.core.simulation

import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId

object GoldenContent {
    fun original() = TestContent.lateNight().let { content ->
        val member = requireNotNull(content.members[MemberId.FRONTMAN])
        val sparse = requireNotNull(content.arrangements[ArrangementId.SPARSE])
        content.copy(
            members = content.members + (
                MemberId.FRONTMAN to member.copy(
                    skill = 78.0,
                    fatigue = 40,
                    suitability = member.suitability + (ArrangementId.SPARSE to 8.0),
                    conceptSuitability = member.conceptSuitability + (ConceptId.MONOCHROME to 0.0),
                )
            ),
            arrangements = content.arrangements + (
                ArrangementId.SPARSE to sparse.copy(
                    baseExecution = 10.0,
                    skillWeight = .80,
                    fatigueWeight = .25,
                    coordinationWeight = 0.0,
                    conceptCoherence = sparse.conceptCoherence + (ConceptId.MONOCHROME to 75.0),
                    conceptFit = sparse.conceptFit + (ConceptId.MONOCHROME to 78.0),
                )
            ),
        )
    }
}
