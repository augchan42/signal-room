package dev.digitalrain.synthwave.feature.booking

import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.digitalrain.synthwave.SignalRoomApp
import dev.digitalrain.synthwave.core.data.FileSnapshotStore
import dev.digitalrain.synthwave.core.data.SnapshotStore
import dev.digitalrain.synthwave.core.data.loadContent
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.model.RunSnapshot
import dev.digitalrain.synthwave.core.simulation.RunAction
import dev.digitalrain.synthwave.core.simulation.newRun
import dev.digitalrain.synthwave.core.simulation.reduceRun
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise both recap buttons with the same durable store used by the installed app. */
@RunWith(AndroidJUnit4::class)
class RetryResetDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var directory: java.nio.file.Path? = null

    @After fun removeTestSave() {
        directory?.let {
            Files.deleteIfExists(it.resolve("run-snapshot.json"))
            Files.deleteIfExists(it)
        }
    }

    @Test fun retryReturnsToBriefWithPersistedSecondAttempt() = checkTransition("confirm-retry", 2)

    @Test fun resetReturnsToBriefWithPersistedFirstAttempt() = checkTransition("reset-demo", 1)

    private fun checkTransition(button: String, expectedAttempt: Int) {
        compose.runOnUiThread {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = loadContent(context.assets.open("content/late-night.json")
            .bufferedReader().use { it.readText() })
        var broadcast = newRun(content)
        for (action in listOf(RunAction.OpenDesk, RunAction.OpenPreparation,
            RunAction.SetPreparation(Preparation(1, 1, 1, false)), RunAction.ConfirmBroadcast,
            RunAction.CheckpointPlayback(19_000))) {
            broadcast = reduceRun(broadcast, action, content)
        }
        val dir = Files.createTempDirectory(context.cacheDir.toPath(), "retry-reset-test-")
        directory = dir
        val disk = FileSnapshotStore(dir)
        runBlocking { disk.save(broadcast) }
        val timed = object : SnapshotStore {
            override suspend fun load(): RunSnapshot? = disk.load()
            override suspend fun save(snapshot: RunSnapshot) {
                val start = SystemClock.elapsedRealtime()
                disk.save(snapshot)
                Log.i("RetryResetTiming", "save ${snapshot.phase}: ${SystemClock.elapsedRealtime() - start}ms")
            }
        }
        compose.setContent { SignalRoomApp(timed) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("0:01 left • paused").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("count-in").assertDoesNotExist()
        compose.onNodeWithTag("broadcast-play-pause").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("open-recap").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("open-recap").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(button).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(button).performScrollTo()
        val start = SystemClock.elapsedRealtime()
        compose.onNodeWithTag(button).performClick()
        try {
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("open-desk").fetchSemanticsNodes().isNotEmpty() }
        } catch (error: Throwable) {
            Log.i("RetryResetTiming", "transition timed out; saved=${runBlocking { disk.load() }}")
            compose.onRoot(useUnmergedTree = true).printToLog("RetryResetUi")
            throw error
        }
        Log.i("RetryResetTiming", "$button to brief: ${SystemClock.elapsedRealtime() - start}ms")
        assertEquals(expectedAttempt, runBlocking { disk.load() }?.attemptId)
    }
}
