package dev.digitalrain.synthwave.feature.booking

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CountInRestoreTest {
    @get:Rule val compose = createComposeRule()

    /** A broadcast save at position 0 is restored paused with no count-in. */
    @Test fun restoredBroadcastAtZeroNeverShowsCountIn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = context.assets.open("content/late-night.json").bufferedReader().use { loadContent(it.readText()) }
        val selection = Selection(ArrangementId.DRIVING, MemberId.FRONTMAN, ConceptId.WARM)
        val prep = Preparation(1, 1, 1, false)
        val store = MemoryStore()
        runBlocking {
            store.save(newRun(content).copy(phase = Phase.BROADCAST, selection = selection, preparation = prep,
                result = scoreBooking(content, selection, prep), viewingCompleted = false, broadcastPositionMs = 0))
        }
        compose.setContent { SignalRoomApp(store) }
        compose.waitUntil(10_000) {
            check(compose.onAllNodesWithTag("count-in").fetchSemanticsNodes().isEmpty()) { "count-in appeared on a restored run" }
            compose.onAllNodesWithText("0:20 left \u2022 paused").fetchSemanticsNodes().isNotEmpty()
        }
        check(compose.onAllNodesWithTag("count-in").fetchSemanticsNodes().isEmpty()) { "count-in appeared on a restored run" }
    }
}
