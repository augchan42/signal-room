package dev.digitalrain.synthwave.feature.booking

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.digitalrain.synthwave.SignalRoomApp
import dev.digitalrain.synthwave.core.data.SnapshotStore
import dev.digitalrain.synthwave.core.data.loadContent
import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Phase
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.model.RunSnapshot
import dev.digitalrain.synthwave.core.model.Selection
import dev.digitalrain.synthwave.core.simulation.newRun
import dev.digitalrain.synthwave.core.simulation.scoreBooking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VerdictExplanationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cappedCoherenceExplainsTheCapWithoutRankingItAsAProducerChoice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = loadContent(context.assets.open("content/late-night.json")
            .bufferedReader().use { it.readText() })
        val selection = Selection(ArrangementId.SPARSE, MemberId.FRONTMAN, ConceptId.MONOCHROME)
        val preparation = Preparation(0, 3, 0, false)
        val result = scoreBooking(content, selection, preparation)
        assertTrue(result.coherence.contributions.any { it.id == "clamp" && it.points < 0 })
        val saved = newRun(content).copy(
            phase = Phase.VERDICT,
            selection = selection,
            preparation = preparation,
            result = result,
            viewingCompleted = true,
            broadcastPositionMs = 20_000,
        )
        val store = object : SnapshotStore {
            override suspend fun load(): RunSnapshot = saved
            override suspend fun save(snapshot: RunSnapshot) = Unit
        }

        compose.setContent { SignalRoomApp(store) }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("WHY IT LANDED THIS WAY").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Preparation refinement: +23.00").assertExists()
        compose.onAllNodesWithText("Bounded to the 0–100 scale", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Coherence reached the 100-point cap", substring = true)
            .assertCountEquals(1)
    }
}
