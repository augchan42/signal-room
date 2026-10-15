package dev.digitalrain.synthwave.core.stage

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

enum class StageBackend { VULKAN, OPENGL }

private const val VULKAN_1_1 = 0x401000

/**
 * Vulkan only where it is known to work: weak Vulkan drivers tend to draw garbage rather than fail
 * to start. Measured on a Pixel 7 Pro (Android 16). `adb shell setprop debug.signalroom.backend
 * vulkan|opengl` overrides the list on the next launch.
 */
fun chooseBackend(manufacturer: String, sdkInt: Int, vulkan11: Boolean, override: String): StageBackend = when (override) {
    "vulkan" -> StageBackend.VULKAN
    "opengl" -> StageBackend.OPENGL
    else -> if (manufacturer.equals("Google", ignoreCase = true) && sdkInt >= 31 && vulkan11) StageBackend.VULKAN
        else StageBackend.OPENGL
}

fun chooseBackend(context: Context): StageBackend = chooseBackend(
    Build.MANUFACTURER,
    Build.VERSION.SDK_INT,
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, VULKAN_1_1),
    systemProperty("debug.signalroom.backend"),
)

private fun systemProperty(name: String): String = runCatching {
    Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as String
}.getOrDefault("")
