package dev.digitalrain.synthwave.feature.booking

import dev.digitalrain.synthwave.core.data.FileSnapshotStore
import dev.digitalrain.synthwave.core.data.SnapshotStore
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.model.RunSnapshot
import dev.digitalrain.synthwave.core.simulation.RunAction
import dev.digitalrain.synthwave.core.simulation.TestContent
import dev.digitalrain.synthwave.core.simulation.newRun
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BookingViewModelTest {
    @get:Rule val temp = TemporaryFolder()
    private val content = TestContent.lateNight()

    @Test fun failedSaveDoesNotRevealResearchOrAdvancePublishedState() = runBlocking {
        val realStore = FileSnapshotStore(temp.root.toPath())
        val holder = BookingViewModel(content, realStore)
        holder.load()
        holder.dispatch(RunAction.OpenDesk)
        val before = holder.state.value
        val failingStore = object : SnapshotStore {
            override suspend fun load(): RunSnapshot? = realStore.load()
            override suspend fun save(snapshot: RunSnapshot) {
                throw IOException("disk unavailable")
            }
        }
        val restored = BookingViewModel(content, failingStore)
        restored.load()
        restored.dispatch(RunAction.PurchaseResearch)

        assertEquals(before, restored.state.value)
        assertEquals(before, realStore.load())
        assertEquals("Could not save; action not applied.", restored.message.value)
    }

    @Test fun concurrentResearchCommandsSpendOneSlot() = runBlocking {
        val store = FileSnapshotStore(temp.root.toPath())
        val holder = BookingViewModel(content, store)
        holder.load()
        holder.dispatch(RunAction.OpenDesk)

        (1..8).map { async(Dispatchers.Default) { holder.dispatch(RunAction.PurchaseResearch) } }.awaitAll()

        assertTrue(holder.state.value.preparation.researched)
        assertEquals(1, holder.state.value.preparation.used)
        assertEquals(holder.state.value, store.load())
    }

    @Test fun confirmedResultRestoresAndCannotBeAppliedTwice() = runBlocking {
        val store = FileSnapshotStore(temp.root.toPath())
        val holder = BookingViewModel(content, store)
        holder.load()
        holder.dispatch(RunAction.OpenDesk)
        holder.dispatch(RunAction.OpenPreparation)
        holder.dispatch(RunAction.SetPreparation(Preparation(1, 1, 1, false)))
        holder.dispatch(RunAction.ConfirmBroadcast)
        val confirmed = holder.state.value
        assertNotNull(confirmed.result)

        holder.dispatch(RunAction.ConfirmBroadcast)
        assertEquals(confirmed, holder.state.value)
        val restored = BookingViewModel(content, store)
        restored.load()
        assertEquals(confirmed, restored.state.value)
    }

    @Test fun incompatibleSaveRequiresExplicitReset() = runBlocking {
        val store = FileSnapshotStore(temp.root.toPath())
        val incompatible = newRun(content).copy(schemaVersion = 999)
        store.save(incompatible)
        val holder = BookingViewModel(content, store)

        holder.load()
        assertTrue(holder.recoveryRequired.value)
        assertEquals(incompatible, store.load())
        holder.dispatch(RunAction.OpenDesk)
        assertEquals(incompatible, store.load())
        holder.startNewDemo()
        assertFalse(holder.recoveryRequired.value)
        assertEquals(newRun(content), store.load())
    }

    @Test fun corruptSaveRequiresExplicitReset() = runBlocking {
        val store = FileSnapshotStore(temp.root.toPath())
        store.save(newRun(content))
        Files.write(temp.root.toPath().resolve("run-snapshot.json"), "not-json".toByteArray())
        val holder = BookingViewModel(content, store)

        holder.load()
        assertTrue(holder.recoveryRequired.value)
        holder.startNewDemo()
        assertFalse(holder.recoveryRequired.value)
        assertEquals(newRun(content), store.load())
    }
}
