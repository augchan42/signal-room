package dev.digitalrain.synthwave.core.data

import dev.digitalrain.synthwave.core.simulation.RunAction
import dev.digitalrain.synthwave.core.simulation.TestContent
import dev.digitalrain.synthwave.core.simulation.newRun
import dev.digitalrain.synthwave.core.simulation.reduceRun
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SnapshotStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val content = TestContent.lateNight()

    @Test fun replacesExistingSnapshotAtomically() = runBlocking {
        val directory = temp.root.toPath()
        val store = FileSnapshotStore(directory)
        val first = newRun(content)
        val second = reduceRun(first, RunAction.OpenDesk, content)

        store.save(first)
        assertEquals(first, store.load())
        store.save(second)
        assertEquals(second, store.load())
        assertEquals(1L, Files.list(directory).use { it.count() })
    }

    @Test fun failedAtomicMoveKeepsPreviousSnapshotAndCleansOnlyItsTempFile() = runBlocking {
        val directory = temp.root.toPath()
        val working = FileSnapshotStore(directory)
        val first = newRun(content)
        working.save(first)
        val failing = FileSnapshotStore(directory) { _, _ -> throw IOException("injected move failure") }

        assertThrows(IOException::class.java) {
            runBlocking { failing.save(reduceRun(first, RunAction.OpenDesk, content)) }
        }
        assertEquals(first, working.load())
        assertEquals(1L, Files.list(directory).use { it.count() })
    }

    @Test fun malformedSnapshotIsReportedInsteadOfSilentlyReset() = runBlocking {
        val directory = temp.root.toPath()
        val store = FileSnapshotStore(directory)
        store.save(newRun(content))
        Files.write(directory.resolve("run-snapshot.json"), "{broken".toByteArray())

        assertTrue(runCatching { store.load() }.isFailure)
    }
}
