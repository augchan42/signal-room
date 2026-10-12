package dev.digitalrain.synthwave.debug

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.digitalrain.synthwave.core.audio.BroadcastClock
import dev.digitalrain.synthwave.core.audio.Media3AudioEngine
import dev.digitalrain.synthwave.core.audio.PlaybackStatus
import dev.digitalrain.synthwave.core.audio.PreviewController
import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.AudioAsset
import dev.digitalrain.synthwave.core.model.MemberId
import kotlinx.coroutines.launch

/** Local diagnostics only. The release variant does not include this screen or its media. */
class SyncFixtureActivity : ComponentActivity() {
    private lateinit var audio: PreviewController
    private lateinit var video: ExoPlayer
    private lateinit var playerView: PlayerView
    private var calibration: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val fixture = AudioAsset("sync-click", "sync/click.m4a", ArrangementId.SPARSE,
        MemberId.FRONTMAN, "debug-fixture")
    private val tick = object : Runnable {
        override fun run() {
            audio.refresh()
            val state = audio.state.value
            if (state.status == PlaybackStatus.PLAYING) {
                val expected = BroadcastClock.visualPosition(state.positionMs)
                if (BroadcastClock.needsCorrection(state.positionMs, video.currentPosition)) video.seekTo(expected)
                video.play()
            } else video.pause()
            handler.postDelayed(this, 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audio = PreviewController(Media3AudioEngine(this))
        video = ExoPlayer.Builder(this).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri("asset:///demo/sync/flash.mp4"))
            prepare()
        }
        val root = FrameLayout(this)
        playerView = PlayerView(this).apply {
            useController = false
            player = video
        }
        root.addView(playerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(label: String, onClick: () -> Unit) {
            buttons.addView(Button(this).apply {
                text = label
                setOnClickListener { onClick() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        button("START") { lifecycleScope.launch { audio.startBroadcast(fixture) } }
        button("SEEK 0") { audio.replay(); video.seekTo(0) }
        button("PAUSE / RESUME") {
            if (audio.state.value.status == PlaybackStatus.PLAYING) audio.pause() else audio.play()
        }
        button("CAL") {
            audio.pause()
            video.pause()
            calibration?.release()
            calibration = ExoPlayer.Builder(this).build().apply {
                setMediaItem(MediaItem.fromUri("asset:///demo/sync/calibration.mp4"))
                prepare()
                play()
            }
            playerView.player = calibration
        }
        root.addView(buttons, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            bottomMargin = (96 * resources.displayMetrics.density).toInt()
        })
        setContentView(root)
        handler.post(tick)
    }

    override fun onStop() {
        super.onStop()
        audio.onBackground()
        video.pause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        audio.close()
        video.release()
        calibration?.release()
        super.onDestroy()
    }
}
