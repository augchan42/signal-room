package dev.digitalrain.synthwave.feature.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.simulation.RunAction

@Composable
internal fun IntroScreen(content: BookingContent, onAction: (RunAction) -> Unit) {
    Text(content.title, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
    Text("23:40 / late-night cable slot", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
    PanelCard {
        Text(INTRO_LINE, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("1 Choose", "2 Prepare", "3 Watch", "4 Reward").forEach {
                Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
    Button(onClick = { onAction(RunAction.OpenDesk) }, modifier = Modifier.fillMaxWidth().testTag("open-desk")) {
        Text("Start", fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}
