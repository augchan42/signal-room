package dev.digitalrain.synthwave.core.ui

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.View
import android.view.Window

const val LAG_TAG = "SignalRoomLag"
private const val BEAT_MS = 100L
private const val STALL_MS = 700L
private const val NO_FRAME_MS = 1_000L
private const val SLOW_FRAME_MS = 250L
private const val TAP_WINDOW_MS = 2_000L // a state change later than this is not put down to the tap

/**
 * Logs, in release builds too, what makes a tap look ignored, under the tag SignalRoomLag:
 * - each tap, when the screen's state changed after it, and when the first frame of the new
 *   state was drawn and sent to the display. Short times here with a screen that still looked
 *   stuck point past the app, at the compositor.
 * - a state change that has drawn no frame after 1 s.
 * - the main thread not answering for 700 ms, with its stack.
 * - frames that took 250 ms or more, with where the time went.
 */
object LagMonitor {
    private val main = Handler(Looper.getMainLooper())
    private var worker: Handler? = null
    private var decor: View? = null // main thread only

    @Volatile private var lastBeat = SystemClock.uptimeMillis()
    private var stallStart = 0L // worker thread only

    @Volatile private var tapName: String? = null
    @Volatile private var tapNanos = 0L
    @Volatile private var changeName: String? = null
    @Volatile private var changeNanos = 0L
    private var changeWarned = false // worker thread only

    fun install(window: Window) {
        if (worker != null) return
        val thread = HandlerThread("lag-monitor").apply { start() }
        val handler = Handler(thread.looper)
        worker = handler
        // Frame metrics sometimes stop arriving for seconds while frames are drawn, so they only
        // report slow frames; the draw listener finds the first frame of a new state.
        window.addOnFrameMetricsAvailableListener({ _, metrics, _ -> onFrame(metrics) }, handler)
        decor = window.decorView
        window.decorView.viewTreeObserver.addOnDrawListener { onDraw() }
        handler.post(object : Runnable {
            override fun run() {
                check()
                main.post { lastBeat = SystemClock.uptimeMillis() }
                handler.postDelayed(this, BEAT_MS)
            }
        })
    }

    /** A tap that should change the screen. Main thread. */
    fun tap(name: String) {
        tapName = name
        tapNanos = System.nanoTime()
        Log.i(LAG_TAG, "tap $name")
    }

    /** The screen's state changed; the next frame drawn shows it. Main thread. */
    fun stateChanged(name: String) {
        val now = System.nanoTime()
        if (now - tapNanos > TAP_WINDOW_MS * 1_000_000) tapName = null
        val tap = tapName
        val afterTap = if (tap != null) " %.0f ms after tap $tap".format((now - tapNanos) / 1e6) else ""
        Log.i(LAG_TAG, "state $name$afterTap")
        changeName = name
        changeNanos = now
        worker?.post { changeWarned = false }
        // Registered before the frame draws, so it belongs to the frame that shows this state.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            decor?.viewTreeObserver?.registerFrameCommitCallback {
                if (changeNanos == now) Log.i(LAG_TAG, "frame for $name sent to the display ${since(System.nanoTime())}")
            }
        }
    }

    private fun onFrame(metrics: FrameMetrics) {
        val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        if (total >= SLOW_FRAME_MS * 1_000_000) {
            fun ms(id: Int) = "%.0f".format(metrics.getMetric(id) / 1e6)
            Log.w(LAG_TAG, "slow frame ${ms(FrameMetrics.TOTAL_DURATION)} ms: input ${ms(FrameMetrics.INPUT_HANDLING_DURATION)}" +
                " animation ${ms(FrameMetrics.ANIMATION_DURATION)} layout ${ms(FrameMetrics.LAYOUT_MEASURE_DURATION)}" +
                " draw ${ms(FrameMetrics.DRAW_DURATION)} sync ${ms(FrameMetrics.SYNC_DURATION)}" +
                " gpu-commands ${ms(FrameMetrics.COMMAND_ISSUE_DURATION)} swap ${ms(FrameMetrics.SWAP_BUFFERS_DURATION)}" +
                " gpu ${ms(FrameMetrics.GPU_DURATION)} unknown-delay ${ms(FrameMetrics.UNKNOWN_DELAY_DURATION)}")
        }
    }

    /** Main thread, while the window draws. */
    private fun onDraw() {
        val change = changeName ?: return
        changeName = null
        Log.i(LAG_TAG, "frame for $change drawn ${since(System.nanoTime())}")
    }

    private fun since(at: Long): String {
        val tap = tapName?.takeIf { tapNanos <= changeNanos }
        return "%.0f ms after the change%s".format((at - changeNanos) / 1e6,
            if (tap != null) ", %.0f ms after tap $tap".format((at - tapNanos) / 1e6) else "")
    }

    private fun check() {
        val now = SystemClock.uptimeMillis()
        val late = now - lastBeat
        if (late >= STALL_MS && stallStart == 0L) {
            stallStart = lastBeat
            val stack = Looper.getMainLooper().thread.stackTrace.take(30).joinToString("\n  at ")
            Log.w(LAG_TAG, "main thread not answering for $late ms; it is at\n  at $stack")
        } else if (late < STALL_MS && stallStart != 0L) {
            Log.w(LAG_TAG, "main thread answered again after ${lastBeat - stallStart} ms")
            stallStart = 0L
        }
        val change = changeName
        if (change != null && !changeWarned && System.nanoTime() - changeNanos >= NO_FRAME_MS * 1_000_000) {
            changeWarned = true
            Log.w(LAG_TAG, "state $change has drawn no frame after $NO_FRAME_MS ms")
        }
    }
}
