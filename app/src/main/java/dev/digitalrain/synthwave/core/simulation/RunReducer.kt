package dev.digitalrain.synthwave.core.simulation

import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Phase
import dev.digitalrain.synthwave.core.model.PrepAction
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.model.RunSnapshot
import dev.digitalrain.synthwave.core.model.Selection

const val RUN_SCHEMA_VERSION = 1
const val BROADCAST_DURATION_MS = 20_000L

sealed interface RunAction {
    data object OpenDesk : RunAction
    data class Select(val selection: Selection) : RunAction
    data class ChooseArrangement(val id: ArrangementId) : RunAction
    data class ChooseMember(val id: MemberId) : RunAction
    data class ChooseConcept(val id: ConceptId) : RunAction
    data class SetPreparation(val preparation: Preparation) : RunAction
    data class AdjustPreparation(val kind: PrepAction, val delta: Int) : RunAction
    data object OpenPreparation : RunAction
    data object ReturnToDesk : RunAction
    data object PurchaseResearch : RunAction
    data object ConfirmBroadcast : RunAction
    data class CheckpointPlayback(val positionMs: Long) : RunAction
    data object CompleteViewing : RunAction
    data object SkipViewing : RunAction
    data object OpenRecap : RunAction
    data object ConfirmRetry : RunAction
}

fun newRun(content: BookingContent): RunSnapshot = RunSnapshot(
    schemaVersion = RUN_SCHEMA_VERSION,
    contentVersion = content.contentVersion,
    attemptId = 1,
    phase = Phase.BRIEF,
    selection = Selection(ArrangementId.SPARSE, MemberId.FRONTMAN, ConceptId.MONOCHROME),
    preparation = Preparation(0, 0, 0, false),
)

fun actionError(state: RunSnapshot, action: RunAction, content: BookingContent): String? {
    val planning = state.phase == Phase.DESK || state.phase == Phase.PREPARATION
    return when (action) {
        RunAction.OpenDesk -> if (state.phase == Phase.BRIEF) null else "Open the booking brief first"
        is RunAction.Select -> when {
            !planning -> "Choices are locked after broadcast"
            action.selection.arrangement !in content.arrangements ||
                action.selection.member !in content.members ||
                action.selection.concept !in content.concepts -> "Unknown selection"
            else -> null
        }
        is RunAction.ChooseArrangement -> when {
            !planning -> "Choices are locked after broadcast"
            action.id !in content.arrangements -> "Unknown arrangement"
            else -> null
        }
        is RunAction.ChooseMember -> when {
            !planning -> "Choices are locked after broadcast"
            action.id !in content.members -> "Unknown member"
            else -> null
        }
        is RunAction.ChooseConcept -> when {
            !planning -> "Choices are locked after broadcast"
            action.id !in content.concepts -> "Unknown concept"
            else -> null
        }
        is RunAction.SetPreparation -> when {
            !planning -> "Preparation is locked after broadcast"
            action.preparation.researched != state.preparation.researched ->
                "Research slot is committed separately"
            action.preparation.rehearsals < 0 || action.preparation.refinements < 0 ||
                action.preparation.rests < 0 -> "Preparation counts cannot be negative"
            action.preparation.used > 3 -> "Only three preparation slots are available"
            else -> null
        }
        is RunAction.AdjustPreparation -> when {
            !planning -> "Preparation is locked after broadcast"
            action.delta !in -1..1 || action.delta == 0 -> "Adjust one preparation slot at a time"
            state.preparation.adjusted(action.kind, action.delta).let {
                it.rehearsals < 0 || it.refinements < 0 || it.rests < 0
            } -> "Preparation counts cannot be negative"
            state.preparation.adjusted(action.kind, action.delta).used > 3 ->
                "Only three preparation slots are available"
            else -> null
        }
        RunAction.OpenPreparation -> if (state.phase == Phase.DESK) null else "Open the arrangement desk first"
        RunAction.ReturnToDesk -> if (state.phase == Phase.PREPARATION) null else "Open preparation first"
        RunAction.PurchaseResearch -> when {
            !planning -> "Research is only available before broadcast"
            state.preparation.researched -> null
            state.preparation.used >= 3 -> "Free one slot first"
            else -> null
        }
        RunAction.ConfirmBroadcast -> when {
            state.phase != Phase.PREPARATION -> "Open preparation before broadcast"
            state.preparation.used != 3 -> "Use exactly three preparation slots"
            state.result != null -> "Broadcast already confirmed"
            else -> null
        }
        is RunAction.CheckpointPlayback -> when {
            state.phase != Phase.BROADCAST -> "No broadcast is playing"
            action.positionMs !in 0..BROADCAST_DURATION_MS -> "Playback position is out of range"
            else -> null
        }
        RunAction.CompleteViewing -> when {
            state.phase != Phase.BROADCAST -> "No broadcast is playing"
            state.broadcastPositionMs < BROADCAST_DURATION_MS -> "Watch the full performance first"
            else -> null
        }
        RunAction.SkipViewing -> when {
            state.phase != Phase.BROADCAST -> "No broadcast is playing"
            !state.viewingCompleted -> "Watch the full performance first"
            else -> null
        }
        RunAction.OpenRecap -> if (state.phase == Phase.VERDICT) null else "View the verdict first"
        RunAction.ConfirmRetry -> if (state.phase == Phase.RECAP) null else "Open the recap before retrying"
    }
}

fun reduceRun(state: RunSnapshot, action: RunAction, content: BookingContent): RunSnapshot {
    if (actionError(state, action, content) != null) return state
    return when (action) {
        RunAction.OpenDesk -> state.copy(phase = Phase.DESK)
        is RunAction.Select -> state.copy(selection = action.selection)
        is RunAction.ChooseArrangement -> state.copy(selection = state.selection.copy(arrangement = action.id))
        is RunAction.ChooseMember -> state.copy(selection = state.selection.copy(member = action.id))
        is RunAction.ChooseConcept -> state.copy(selection = state.selection.copy(concept = action.id))
        is RunAction.SetPreparation -> state.copy(preparation = action.preparation)
        is RunAction.AdjustPreparation -> state.copy(preparation = state.preparation.adjusted(action.kind, action.delta))
        RunAction.OpenPreparation -> state.copy(phase = Phase.PREPARATION)
        RunAction.ReturnToDesk -> state.copy(phase = Phase.DESK)
        RunAction.PurchaseResearch -> if (state.preparation.researched) {
            state
        } else {
            state.copy(preparation = state.preparation.copy(researched = true))
        }
        RunAction.ConfirmBroadcast -> state.copy(
            phase = Phase.BROADCAST,
            result = scoreBooking(content, state.selection, state.preparation),
            broadcastPositionMs = 0,
        )
        is RunAction.CheckpointPlayback -> state.copy(broadcastPositionMs = action.positionMs)
        RunAction.CompleteViewing -> state.copy(phase = Phase.VERDICT, viewingCompleted = true)
        RunAction.SkipViewing -> state.copy(phase = Phase.VERDICT)
        RunAction.OpenRecap -> state.copy(phase = Phase.RECAP)
        RunAction.ConfirmRetry -> newRun(content).copy(
            attemptId = state.attemptId + 1,
            previousResult = state.result,
            viewingCompleted = state.viewingCompleted,
        )
    }
}

fun Preparation.adjusted(kind: PrepAction, delta: Int): Preparation = when (kind) {
    PrepAction.REHEARSE -> copy(rehearsals = rehearsals + delta)
    PrepAction.REFINE -> copy(refinements = refinements + delta)
    PrepAction.REST -> copy(rests = rests + delta)
}

fun Preparation.count(kind: PrepAction): Int = when (kind) {
    PrepAction.REHEARSE -> rehearsals
    PrepAction.REFINE -> refinements
    PrepAction.REST -> rests
}
