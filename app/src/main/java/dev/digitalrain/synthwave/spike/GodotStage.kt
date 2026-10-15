package dev.digitalrain.synthwave.spike

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot

/**
 * Spike (throwaway): the live Godot stage that replaces the broadcast video.
 *
 * The engine starts once with the activity and keeps running; the broadcast screen only attaches
 * its render view. The script reads the show position from [SignalRoomClockPlugin] every frame.
 */
object GodotStage {
    /** Godot's render view; MainActivity points this at its Godot fragment. */
    var view: () -> View? = { null }
}

/** Spike: which live stage the broadcast screen shows, from `debug.signalroom.stage` at launch. */
object StageChoice {
    val filament: Boolean by lazy { systemProperty("debug.signalroom.stage") == "filament" }
}

internal fun systemProperty(name: String): String = runCatching {
    Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as String
}.getOrDefault("")

/** The show position for the live stages, carried forward from the app's audio samples. */
object StageClock {
    @Volatile private var sampledPositionMs = 0L
    @Volatile private var sampledAtMs = 0L
    @Volatile private var playing = false

    /** Called with each audio position sample (every 50 ms during the broadcast). */
    fun update(positionMs: Long, isPlaying: Boolean) {
        sampledPositionMs = positionMs
        sampledAtMs = SystemClock.elapsedRealtime()
        playing = isPlaying
    }

    /** The audio position now, carried forward from the last sample while the audio plays. */
    fun positionMs(): Long =
        if (playing) sampledPositionMs + (SystemClock.elapsedRealtime() - sampledAtMs) else sampledPositionMs
}

class SignalRoomClockPlugin(godot: Godot) : GodotPlugin(godot) {
    override fun getPluginName() = "SignalRoomClock"

    @UsedByGodot
    fun positionMs(): Long = StageClock.positionMs()
}

@Composable
fun GodotStageView(modifier: Modifier = Modifier) {
    LiveStageView({ GodotStage.view() }, modifier)
}

/** Attaches a long-lived render view, taking it from wherever it was attached before. */
@Composable
internal fun LiveStageView(view: (android.content.Context) -> View?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            FrameLayout(context).apply {
                view(context)?.let { view ->
                    (view.parent as? ViewGroup)?.removeView(view)
                    addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT))
                }
            }
        },
        onRelease = { it.removeAllViews() },
        modifier = modifier,
    )
}
