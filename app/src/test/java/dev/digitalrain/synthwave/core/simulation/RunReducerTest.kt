package dev.digitalrain.synthwave.core.simulation

import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Phase
import dev.digitalrain.synthwave.core.model.PrepAction
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.model.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunReducerTest {
    @Test fun rapidPartialChoicesAndPreparationDoNotOverwriteOneAnother() {
        val content = TestContent.lateNight()
        var state = reduceRun(newRun(content), RunAction.OpenDesk, content)
        state = reduceRun(state, RunAction.ChooseArrangement(ArrangementId.DRIVING), content)
        state = reduceRun(state, RunAction.ChooseConcept(ConceptId.WARM), content)
        state = reduceRun(state, RunAction.OpenPreparation, content)
        state = reduceRun(state, RunAction.AdjustPreparation(PrepAction.REHEARSE, 1), content)
        state = reduceRun(state, RunAction.AdjustPreparation(PrepAction.REFINE, 1), content)
        state = reduceRun(state, RunAction.AdjustPreparation(PrepAction.REST, 1), content)
        assertEquals(ArrangementId.DRIVING, state.selection.arrangement)
        assertEquals(ConceptId.WARM, state.selection.concept)
        assertEquals(Preparation(1, 1, 1, false), state.preparation)
    }
    private val content = TestContent.lateNight()

    @Test fun researchConsumesOneSlotOnlyOnce() {
        val desk = reduceRun(newRun(content), RunAction.OpenDesk, content)
        val researched = reduceRun(desk, RunAction.PurchaseResearch, content)

        assertEquals(1, researched.preparation.used)
        assertEquals(researched, reduceRun(researched, RunAction.PurchaseResearch, content))
    }

    @Test fun fullPlanMustFreeOneSlotBeforeResearch() {
        val desk = reduceRun(newRun(content), RunAction.OpenDesk, content)
        val full = reduceRun(desk, RunAction.SetPreparation(Preparation(1, 1, 1, false)), content)

        assertEquals("Free one slot first", actionError(full, RunAction.PurchaseResearch, content))
        assertEquals(full, reduceRun(full, RunAction.PurchaseResearch, content))
    }

    @Test fun cannotReclaimResearchSlot() {
        val desk = reduceRun(newRun(content), RunAction.OpenDesk, content)
        val researched = reduceRun(desk, RunAction.PurchaseResearch, content)
        val edited = reduceRun(
            researched,
            RunAction.SetPreparation(Preparation(1, 1, 1, false)),
            content,
        )

        assertEquals(researched, edited)
        assertTrue(edited.preparation.researched)
    }

    @Test fun confirmationScoresOnceAndFreezesChoices() {
        val selected = Selection(ArrangementId.DRIVING, MemberId.FRONTMAN, ConceptId.WARM)
        val desk = reduceRun(newRun(content), RunAction.OpenDesk, content)
        val chosen = reduceRun(desk, RunAction.Select(selected), content)
        val prepared = reduceRun(
            reduceRun(chosen, RunAction.OpenPreparation, content),
            RunAction.SetPreparation(Preparation(1, 1, 1, false)),
            content,
        )
        val broadcast = reduceRun(prepared, RunAction.ConfirmBroadcast, content)

        assertEquals(Phase.BROADCAST, broadcast.phase)
        assertNotNull(broadcast.result)
        assertEquals(broadcast, reduceRun(broadcast, RunAction.ConfirmBroadcast, content))
        assertEquals(
            broadcast,
            reduceRun(
                broadcast,
                RunAction.Select(Selection(ArrangementId.SPARSE, MemberId.DRUMMER, ConceptId.MONOCHROME)),
                content,
            ),
        )
    }

    @Test fun completionRequiresFullPlaybackCheckpoint() {
        val broadcast = readyBroadcast()
        assertEquals(broadcast, reduceRun(broadcast, RunAction.CompleteViewing, content))
        val ended = reduceRun(broadcast, RunAction.CheckpointPlayback(20_000), content)
        val verdict = reduceRun(ended, RunAction.CompleteViewing, content)

        assertEquals(Phase.VERDICT, verdict.phase)
        assertTrue(verdict.viewingCompleted)
    }

    @Test fun retryRetainsPreviousResultButRestoresStartingConditions() {
        val broadcast = readyBroadcast()
        val ended = reduceRun(broadcast, RunAction.CheckpointPlayback(20_000), content)
        val verdict = reduceRun(ended, RunAction.CompleteViewing, content)
        val recap = reduceRun(verdict, RunAction.OpenRecap, content)
        val retried = reduceRun(recap, RunAction.ConfirmRetry, content)

        assertEquals(2, retried.attemptId)
        assertEquals(Phase.BRIEF, retried.phase)
        assertEquals(verdict.result, retried.previousResult)
        assertEquals(null, retried.result)
        assertEquals(0, retried.preparation.used)
        assertTrue(retried.viewingCompleted)
        assertEquals(0L, retried.broadcastPositionMs)
    }

    @Test fun finalMemberReceivesPreparationAndOtherMembersRemainUnchanged() {
        val desk = reduceRun(newRun(content), RunAction.OpenDesk, content)
        val prep = reduceRun(desk, RunAction.SetPreparation(Preparation(1, 1, 1, false)), content)
        val keyboardist = reduceRun(
            prep,
            RunAction.Select(Selection(ArrangementId.LAYERED, MemberId.KEYBOARDIST, ConceptId.WARM)),
            content,
        )
        val broadcast = reduceRun(
            reduceRun(keyboardist, RunAction.OpenPreparation, content),
            RunAction.ConfirmBroadcast,
            content,
        )

        assertEquals(2, broadcast.result?.fatigueAfter?.get(MemberId.KEYBOARDIST))
        assertEquals(40, broadcast.result?.fatigueAfter?.get(MemberId.FRONTMAN))
    }

    private fun readyBroadcast() = run {
        val desk = reduceRun(newRun(content), RunAction.OpenDesk, content)
        val prep = reduceRun(desk, RunAction.SetPreparation(Preparation(1, 1, 1, false)), content)
        reduceRun(reduceRun(prep, RunAction.OpenPreparation, content), RunAction.ConfirmBroadcast, content)
    }
}
