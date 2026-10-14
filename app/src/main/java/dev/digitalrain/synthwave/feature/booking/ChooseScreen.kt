package dev.digitalrain.synthwave.feature.booking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.digitalrain.synthwave.core.audio.PlaybackState
import dev.digitalrain.synthwave.core.audio.PlaybackStatus
import dev.digitalrain.synthwave.core.data.AssetCatalog
import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.RunSnapshot
import dev.digitalrain.synthwave.core.simulation.RunAction
import dev.digitalrain.synthwave.core.simulation.fitDots
import dev.digitalrain.synthwave.core.ui.SceneBackdrop

@Composable
internal fun ChooseScreen(snapshot: RunSnapshot, content: BookingContent, catalog: AssetCatalog,
                          onAction: (RunAction) -> Unit) {
    val selection = snapshot.selection
    Text("Choose your show", style = MaterialTheme.typography.headlineLarge)
    CardRow("MIX") {
        ArrangementId.entries.forEach { id ->
            val rules = content.arrangements.getValue(id)
            ChoiceCard(rules.displayName, rules.riskDescription, selection.arrangement == id,
                "arrangement-${id.name.lowercase()}", { onAction(RunAction.ChooseArrangement(id)) })
        }
    }
    CardRow("FEATURED MEMBER") {
        MemberId.entries.forEach { id ->
            val member = content.members.getValue(id)
            ChoiceCard(member.displayName, member.strength, selection.member == id,
                "member-${id.name.lowercase()}", { onAction(RunAction.ChooseMember(id)) },
                picture = { SceneBackdrop("demo/${catalog.member(id).stillPath}", "${member.displayName} still",
                    Modifier.fillMaxWidth().height(120.dp)) },
                footer = { LevelBar("Fatigue", member.fatigue) })
        }
    }
    CardRow("STAGE") {
        ConceptId.entries.forEach { id ->
            val concept = content.concepts.getValue(id)
            ChoiceCard(concept.displayName, concept.description, selection.concept == id,
                "concept-${id.name.lowercase()}", { onAction(RunAction.ChooseConcept(id)) },
                picture = { SceneBackdrop("demo/${catalog.concept(id).stillPath}", "${concept.displayName} preview",
                    Modifier.fillMaxWidth().height(120.dp)) })
        }
    }
    val key = "${selection.arrangement.name.lowercase()}-${selection.member.name.lowercase()}"
    PanelCard {
        Text("NOW PLAYING", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
        Text(content.mixDescriptions.getValue(key), fontSize = 16.sp)
    }
}

/** Fixed bar under the Choose screen: the pick, play/pause and Next. */
@Composable
internal fun ChooseBar(snapshot: RunSnapshot, content: BookingContent, playback: PlaybackState,
                       onAction: (RunAction) -> Unit, onPlayPause: () -> Unit) {
    val s = snapshot.selection
    Surface(color = Panel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${content.arrangements.getValue(s.arrangement).displayName} · " +
                "${content.members.getValue(s.member).displayName} · ${content.concepts.getValue(s.concept).displayName}",
                fontSize = 16.sp, fontWeight = FontWeight.Medium)
            FitDots(fitDots(content, s))
            if (playback.status == PlaybackStatus.ERROR) Text("Could not play ${playback.errorAssetId}.",
                color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onPlayPause, modifier = Modifier.testTag("preview-play-pause")) {
                    Text(if (playback.status == PlaybackStatus.PLAYING) "Pause mix" else "Play mix", fontSize = 16.sp,
                        fontWeight = FontWeight.Medium)
                }
                Button(onClick = { onAction(RunAction.OpenPreparation) },
                    modifier = Modifier.weight(1f).testTag("open-preparation")) { Text("Next", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

@Composable
private fun CardRow(label: String, cards: @Composable () -> Unit) {
    Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) { cards() }
}

@Composable
private fun ChoiceCard(title: String, line: String, selected: Boolean, tag: String, onClick: () -> Unit,
                       picture: (@Composable () -> Unit)? = null, footer: (@Composable () -> Unit)? = null) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFF12324A) else Panel,
        border = if (selected) BorderStroke(3.dp, primary) else BorderStroke(1.dp, Color(0x40E8E1D2)),
        modifier = Modifier.width(170.dp).testTag(tag).semantics { this.selected = selected }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            picture?.invoke()
            Text((if (selected) "✓ " else "") + title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                color = if (selected) primary else MaterialTheme.colorScheme.onBackground)
            Text(line, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
            footer?.invoke()
        }
    }
}
