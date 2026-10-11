package dev.digitalrain.synthwave.feature.booking

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalrain.synthwave.SignalRoomApp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RetryFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstViewMustFinishButRetryCanSkip() {
        val store = MemoryStore()
        compose.setContent { SignalRoomApp(store) }
        bookThreeSlotPlan(store)
        waitTag("broadcast-stage")
        compose.onNodeWithTag("skip-performance").assertDoesNotExist()
        waitTag("open-recap", 35_000)
        assertTrue(store.current?.viewingCompleted == true)
        compose.onNodeWithTag("open-recap").performScrollTo().performClick()
        waitTag("confirm-retry")
        compose.onNodeWithTag("confirm-retry").performScrollTo().performClick()
        waitTag("open-desk")
        assertTrue(store.current?.viewingCompleted == true)
        bookThreeSlotPlan(store)
        waitTag("skip-performance")
        compose.onNodeWithTag("skip-performance").performScrollTo().performClick()
        waitTag("open-recap")
    }

    private fun bookThreeSlotPlan(store: MemoryStore) {
        waitTag("open-desk")
        compose.onNodeWithTag("open-desk").performClick()
        waitTag("open-preparation")
        compose.onNodeWithTag("open-preparation").performScrollTo().performClick()
        waitTag("prep-rehearse")
        compose.onNodeWithTag("prep-rehearse").performScrollTo().performClick()
        compose.onNodeWithTag("prep-refine").performScrollTo().performClick()
        compose.onNodeWithTag("prep-rest").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("0 preparation slots remaining").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("confirm-broadcast").performScrollTo().performClick()
        try {
            waitTag("broadcast-stage")
        } catch (error: Throwable) {
            throw AssertionError("Broadcast did not open. Saved state: ${store.current}", error)
        }
    }

    private fun waitTag(tag: String, timeoutMs: Long = 5_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}
