package dev.digitalrain.synthwave.feature.booking

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalrain.synthwave.SignalRoomApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResearchFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun researchCommitsOneSlotAndSurvivesMixChanges() {
        compose.setContent { SignalRoomApp(MemoryStore()) }
        compose.onNodeWithTag("open-desk").performScrollTo().performClick()
        waitFor("Producer desk")
        compose.onNodeWithTag("open-preparation").performScrollTo().performClick()
        waitFor("Preparation")
        compose.onNodeWithTag("research-open").performScrollTo().performClick()
        waitFor("2 preparation slots remaining")
        waitFor("Audience report")
        compose.onNodeWithTag("return-desk").performScrollTo().performClick()
        waitFor("Producer desk")
        compose.onNodeWithTag("arrangement-driving").performScrollTo().performClick()
        waitFor("Driving • selected")
        compose.onNodeWithTag("open-preparation").performScrollTo().performClick()
        waitFor("Audience report")
        waitFor("2 preparation slots remaining")
    }

    private fun waitFor(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
