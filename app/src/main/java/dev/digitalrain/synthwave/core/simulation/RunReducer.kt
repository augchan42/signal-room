package dev.digitalrain.synthwave.core.simulation

import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Phase
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.model.RunSnapshot
import dev.digitalrain.synthwave.core.model.Selection

const val RUN_SCHEMA_VERSION = 1
const val BROADCAST_DURATION_MS = 20_000L

sealed interface RunAction {
    data object OpenDesk : RunAction
    data class Select(val selection: Selection) : RunAction
    data class SetPreparation(val preparation: Preparation) : RunAction
    data object OpenPreparation : RunAction
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
        is RunAction.SetPreparation -> when {
            !planning -> "Preparation is locked after broadcast"
            action.preparation.researched != state.preparation.researched ->
                "Research slot is committed separately"
            action.preparation.rehearsals < 0 || action.preparation.refinements < 0 ||
                action.preparation.rests < 0 -> "Preparation counts cannot be negative"
            action.preparation.used > 3 -> "Only three preparation slots are available"
            else -> null
        }
        RunAction.OpenPreparation -> if (state.phase == Phase.DESK) null else "Open the arrangement desk first"
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
        is RunAction.SetPreparation -> state.copy(preparation = action.preparation)
        RunAction.OpenPreparation -> state.copy(phase = Phase.PREPARATION)
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
