package dev.digitalrain.synthwave.core.stage

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.Log
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
import com.google.android.filament.SwapChainFlags
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max

internal const val STAGE_TAG = "LiveStage"
// Blender watts to candela, chosen by eye against the rendered videos.
private const val AREA_CANDELA_PER_WATT = 0.15f
private const val SPOT_CANDELA_PER_WATT = 0.22f
private const val SCREEN_MATERIAL = "REAR SCREEN | replaceable feed"

/** Picture settings: the spike's, kept until the device check (Task 7) shows a cheaper set looks the same. */
internal object StageQuality {
    const val MSAA = true
    const val BLOOM = true
    const val SHADOW_MAP_SIZE = 1024
}

private object FilamentLibraries {
    val ready: Unit by lazy {
        Filament.init()
        Gltfio.init()
    }
}

/**
 * The stage's Filament objects: engine, scene, the broadcast and member-cam views, and the current
 * mix and concept. Only the render thread in [FilamentStage] touches it.
 */
internal class StageScene(private val assets: AssetManager, backend: StageBackend, stagePath: String, infoPath: String) {
    init {
        FilamentLibraries.ready // before any property below creates a Filament object
    }

    val engine: Engine
    val mainView: View
    val camView: View
    private val scene: com.google.android.filament.Scene
    private val provider: UbershaderProvider
    private val loader: AssetLoader
    private val asset: FilamentAsset
    private val info: StageInfo
    private val indirectLight: IndirectLight
    private val colorGrading: ColorGrading
    private val restPose = HashMap<Int, FloatArray>() // entity -> local transform as exported
    private val lights = HashMap<String, Int>() // light name -> entity
    private val areaLights = HashSet<String>()
    private val materials = HashMap<String, MutableList<MaterialInstance>>()
    private val screens = HashMap<String, Texture>()
    private val sampler = TextureSampler(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR,
        TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE)
    private val broadcastCamera: Camera
    private var memberCamera: Camera? = null
    private var memberCameraName: String? = null

    private class MovingNode(val instance: Int, val motion: NodeMotion)
    private class LightLevel(val instance: Int, val name: String, val watts: FloatArray, var scale: Float)
    private class Emission(val materials: List<MaterialInstance>, val strength: FloatArray)

    private var clip: MotionClip? = null
    private var moving = emptyList<MovingNode>()
    private var levels = emptyList<LightLevel>()
    private var emissions = emptyList<Emission>()
    var mixPath: String? = null
        private set
    private val matrix = FloatArray(16)
    private val trs = FloatArray(10)

    val duration: Float get() = clip?.duration ?: 0f

    init {
        engine = createEngine(backend)
        scene = engine.createScene()
        colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.Filmic()).build(engine)
        mainView = engine.createView().apply {
            scene = this@StageScene.scene
            colorGrading = this@StageScene.colorGrading
            bloomOptions = bloomOptions.apply { enabled = StageQuality.BLOOM; strength = 0.12f }
            multiSampleAntiAliasingOptions = multiSampleAntiAliasingOptions.apply {
                enabled = StageQuality.MSAA; sampleCount = 2
            }
        }
        // The inset is small: no shadows, bloom or MSAA.
        camView = engine.createView().apply {
            scene = this@StageScene.scene
            colorGrading = this@StageScene.colorGrading
            setShadowingEnabled(false)
        }
        indirectLight = IndirectLight.Builder().irradiance(1, floatArrayOf(0.03f, 0.036f, 0.054f)).intensity(1f).build(engine)
        scene.indirectLight = indirectLight
        provider = UbershaderProvider(engine)
        loader = AssetLoader(engine, provider, EntityManager.get())
        asset = requireNotNull(loader.createAsset(readBuffer(stagePath))) { "Stage file did not load" }
        ResourceLoader(engine).apply { loadResources(asset); destroy() }
        asset.releaseSourceData()
        scene.addEntities(asset.entities)
        info = StageInfo.parse(readText(infoPath))
        val transforms = engine.transformManager
        for (entity in asset.entities) {
            val instance = transforms.getInstance(entity)
            if (instance != 0) restPose[entity] = transforms.getTransform(instance, FloatArray(16))
        }
        for (entity in asset.lightEntities) asset.getName(entity)?.let { lights[it] = entity }
        for (mi in asset.instance.materialInstances) materials.getOrPut(mi.name) { mutableListOf() }.add(mi)
        for (area in info.areaLights) addAreaLight(area)
        broadcastCamera = camera(BROADCAST_CAMERA)
        mainView.camera = broadcastCamera
    }

    private fun createEngine(backend: StageBackend): Engine {
        if (backend == StageBackend.VULKAN) {
            runCatching { return Engine.create(Engine.Backend.VULKAN) }
                .onFailure { Log.w(STAGE_TAG, "Vulkan did not start; using OpenGL ES", it) }
        }
        return Engine.create(Engine.Backend.OPENGL)
    }

    private fun readBuffer(path: String): ByteBuffer {
        val bytes = assets.open(path).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { flip() }
    }

    private fun readText(path: String): String = assets.open(path).bufferedReader().use { it.readText() }

    private fun camera(name: String): Camera {
        val entity = asset.cameraEntities.firstOrNull { asset.getName(it) == name } ?: error("No camera $name in the stage")
        return requireNotNull(engine.getCameraComponent(entity)) { "$name has no camera" }.apply {
            setExposure(1f, 1.2f, 100f) // exposure 1: light values reach the screen unscaled
        }
    }

    /** glTF has no area lights: a wide soft spot light at the same place and aim stands in. */
    private fun addAreaLight(area: AreaLight) {
        val entity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.SPOT)
            .color(area.color[0], area.color[1], area.color[2])
            .intensityCandela(area.watts * AREA_CANDELA_PER_WATT)
            .position(area.position[0], area.position[1], area.position[2])
            .direction(area.direction[0], area.direction[1], area.direction[2])
            .spotLightCone(0.4f * 1.22f, 1.22f)
            .falloff(20f)
            .castShadows(area.name.startsWith("Warm portrait"))
            .shadowOptions(LightManager.ShadowOptions().apply { mapSize = StageQuality.SHADOW_MAP_SIZE })
            .build(engine, entity)
        scene.addEntity(entity)
        lights[area.name] = entity
        areaLights += area.name
    }

    /** Puts every node back where the stage file has it, then applies [motion] and [tracks]. */
    fun useMix(path: String, motion: MotionClip, tracks: StageTracks) {
        val transforms = engine.transformManager
        for ((entity, local) in restPose) transforms.setTransform(transforms.getInstance(entity), local)
        val moved = ArrayList<MovingNode>()
        for (node in motion.nodes) {
            val entity = asset.getFirstEntityByName(node.name)
            require(entity != 0) { "Motion names ${node.name}, which the stage does not have" }
            val instance = transforms.getInstance(entity)
            node.matrixAt(0f, motion.fps, motion.frames, matrix, trs)
            transforms.setTransform(instance, matrix)
            if (node.channel != NodeMotion.NONE) moved += MovingNode(instance, node)
        }
        val lightManager = engine.lightManager
        levels = tracks.lights.mapNotNull { track ->
            lights[track.light]?.let { entity ->
                val perWatt = if (track.light in areaLights) AREA_CANDELA_PER_WATT else SPOT_CANDELA_PER_WATT
                LightLevel(lightManager.getInstance(entity), track.light, track.watts.toFloatArray(), perWatt)
            } ?: null.also { Log.w(STAGE_TAG, "No light ${track.light} in the stage") }
        }
        emissions = tracks.emissive.mapNotNull { track ->
            materials[track.material]?.let { instances ->
                for (mi in instances) mi.setParameter("emissiveFactor", track.color[0], track.color[1], track.color[2])
                Emission(instances, track.strength.toFloatArray())
            }
        }
        moving = moved
        clip = motion
        mixPath = path
    }

    /** Suit colours, light colours and levels, and the rear-screen image of [concept]. */
    fun useConcept(concept: ConceptId, screenPath: String) {
        val look = info.concepts.getValue(concept)
        look.suits.forEachIndexed { index, color ->
            materials["SUIT $index"]?.forEach { it.setParameter("baseColorFactor", color[0], color[1], color[2], 1f) }
        }
        val lightManager = engine.lightManager
        for ((name, light) in look.lights) {
            lights[name]?.let { lightManager.setColor(lightManager.getInstance(it), light.color[0], light.color[1], light.color[2]) }
        }
        for (level in levels) {
            val perWatt = if (level.name in areaLights) AREA_CANDELA_PER_WATT else SPOT_CANDELA_PER_WATT
            level.scale = perWatt * (look.lights[level.name]?.factor ?: 1f)
        }
        val screen = screens.getOrPut(screenPath) { loadTexture(screenPath) }
        materials[SCREEN_MATERIAL]?.forEach { it.setParameter("emissiveMap", screen, sampler) }
    }

    private fun loadTexture(path: String): Texture {
        val bitmap = assets.open(path).use { BitmapFactory.decodeStream(it) }
        val levels = floor(log2(max(bitmap.width, bitmap.height).toFloat())).toInt() + 1
        val texture = Texture.Builder().width(bitmap.width).height(bitmap.height).levels(levels)
            .sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.SRGB8_A8)
            .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE).build(engine)
        TextureHelper.setBitmap(engine, texture, 0, bitmap)
        texture.generateMipmaps(engine)
        return texture
    }

    fun useMember(member: MemberId) {
        val name = fancamName(member)
        memberCamera = camera(name)
        memberCameraName = name
        camView.camera = memberCamera
    }

    /** Poses the band and sets the lights for [time] seconds into the show. */
    fun pose(time: Float) {
        val motion = clip ?: return
        val transforms = engine.transformManager
        for (node in moving) {
            node.motion.matrixAt(time, motion.fps, motion.frames, matrix, trs)
            transforms.setTransform(node.instance, matrix)
        }
        val lightManager = engine.lightManager
        for (level in levels) lightManager.setIntensityCandela(level.instance, sampleTrack(level.watts, time, motion.fps) * level.scale)
        for (emission in emissions) {
            val strength = sampleTrack(emission.strength, time, motion.fps)
            for (mi in emission.materials) mi.setParameter("emissiveStrength", strength)
        }
    }

    /** Sizes [view]'s viewport and its orthographic camera to [width] x [height], keeping the camera's height. */
    fun fit(main: Boolean, width: Int, height: Int) {
        val view = if (main) mainView else camView
        val name = if (main) BROADCAST_CAMERA else memberCameraName ?: return
        val camera = if (main) broadcastCamera else memberCamera ?: return
        view.viewport = Viewport(0, 0, width, height)
        val halfHeight = info.cameras.getValue(name).ymag.toDouble()
        val halfWidth = halfHeight * width / height
        camera.setProjection(Camera.Projection.ORTHO, -halfWidth, halfWidth, -halfHeight, halfHeight, 0.1, 1000.0)
    }

    /**
     * Draws one view at [time] into an off-screen swap chain. With [readBack], returns its RGBA pixels
     * (top row first on Vulkan); either way Vulkan has built the pipelines the view needs.
     */
    fun drawOffScreen(main: Boolean, width: Int, height: Int, time: Float, readBack: Boolean): ByteArray? {
        pose(time)
        fit(main, width, height)
        val renderer = engine.createRenderer()
        val chain = engine.createSwapChain(width, height, SwapChainFlags.CONFIG_READABLE)
        val pixels = if (readBack) ByteBuffer.allocateDirect(width * height * 4) else null
        try {
            var drawn = false
            for (attempt in 0 until 3) {
                if (renderer.beginFrame(chain, 0)) {
                    renderer.render(if (main) mainView else camView)
                    if (pixels != null) renderer.readPixels(0, 0, width, height,
                        Texture.PixelBufferDescriptor(pixels, Texture.Format.RGBA, Texture.Type.UBYTE, 1, 0, 0, width, null, null))
                    renderer.endFrame()
                    drawn = true
                    break
                }
                engine.flushAndWait()
            }
            engine.flushAndWait()
            check(drawn) { "The renderer skipped every off-screen frame" }
        } finally {
            engine.destroySwapChain(chain)
            engine.destroyRenderer(renderer)
            engine.flushAndWait()
        }
        return pixels?.let { ByteArray(it.capacity()).also { bytes -> it.rewind(); it.get(bytes) } }
    }

    fun destroy() {
        for (texture in screens.values) engine.destroyTexture(texture)
        for (name in areaLights) lights[name]?.let { engine.destroyEntity(it); EntityManager.get().destroy(it) }
        scene.removeEntities(asset.entities)
        loader.destroyAsset(asset)
        loader.destroy()
        provider.destroyMaterials()
        provider.destroy()
        engine.destroyIndirectLight(indirectLight)
        engine.destroyColorGrading(colorGrading)
        engine.destroyView(mainView)
        engine.destroyView(camView)
        engine.destroyScene(scene)
        engine.destroy()
    }
}
