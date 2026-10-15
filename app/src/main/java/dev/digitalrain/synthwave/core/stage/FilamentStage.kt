package dev.digitalrain.synthwave.core.stage

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val FRAME_RATE = 30
private const val FRAME_INTERVAL_NANOS = 1_000_000_000L / FRAME_RATE
private const val VSYNC_SLACK_NANOS = 4_000_000L

/**
 * The live stage, drawn by Filament. One thread, "stage-render", makes every Filament call; the
 * public methods post to it. Detaching a surface waits until that thread has destroyed its swap
 * chain, since the surface is gone once the view's destroy callback returns.
 */
class FilamentStage(
    context: Context,
    private val clock: StageClock,
    private val backend: StageBackend,
) : StageEngine, Choreographer.FrameCallback {
    private val assets = context.applicationContext.assets
    private val thread = HandlerThread("stage-render").apply { start() }
    private val handler = Handler(thread.looper)

    /** A surface the stage draws into: the broadcast SurfaceView or the member-cam TextureView. */
    private class Target(val main: Boolean) {
        var surface: Surface? = null
        var width = 0
        var height = 0
        var swapChain: SwapChain? = null
        var renderer: Renderer? = null
    }

    // Render thread only.
    private var stage: StageScene? = null
    private val mainTarget = Target(main = true)
    private val camTarget = Target(main = false)
    private var ticking = false
    private var lastFrameNanos = 0L
    private var lastTime = -1f
    private val stats = FrameStats()

    override suspend fun prepare(request: StageRequest): Boolean = suspendCancellableCoroutine { continuation ->
        val posted = handler.post {
            val drawn = runCatching { prepareOnRenderThread(request) }
                .onFailure { Log.e(STAGE_TAG, "Live stage failed to load", it) }.isSuccess
            if (continuation.isActive) continuation.resume(drawn)
        }
        if (!posted && continuation.isActive) continuation.resume(false)
    }

    private fun prepareOnRenderThread(request: StageRequest) {
        val start = SystemClock.elapsedRealtime()
        val scene = stage ?: StageScene(assets, backend, request.stagePath, request.infoPath).also { stage = it }
        if (scene.mixPath != request.motionPath) {
            val motion = MotionClip.parse(assets.open(request.motionPath).use { it.readBytes() })
            val tracks = StageTracks.parse(assets.open(request.tracksPath).bufferedReader().use { it.readText() })
            scene.useMix(request.motionPath, motion, tracks)
        }
        scene.useConcept(request.concept, request.screenPath)
        scene.useMember(request.member)
        // Draw both views once now (during the count-in), so Vulkan builds its pipelines before the show.
        scene.drawOffScreen(main = true, 540, 960, 0f, readBack = false)
        scene.drawOffScreen(main = false, 280, 360, 0f, readBack = false)
        for (target in listOf(mainTarget, camTarget)) if (target.surface != null) bind(target, scene)
        lastTime = -1f
        if (mainTarget.surface != null || camTarget.surface != null) startTicking()
        Log.i(STAGE_TAG, "Prepared ${request.motionPath} ${request.concept} in ${SystemClock.elapsedRealtime() - start} ms")
    }

    /** Test and look-check hook: one view at [time] seconds, as RGBA bytes (top row first on Vulkan). */
    suspend fun capture(main: Boolean, width: Int, height: Int, time: Float): ByteArray = suspendCancellableCoroutine { continuation ->
        handler.post {
            val scene = checkNotNull(stage) { "Prepare the stage first" }
            val pixels = scene.drawOffScreen(main, width, height, time, readBack = true)!!
            for (target in listOf(mainTarget, camTarget)) if (target.surface != null) bind(target, scene)
            lastTime = -1f
            if (continuation.isActive) continuation.resume(pixels)
        }
    }

    fun attachMain(surface: Surface, width: Int, height: Int) {
        handler.post { attach(mainTarget, surface, width, height) }
    }

    fun detachMain() = detachAndWait(mainTarget)

    fun attachCam(surface: Surface, width: Int, height: Int) {
        handler.post { attach(camTarget, surface, width, height) }
    }

    fun detachCam() = detachAndWait(camTarget)

    private fun attach(target: Target, surface: Surface, width: Int, height: Int) {
        if (target.surface !== surface) {
            releaseGpu(target)
            target.surface = surface
        }
        target.width = width
        target.height = height
        stage?.let { bind(target, it) }
        lastTime = -1f
        startTicking()
    }

    private fun bind(target: Target, scene: StageScene) {
        val surface = target.surface ?: return
        if (target.swapChain == null) target.swapChain = scene.engine.createSwapChain(surface)
        if (target.renderer == null) target.renderer = scene.engine.createRenderer().apply {
            clearOptions = clearOptions.apply { clearColor = doubleArrayOf(0.0, 0.0, 0.0, 1.0); clear = true }
        }
        scene.fit(target.main, target.width, target.height)
        if (target.main && Build.VERSION.SDK_INT >= 30) {
            surface.setFrameRate(FRAME_RATE.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        }
    }

    private fun detachAndWait(target: Target) {
        val done = CountDownLatch(1)
        val posted = handler.post {
            releaseGpu(target)
            target.surface = null
            if (mainTarget.surface == null && camTarget.surface == null) stopTicking()
            done.countDown()
        }
        if (posted) done.await()
    }

    private fun releaseGpu(target: Target) {
        val scene = stage
        if (scene != null) {
            target.swapChain?.let { scene.engine.destroySwapChain(it); scene.engine.flushAndWait() }
            target.renderer?.let { scene.engine.destroyRenderer(it) }
        }
        target.swapChain = null
        target.renderer = null
    }

    override fun unload() {
        handler.post { unloadOnRenderThread() }
    }

    private fun unloadOnRenderThread() {
        stopTicking()
        releaseGpu(mainTarget)
        releaseGpu(camTarget)
        stage?.destroy()
        stage = null
    }

    /** Frees everything and stops the render thread; the stage can't be used afterwards. */
    fun release() {
        handler.post { unloadOnRenderThread() }
        thread.quitSafely()
    }

    private fun startTicking() {
        if (ticking) return
        ticking = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun stopTicking() {
        ticking = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!ticking) return
        Choreographer.getInstance().postFrameCallback(this)
        val scene = stage ?: return
        if (frameTimeNanos - lastFrameNanos < FRAME_INTERVAL_NANOS - VSYNC_SLACK_NANOS) return
        val time = (clock.positionMs() / 1000f).coerceIn(0f, scene.duration)
        if (time == lastTime) return // paused: the last frame stays on screen
        val start = System.nanoTime()
        scene.pose(time)
        val drawn = draw(mainTarget, scene.mainView, frameTimeNanos)
        draw(camTarget, scene.camView, frameTimeNanos)
        if (drawn) {
            stats.add(frameTimeNanos, lastFrameNanos, System.nanoTime() - start)
            lastTime = time
            lastFrameNanos = frameTimeNanos
        }
    }

    private fun draw(target: Target, view: View, frameTimeNanos: Long): Boolean {
        val chain = target.swapChain ?: return false
        val renderer = target.renderer ?: return false
        if (!renderer.beginFrame(chain, frameTimeNanos)) return false
        renderer.render(view)
        renderer.endFrame()
        return true
    }

    /** Frame interval and render-thread time, logged every 10 s for the device check. */
    private class FrameStats {
        private val intervals = ArrayList<Float>()
        private val work = ArrayList<Float>()
        private var reportAt = 0L

        fun add(frameNanos: Long, previousNanos: Long, workNanos: Long) {
            if (previousNanos != 0L) intervals += (frameNanos - previousNanos) / 1e6f
            work += workNanos / 1e6f
            val now = SystemClock.elapsedRealtime()
            if (now < reportAt) return
            reportAt = now + 10_000
            if (intervals.isNotEmpty()) {
                val i = intervals.sorted()
                val w = work.sorted()
                Log.i(STAGE_TAG, "frames=%d interval p50=%.1f p99=%.1f ms work p50=%.2f max=%.2f ms".format(
                    i.size, i[i.size / 2], i[minOf(i.size - 1, (i.size * 0.99).toInt())], w[w.size / 2], w.last()))
            }
            intervals.clear()
            work.clear()
        }
    }
}
