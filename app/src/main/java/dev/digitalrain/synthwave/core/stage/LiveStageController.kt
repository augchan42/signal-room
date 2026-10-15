package dev.digitalrain.synthwave.core.stage

import dev.digitalrain.synthwave.core.audio.PlaybackStatus
import dev.digitalrain.synthwave.core.data.AssetCatalog
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.Selection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StageStatus { IDLE, LOADING, READY, FALLBACK }

data class StageState(
    val concept: ConceptId? = null,
    val status: StageStatus = StageStatus.IDLE,
    /** The concept's still: shown until the stage is ready, and in its place if it can't be drawn. */
    val stillPath: String? = null,
)

/**
 * The band drawn live for the selected show. Audio position is authoritative: [sync] passes it to
 * the clock the render thread reads each frame. Call from the main thread.
 */
class LiveStageController(
    private val catalog: AssetCatalog,
    private val engine: StageEngine,
    val clock: StageClock,
) {
    private val mutableState = MutableStateFlow(StageState())
    val state: StateFlow<StageState> = mutableState.asStateFlow()
    private var loads = 0

    suspend fun load(selection: Selection) {
        val load = ++loads
        val still = catalog.concept(selection.concept).stillPath
        val request = catalog.liveStage(selection)
        if (request == null) {
            mutableState.value = StageState(selection.concept, StageStatus.FALLBACK, still)
            return
        }
        mutableState.value = StageState(selection.concept, StageStatus.LOADING, still)
        val drawn = engine.prepare(request)
        if (load != loads) return // a later load or unload took over
        mutableState.value = StageState(selection.concept, if (drawn) StageStatus.READY else StageStatus.FALLBACK, still)
    }

    fun sync(positionMs: Long, status: PlaybackStatus) {
        clock.update(positionMs, status == PlaybackStatus.PLAYING)
    }

    /** Frees the engine's memory; the next [load] starts it again. */
    fun unload() {
        loads++
        engine.unload()
        mutableState.value = StageState()
    }
}
