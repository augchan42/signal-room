package dev.digitalrain.synthwave.core.stage

import dev.digitalrain.synthwave.core.audio.PlaybackStatus
import dev.digitalrain.synthwave.core.data.AssetCatalog
import dev.digitalrain.synthwave.core.data.AssetOpener
import dev.digitalrain.synthwave.core.data.loadAssetManifest
import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Selection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveStageControllerTest {
    private val manifest by lazy {
        loadAssetManifest(requireNotNull(javaClass.classLoader?.getResource("demo/manifest.json")).readText())
    }
    private val warmKeytar = Selection(ArrangementId.DRIVING, MemberId.KEYTAR, ConceptId.WARM)
    private val electricDrummer = Selection(ArrangementId.SPARSE, MemberId.DRUMMER, ConceptId.ELECTRIC)

    private class FakeEngine(var drawn: Boolean = true) : StageEngine {
        val prepared = mutableListOf<StageRequest>()
        val gates = ArrayDeque<CompletableDeferred<Unit>>()
        var unloads = 0
        override suspend fun prepare(request: StageRequest): Boolean {
            prepared += request
            gates.removeFirstOrNull()?.await()
            return drawn
        }
        override fun unload() { unloads++ }
    }

    private fun controller(engine: StageEngine, opener: AssetOpener = AssetOpener { true }, clock: StageClock = StageClock { 0 }) =
        LiveStageController(AssetCatalog(manifest, opener), engine, clock)

    @Test fun readyOnceTheEngineHasDrawnTheShow() = runBlocking {
        val engine = FakeEngine()
        val stage = controller(engine)
        stage.load(warmKeytar)
        assertEquals(StageState(ConceptId.WARM, StageStatus.READY, "visual/concept-warm.webp"), stage.state.value)
        assertEquals("demo/live/mix/driving-keytar.motion", engine.prepared.single().motionPath)
    }

    @Test fun engineFailureFallsBackToTheStill() = runBlocking {
        val stage = controller(FakeEngine(drawn = false))
        stage.load(warmKeytar)
        assertEquals(StageState(ConceptId.WARM, StageStatus.FALLBACK, "visual/concept-warm.webp"), stage.state.value)
    }

    @Test fun missingFileFallsBackWithoutTheEngine() = runBlocking {
        val engine = FakeEngine()
        val stage = controller(engine, AssetOpener { !it.endsWith(".motion") })
        stage.load(warmKeytar)
        assertEquals(StageStatus.FALLBACK, stage.state.value.status)
        assertTrue(engine.prepared.isEmpty())
    }

    @Test fun laterLoadWins() = runBlocking {
        val engine = FakeEngine()
        val slow = CompletableDeferred<Unit>()
        engine.gates += slow
        val stage = controller(engine)
        val first = launch(start = CoroutineStart.UNDISPATCHED) { stage.load(warmKeytar) }
        assertEquals(StageStatus.LOADING, stage.state.value.status)
        stage.load(electricDrummer)
        slow.complete(Unit)
        first.join()
        assertEquals(StageState(ConceptId.ELECTRIC, StageStatus.READY, "visual/concept-electric.webp"), stage.state.value)
    }

    @Test fun unloadFreesTheEngineAndReturnsToIdle() = runBlocking {
        val engine = FakeEngine()
        val stage = controller(engine)
        stage.load(warmKeytar)
        stage.unload()
        assertEquals(StageState(), stage.state.value)
        assertEquals(1, engine.unloads)
    }

    @Test fun syncFeedsTheClockAndPauseHoldsIt() {
        var now = 0L
        val clock = StageClock { now }
        val stage = controller(FakeEngine(), clock = clock)
        stage.sync(9_250, PlaybackStatus.PLAYING)
        now += 40
        assertEquals(9_290, clock.positionMs())
        stage.sync(9_300, PlaybackStatus.PAUSED)
        now += 1_000
        assertEquals(9_300, clock.positionMs())
    }
}
