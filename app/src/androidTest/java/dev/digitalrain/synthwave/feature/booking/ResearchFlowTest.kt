package dev.digitalrain.synthwave.feature.booking

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.digitalrain.synthwave.SignalRoomApp
import dev.digitalrain.synthwave.core.data.loadContent
import dev.digitalrain.synthwave.core.model.Phase
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.simulation.newRun
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResearchFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun legacyResearchSaveShowsALockedToken() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = loadContent(context.assets.open("content/late-night.json").bufferedReader().use { it.readText() })
        val store = MemoryStore()
        runBlocking {
            store.save(newRun(content).copy(phase = Phase.PREPARATION, preparation = Preparation(0, 0, 0, true)))
        }
        compose.setContent { SignalRoomApp(store) }
        waitFor("Research (locked)")
        waitFor("Place 2 more tokens")
        compose.onNodeWithTag("confirm-broadcast").assertIsNotEnabled()
        compose.onNodeWithTag("slot-0").performScrollTo().performClick()
        compose.waitForIdle()
        waitFor("Place 2 more tokens")
        waitFor("Research (locked)")
        assertEquals(true, store.current?.preparation?.researched)
        compose.onNodeWithTag("prep-rest").performScrollTo().performClick()
        compose.onNodeWithTag("prep-rest").performScrollTo().performClick()
        waitFor("Go on air")
    }

    private fun waitFor(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
