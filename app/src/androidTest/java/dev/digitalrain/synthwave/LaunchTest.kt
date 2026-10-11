package dev.digitalrain.synthwave

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.digitalrain.synthwave.core.data.FileSnapshotStore
import dev.digitalrain.synthwave.core.data.loadContent
import dev.digitalrain.synthwave.core.simulation.newRun
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private var originalSave: ByteArray? = null
    private val savePath get() = InstrumentationRegistry.getInstrumentation().targetContext
        .filesDir.toPath().resolve("run-snapshot.json")

    @get:Rule val rules = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            originalSave = if (Files.exists(savePath)) Files.readAllBytes(savePath) else null
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val content = context.assets.open("content/late-night.json").bufferedReader().use { loadContent(it.readText()) }
            runBlocking { FileSnapshotStore(context.filesDir.toPath()).save(newRun(content)) }
        }

        override fun after() {
            val backup = originalSave
            if (backup == null) Files.deleteIfExists(savePath) else Files.write(savePath, backup)
        }
    }).around(compose)

    @Test
    fun launchesIntoLateNightBooking() {
        compose.onNodeWithText("Late-night debut").assertIsDisplayed()
    }

    @Test
    fun bookingTitleHasReadableLightPixels() {
        val pixels = compose.onNodeWithText("Late-night debut")
            .assertIsDisplayed()
            .captureToImage()
            .toPixelMap()

        val hasLightTextPixel = (0 until pixels.height).any { y ->
            (0 until pixels.width).any { x ->
                val color = pixels[x, y]
                color.red + color.green + color.blue > 1.8f
            }
        }
        assert(hasLightTextPixel) { "Booking title has no readable light pixels" }
    }

    @Test
    fun showsBlenderControlRoomBackdrop() {
        compose.onNodeWithContentDescription("Signal Room control room")
            .assertIsDisplayed()
    }
}
