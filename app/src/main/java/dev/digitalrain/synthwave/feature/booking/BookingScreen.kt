package dev.digitalrain.synthwave.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.model.BookingResult
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.Dimension
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Phase
import dev.digitalrain.synthwave.core.model.PrepAction
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.model.RunSnapshot
import dev.digitalrain.synthwave.core.simulation.RunAction
import dev.digitalrain.synthwave.core.ui.SceneBackdrop

private val Panel = Color(0xF5081422)

@Composable
fun BookingScreen(
    snapshot: RunSnapshot,
    content: BookingContent,
    catalog: AssetCatalog,
    playback: PlaybackState,
    visual: VideoState,
    videoPlayer: ExoPlayer,
    message: String?,
    recoveryRequired: Boolean,
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
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
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
                    Phase.BRIEF -> Brief(content, onAction)
                    Phase.DESK -> Desk(snapshot, content, catalog, playback, onAction, onPlayPause)
                    Phase.PREPARATION -> PreparationScreen(snapshot, content, onAction)
                    Phase.BROADCAST -> BroadcastScreen(snapshot, content, catalog, playback, visual,
                        videoPlayer, onAction, onReset, onPlayPause, onMute)
                    Phase.VERDICT -> Verdict(snapshot, content, onAction)
                    Phase.RECAP -> Recap(snapshot, content, onAction, onReset)
                }
                if (message != null) PanelCard { Text(message, color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(24.dp))
        }
        }
        }
    }
}

@Composable
private fun Brief(content: BookingContent, onAction: (RunAction) -> Unit) {
    Text(content.title, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
    PanelCard {
        Text("23:40 / late-night cable slot", color = MaterialTheme.colorScheme.primary)
        Text("Instrumental special stage", style = MaterialTheme.typography.titleLarge)
        content.brief.forEach { Text(it) }
        Text("Invitation target: ${displayScore(content.balance.invitationThreshold)}")
        Text("Three preparation slots. Your audience response carries into the recap.")
    }
    Button(onClick = { onAction(RunAction.OpenDesk) }, modifier = Modifier.testTag("open-desk")) {
        Text("Open producer desk")
    }
}

@Composable
private fun Desk(
    snapshot: RunSnapshot,
    content: BookingContent,
    catalog: AssetCatalog,
    playback: PlaybackState,
    onAction: (RunAction) -> Unit,
    onPlayPause: () -> Unit,
) {
    Text("Producer desk", style = MaterialTheme.typography.headlineLarge)
    Text("Choose a mix, featured member, and stage concept. All 12 mixes are available offline.")
    PanelCard {
        Text("ARRANGEMENT", color = MaterialTheme.colorScheme.primary)
        ArrangementId.entries.forEach { id ->
            val rules = content.arrangements.getValue(id)
            Choice("${rules.displayName}${if (snapshot.selection.arrangement == id) " • selected" else ""}",
                "arrangement-${id.name.lowercase()}", onClick = {
                    onAction(RunAction.ChooseArrangement(id))
                })
            Text(rules.riskDescription, style = MaterialTheme.typography.bodySmall)
        }
    }
    PanelCard {
        Text("FEATURED MEMBER", color = MaterialTheme.colorScheme.primary)
        MemberId.entries.forEach { id ->
            val member = content.members.getValue(id)
            Choice("${member.displayName}${if (snapshot.selection.member == id) " • selected" else ""}",
                "member-${id.name.lowercase()}", onClick = {
                    onAction(RunAction.ChooseMember(id))
                })
            Text("${member.strength} Fatigue ${member.fatigue}/100.", style = MaterialTheme.typography.bodySmall)
        }
    }
    PanelCard {
        Text("STAGE CONCEPT", color = MaterialTheme.colorScheme.primary)
        ConceptId.entries.forEach { id ->
            val path = catalog.concept(id).stillPath
            SceneBackdrop("demo/$path", "${content.concepts.getValue(id).displayName} preview",
                Modifier.fillMaxWidth().height(160.dp))
            Choice("${content.concepts.getValue(id).displayName}${if (snapshot.selection.concept == id) " • selected" else ""}",
                "concept-${id.name.lowercase()}", onClick = {
                    onAction(RunAction.ChooseConcept(id))
                })
        }
    }
    PanelCard {
        Text("NOW AUDITIONING", color = MaterialTheme.colorScheme.primary)
        Text("${content.arrangements.getValue(snapshot.selection.arrangement).displayName} / " +
            content.members.getValue(snapshot.selection.member).displayName)
        val key = "${snapshot.selection.arrangement.name.lowercase()}-${snapshot.selection.member.name.lowercase()}"
        Text(content.mixDescriptions.getValue(key))
        Text("${playback.positionMs / 1_000}s / 20s • ${playback.status.name.lowercase()}")
        if (playback.status == PlaybackStatus.ERROR) Text("Could not play ${playback.errorAssetId}. Check the bundled audio.")
        Button(onClick = onPlayPause, modifier = Modifier.testTag("preview-play-pause")) {
            Text(if (playback.status == PlaybackStatus.PLAYING) "Pause mix" else "Play mix")
        }
        Text("Arrangement risk is public. The audience report is revealed by Research.",
            style = MaterialTheme.typography.bodySmall)
    }
    Button(onClick = { onAction(RunAction.OpenPreparation) }, modifier = Modifier.testTag("open-preparation")) {
        Text("Plan preparation")
    }
}

@Composable
private fun PreparationScreen(snapshot: RunSnapshot, content: BookingContent, onAction: (RunAction) -> Unit) {
    val prep = snapshot.preparation
    Text("Preparation", style = MaterialTheme.typography.headlineLarge)
    OutlinedButton(onClick = { onAction(RunAction.ReturnToDesk) }, modifier = Modifier.testTag("return-desk")) {
        Text("Edit mix or stage")
    }
    PanelCard {
        Text("${3 - prep.used} preparation slots remaining", style = MaterialTheme.typography.titleLarge)
        Text("Rehearsal improves execution but adds fatigue; later rehearsals help less.")
        PrepControl("Rehearse", "prep-rehearse", prep.rehearsals, prep, PrepAction.REHEARSE, onAction)
        Text("Refinement improves coherence and layered coordination; later passes help less.")
        PrepControl("Refine", "prep-refine", prep.refinements, prep, PrepAction.REFINE, onAction)
        Text("Rest reduces featured-member fatigue.")
        PrepControl("Rest", "prep-rest", prep.rests, prep, PrepAction.REST, onAction)
    }
    PanelCard {
        Text("AUDIENCE RESEARCH", color = MaterialTheme.colorScheme.primary)
        if (prep.researched) {
            Text("Audience report", style = MaterialTheme.typography.titleLarge)
            content.researchReport.forEach { Text(it) }
            Text("Research spent one slot. It cannot be removed.")
        } else {
            Text("Reveal audience preferences and show risks. Research immediately commits one slot.")
            Button(onClick = { onAction(RunAction.PurchaseResearch) },
                modifier = Modifier.testTag("research-open")) { Text("Spend a slot on Research") }
            if (prep.used == 3) Text("Free one slot first to buy Research.")
        }
    }
    PanelCard {
        Text("BROADCAST CONFIRMATION", color = MaterialTheme.colorScheme.primary)
        Text("${content.arrangements.getValue(snapshot.selection.arrangement).displayName} / " +
            "${content.members.getValue(snapshot.selection.member).displayName} / " +
            content.concepts.getValue(snapshot.selection.concept).displayName)
        Text("Plan: ${prep.rehearsals} rehearsal, ${prep.refinements} refinement, ${prep.rests} rest" +
            if (prep.researched) ", Research" else "")
        Text("First viewing runs the full 20 seconds. The result is saved on confirmation.")
        Button(onClick = { onAction(RunAction.ConfirmBroadcast) }, enabled = prep.used == 3,
            modifier = Modifier.testTag("confirm-broadcast")) { Text("Confirm broadcast") }
    }
}

@Composable
private fun PrepControl(label: String, tag: String, count: Int, prep: Preparation,
                        kind: PrepAction,
                        onAction: (RunAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { onAction(RunAction.AdjustPreparation(kind, 1)) }, enabled = prep.used < 3,
            modifier = Modifier.testTag(tag)) { Text("Add $label") }
        Text("×$count")
        OutlinedButton(onClick = { onAction(RunAction.AdjustPreparation(kind, -1)) }, enabled = count > 0,
            modifier = Modifier.testTag("remove-${tag.removePrefix("prep-")}")) { Text("−") }
    }
}

@Composable
private fun BroadcastScreen(snapshot: RunSnapshot, content: BookingContent, catalog: AssetCatalog,
                            playback: PlaybackState, visual: VideoState, videoPlayer: ExoPlayer,
                            onAction: (RunAction) -> Unit, onReset: () -> Unit,
                            onPlayPause: () -> Unit, onMute: () -> Unit) {
    Text("ON AIR", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.secondary)
    Text("${content.arrangements.getValue(snapshot.selection.arrangement).displayName} / " +
        "${content.members.getValue(snapshot.selection.member).displayName} / " +
        content.concepts.getValue(snapshot.selection.concept).displayName)
    Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).background(Color.Black).testTag("broadcast-stage")) {
        if (visual.status == VideoStatus.READY) {
            AndroidView(factory = { context -> PlayerView(context).apply {
                useController = false
                player = videoPlayer
            } }, update = { it.player = videoPlayer }, modifier = Modifier.fillMaxSize())
        } else {
            SceneBackdrop("demo/${visual.stillPath ?: catalog.concept(snapshot.selection.concept).stillPath}",
                "Selected stage concept", Modifier.fillMaxSize())
        }
        if (visual.status == VideoStatus.FALLBACK) {
            Text("Still image: video unavailable", Modifier.align(Alignment.TopStart).background(Panel).padding(6.dp))
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(84.dp, 112.dp).background(Panel)) {
            SceneBackdrop("demo/${catalog.member(snapshot.selection.member).stillPath}",
                "${content.members.getValue(snapshot.selection.member).displayName} fancam inset", Modifier.fillMaxSize())
        }
    }
    PanelCard {
        Text("${playback.positionMs / 1_000}s / 20s • ${playback.status.name.lowercase()}")
        if (playback.status == PlaybackStatus.ERROR) {
            Text("Audio ${playback.errorAssetId} failed. The booking cannot continue.",
                color = MaterialTheme.colorScheme.error)
            Text("The bundled media needs repair or reinstalling. Your saved run remains on this device.")
            OutlinedButton(onClick = onReset, modifier = Modifier.testTag("reset-demo")) { Text("Start new demo") }
        } else {
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

@Composable
private fun Verdict(snapshot: RunSnapshot, content: BookingContent, onAction: (RunAction) -> Unit) {
    val result = requireNotNull(snapshot.result)
    Text(if (result.invited) "INVITED BACK" else "NOT INVITED", style = MaterialTheme.typography.headlineLarge,
        color = if (result.invited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
    PanelCard {
        Text("${displayScore(result.invitationScore)} / 100", style = MaterialTheme.typography.displayMedium)
        Text("Needed ${displayScore(content.balance.invitationThreshold)}")
        DimensionRows("Coherence", result.coherence, content.balance.weights[0])
        DimensionRows("Execution", result.execution, content.balance.weights[1])
        DimensionRows("Brief fit", result.briefFit, content.balance.weights[2])
    }
    PanelCard {
        Text("WHY IT LANDED THIS WAY", color = MaterialTheme.colorScheme.primary)
        result.coherence.contributions.filter { it.id != "concept-base" }.sortedByDescending { kotlin.math.abs(it.points) }
            .take(2).forEach { Text("${it.explanation}: ${signed(it.points)}") }
        result.execution.contributions.filter { it.id != "arrangement-base" }.sortedByDescending { kotlin.math.abs(it.points) }
            .take(2).forEach { Text("${it.explanation}: ${signed(it.points)}") }
    }
    PanelCard {
        Text("FIVE AUDIENCES", color = MaterialTheme.colorScheme.primary)
        content.segments.forEach { (id, segment) ->
            val after = result.audienceAfter.getValue(id)
            Text("${segment.displayName}: awareness ${signed(after.awareness - segment.initial.awareness)}, " +
                "affinity ${signed(after.affinity - segment.initial.affinity)}")
        }
        Text("Audience gains are separate from the invitation score.")
    }
    Button(onClick = { onAction(RunAction.OpenRecap) }, modifier = Modifier.testTag("open-recap")) {
        Text("Open recap")
    }
}

@Composable
private fun DimensionRows(label: String, dimension: Dimension, weight: Double) {
    Text("$label ${displayScore(dimension.value)} × ${displayScore(weight)} = " +
        displayScore(dimension.value * weight))
}

@Composable
private fun Recap(snapshot: RunSnapshot, content: BookingContent,
                  onAction: (RunAction) -> Unit, onReset: () -> Unit) {
    val result = requireNotNull(snapshot.result)
    Text("Booking recap", style = MaterialTheme.typography.headlineLarge)
    PanelCard {
        Text("Attempt ${snapshot.attemptId}: ${displayScore(result.invitationScore)} — " +
            if (result.invited) "invited back" else "not invited")
        val previous = snapshot.previousResult
        if (previous != null) {
            Text("Previous attempt: ${displayScore(previous.invitationScore)}")
            Text("Change: ${signed(result.invitationScore - previous.invitationScore)}")
        }
        Text("The five audience changes above carry into this recap; this demo ends after one booking.")
        Text("Try another production plan for ${content.title}.")
    }
    Button(onClick = { onAction(RunAction.ConfirmRetry) }, modifier = Modifier.testTag("confirm-retry")) {
        Text("Retry booking")
    }
    OutlinedButton(onClick = onReset, modifier = Modifier.testTag("reset-demo")) { Text("Reset demo") }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun Choice(text: String, tag: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(text) }
}

private fun signed(value: Double): String = (if (value >= 0) "+" else "−") + displayScore(kotlin.math.abs(value))
