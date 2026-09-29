@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mamadrones.gcs.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.presentation.theme.MamaSpacing

enum class ConsoleIcon { DASHBOARD, MAP, CONTROL, MISSION, MORE }

/** Original vector-like console glyphs, drawn locally with no external assets. */
@Composable
fun MamaIcon(icon: ConsoleIcon, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        val unit = size.width / 24f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), 1.7f * unit, StrokeCap.Round)
        when (icon) {
            ConsoleIcon.DASHBOARD -> listOf(4f to 4f, 14f to 4f, 4f to 14f, 14f to 14f).forEach { (x, y) ->
                drawRect(color, Offset(x * unit, y * unit), Size(6 * unit, 6 * unit), style = Stroke(1.6f * unit))
            }
            ConsoleIcon.MAP -> { line(3f, 6f, 9f, 3f); line(9f, 3f, 15f, 6f); line(15f, 6f, 21f, 3f); line(3f, 6f, 3f, 21f); line(9f, 3f, 9f, 18f); line(15f, 6f, 15f, 21f); line(21f, 3f, 21f, 18f); line(3f, 21f, 9f, 18f); line(9f, 18f, 15f, 21f); line(15f, 21f, 21f, 18f) }
            ConsoleIcon.CONTROL -> { line(4f, 7f, 20f, 7f); line(4f, 17f, 20f, 17f); drawCircle(color, 3 * unit, Offset(9 * unit, 7 * unit)); drawCircle(color, 3 * unit, Offset(16 * unit, 17 * unit)) }
            ConsoleIcon.MISSION -> { line(5f, 18f, 12f, 6f); line(12f, 6f, 20f, 16f); listOf(5f to 18f, 12f to 6f, 20f to 16f).forEach { (x,y) -> drawCircle(color, 2.5f * unit, Offset(x * unit,y * unit)) } }
            ConsoleIcon.MORE -> listOf(5f, 12f, 19f).forEach { drawCircle(color, 2 * unit, Offset(it * unit, 12 * unit)) }
        }
    }
}

@Composable
fun ScreenBody(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MamaSpacing.medium), verticalArrangement = Arrangement.spacedBy(MamaSpacing.medium), content = content)
}

@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatusBadge(label: String, urgent: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(color = if (urgent) colors.errorContainer else colors.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Text("${if (urgent) "!" else "●"}  $label", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = if (urgent) colors.onErrorContainer else colors.onSurfaceVariant)
    }
}

@Composable
fun Notice(title: String, description: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(MamaSpacing.medium), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

data class PanelSpec(val title: String, val status: String, val rows: List<Pair<String, String>>, val note: String? = null)

@Composable
fun SubsystemCard(spec: PanelSpec, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(MamaSpacing.medium), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(spec.title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
            Text(spec.status, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            spec.rows.forEach { (label, value) -> TelemetryRow(label, value) }
            spec.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun TelemetryRow(label: String, value: String) {
    FlowRow(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun CardGrid(cards: List<PanelSpec>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val count = when { maxWidth >= 1000.dp -> 3; maxWidth >= 620.dp -> 2; else -> 1 }
        Column(verticalArrangement = Arrangement.spacedBy(MamaSpacing.medium)) {
            cards.chunked(count).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(MamaSpacing.medium)) {
                    row.forEach { SubsystemCard(it, Modifier.weight(1f)) }
                    repeat(count - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun UnavailableActions(vararg labels: String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { label -> OutlinedButton(enabled = false, onClick = {}, modifier = Modifier.heightIn(min = MamaSpacing.touchTarget)) { Text(label) } }
    }
}

@Composable
fun EmergencyStopButton(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Button(
        enabled = false, onClick = {}, modifier = modifier.heightIn(min = 56.dp).semantics { stateDescription = "Unavailable. Cannot command or confirm a vehicle stop." },
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(disabledContainerColor = colors.errorContainer, disabledContentColor = colors.onErrorContainer)
    ) { Text("■  EMERGENCY STOP · UNAVAILABLE", style = MaterialTheme.typography.labelLarge) }
}
