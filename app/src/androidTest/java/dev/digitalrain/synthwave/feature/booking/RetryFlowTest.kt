package dev.digitalrain.synthwave.feature.booking

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.digitalrain.synthwave.SignalRoomApp
import dev.digitalrain.synthwave.core.data.loadContent
import org.junit.Assert.assertFalse
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

    @Test fun failedInvitationGainsAffinityAndSuccessfulRetryCanSkip() {
        val store = MemoryStore()
        compose.setContent { SignalRoomApp(store) }
        waitTag("open-desk")
        compose.onNodeWithTag("open-desk").performScrollTo().performClick()
        waitTag("concept-electric")
        compose.onNodeWithTag("concept-electric").performScrollTo().performClick()
        prepareAndBroadcast(rehearsals = 1, refinements = 1, rests = 1)
        waitTag("open-recap", 35_000)

        val failure = requireNotNull(store.current?.result)
        assertFalse(failure.invited)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = loadContent(context.assets.open("content/late-night.json")
            .bufferedReader().use { it.readText() })
        assertTrue(content.segments.any { (id, segment) ->
            requireNotNull(failure.audienceAfter[id]).affinity > segment.initial.affinity
        })

        compose.onNodeWithTag("open-recap").performScrollTo().performClick()
        waitTag("confirm-retry")
        compose.onNodeWithTag("confirm-retry").performScrollTo().performClick()
        waitTag("open-desk")
        compose.onNodeWithTag("open-desk").performScrollTo().performClick()
        prepareAndBroadcast(rehearsals = 1, refinements = 0, rests = 2)
        waitTag("skip-performance")
        compose.onNodeWithTag("skip-performance").performScrollTo().performClick()
        waitTag("open-recap")
        assertTrue(requireNotNull(store.current?.result).invited)
    }

    private fun bookThreeSlotPlan(store: MemoryStore) {
        waitTag("open-desk")
        compose.onNodeWithTag("open-desk").performScrollTo().performClick()
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

    private fun prepareAndBroadcast(rehearsals: Int, refinements: Int, rests: Int) {
        waitTag("open-preparation")
        compose.onNodeWithTag("open-preparation").performScrollTo().performClick()
        waitTag("prep-rehearse")
        repeat(rehearsals) { compose.onNodeWithTag("prep-rehearse").performScrollTo().performClick() }
        repeat(refinements) { compose.onNodeWithTag("prep-refine").performScrollTo().performClick() }
        repeat(rests) { compose.onNodeWithTag("prep-rest").performScrollTo().performClick() }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("0 preparation slots remaining").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("confirm-broadcast").performScrollTo().performClick()
        waitTag("broadcast-stage")
    }

    private fun waitTag(tag: String, timeoutMs: Long = 5_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}
