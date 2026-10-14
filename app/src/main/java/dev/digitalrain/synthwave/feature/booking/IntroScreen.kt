package dev.digitalrain.synthwave.feature.booking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.simulation.RunAction

private enum class Step(val label: String) { CHOOSE("Choose"), PREPARE("Prepare"), WATCH("Watch"), REWARD("Reward") }

@Composable
internal fun IntroScreen(content: BookingContent, onAction: (RunAction) -> Unit) {
    Text(content.title, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
    Text("23:40 / late-night cable slot", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
    PanelCard {
        Text(INTRO_LINE, style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Step.entries.forEach { step ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StepIcon(step)
                    Text(step.label, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp,
                        fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
    Button(onClick = { onAction(RunAction.OpenDesk) }, modifier = Modifier.fillMaxWidth().testTag("open-desk")) {
        Text("Start", fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StepIcon(step: Step) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(44.dp)) {
        when (step) {
            Step.CHOOSE -> {
                val w = size.width * .28f
                val gap = (size.width - 3 * w) / 2
                repeat(3) { i ->
                    drawRoundRect(color, Offset(i * (w + gap), size.height * .15f), Size(w, size.height * .7f),
                        CornerRadius(4.dp.toPx()), style = Stroke(width = 3.dp.toPx()))
                }
            }
            Step.PREPARE -> {
                val r = size.width * .14f
                repeat(3) { i -> drawCircle(color, r, Offset(size.width * (.17f + .33f * i), size.height / 2)) }
            }
            Step.WATCH -> drawPath(Path().apply {
                moveTo(size.width * .25f, size.height * .12f)
                lineTo(size.width * .85f, size.height / 2)
                lineTo(size.width * .25f, size.height * .88f)
                close()
            }, color)
            Step.REWARD -> drawPath(starPath(size.width / 2, size.height / 2, size.minDimension / 2), color)
        }
    }
}
