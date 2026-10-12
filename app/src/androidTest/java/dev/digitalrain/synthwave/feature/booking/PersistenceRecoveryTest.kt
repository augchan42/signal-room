package dev.digitalrain.synthwave.feature.booking

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.digitalrain.synthwave.core.data.FileSnapshotStore
import dev.digitalrain.synthwave.core.data.loadContent
import dev.digitalrain.synthwave.core.model.Phase
import dev.digitalrain.synthwave.core.model.PrepAction
import dev.digitalrain.synthwave.core.simulation.RunAction
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceRecoveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val content by lazy {
        loadContent(context.assets.open("content/late-night.json").bufferedReader().use { it.readText() })
    }

    @Test fun researchAndUnconfirmedChoicesSurviveProcessReplacement() = runBlocking {
        withStore { store, _ ->
            val first = BookingViewModel(content, store)
            first.load()
            first.dispatch(RunAction.OpenDesk)
            first.dispatch(RunAction.PurchaseResearch)
            first.dispatch(RunAction.OpenPreparation)
            first.dispatch(RunAction.AdjustPreparation(PrepAction.REHEARSE, 1))
            val saved = first.state.value

            val replacement = BookingViewModel(content, store)
            replacement.load()
            assertEquals(saved, replacement.state.value)
            assertTrue(replacement.state.value.preparation.researched)
            assertEquals(2, replacement.state.value.preparation.used)
            assertEquals(Phase.PREPARATION, replacement.state.value.phase)
        }
    }

    @Test fun confirmedResultAndPlaybackCheckpointRestoreWithoutRescoring() = runBlocking {
        withStore { store, _ ->
            val first = BookingViewModel(content, store)
            first.load()
            first.dispatch(RunAction.OpenDesk)
            first.dispatch(RunAction.OpenPreparation)
            first.dispatch(RunAction.AdjustPreparation(PrepAction.REHEARSE, 1))
            first.dispatch(RunAction.AdjustPreparation(PrepAction.REFINE, 1))
            first.dispatch(RunAction.AdjustPreparation(PrepAction.REST, 1))
            first.dispatch(RunAction.ConfirmBroadcast)
            val confirmed = first.state.value
            assertNotNull(confirmed.result)
            first.dispatch(RunAction.ConfirmBroadcast)
            assertEquals(confirmed, first.state.value)

            first.dispatch(RunAction.CheckpointPlayback(8_000))
            val midway = first.state.value
            val replacement = BookingViewModel(content, store)
            replacement.load()
            assertEquals(midway, replacement.state.value)
            assertEquals(Phase.BROADCAST, replacement.state.value.phase)
            assertEquals(8_000L, replacement.state.value.broadcastPositionMs)
            assertFalse(replacement.state.value.viewingCompleted)
            replacement.dispatch(RunAction.ConfirmBroadcast)
            assertEquals(midway, replacement.state.value)
        }
    }

    @Test fun incompatibleAndCorruptSavesRequireExplicitReset() = runBlocking {
        withStore { store, directory ->
            val first = BookingViewModel(content, store)
            first.startNewDemo()
            store.save(first.state.value.copy(schemaVersion = 999))
            val incompatible = BookingViewModel(content, store)
            incompatible.load()
            assertTrue(incompatible.recoveryRequired.value)
            incompatible.dispatch(RunAction.OpenDesk)
            assertEquals(999, store.load()?.schemaVersion)
            incompatible.startNewDemo()
            assertFalse(incompatible.recoveryRequired.value)

            Files.write(directory.resolve("run-snapshot.json"), "broken-json".toByteArray())
            val corrupt = BookingViewModel(content, store)
            corrupt.load()
            assertTrue(corrupt.recoveryRequired.value)
            corrupt.dispatch(RunAction.OpenDesk)
            assertEquals(Phase.BRIEF, corrupt.state.value.phase)
            corrupt.startNewDemo()
            assertFalse(corrupt.recoveryRequired.value)
            assertEquals(Phase.BRIEF, store.load()?.phase)
        }
    }

    private suspend fun withStore(block: suspend (FileSnapshotStore, java.nio.file.Path) -> Unit) {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "recovery-test-")
        try {
            block(FileSnapshotStore(directory), directory)
        } finally {
            Files.deleteIfExists(directory.resolve("run-snapshot.json"))
            Files.deleteIfExists(directory)
        }
    }

}
