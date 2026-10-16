package dev.digitalrain.synthwave

import android.content.ComponentCallbacks2
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.digitalrain.synthwave.core.audio.Media3AudioEngine
import dev.digitalrain.synthwave.core.audio.PlaybackStatus
import dev.digitalrain.synthwave.core.audio.PreviewController
import dev.digitalrain.synthwave.core.data.FileSnapshotStore
import dev.digitalrain.synthwave.core.data.SnapshotStore
import dev.digitalrain.synthwave.core.data.loadAndroidAssetCatalog
import dev.digitalrain.synthwave.core.data.loadContent
import dev.digitalrain.synthwave.core.model.Phase
import dev.digitalrain.synthwave.core.simulation.RunAction
import dev.digitalrain.synthwave.core.stage.FilamentStage
import dev.digitalrain.synthwave.core.stage.LiveStageController
import dev.digitalrain.synthwave.core.stage.StageClock
import dev.digitalrain.synthwave.core.stage.StageStatus
import dev.digitalrain.synthwave.core.stage.chooseBackend
import dev.digitalrain.synthwave.core.ui.SignalRoomTheme
import dev.digitalrain.synthwave.feature.booking.BookingScreen
import dev.digitalrain.synthwave.feature.booking.BookingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun SignalRoomApp(snapshotStore: SnapshotStore? = null) {
    val context = LocalContext.current
    val debugTiming = remember(context) {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val content = remember { context.assets.open("content/late-night.json").bufferedReader().use { loadContent(it.readText()) } }
    val catalog = remember { loadAndroidAssetCatalog(context.assets) }
    val model = remember(snapshotStore) {
        BookingViewModel(content, snapshotStore ?: FileSnapshotStore(context.filesDir.toPath()))
    }
    val audio = remember { PreviewController(Media3AudioEngine(context)) }
    val stageClock = remember { StageClock() }
    val liveStage = remember { FilamentStage(context, stageClock, chooseBackend(context)) }
    val stage = remember { LiveStageController(catalog, liveStage, stageClock) }
    val scope = rememberCoroutineScope()
    val snapshot by model.state.collectAsState()
    val message by model.message.collectAsState()
    val recoveryRequired by model.recoveryRequired.collectAsState()
    val restoredFromStore by model.restoredFromStore.collectAsState()
    val playback by audio.state.collectAsState()
    val stageState by stage.state.collectAsState()
    val broadcastLoadedAttempt = remember { mutableIntStateOf(0) }
    var countIn by remember { mutableIntStateOf(0) }

    LaunchedEffect(model) { model.load() }
    DisposableEffect(lifecycleOwner) {
        var returning = false // the first ON_START replays as the observer is added; the phase effect loads then
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) audio.onBackground()
            // The stage may have been freed, or failed, while the app was away; bring it back for the show.
            if (event == Lifecycle.Event.ON_START && returning && model.state.value.phase == Phase.BROADCAST &&
                stage.state.value.status.let { it == StageStatus.IDLE || it == StageStatus.FALLBACK }
            ) scope.launch { stage.load(model.state.value.selection) }
            if (event == Lifecycle.Event.ON_START) returning = true
        }
        // The stage holds a few hundred MB; give it back when the system asks.
        val memory = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) stage.unload()
            }
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            // onTrimMemory covers the background case; unloading here would drop the stage mid-show.
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        context.registerComponentCallbacks(memory)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterComponentCallbacks(memory)
            audio.close()
            stage.unload()
            liveStage.release()
        }
    }
    LaunchedEffect(snapshot.phase, snapshot.selection.arrangement, snapshot.selection.member, snapshot.attemptId) {
        if (snapshot.phase == Phase.DESK || snapshot.phase == Phase.PREPARATION) {
            audio.select(catalog.audio(snapshot.selection))
        }
    }
    LaunchedEffect(snapshot.phase, snapshot.attemptId) {
        if (snapshot.phase == Phase.BROADCAST) {
            // Loads during the count-in, so the first frame is ready when the show starts.
            launch { stage.load(snapshot.selection) }
            if (shouldCountIn(restoredFromStore, snapshot.viewingCompleted, snapshot.broadcastPositionMs)) {
                audio.reset()
                try {
                    for (n in 3 downTo 1) { countIn = n; delay(1_000) }
                } finally {
                    countIn = 0
                }
            }
            val inForeground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            val audioLoad = launch {
                audio.startBroadcast(catalog.audio(snapshot.selection), snapshot.broadcastPositionMs,
                    autoPlay = !restoredFromStore && inForeground)
                broadcastLoadedAttempt.intValue = snapshot.attemptId
            }
            audioLoad.join()
        } else {
            stage.unload()
            if (snapshot.phase == Phase.VERDICT || snapshot.phase == Phase.RECAP) {
                audio.pause()
            } else if (snapshot.phase == Phase.BRIEF) {
                audio.reset()
            }
        }
    }
    LaunchedEffect(snapshot.phase, snapshot.attemptId) {
        while (isActive && snapshot.phase == Phase.BROADCAST) {
            audio.refresh()
            stage.sync(audio.state.value.positionMs, audio.state.value.status)
            delay(50)
        }
    }
    LaunchedEffect(snapshot.phase, playback.status, snapshot.attemptId, broadcastLoadedAttempt.intValue) {
        if (snapshot.phase == Phase.BROADCAST && playback.status == PlaybackStatus.ENDED &&
            broadcastLoadedAttempt.intValue == snapshot.attemptId
        ) {
            model.dispatch(RunAction.CheckpointPlayback(20_000))
            model.dispatch(RunAction.CompleteViewing)
        }
    }
    LaunchedEffect(snapshot.phase, playback.positionMs / 1_000, snapshot.attemptId) {
        if (snapshot.phase == Phase.BROADCAST && playback.status == PlaybackStatus.PLAYING &&
            playback.positionMs in 1_000 until 20_000
        ) model.dispatch(RunAction.CheckpointPlayback(playback.positionMs))
    }

    SignalRoomTheme {
        BookingScreen(
            snapshot = snapshot,
            content = content,
            catalog = catalog,
            playback = playback,
            stage = stageState,
            liveStage = liveStage,
            message = message,
            recoveryRequired = recoveryRequired,
            countIn = countIn,
            onAction = { action ->
                val start = SystemClock.elapsedRealtime()
                if (debugTiming) Log.d("SignalRoomActions", "${action.javaClass.simpleName} requested")
                scope.launch {
                    model.dispatch(action)
                    if (debugTiming) Log.d("SignalRoomActions", "${action.javaClass.simpleName} completed " +
                        "in ${SystemClock.elapsedRealtime() - start}ms; phase=${model.state.value.phase}")
                }
            },
            onReset = {
                val start = SystemClock.elapsedRealtime()
                if (debugTiming) Log.d("SignalRoomActions", "ResetDemo requested")
                scope.launch {
                    model.startNewDemo()
                    if (debugTiming) Log.d("SignalRoomActions", "ResetDemo completed " +
                        "in ${SystemClock.elapsedRealtime() - start}ms; phase=${model.state.value.phase}")
                }
            },
            onPlayPause = { if (playback.status == PlaybackStatus.PLAYING) audio.pause() else audio.play() },
            onMute = { audio.setMuted(!playback.muted) },
        )
    }
}

/** The 3-2-1 count-in runs only on a fresh first viewing, never on a restored or already-watched show. */
internal fun shouldCountIn(restored: Boolean, viewingCompleted: Boolean, positionMs: Long): Boolean =
    !restored && !viewingCompleted && positionMs == 0L
