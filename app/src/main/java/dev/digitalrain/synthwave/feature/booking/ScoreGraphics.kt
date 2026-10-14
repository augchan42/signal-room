package dev.digitalrain.synthwave.feature.booking

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.digitalrain.synthwave.core.simulation.Readiness
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val Panel = Color(0xF5081422)

@Composable
fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** 0–100 bar with markers at the return booking (65) and three stars (75). */
@Composable
fun ScoreBar(score: Double, previous: Double?, modifier: Modifier = Modifier) {
    val fill = remember { Animatable(0f) }
    LaunchedEffect(score) { fill.animateTo((score / 100.0).toFloat().coerceIn(0f, 1f), tween(900)) }
    val track = MaterialTheme.colorScheme.onBackground.copy(alpha = .15f)
    val bar = MaterialTheme.colorScheme.primary
    val target = MaterialTheme.colorScheme.secondary
    val ghost = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f)
    Canvas(modifier.fillMaxWidth().height(28.dp).testTag("score-bar")
        .semantics { contentDescription = "Score ${displayScore(score)} of 100" }) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        drawRoundRect(bar, size = Size(size.width * fill.value, size.height), cornerRadius = r)
        fun mark(at: Double, color: Color, width: Float) {
            val x = size.width * (at / 100.0).toFloat()
            drawLine(color, Offset(x, -4f), Offset(x, size.height + 4f), strokeWidth = width)
        }
        if (previous != null) mark(previous, ghost, 3f)
        mark(65.0, target, 5f)
        mark(75.0, bar, 5f)
    }
}

private fun starPath(cx: Float, cy: Float, outer: Float): Path = Path().apply {
    val inner = outer * .45f
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) outer else inner
        val angle = -PI / 2 + i * PI / 5
        val x = cx + radius * cos(angle).toFloat()
        val y = cy + radius * sin(angle).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Three stars; the earned ones fill one by one, 0.4 s apart. */
@Composable
fun StarRow(stars: Int, modifier: Modifier = Modifier) {
    val fills = remember { List(3) { Animatable(0f) } }
    LaunchedEffect(stars) {
        fills.forEachIndexed { index, fill ->
            if (index < stars) fill.animateTo(1f, tween(400)) else fill.snapTo(0f)
        }
    }
    val lit = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onBackground.copy(alpha = .25f)
    Row(modifier.testTag("stars-$stars").semantics { contentDescription = "$stars of 3 stars" },
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        fills.forEach { fill ->
            Canvas(Modifier.size(64.dp)) {
                val path = starPath(size.width / 2, size.height / 2, size.minDimension / 2)
                drawPath(path, dim, style = Stroke(width = 4f))
                drawPath(path, lit.copy(alpha = fill.value))
            }
        }
    }
}

@Composable
fun ReadinessLight(label: String, level: Readiness) {
    val color = when (level) {
        Readiness.LOW -> MaterialTheme.colorScheme.secondary
        Readiness.GOOD -> MaterialTheme.colorScheme.primary
        Readiness.MAX -> Color(0xFFFFE08A)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.semantics { contentDescription = "$label ${readinessText(level)}" }) {
        Canvas(Modifier.size(18.dp)) { drawCircle(color) }
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(readinessText(level), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

/** A labelled 0–100 bar, used for fatigue. */
@Composable
fun LevelBar(label: String, value: Int) {
    val track = MaterialTheme.colorScheme.onBackground.copy(alpha = .15f)
    val fill = MaterialTheme.colorScheme.secondary
    Column(verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics { contentDescription = "$label $value of 100" }) {
        Text("$label $value", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            drawRoundRect(fill, size = Size(size.width * value.coerceIn(0, 100) / 100f, size.height), cornerRadius = r)
        }
    }
}

@Composable
fun FitDots(dots: Int) {
    val lit = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onBackground.copy(alpha = .25f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics { contentDescription = "Late-night fit $dots of 3" }) {
        Text("Late-night fit", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        repeat(3) { index -> Canvas(Modifier.size(12.dp)) { drawCircle(if (index < dots) lit else dim) } }
    }
}
