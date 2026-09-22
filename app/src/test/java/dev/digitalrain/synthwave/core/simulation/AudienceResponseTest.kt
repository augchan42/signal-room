package dev.digitalrain.synthwave.core.simulation

import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Selection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudienceResponseTest {
    @Test
    fun lowFitCanStillGainAffinityWhenQualityIsStrong() {
        val content = TestContent.lateNight()
        val selection = Selection(ArrangementId.SPARSE, MemberId.FRONTMAN, ConceptId.MONOCHROME)
        val response = audienceResponse(content, selection, coherence = 95.0, execution = 90.0)

        assertTrue(requireNotNull(response["performance"]).affinityDelta >= 3.0)
    }

    @Test
    fun adoptionBoundaryIsInclusive() {
        val rules = TestContent.lateNight().balance.audience

        assertTrue(isAdopted(10.0, 10.0, 30.0, rules))
        assertFalse(isAdopted(9.999, 10.0, 30.0, rules))
        assertFalse(isAdopted(10.0, 9.999, 30.0, rules))
        assertFalse(isAdopted(10.0, 10.0, 29.999, rules))
    }

    @Test
    fun conceptOnlyChangeAffectsFashionResponse() {
        val content = TestContent.lateNight()
        val mono = Selection(ArrangementId.DRIVING, MemberId.KEYTAR, ConceptId.MONOCHROME)
        val electric = mono.copy(concept = ConceptId.ELECTRIC)

        val monoResponse = requireNotNull(audienceResponse(content, mono, 80.0, 80.0)["fashion"])
        val electricResponse = requireNotNull(audienceResponse(content, electric, 80.0, 80.0)["fashion"])

        assertNotEquals(monoResponse.fit, electricResponse.fit, 1e-9)
        assertNotEquals(monoResponse.awarenessDelta, electricResponse.awarenessDelta, 1e-9)
    }
}
