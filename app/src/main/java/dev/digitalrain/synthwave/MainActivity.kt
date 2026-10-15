package dev.digitalrain.synthwave

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import dev.digitalrain.synthwave.core.ui.SignalRoomTheme
import dev.digitalrain.synthwave.core.ui.SplashScreen
import dev.digitalrain.synthwave.spike.GodotStage
import dev.digitalrain.synthwave.spike.SignalRoomClockPlugin
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotFragment
import org.godotengine.godot.GodotHost
import org.godotengine.godot.plugin.GodotPlugin

class MainActivity : FragmentActivity(), GodotHost {
    private var godotFragment: GodotFragment? = null
    private var clockPlugin: SignalRoomClockPlugin? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Spike (throwaway): start Godot once, with no container; the broadcast screen attaches its view.
        godotFragment = supportFragmentManager.findFragmentByTag(GODOT_TAG) as? GodotFragment
            ?: GodotFragment().also { supportFragmentManager.beginTransaction().add(it, GODOT_TAG).commitNow() }
        GodotStage.view = { godotFragment?.view }
        setContent {
            // The splash plays once per launch; rotation and process restore skip it.
            var showSplash by rememberSaveable { mutableStateOf(true) }
            Box {
                SignalRoomApp()
                AnimatedVisibility(showSplash, enter = EnterTransition.None, exit = fadeOut(tween(450))) {
                    SignalRoomTheme { SplashScreen(onDone = { showSplash = false }) }
                }
            }
        }
    }

    override fun getActivity() = this

    override fun getGodot(): Godot? = godotFragment?.godot

    // Spike: `adb shell setprop debug.signalroom.maxfps 30` caps Godot's frame rate on the next launch.
    override fun getCommandLine(): List<String> {
        val maxFps = runCatching {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
                .invoke(null, "debug.signalroom.maxfps") as String
        }.getOrDefault("")
        return listOf("--audio-driver", "Dummy") + if (maxFps.isNotEmpty()) listOf("--max-fps", maxFps) else emptyList()
    }

    override fun getHostPlugins(godot: Godot): Set<GodotPlugin> =
        setOf(clockPlugin ?: SignalRoomClockPlugin(godot).also { clockPlugin = it })

    private companion object {
        const val GODOT_TAG = "godot-stage"
    }
}
