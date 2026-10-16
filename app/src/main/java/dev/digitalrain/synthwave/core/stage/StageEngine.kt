package dev.digitalrain.synthwave.core.stage

import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId

/** One show's live stage files, as paths from the root of the app's assets. */
data class StageRequest(
    val stagePath: String,
    val infoPath: String,
    val motionPath: String,
    val tracksPath: String,
    val screenPath: String,
    val concept: ConceptId,
    val member: MemberId,
)

/** What the broadcast screen needs from the live stage; a fake stands in for it in unit tests. */
interface StageEngine {
    /** Loads [request] and draws it once off screen. False if the stage can't be drawn. */
    suspend fun prepare(request: StageRequest): Boolean

    /** Frees the engine and everything it holds; the next [prepare] starts again. */
    fun unload()

    /** [listener] runs on the main thread if a prepared stage stops drawing (it has been unloaded). */
    fun setOnLost(listener: () -> Unit)
}
