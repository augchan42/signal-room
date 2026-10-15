package dev.digitalrain.synthwave.feature.booking

import dev.digitalrain.synthwave.spike.GodotStageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.digitalrain.synthwave.core.audio.PlaybackState
import dev.digitalrain.synthwave.core.audio.PlaybackStatus
import dev.digitalrain.synthwave.core.audio.VideoState
import dev.digitalrain.synthwave.core.audio.VideoStatus
import dev.digitalrain.synthwave.core.data.AssetCatalog
import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.model.Phase
import dev.digitalrain.synthwave.core.model.RunSnapshot
import dev.digitalrain.synthwave.core.simulation.RunAction
import dev.digitalrain.synthwave.core.ui.SceneBackdrop

@Composable
fun BookingScreen(
    snapshot: RunSnapshot,
    content: BookingContent,
    catalog: AssetCatalog,
    playback: PlaybackState,
    visual: VideoState,
    videoPlayer: ExoPlayer,
    camPlayer: ExoPlayer,
    message: String?,
    recoveryRequired: Boolean,
    countIn: Int = 0,
    onAction: (RunAction) -> Unit,
    onReset: () -> Unit,
    onPlayPause: () -> Unit,
    onMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SceneBackdrop(
            assetPath = "demo/${catalog.controlRoom()}",
            contentDescription = "Signal Room control room",
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color(0xB808111D)))
        key(snapshot.phase, snapshot.attemptId) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                Modifier.weight(1f).testTag("booking-scroll").verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("SIGNAL ROOM / BOOKING 01 / ATTEMPT ${snapshot.attemptId}",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (recoveryRequired) {
                    PanelCard {
                        Text(message ?: "Save could not be read", color = MaterialTheme.colorScheme.error)
                        Button(onClick = onReset, modifier = Modifier.testTag("reset-demo")) { Text("Start new demo") }
                    }
                } else {
                    when (snapshot.phase) {
                        Phase.BRIEF -> IntroScreen(content, onAction)
                        Phase.DESK -> ChooseScreen(snapshot, content, catalog, onAction)
                        Phase.PREPARATION -> PrepareScreen(snapshot, content, onAction)
                        Phase.BROADCAST -> BroadcastScreen(snapshot, content, catalog, playback, visual,
                            videoPlayer, camPlayer, countIn, onAction, onReset, onPlayPause, onMute)
                        Phase.VERDICT, Phase.RECAP -> RewardScreen(snapshot, content, onAction, onReset)
                    }
                    if (message != null) PanelCard { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.height(24.dp))
            }
            if (!recoveryRequired && snapshot.phase == Phase.DESK) {
                ChooseBar(snapshot, content, playback, onAction, onPlayPause)
            }
            if (!recoveryRequired && snapshot.phase == Phase.PREPARATION) PrepareBar(snapshot, onAction)
        }
        }
        }
    }
}

@Composable
private fun BroadcastScreen(snapshot: RunSnapshot, content: BookingContent, catalog: AssetCatalog,
                            playback: PlaybackState, visual: VideoState, videoPlayer: ExoPlayer, camPlayer: ExoPlayer,
                            countIn: Int, onAction: (RunAction) -> Unit, onReset: () -> Unit,
                            onPlayPause: () -> Unit, onMute: () -> Unit) {
    Text("ON AIR", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.secondary)
    Text("${content.arrangements.getValue(snapshot.selection.arrangement).displayName} / " +
        "${content.members.getValue(snapshot.selection.member).displayName} / " +
        content.concepts.getValue(snapshot.selection.concept).displayName)
    Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).background(Color.Black).testTag("broadcast-stage")) {
        if (visual.status == VideoStatus.READY && playback.status == PlaybackStatus.PLAYING) {
            // Spike (throwaway): the live Godot stage in place of the performance video.
            GodotStageView(Modifier.fillMaxSize().testTag("godot-stage"))
        } else {
            SceneBackdrop("demo/${visual.stillPath ?: catalog.concept(snapshot.selection.concept).stillPath}",
                "Selected stage concept", Modifier.fillMaxSize())
        }
        if (visual.status == VideoStatus.FALLBACK) {
            Text("Still image: video unavailable", Modifier.align(Alignment.TopStart).background(Panel).padding(6.dp))
        }
        Text(timeLeft(playback.positionMs), Modifier.align(Alignment.TopEnd).background(Panel).padding(6.dp),
            fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(140.dp, 180.dp).background(Panel).clipToBounds()) {
            if (visual.status == VideoStatus.READY && !visual.loops && playback.status == PlaybackStatus.PLAYING) {
                MemberCam(camPlayer, snapshot.selection.member, Modifier.fillMaxSize().testTag("member-cam"))
            } else {
                SceneBackdrop("demo/${catalog.member(snapshot.selection.member).stillPath}",
                    "${content.members.getValue(snapshot.selection.member).displayName} fancam inset", Modifier.fillMaxSize())
            }
            Text("${content.members.getValue(snapshot.selection.member).displayName.uppercase()} CAM",
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Panel).padding(3.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        if (countIn > 0) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).testTag("count-in"),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$countIn", fontSize = 96.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary)
                    Text("ON AIR", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
    PanelCard {
        if (countIn == 0) Text("${timeLeft(playback.positionMs)} left • ${playback.status.name.lowercase()}",
            fontSize = 18.sp, fontWeight = FontWeight.Medium)
        if (playback.status == PlaybackStatus.ERROR) {
            Text("Audio ${playback.errorAssetId} failed. The booking cannot continue.",
                color = MaterialTheme.colorScheme.error)
            Text("The bundled media needs repair or reinstalling. Your saved run remains on this device.")
            OutlinedButton(onClick = onReset, modifier = Modifier.testTag("reset-demo")) { Text("Start new demo") }
        } else if (countIn == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlayPause, modifier = Modifier.testTag("broadcast-play-pause")) {
                    Text(if (playback.status == PlaybackStatus.PLAYING) "Pause" else "Resume")
                }
                OutlinedButton(onClick = onMute, modifier = Modifier.testTag("broadcast-mute")) {
                    Text(if (playback.muted) "Unmute" else "Mute")
                }
            }
        }
        if (snapshot.viewingCompleted) {
            OutlinedButton(onClick = { onAction(RunAction.SkipViewing) },
                modifier = Modifier.testTag("skip-performance")) { Text("Skip performance") }
        } else Text("First viewing is required before the verdict.")
    }
}
