package dev.digitalrain.synthwave.feature.booking

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.digitalrain.synthwave.core.audio.Media3AudioEngine
import dev.digitalrain.synthwave.core.audio.PlaybackStatus
import dev.digitalrain.synthwave.core.audio.PreviewController
import dev.digitalrain.synthwave.core.data.FileSnapshotStore
import dev.digitalrain.synthwave.core.data.loadAndroidAssetCatalog
import dev.digitalrain.synthwave.core.data.loadContent
import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.AudioAsset
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.simulation.RunAction
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineDemoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun allRequiredMediaOpensWithoutNetworkPermission() {
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_PERMISSIONS)
        assertFalse(packageInfo.requestedPermissions.orEmpty().contains(Manifest.permission.INTERNET))
        assertFalse(packageInfo.requestedPermissions.orEmpty().contains(Manifest.permission.ACCESS_NETWORK_STATE))

        val catalog = loadAndroidAssetCatalog(context.assets)
        for (arrangement in ArrangementId.entries) for (member in MemberId.entries) {
            val audio = catalog.audio(dev.digitalrain.synthwave.core.model.Selection(
                arrangement, member, ConceptId.MONOCHROME))
            assertTrue(audio.path.endsWith(".m4a"))
        }
        for (concept in ConceptId.entries) {
            assertNotNull(catalog.concept(concept))
            for (arrangement in ArrangementId.entries) for (member in MemberId.entries) {
                val selection = dev.digitalrain.synthwave.core.model.Selection(arrangement, member, concept)
                assertNotNull(catalog.liveStage(selection))
            }
        }
        for (member in MemberId.entries) assertNotNull(catalog.member(member))
        assertTrue(catalog.controlRoom().endsWith(".webp"))
    }

    @Test fun missingAudioDoesNotErasePersistedBooking() = runBlocking {
        val content = loadContent(context.assets.open("content/late-night.json")
            .bufferedReader().use { it.readText() })
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "offline-test-")
        try {
            val store = FileSnapshotStore(directory)
            val holder = BookingViewModel(content, store)
            holder.load()
            holder.dispatch(RunAction.OpenDesk)
            val saved = holder.state.value

            withContext(Dispatchers.Main) {
                val controller = PreviewController(Media3AudioEngine(context))
                try {
                    withTimeout(10_000) {
                        controller.startBroadcast(AudioAsset("missing-audio", "missing/track.m4a",
                            ArrangementId.SPARSE, MemberId.FRONTMAN, "missing"))
                    }
                    assertEquals(PlaybackStatus.ERROR, controller.state.value.status)
                    assertEquals("missing-audio", controller.state.value.errorAssetId)
                } finally {
                    controller.close()
                }
            }

            assertEquals(saved, store.load())
            val replacement = BookingViewModel(content, store)
            replacement.load()
            assertEquals(saved, replacement.state.value)
        } finally {
            Files.deleteIfExists(directory.resolve("run-snapshot.json"))
            Files.deleteIfExists(directory)
        }
    }
}
