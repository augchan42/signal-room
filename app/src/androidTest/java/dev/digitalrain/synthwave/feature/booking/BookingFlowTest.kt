package dev.digitalrain.synthwave.feature.booking

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
        awaitText(INTRO_LINE)
        compose.onNodeWithTag("open-desk").performScrollTo().performClick()
        awaitTag("arrangement-driving")
        compose.onNodeWithTag("arrangement-driving").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("arrangement-driving").assertIsSelected() }.isSuccess
        }
        compose.onNodeWithTag("member-frontman").performScrollTo().performClick()
        // performScrollTo only moves the nearest scroll container (the horizontal card row), so swipe the page
        // until the stage cards clear the fixed bottom bar. The swipe starts mid-screen, away from the bar, and the
        // sleep lets the fling settle: a tap during the fling only stops it.
        compose.onRoot().performTouchInput { swipeUp(startY = height * 0.7f, endY = height * 0.1f) }
        compose.waitForIdle(); Thread.sleep(1500)
        compose.onNodeWithTag("concept-warm").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("concept-warm").assertIsSelected() }.isSuccess
        }
        awaitText("Driving · Frontman · Warm transmission")
        compose.onNodeWithTag("open-preparation").performClick()
        awaitTag("prep-rehearse")
        compose.onNodeWithTag("prep-rehearse").performScrollTo().performClick()
        compose.onNodeWithTag("prep-refine").performScrollTo().performClick()
        awaitText("Place 1 more token")
        compose.onNodeWithTag("confirm-broadcast").assertIsNotEnabled()
        compose.onNodeWithTag("prep-rest").performScrollTo().performClick()
        awaitText("Go on air")
        compose.onNodeWithTag("confirm-broadcast").performClick()
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
