package dev.digitalrain.synthwave.spike

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.float
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "FilamentStage"
private const val SCENE = "filament/warm-driving-frontman.glb"
private const val TRACKS = "filament/warm-driving-frontman.tracks.json"
private const val FPS = 24f
private const val FRAMES = 480
private const val CAMERA_NAME = "CAM | Broadcast group"
private const val CAMERA_HALF_HEIGHT = 4.65 // the glTF camera's ymag
// Blender watts to candela, chosen by eye against the rendered video.
private const val AREA_CANDELA_PER_WATT = 0.15f
private const val SPOT_CANDELA_PER_WATT = 0.22f

/**
 * Spike (throwaway): the same performance as the Godot stage, drawn with Filament.
 *
 * The engine and scene are made on first use and kept for the life of the process; the broadcast
 * screen attaches the one [SurfaceView]. Frames are drawn only while it is attached.
 */
@Composable
fun FilamentStageView(modifier: Modifier = Modifier) {
    LiveStageView({ context -> FilamentStage.view(context) }, modifier)
}

private object FilamentStage {
    private var stage: FilamentStageRenderer? = null

    fun view(context: Context): SurfaceView =
        (stage ?: FilamentStageRenderer(context.applicationContext).also { stage = it }).surfaceView
}

private class FilamentStageRenderer(private val context: Context) : Choreographer.FrameCallback {
    val surfaceView = SurfaceView(context)
    private val engine: Engine
    private val renderer: Renderer
    private val scene: com.google.android.filament.Scene
    private val view: View
    private val asset: FilamentAsset
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var swapChain: SwapChain? = null
    private var camera: Camera? = null
    private val tracks: JsonObject
    private val lightsByName = mutableMapOf<String, Int>() // Blender light name -> entity
    private val materialsByName = mutableMapOf<String, MutableList<MaterialInstance>>()
    private val maxFps = systemProperty("debug.signalroom.maxfps").toIntOrNull() ?: 0
    private var lastRenderNanos = 0L
    private val intervals = ArrayList<Float>()
    private val cpuTimes = ArrayList<Float>()
    private var reportAt = 0L

    init {
        val start = SystemClock.elapsedRealtime()
        Filament.init()
        Gltfio.init()
        // Spike: `adb shell setprop debug.signalroom.backend vulkan` picks Vulkan on the next launch.
        engine = if (systemProperty("debug.signalroom.backend") == "vulkan") Engine.create(Engine.Backend.VULKAN)
            else Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView().also { it.scene = scene }
        renderer.clearOptions = renderer.clearOptions.apply {
            clearColor = doubleArrayOf(0.008, 0.012, 0.022, 1.0)
            clear = true
        }
        view.colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.Filmic()).build(engine)
        view.bloomOptions = view.bloomOptions.apply { enabled = true; strength = 0.12f }
        view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply {
            enabled = true; sampleCount = 2
        }
        scene.indirectLight = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(0.03f, 0.036f, 0.054f))
            .intensity(1f)
            .build(engine)

        val loader = AssetLoader(engine, UbershaderProvider(engine), EntityManager.get())
        asset = loader.createAsset(readAsset(SCENE))!!
        ResourceLoader(engine).apply { loadResources(asset); destroy() }
        asset.releaseSourceData()
        scene.addEntities(asset.entities)
        tracks = Json.parseToJsonElement(context.assets.open(TRACKS).bufferedReader().readText()).jsonObject

        // Blender's volume haze arrives as a solid box; Filament has no volumetric fog either.
        asset.getFirstEntityByName("T3 bounded haze").takeIf { it != 0 }?.let { scene.removeEntity(it) }
        for (entity in asset.cameraEntities) {
            if (asset.getName(entity) == CAMERA_NAME) camera = engine.getCameraComponent(entity)
        }
        camera?.setExposure(1f, 1.2f, 100f) // exposure 1: light values reach the screen unscaled
        view.camera = camera
        for (entity in asset.lightEntities) asset.getName(entity)?.let { lightsByName[it] = entity }
        for (mi in asset.instance.materialInstances) materialsByName.getOrPut(mi.name) { mutableListOf() }.add(mi)
        addAreaLights()

        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface)
            }

            override fun onDetachedFromSurface() {
                swapChain?.let { engine.destroySwapChain(it); engine.flushAndWait() }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
                val halfWidth = CAMERA_HALF_HEIGHT * width / height
                camera?.setProjection(Camera.Projection.ORTHO, -halfWidth, halfWidth,
                    -CAMERA_HALF_HEIGHT, CAMERA_HALF_HEIGHT, 0.1, 1000.0)
            }
        }
        uiHelper.attachTo(surfaceView)
        surfaceView.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) {
                Choreographer.getInstance().postFrameCallback(this@FilamentStageRenderer)
            }

            override fun onViewDetachedFromWindow(v: android.view.View) {
                Choreographer.getInstance().removeFrameCallback(this@FilamentStageRenderer)
            }
        })
        Log.i(TAG, "FSPIKE setup ${SystemClock.elapsedRealtime() - start} ms, " +
            "${asset.entities.size} entities, ${asset.instance.animator.animationCount} animations, " +
            "lights ${lightsByName.keys}, camera ${camera != null}")
    }

    private fun readAsset(path: String): ByteBuffer {
        val bytes = context.assets.open(path).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { flip() }
    }

    /** glTF has no area lights: stand in a wide soft spot light at the same place and aim. */
    private fun addAreaLights() {
        for ((name, element) in tracks.getValue("lights").jsonObject) {
            val info = element.jsonObject
            if (info.getValue("type").jsonPrimitive.content != "AREA") continue
            val m = info.getValue("matrix").jsonArray.map { row -> row.jsonArray.map { it.jsonPrimitive.float } }
            val color = info.getValue("color").jsonArray.map { it.jsonPrimitive.float }
            // Blender is Z-up, glTF Y-up: (x, y, z) -> (x, z, -y). The light shines down its own -Z.
            val entity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.SPOT)
                .color(color[0], color[1], color[2])
                .intensityCandela(info.getValue("energy").jsonPrimitive.float * AREA_CANDELA_PER_WATT)
                .position(m[0][3], m[2][3], -m[1][3])
                .direction(-m[0][2], -m[2][2], m[1][2])
                .spotLightCone(0.4f * 1.22f, 1.22f)
                .falloff(20f)
                .castShadows(name.startsWith("Warm portrait"))
                .build(engine, entity)
            scene.addEntity(entity)
            lightsByName[name] = entity
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        Choreographer.getInstance().postFrameCallback(this)
        if (maxFps > 0 && frameTimeNanos - lastRenderNanos < 1_000_000_000L / maxFps - 4_000_000L) return
        val cpuStart = System.nanoTime()
        val time = (StageClock.positionMs() / 1000f).coerceIn(0f, (FRAMES - 1) / FPS)
        val animator = asset.instance.animator
        for (i in 0 until animator.animationCount) animator.applyAnimation(i, time)
        applyTracks((time * FPS).toInt().coerceIn(0, FRAMES - 1))
        val chain = swapChain
        if (uiHelper.isReadyToRender && chain != null && renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
        if (lastRenderNanos != 0L) intervals.add((frameTimeNanos - lastRenderNanos) / 1e6f)
        cpuTimes.add((System.nanoTime() - cpuStart) / 1e6f)
        lastRenderNanos = frameTimeNanos
        report()
    }

    /** One animated value per frame, already resolved to the light or materials it drives. */
    private class Track(val values: FloatArray, val light: Int, val perWatt: Float, val materials: List<MaterialInstance>)

    private val parsedTracks: List<Track> by lazy {
        val lightInfo = tracks.getValue("lights").jsonObject
        tracks.getValue("tracks").jsonObject.mapNotNull { (key, element) ->
            val parts = key.split("|")
            // Names can contain "|": the owner is everything between the kind and the last two fields.
            val owner = parts.subList(1, parts.size - 2).joinToString("|")
            val values = element.jsonArray.map { it.jsonPrimitive.float }.toFloatArray()
            when (parts[0]) {
                "light" -> lightsByName[owner]?.let { entity ->
                    val area = lightInfo.getValue(owner).jsonObject.getValue("type").jsonPrimitive.content == "AREA"
                    Track(values, entity, if (area) AREA_CANDELA_PER_WATT else SPOT_CANDELA_PER_WATT, emptyList())
                }
                "material" -> materialsByName[owner]?.let { Track(values, 0, 0f, it) }
                else -> null
            }
        }
    }

    private fun applyTracks(frame: Int) {
        val lights = engine.lightManager
        for (track in parsedTracks) {
            val value = track.values[frame]
            if (track.light != 0) lights.setIntensityCandela(lights.getInstance(track.light), value * track.perWatt)
            for (mi in track.materials) mi.setParameter("emissiveStrength", value)
        }
    }

    private fun report() {
        val now = SystemClock.elapsedRealtime()
        if (now < reportAt) return
        reportAt = now + 1_000
        if (intervals.isNotEmpty()) {
            val sorted = intervals.sorted()
            val cpu = cpuTimes.sorted()
            Log.i(TAG, "FSPIKE fps=${intervals.size} p50=%.1fms p99=%.1fms max=%.1fms cpu_p50=%.2fms cpu_max=%.2fms".format(
                sorted[sorted.size / 2], sorted[minOf(sorted.size - 1, (sorted.size * 0.99).toInt())], sorted.last(),
                cpu[cpu.size / 2], cpu.last()))
        }
        intervals.clear()
        cpuTimes.clear()
    }
}
