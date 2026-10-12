package dev.digitalrain.synthwave.feature.booking

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.digitalrain.synthwave.MainActivity
import dev.digitalrain.synthwave.core.data.FileSnapshotStore
import dev.digitalrain.synthwave.core.data.loadContent
import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Phase
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.model.Selection
import dev.digitalrain.synthwave.core.simulation.newRun
import dev.digitalrain.synthwave.core.simulation.scoreBooking
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BroadcastLifecycleTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private var originalSave: ByteArray? = null
    private val savePath get() = InstrumentationRegistry.getInstrumentation().targetContext
        .filesDir.toPath().resolve("run-snapshot.json")

    @get:Rule val rules = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            originalSave = if (Files.exists(savePath)) Files.readAllBytes(savePath) else null
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val content = context.assets.open("content/late-night.json").bufferedReader().use { loadContent(it.readText()) }
            val selection = Selection(ArrangementId.LAYERED, MemberId.KEYBOARDIST, ConceptId.ELECTRIC)
            val prep = Preparation(1, 1, 1, false)
            runBlocking {
                FileSnapshotStore(context.filesDir.toPath()).save(newRun(content).copy(
                    phase = Phase.BROADCAST,
                    selection = selection,
                    preparation = prep,
                    result = scoreBooking(content, selection, prep),
                    broadcastPositionMs = 8_000,
                ))
            }
        }

        override fun after() {
            val backup = originalSave
            if (backup == null) Files.deleteIfExists(savePath) else Files.write(savePath, backup)
        }
    }).around(compose)

    @Test fun restoredBroadcastAndRotationRequireResume() {
        waitFor("paused")
        compose.onNodeWithTag("broadcast-play-pause").performScrollTo().performClick()
        waitFor("playing")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitFor("paused")
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        waitFor("paused", 15_000)
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        waitFor("paused", 15_000)
    }

    private fun waitFor(value: String, timeoutMs: Long = 8_000) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodesWithText(value, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
