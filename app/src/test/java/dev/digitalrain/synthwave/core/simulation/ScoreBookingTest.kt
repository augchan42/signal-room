package dev.digitalrain.synthwave.core.simulation

import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.model.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreBookingTest {
    private val selection = Selection(
        ArrangementId.SPARSE,
        MemberId.FRONTMAN,
        ConceptId.MONOCHROME,
    )
    private val balancedPrep = Preparation(1, 1, 1, false)

    @Test
    fun fatigueIsSummedBeforeClamping() {
        val preparation = Preparation(1, 0, 2, false)
        assertEquals(0, effectiveFatigue(15, preparation, TestContent.lateNight().balance))
    }

    @Test
    fun repeatedEvaluationIsIdentical() {
        val content = TestContent.lateNight()
        val driving = Selection(ArrangementId.DRIVING, MemberId.FRONTMAN, ConceptId.WARM)
        val preparation = Preparation(1, 1, 1, false)

        assertEquals(scoreBooking(content, driving, preparation), scoreBooking(content, driving, preparation))
    }

    @Test
    fun originalArithmeticFixtureMatchesDocumentedBreakdown() {
        val result = scoreBooking(GoldenContent.original(), selection, balancedPrep)
        val expectedInvitation = .35 * 95.0 + .40 * 75.65 + .25 * 78.0

        assertEquals(95.0, result.coherence.value, 1e-6)
        assertEquals(75.65, result.execution.value, 1e-6)
        assertEquals(78.0, result.briefFit.value, 1e-6)
        assertEquals(expectedInvitation, result.invitationScore, 1e-6)
    }

    @Test
    fun revisedSparseFrontmanMonochromeMatchesContentBreakdown() {
        val result = scoreBooking(TestContent.lateNight(), selection, balancedPrep)
        val expectedInvitation = .35 * 100.0 + .40 * 60.05 + .25 * 84.0

        assertEquals(100.0, result.coherence.value, 1e-6)
        assertEquals(60.05, result.execution.value, 1e-6)
        assertEquals(84.0, result.briefFit.value, 1e-6)
        assertEquals(expectedInvitation, result.invitationScore, 1e-6)
        assertTrue(result.coherence.contributions.any { it.id == "clamp" && it.points < 0 })
    }

    @Test
    fun onlyFeaturedMemberFatigueChanges() {
        val content = TestContent.lateNight()
        val result = scoreBooking(content, selection, balancedPrep)

        assertEquals(27, result.fatigueAfter[MemberId.FRONTMAN])
        assertEquals(15, result.fatigueAfter[MemberId.KEYBOARDIST])
        assertEquals(25, result.fatigueAfter[MemberId.KEYTAR])
        assertEquals(30, result.fatigueAfter[MemberId.DRUMMER])
    }

    @Test
    fun researchHasNoDirectScoreBonus() {
        val base = TestContent.lateNight()
        val content = base.copy(
            members = base.members.mapValues { (_, member) -> member.copy(fatigue = 0) },
            balance = base.balance.copy(fatiguePerRehearsal = 0, recoveryPerRest = 0),
        )
        val withoutResearch = scoreBooking(content, selection, Preparation(1, 1, 1, false))
        val withResearch = scoreBooking(content, selection, Preparation(1, 1, 0, true))

        assertEquals(withoutResearch.invitationScore, withResearch.invitationScore, 1e-9)
    }

    @Test
    fun invalidPreparationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            scoreBooking(TestContent.lateNight(), selection, Preparation(-1, 2, 2, false))
        }
        assertThrows(IllegalArgumentException::class.java) {
            scoreBooking(TestContent.lateNight(), selection, Preparation(1, 1, 0, false))
        }
    }

    @Test
    fun evaluationDoesNotMutateInitialAudience() {
        val content = TestContent.lateNight()
        val before = content.segments.mapValues { it.value.initial.copy() }

        scoreBooking(content, selection, balancedPrep)

        assertEquals(before, content.segments.mapValues { it.value.initial })
        assertNotSame(before, content.segments)
    }
}
