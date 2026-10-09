package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.dashboard.ConsolePanels
import com.mamadrones.gcs.presentation.dashboard.forDisplay
import com.mamadrones.gcs.presentation.dashboard.reading
import com.mamadrones.gcs.presentation.map.VehicleMap
import java.util.Locale

/** Map-first operation. Instruments have measured space, leaving attribution unobstructed. */
@Composable
fun DashboardScreen(state: VehicleState, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val live = state.forDisplay()
    var showInstruments by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        MapWorkspace(state, Modifier.weight(1f).fillMaxWidth(), onNavigate, showOperatorTools = true)
        Surface(shadowElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Instrument("GROUND SPEED", live.speedMetersPerSecond.reading("m/s"))
                    Instrument("HEADING", live.headingDegrees.reading("°"))
                    Instrument("BATTERY", live.battery.percentage?.let { "$it %" } ?: "UNKNOWN")
                    Instrument("GPS", live.gps.fix.name.replace('_', ' '))
                    Instrument("DIRECTION", live.direction.name)
                }
                HeadingInstrument(live.headingDegrees)
                TextButton(onClick = { showInstruments = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("show-instruments")) {
                    Text("More", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    if (showInstruments) AlertDialog(
        onDismissRequest = { showInstruments = false },
        title = { Text("Rover instruments") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TelemetryRow("Mode", live.mode ?: "UNKNOWN")
                TelemetryRow("Arming", live.armed?.let { if (it) "ARMED" else "DISARMED" } ?: "UNKNOWN")
                TelemetryRow("Satellites", live.gps.satellites?.toString() ?: "UNKNOWN")
                TelemetryRow("HDOP", live.gps.hdop.reading(""))
                listOf("All telemetry" to "telemetry", "Motors" to "motors", "Spray" to "spray",
                    "Hydraulics" to "hydraulic", "Messages" to "diagnostics").forEach { (label, route) ->
                    OutlinedButton(onClick = { showInstruments = false; onNavigate(route) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showInstruments = false }) { Text("Close") } },
    )
}

@Composable
fun Instrument(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}.padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
    }
}

/** North-fixed heading dial, not a phone compass. Missing telemetry never draws a north-pointing rover. */
@Composable
private fun HeadingInstrument(heading: Double?) {
    val validHeading = heading?.takeIf { it.isFinite() }
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.size(64.dp).testTag("rover-heading").semantics(mergeDescendants = true) {
        contentDescription = validHeading?.let { "Rover heading ${it.reading("degrees")}; north fixed" } ?: "Rover heading unknown"
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            val radius = size.minDimension / 2f
            drawCircle(ink.copy(alpha = 0.45f), radius, style = Stroke(1.dp.toPx()))
            repeat(12) { index ->
                rotate(index * 30f) {
                    drawLine(ink, Offset(center.x, 0f), Offset(center.x, if (index % 3 == 0) 7.dp.toPx() else 3.dp.toPx()), 1.dp.toPx())
                }
            }
            validHeading?.let {
                rotate(it.toFloat()) {
                    val arrow = Path().apply {
                        moveTo(center.x, center.y - radius * 0.6f)
                        lineTo(center.x + radius * 0.3f, center.y + radius * 0.4f)
                        lineTo(center.x, center.y + radius * 0.15f)
                        lineTo(center.x - radius * 0.3f, center.y + radius * 0.4f)
                        close()
                    }
                    drawPath(arrow, accent)
                }
            }
        }
        if (validHeading == null) Text("?", style = MaterialTheme.typography.titleMedium)
        Text("N", Modifier.align(Alignment.TopCenter), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun MapWorkspace(
    state: VehicleState,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit = {},
    showOperatorTools: Boolean = false,
) {
    val live = state.forDisplay()
    val position = live.position
    Box(modifier.testTag("operation-map")) {
        VehicleMap(live, Modifier.fillMaxSize().testTag("operation-map-viewport"))
        if (showOperatorTools) Surface(
            Modifier.align(Alignment.TopStart).padding(8.dp).width(72.dp).testTag("operation-toolbar"),
            shape = MaterialTheme.shapes.small, shadowElevation = 2.dp,
        ) {
            Column(Modifier.heightIn(max = 192.dp).verticalScroll(rememberScrollState())) {
                MapAction("Plan", ConsoleIcon.MISSION) { onNavigate("mission") }
                MapAction("Drive", ConsoleIcon.CONTROL) { onNavigate("control") }
                MapAction("Systems", ConsoleIcon.MORE) { onNavigate("more") }
            }
        }
        Surface(
            Modifier.align(Alignment.TopCenter).padding(start = if (showOperatorTools) 88.dp else 8.dp, end = 88.dp, top = 8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.small,
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("${live.mode ?: "MODE UNKNOWN"} · ${live.armed?.let { if (it) "ARMED" else "DISARMED" } ?: "ARMING UNKNOWN"}",
                    style = MaterialTheme.typography.labelMedium)
                Text(if (position.latitude != null && position.longitude != null)
                    String.format(Locale.US, "%.7f, %.7f", position.latitude, position.longitude) else "Position unavailable",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MapAction(label: String, icon: ConsoleIcon, onClick: () -> Unit) {
    TextButton(onClick = onClick, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MamaIcon(icon)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun TelemetryScreen(state: VehicleState, modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Telemetry", "Vehicle measurements and receive ages")
    CardGrid(listOf(
        ConsolePanels.vehicle(state), ConsolePanels.gps(state), ConsolePanels.position(state),
        ConsolePanels.battery(state), ConsolePanels.attitude(state), ConsolePanels.systemStatus(state),
        ConsolePanels.spray(state), ConsolePanels.hydraulic(state), ConsolePanels.motors(state),
    ))
}
