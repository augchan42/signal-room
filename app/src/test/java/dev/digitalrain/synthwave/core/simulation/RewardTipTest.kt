package dev.digitalrain.synthwave.core.simulation

import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.model.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RewardTipTest {
    private val content = TestContent.lateNight()
    private val sfm = Selection(ArrangementId.SPARSE, MemberId.FRONTMAN, ConceptId.MONOCHROME)
    private val dke = Selection(ArrangementId.DRIVING, MemberId.KEYTAR, ConceptId.ELECTRIC)

    private fun selections() = ArrangementId.entries.flatMap { a ->
        MemberId.entries.flatMap { m -> ConceptId.entries.map { c -> Selection(a, m, c) } }
    }

    @Test fun projectionEqualsScoreForFullPlans() {
        for (selection in selections()) for (plan in allPlans()) {
            assertEquals(scoreBooking(content, selection, plan), projectBooking(content, selection, plan))
        }
    }

    @Test fun projectionAcceptsPartialPlansAndRejectsOverfullOnes() {
        assertEquals(74.37, projectScore(content, sfm, Preparation(0, 0, 0, false)), 1e-2)
        assertThrows(IllegalArgumentException::class.java) {
            projectBooking(content, sfm, Preparation(2, 2, 0, false))
        }
    }

    @Test fun extraRehearsalsCanLowerTheScore() {
        val one = projectScore(content, dke, Preparation(1, 0, 0, false))
        val two = projectScore(content, dke, Preparation(2, 0, 0, false))
        assertEquals(67.25, one, 1e-2)
        assertEquals(66.05, two, 1e-2)
    }

    @Test fun starBoundaries() {
        assertEquals(1, starsFor(64.99))
        assertEquals(2, starsFor(65.0))
        assertEquals(2, starsFor(74.99))
        assertEquals(3, starsFor(75.0))
    }

    @Test fun starSplitOverAllPlans() {
        val counts = selections().flatMap { s -> allPlans().map { starsFor(scoreBooking(content, s, it).invitationScore) } }
            .groupingBy { it }.eachCount()
        assertEquals(mapOf(1 to 148, 2 to 177, 3 to 35), counts)
    }

    @Test fun sevenSelectionsCannotBeInvited() {
        assertEquals(7, selections().count { !canBeInvited(content, it) })
        assertEquals(10, allPlans().size)
    }

    @Test fun readinessLevels() {
        assertEquals(Readiness.LOW, readiness(59.99))
        assertEquals(Readiness.GOOD, readiness(60.0))
        assertEquals(Readiness.GOOD, readiness(99.99))
        assertEquals(Readiness.MAX, readiness(100.0))
    }

    @Test fun fitDotsFollowConceptFit() {
        assertEquals(3, fitDots(content, sfm)) // 84
        assertEquals(2, fitDots(content, dke)) // 55
        assertEquals(1, fitDots(content, Selection(ArrangementId.SPARSE, MemberId.FRONTMAN, ConceptId.ELECTRIC))) // 35
        assertEquals(1, fitDots(content, Selection(ArrangementId.LAYERED, MemberId.FRONTMAN, ConceptId.MONOCHROME))) // 48
        assertEquals(2, fitDots(content, Selection(ArrangementId.DRIVING, MemberId.FRONTMAN, ConceptId.MONOCHROME))) // 50
    }
}
