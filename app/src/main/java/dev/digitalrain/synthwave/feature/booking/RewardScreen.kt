package dev.digitalrain.synthwave.feature.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.model.BookingResult
import dev.digitalrain.synthwave.core.model.Dimension
import dev.digitalrain.synthwave.core.model.RunSnapshot
import dev.digitalrain.synthwave.core.simulation.RunAction
import dev.digitalrain.synthwave.core.simulation.bestSwap
import dev.digitalrain.synthwave.core.simulation.canBeInvited
import dev.digitalrain.synthwave.core.simulation.highlight
import dev.digitalrain.synthwave.core.simulation.starsFor

@Composable
internal fun RewardScreen(snapshot: RunSnapshot, content: BookingContent,
                          onAction: (RunAction) -> Unit, onReset: () -> Unit) {
    val result = requireNotNull(snapshot.result)
    val stars = starsFor(result.invitationScore)
    var details by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("reward"), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StarRow(stars)
        Text(headline(stars), style = MaterialTheme.typography.headlineLarge,
            color = if (result.invited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
        Text(positiveLine(highlight(content, result)), fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
    PanelCard {
        val previous = snapshot.previousResult?.invitationScore
        Text("${displayScore(result.invitationScore)} / 100" +
            (previous?.let { "   (${signed(result.invitationScore - it)} vs last time)" } ?: ""),
            fontSize = 18.sp, fontWeight = FontWeight.Medium)
        ScoreBar(result.invitationScore, previous)
        Text("Return booking at 65 · three stars at 75", fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
    PanelCard {
        Text("NEXT TIME", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(tipText(bestSwap(content, snapshot.selection, snapshot.preparation)), fontSize = 16.sp)
        if (!canBeInvited(content, snapshot.selection)) Text(HARD_SELL, fontSize = 16.sp)
    }
    Button(onClick = { onAction(RunAction.ConfirmRetry) }, modifier = Modifier.fillMaxWidth().testTag("confirm-retry")) {
        Text("Play again", fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
    TextButton(onClick = { details = !details }, modifier = Modifier.testTag("score-details")) {
        Text(if (details) "Hide how the score works" else "How the score works",
            fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
    if (details) ScoreDetails(result, content)
    OutlinedButton(onClick = onReset, modifier = Modifier.testTag("reset-demo")) {
        Text("Reset demo", fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/** The former verdict and recap explanation, with labelled rows and the brief and audience report added. */
@Composable
private fun ScoreDetails(result: BookingResult, content: BookingContent) {
    PanelCard {
        Text("THE SCORE", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        DimensionRow("Coherence (look & sound)", result.coherence, content.balance.weights[0])
        DimensionRow("Execution (playing)", result.execution, content.balance.weights[1])
        DimensionRow("Brief fit (late-night slot)", result.briefFit, content.balance.weights[2])
    }
    PanelCard {
        Text("WHY IT LANDED THIS WAY", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        result.coherence.contributions.filter { it.id != "concept-base" && it.id != "clamp" }
            .sortedByDescending { kotlin.math.abs(it.points) }
            .take(2).forEach { Text("${it.explanation}: ${signed(it.points)}", fontSize = 16.sp) }
        result.execution.contributions.filter { it.id != "arrangement-base" && it.id != "clamp" }
            .sortedByDescending { kotlin.math.abs(it.points) }
            .take(2).forEach { Text("${it.explanation}: ${signed(it.points)}", fontSize = 16.sp) }
        listOf("Coherence" to result.coherence, "Execution" to result.execution).forEach { (label, dimension) ->
            val overflow = dimension.contributions.firstOrNull { it.id == "clamp" }?.points ?: 0.0
            if (overflow < 0) Text("$label reached the 100-point cap; " +
                "${displayScore(-overflow)} extra points did not count.", fontSize = 16.sp)
        }
    }
    PanelCard {
        Text("FIVE AUDIENCES", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text("Awareness: how many of them know the band.", fontSize = 16.sp)
        Text("Affinity: how much they like it.", fontSize = 16.sp)
        content.segments.forEach { (id, segment) ->
            val after = result.audienceAfter.getValue(id)
            Text("${segment.displayName}: awareness ${signed(after.awareness - segment.initial.awareness)}, " +
                "affinity ${signed(after.affinity - segment.initial.affinity)}", fontSize = 16.sp)
        }
        Text("Audience gains are separate from the invitation score.", fontSize = 16.sp)
    }
    PanelCard {
        Text("THE BRIEF AND THE AUDIENCE REPORT", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        content.brief.forEach { Text(it, fontSize = 16.sp) }
        content.researchReport.forEach { Text(it, fontSize = 16.sp) }
    }
}

@Composable
private fun DimensionRow(label: String, dimension: Dimension, weight: Double) {
    Text("$label ${displayScore(dimension.value)} × ${displayScore(weight)} = " +
        displayScore(dimension.value * weight), fontSize = 16.sp)
}

internal fun signed(value: Double): String = (if (value >= 0) "+" else "−") + displayScore(kotlin.math.abs(value))
