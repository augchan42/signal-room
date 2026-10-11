package dev.digitalrain.synthwave.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.digitalrain.synthwave.core.simulation.RunAction
import dev.digitalrain.synthwave.core.simulation.newRun
import dev.digitalrain.synthwave.core.simulation.reduceRun
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileSnapshotStoreDeviceTest {
    @Test fun replacesExistingSnapshotOnDeviceFilesystem() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = loadContent(context.assets.open("content/late-night.json").bufferedReader().use { it.readText() })
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "snapshot-test-")
        try {
            val store = FileSnapshotStore(directory)
            val first = newRun(content)
            val second = reduceRun(first, RunAction.OpenDesk, content)
            store.save(first)
            store.save(second)

            assertEquals(second, store.load())
            assertEquals(1L, Files.list(directory).use { it.count() })
        } finally {
            Files.deleteIfExists(directory.resolve("run-snapshot.json"))
            Files.deleteIfExists(directory)
        }
    }
}
