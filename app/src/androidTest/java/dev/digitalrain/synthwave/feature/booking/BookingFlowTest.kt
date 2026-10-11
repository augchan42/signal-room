package dev.digitalrain.synthwave.feature.booking

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalrain.synthwave.SignalRoomApp
import dev.digitalrain.synthwave.core.data.SnapshotStore
import dev.digitalrain.synthwave.core.model.RunSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookingFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun choosesPlanAndConfirmsBroadcast() {
        compose.setContent { SignalRoomApp(MemoryStore()) }
        compose.onNodeWithTag("open-desk").performClick()
        awaitTag("arrangement-driving")
        compose.onNodeWithTag("arrangement-driving").performScrollTo().performClick()
        awaitText("Driving • selected")
        compose.onNodeWithTag("member-frontman").performScrollTo().performClick()
        compose.onNodeWithTag("concept-warm").performScrollTo().performClick()
        awaitText("Warm transmission • selected")
        compose.onNodeWithTag("open-preparation").performScrollTo().performClick()
        awaitTag("prep-rehearse")
        compose.onNodeWithTag("prep-rehearse").performScrollTo().performClick()
        compose.onNodeWithTag("prep-refine").performScrollTo().performClick()
        compose.onNodeWithTag("prep-rest").performScrollTo().performClick()
        awaitText("0 preparation slots remaining")
        compose.onNodeWithTag("confirm-broadcast").performScrollTo().performClick()
        awaitTag("broadcast-stage")
        compose.onNodeWithText("Driving / Frontman / Warm transmission").assertIsDisplayed()
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}

internal class MemoryStore : SnapshotStore {
    private var snapshot: RunSnapshot? = null
    val current: RunSnapshot? get() = snapshot
    override suspend fun load(): RunSnapshot? = snapshot
    override suspend fun save(snapshot: RunSnapshot) { this.snapshot = snapshot }
}
