package dev.digitalrain.synthwave.core.data

import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.simulation.TestContent
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentValidatorTest {
    @Test
    fun checkedInContentIsCompleteAndValid() {
        val content = TestContent.lateNight()

        assertEquals(MemberId.entries.toSet(), content.members.keys)
        assertEquals(ArrangementId.entries.toSet(), content.arrangements.keys)
        assertEquals(ConceptId.entries.toSet(), content.concepts.keys)
        assertEquals(5, content.segments.size)
        assertEquals(12, content.mixDescriptions.size)
        assertEquals(emptyList<String>(), validateContent(content))
    }

    @Test
    fun rejectsInvalidWeights() {
        val content = TestContent.lateNight()
        val invalid = content.copy(
            balance = content.balance.copy(weights = listOf(.35, .40, .40)),
        )

        assertTrue(validateContent(invalid).any { it.contains("weights") })
    }

    @Test
    fun rejectsMissingCompatibilityAndBlankMixCopy() {
        val content = TestContent.lateNight()
        val sparse = requireNotNull(content.arrangements[ArrangementId.SPARSE])
        val missing = content.copy(
            arrangements = content.arrangements + (
                ArrangementId.SPARSE to sparse.copy(
                    conceptFit = sparse.conceptFit - ConceptId.ELECTRIC,
                )
            ),
            mixDescriptions = content.mixDescriptions + ("sparse-frontman" to " "),
        )

        val errors = validateContent(missing)
        assertTrue(errors.any { it.contains("SPARSE conceptFit") })
        assertTrue(errors.any { it.contains("sparse-frontman") })
    }

    @Test
    fun rejectsInvalidRangesAndIncreasingPreparationGains() {
        val content = TestContent.lateNight()
        val frontman = requireNotNull(content.members[MemberId.FRONTMAN])
        val invalid = content.copy(
            members = content.members + (MemberId.FRONTMAN to frontman.copy(skill = 101.0)),
            balance = content.balance.copy(rehearsalGains = listOf(3.0, 6.0, 10.0)),
        )

        val errors = validateContent(invalid)
        assertTrue(errors.any { it.contains("FRONTMAN skill") })
        assertTrue(errors.any { it.contains("rehearsalGains") })
    }

    @Test(expected = SerializationException::class)
    fun rejectsUnknownEnumIds() {
        loadContent(checkedInJson().replace("\"SPARSE\"", "\"UNKNOWN\""))
    }

    private fun checkedInJson(): String = requireNotNull(
        javaClass.classLoader?.getResource("content/late-night.json"),
    ).readText()
}
