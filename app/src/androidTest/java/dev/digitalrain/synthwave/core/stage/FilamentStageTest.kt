package dev.digitalrain.synthwave.core.stage

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.digitalrain.synthwave.core.data.loadAndroidAssetCatalog
import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Selection
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilamentStageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val catalog = loadAndroidAssetCatalog(context.assets)

    private fun request(arrangement: ArrangementId, member: MemberId, concept: ConceptId) =
        requireNotNull(catalog.liveStage(Selection(arrangement, member, concept)))

    private fun withStage(backend: StageBackend, block: suspend (FilamentStage) -> Unit) = runBlocking {
        val stage = FilamentStage(context, StageClock(), backend)
        try { block(stage) } finally { stage.release() }
    }

    /** Share of pixels that are not near black. */
    private fun lit(pixels: ByteArray): Float {
        var count = 0
        for (i in pixels.indices step 4) {
            if ((pixels[i].toInt() and 255) + (pixels[i + 1].toInt() and 255) + (pixels[i + 2].toInt() and 255) > 30) count++
        }
        return count * 4f / pixels.size
    }

    /** Mean difference per colour channel, 0-255. */
    private fun difference(a: ByteArray, b: ByteArray): Float {
        var total = 0L
        for (i in a.indices) if (i % 4 != 3) total += abs((a[i].toInt() and 255) - (b[i].toInt() and 255))
        return total / (a.size * 0.75f)
    }

    private fun save(pixels: ByteArray, width: Int, height: Int, name: String) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
        File(requireNotNull(context.getExternalFilesDir(null)), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** An activity restart builds the new screen's stage before the old one is gone. */
    @Test fun aSecondStageOutlivesTheFirst() = runBlocking {
        val show = request(ArrangementId.DRIVING, MemberId.FRONTMAN, ConceptId.WARM)
        val first = FilamentStage(context, StageClock(), StageBackend.VULKAN)
        assertTrue(first.prepare(show))
        val second = FilamentStage(context, StageClock(), StageBackend.VULKAN)
        try {
            // Asks for the second engine before the first is freed, as an activity restart does.
            val loaded = async(start = CoroutineStart.UNDISPATCHED) { second.prepare(show) }
            first.release()
            assertTrue(loaded.await())
            assertTrue(lit(second.capture(main = true, 270, 480, 10f)) > 0.2f)
            second.unload()
            assertTrue(second.prepare(show))
        } finally {
            second.release()
        }
    }

    @Test fun everyShowLoadsAndDrawsTheBand() = withStage(StageBackend.VULKAN) { stage ->
        for (arrangement in ArrangementId.entries) for (member in MemberId.entries) {
            assertTrue("$arrangement $member", stage.prepare(request(arrangement, member, ConceptId.WARM)))
        }
        val early = stage.capture(main = true, 270, 480, 2f)
        val late = stage.capture(main = true, 270, 480, 10f)
        assertTrue("lit ${lit(early)}", lit(early) > 0.2f)
        assertTrue("moved ${difference(early, late)}", difference(early, late) > 1f)
    }

    @Test fun conceptsLookDifferent() = withStage(StageBackend.VULKAN) { stage ->
        val frames = ConceptId.entries.map { concept ->
            assertTrue(stage.prepare(request(ArrangementId.DRIVING, MemberId.FRONTMAN, concept)))
            stage.capture(main = true, 540, 960, 10f).also { save(it, 540, 960, "live-${concept.name.lowercase()}-10s.png") }
        }
        for (i in frames.indices) for (j in i + 1 until frames.size) {
            assertTrue("concepts $i $j", difference(frames[i], frames[j]) > 3f)
        }
    }

    @Test fun memberCamDrawsTheFeaturedMember() = withStage(StageBackend.VULKAN) { stage ->
        val cams = MemberId.entries.map { member ->
            assertTrue(stage.prepare(request(ArrangementId.LAYERED, member, ConceptId.ELECTRIC)))
            val cam = stage.capture(main = false, 280, 360, 10f)
            save(cam, 280, 360, "live-cam-${member.name.lowercase()}-10s.png")
            assertTrue("$member lit ${lit(cam)}", lit(cam) > 0.2f)
            cam
        }
        // Each cam frames a different member, so no two look alike.
        for (i in cams.indices) for (j in i + 1 until cams.size) {
            assertTrue("cams $i $j", difference(cams[i], cams[j]) > 3f)
        }
    }

    @Test fun openGlDrawsTheSameShow() = withStage(StageBackend.OPENGL) { stage ->
        assertTrue(stage.prepare(request(ArrangementId.SPARSE, MemberId.DRUMMER, ConceptId.MONOCHROME)))
        assertTrue(lit(stage.capture(main = true, 270, 480, 10f)) > 0.2f)
    }

    @Test fun unloadThenPrepareStartsAgain() = withStage(StageBackend.VULKAN) { stage ->
        assertTrue(stage.prepare(request(ArrangementId.SPARSE, MemberId.KEYTAR, ConceptId.WARM)))
        stage.unload()
        assertTrue(stage.prepare(request(ArrangementId.SPARSE, MemberId.KEYTAR, ConceptId.WARM)))
        assertTrue(lit(stage.capture(main = true, 270, 480, 10f)) > 0.2f)
    }
}
