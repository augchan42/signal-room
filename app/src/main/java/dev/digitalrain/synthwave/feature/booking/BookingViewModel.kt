package dev.digitalrain.synthwave.feature.booking

import dev.digitalrain.synthwave.core.data.SnapshotStore
import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.model.Phase
import dev.digitalrain.synthwave.core.model.RunSnapshot
import dev.digitalrain.synthwave.core.simulation.BROADCAST_DURATION_MS
import dev.digitalrain.synthwave.core.simulation.RUN_SCHEMA_VERSION
import dev.digitalrain.synthwave.core.simulation.RunAction
import dev.digitalrain.synthwave.core.simulation.actionError
import dev.digitalrain.synthwave.core.simulation.newRun
import dev.digitalrain.synthwave.core.simulation.reduceRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

class BookingViewModel(
    private val content: BookingContent,
    private val store: SnapshotStore,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(newRun(content))
    private val _message = MutableStateFlow<String?>(null)
    private val _recoveryRequired = MutableStateFlow(false)
    private val _restoredFromStore = MutableStateFlow(false)

    val state: StateFlow<RunSnapshot> = _state
    val message: StateFlow<String?> = _message
    val recoveryRequired: StateFlow<Boolean> = _recoveryRequired
    val restoredFromStore: StateFlow<Boolean> = _restoredFromStore

    suspend fun load() {
        mutex.lock()
        try {
            try {
                val saved = withContext(Dispatchers.IO) { store.load() }
                if (saved != null) {
                    val error = snapshotError(saved)
                    if (error != null) {
                        _recoveryRequired.value = true
                        _message.value = error
                    } else {
                        _restoredFromStore.value = true
                        _state.value = saved
                        _recoveryRequired.value = false
                        _message.value = null
                    }
                }
            } catch (_: Exception) {
                _recoveryRequired.value = true
                _message.value = "Save could not be read. Start new demo to reset it."
            }
        } finally {
            mutex.unlock()
        }
    }

    suspend fun dispatch(action: RunAction) {
        mutex.lock()
        try {
            if (_recoveryRequired.value) {
                _message.value = "Start new demo to reset the unreadable save."
                return
            }
            val current = _state.value
            val error = actionError(current, action, content)
            if (error != null) {
                _message.value = error
                return
            }
            val next = reduceRun(current, action, content)
            if (next == current) {
                _message.value = null
                return
            }
            try {
                withContext(Dispatchers.IO) { store.save(next) }
            } catch (_: Exception) {
                _message.value = "Could not save; action not applied."
                return
            }
            if (action == RunAction.ConfirmBroadcast) _restoredFromStore.value = false
            _state.value = next
            _message.value = null
        } finally {
            mutex.unlock()
        }
    }

    suspend fun startNewDemo() {
        mutex.lock()
        try {
            val fresh = newRun(content)
            try {
                withContext(Dispatchers.IO) { store.save(fresh) }
            } catch (_: Exception) {
                _message.value = "Could not save; action not applied."
                return
            }
            _restoredFromStore.value = false
            _state.value = fresh
            _recoveryRequired.value = false
            _message.value = null
        } finally {
            mutex.unlock()
        }
    }

    private fun snapshotError(saved: RunSnapshot): String? = when {
        saved.schemaVersion != RUN_SCHEMA_VERSION -> "Save version is not supported. Start new demo to reset it."
        saved.contentVersion != content.contentVersion -> "Content version changed. Start new demo to reset it."
        saved.attemptId < 1 -> "Save is invalid. Start new demo to reset it."
        saved.selection.arrangement !in content.arrangements ||
            saved.selection.member !in content.members ||
            saved.selection.concept !in content.concepts -> "Save has unknown selections. Start new demo to reset it."
        saved.preparation.rehearsals < 0 || saved.preparation.refinements < 0 ||
            saved.preparation.rests < 0 || saved.preparation.used > 3 ->
            "Save has invalid preparation. Start new demo to reset it."
        saved.broadcastPositionMs !in 0..BROADCAST_DURATION_MS ->
            "Save has invalid playback position. Start new demo to reset it."
        saved.phase in setOf(Phase.BROADCAST, Phase.VERDICT, Phase.RECAP) && saved.result == null ->
            "Save is missing its result. Start new demo to reset it."
        saved.phase in setOf(Phase.VERDICT, Phase.RECAP) && saved.preparation.used < 3 ->
            "Save has incomplete preparation. Start new demo to reset it."
        saved.phase in setOf(Phase.BRIEF, Phase.DESK, Phase.PREPARATION) && saved.result != null ->
            "Save has a result before broadcast. Start new demo to reset it."
        else -> null
    }
}
