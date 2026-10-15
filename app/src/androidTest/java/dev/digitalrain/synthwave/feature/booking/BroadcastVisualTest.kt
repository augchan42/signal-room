package dev.digitalrain.synthwave.feature.booking

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.digitalrain.synthwave.SignalRoomApp
import dev.digitalrain.synthwave.core.data.loadContent
import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Phase
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.model.Selection
import dev.digitalrain.synthwave.core.simulation.newRun
import dev.digitalrain.synthwave.core.simulation.scoreBooking
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BroadcastVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pausedBroadcastShowsTheFrozenLiveStage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = context.assets.open("content/late-night.json").bufferedReader().use { loadContent(it.readText()) }
        val selection = Selection(ArrangementId.SPARSE, MemberId.FRONTMAN, ConceptId.MONOCHROME)
        val prep = Preparation(1, 1, 1, false)
        val store = MemoryStore()
        runBlocking {
            store.save(newRun(content).copy(phase = Phase.BROADCAST, selection = selection,
                preparation = prep, result = scoreBooking(content, selection, prep)))
        }
        compose.setContent { SignalRoomApp(store) }
        // A restored broadcast starts paused; the stage still loads and holds its frame.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("live-stage").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Selected stage concept").assertDoesNotExist()
        compose.onNodeWithContentDescription("Frontman fancam inset").assertExists()
    }

    @Test fun selectedKeyboardistAndElectricStageRenderInPortrait() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = context.assets.open("content/late-night.json").bufferedReader().use { loadContent(it.readText()) }
        val selection = Selection(ArrangementId.LAYERED, MemberId.KEYBOARDIST, ConceptId.ELECTRIC)
        val prep = Preparation(1, 1, 1, false)
        val store = MemoryStore()
        runBlocking {
            store.save(newRun(content).copy(phase = Phase.BROADCAST, selection = selection,
                preparation = prep, result = scoreBooking(content, selection, prep)))
        }
        compose.setContent { SignalRoomApp(store) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("broadcast-stage").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("broadcast-stage").assertIsDisplayed()
        val caption = compose.onNodeWithText("Layered / Keyboardist / Electric broadcast")
            .assertIsDisplayed().captureToImage().toPixelMap()
        check((0 until caption.height).any { y -> (0 until caption.width).any { x ->
            val pixel = caption[x, y]
            pixel.red + pixel.green + pixel.blue > 1.8f
        } }) { "Broadcast selection caption has no readable light pixels" }
        Thread.sleep(2_000)
        val directory = requireNotNull(context.getExternalFilesDir(null))
        File(directory, "broadcast-keyboardist-electric-top.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithContentDescription("Keyboardist fancam inset").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("KEYBOARDIST CAM").assertIsDisplayed()
        File(directory, "broadcast-keyboardist-electric-inset.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun warmFrontmanSelectionUsesMatchingCaptionAndInset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = context.assets.open("content/late-night.json").bufferedReader().use { loadContent(it.readText()) }
        val selection = Selection(ArrangementId.SPARSE, MemberId.FRONTMAN, ConceptId.WARM)
        val prep = Preparation(1, 1, 1, false)
        val store = MemoryStore()
        runBlocking {
            store.save(newRun(content).copy(phase = Phase.BROADCAST, selection = selection,
                preparation = prep, result = scoreBooking(content, selection, prep)))
        }
        compose.setContent { SignalRoomApp(store) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("broadcast-stage").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Sparse / Frontman / Warm transmission").assertIsDisplayed()
        compose.onNodeWithContentDescription("Frontman fancam inset").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("FRONTMAN CAM").assertIsDisplayed()
        val output = File(requireNotNull(context.getExternalFilesDir(null)), "broadcast-frontman-warm.png")
        output.outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
