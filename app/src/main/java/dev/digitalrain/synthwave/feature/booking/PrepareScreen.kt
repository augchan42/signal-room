package dev.digitalrain.synthwave.feature.booking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.model.PrepAction
import dev.digitalrain.synthwave.core.model.RunSnapshot
import dev.digitalrain.synthwave.core.simulation.RunAction
import dev.digitalrain.synthwave.core.simulation.count
import dev.digitalrain.synthwave.core.simulation.projectBooking
import dev.digitalrain.synthwave.core.simulation.readiness
import dev.digitalrain.synthwave.core.simulation.tokenNote

/** Slot contents in display order: a locked Research token first, then Rehearse, Refine, Rest. */
internal fun slotTokens(prep: dev.digitalrain.synthwave.core.model.Preparation): List<PrepAction?> =
    (if (prep.researched) listOf<PrepAction?>(null) else emptyList()) +
        PrepAction.entries.flatMap { kind -> List(prep.count(kind)) { kind } }

@Composable
internal fun PrepareScreen(snapshot: RunSnapshot, content: BookingContent, onAction: (RunAction) -> Unit) {
    val prep = snapshot.preparation
    val projected = projectBooking(content, snapshot.selection, prep)
    val member = content.members.getValue(snapshot.selection.member)
    Text("Get the band ready", style = MaterialTheme.typography.headlineLarge)
    OutlinedButton(onClick = { onAction(RunAction.ReturnToDesk) }, modifier = Modifier.testTag("return-desk")) {
        Text("Back", fontSize = 16.sp)
    }
    val tokens = slotTokens(prep)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { index ->
            val token = tokens.getOrNull(index)
            val filled = index < tokens.size
            val label = when {
                !filled -> "Empty"
                token == null -> "Research (locked)"
                else -> actionLabel(token)
            }
            Surface(
                onClick = { if (token != null) onAction(RunAction.AdjustPreparation(token, -1)) },
                enabled = filled && token != null,
                shape = RoundedCornerShape(12.dp),
                color = if (filled) Color(0xFF12324A) else Panel,
                border = BorderStroke(if (filled) 2.dp else 1.dp,
                    if (filled) MaterialTheme.colorScheme.primary else Color(0x40E8E1D2)),
                modifier = Modifier.weight(1f).height(72.dp).testTag("slot-$index"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
    PanelCard {
        ReadinessLight("Look & sound", readiness(projected.coherence.value))
        ReadinessLight("Playing", readiness(projected.execution.value))
        LevelBar("${member.displayName} fatigue", projected.fatigueAfter.getValue(snapshot.selection.member))
    }
    PrepAction.entries.forEach { kind ->
        PanelCard {
            Text(actionLabel(kind), style = MaterialTheme.typography.titleLarge)
            Text(actionEffect(kind), fontSize = 16.sp)
            tokenNote(content, snapshot.selection, prep, kind)?.let {
                Text(noteText(it), fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
            }
            Button(onClick = { onAction(RunAction.AdjustPreparation(kind, 1)) }, enabled = prep.used < 3,
                modifier = Modifier.testTag("prep-${kind.name.lowercase()}")) {
                Text("Add ${actionLabel(kind)}", fontSize = 16.sp)
            }
        }
    }
    Text("Tap a placed token to take it back.", fontSize = 16.sp)
}

@Composable
internal fun PrepareBar(snapshot: RunSnapshot, onAction: (RunAction) -> Unit) {
    Surface(color = Panel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Button(onClick = { onAction(RunAction.ConfirmBroadcast) }, enabled = snapshot.preparation.used == 3,
                modifier = Modifier.fillMaxWidth().testTag("confirm-broadcast")) {
                Text(goOnAirLabel(snapshot.preparation.used), fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
