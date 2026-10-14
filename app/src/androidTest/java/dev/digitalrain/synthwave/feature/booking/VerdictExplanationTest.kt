package dev.digitalrain.synthwave.feature.booking

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import dev.digitalrain.synthwave.core.simulation.starsFor
import kotlinx.coroutines.runBlocking
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
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("reward").fetchSemanticsNodes().isNotEmpty() }
        val stars = starsFor(result.invitationScore)
        compose.onNodeWithTag("stars-$stars").assertExists()
        compose.onNodeWithText(headline(stars)).assertExists()
        compose.onAllNodesWithText("WHY IT LANDED THIS WAY").assertCountEquals(0)
        compose.onNodeWithTag("score-details").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("WHY IT LANDED THIS WAY").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Preparation refinement: +23.00").assertExists()
        compose.onAllNodesWithText("Bounded to the 0–100 scale", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Coherence reached the 100-point cap", substring = true)
            .assertCountEquals(1)
    }

    @Test fun recapSaveOpensTheRewardAndRetries() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = loadContent(context.assets.open("content/late-night.json")
            .bufferedReader().use { it.readText() })
        val selection = Selection(ArrangementId.DRIVING, MemberId.FRONTMAN, ConceptId.WARM)
        val preparation = Preparation(1, 1, 1, false)
        val store = MemoryStore()
        runBlocking {
            store.save(newRun(content).copy(phase = Phase.RECAP, selection = selection, preparation = preparation,
                result = scoreBooking(content, selection, preparation), viewingCompleted = true,
                broadcastPositionMs = 20_000))
        }
        compose.setContent { SignalRoomApp(store) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("reward").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("stars-1").assertExists() // 64.80
        compose.onNodeWithTag("confirm-retry").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("open-desk").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(store.current?.attemptId == 2)
    }
}
