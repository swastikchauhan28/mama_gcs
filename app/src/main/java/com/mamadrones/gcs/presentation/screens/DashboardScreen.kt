package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.VehicleConnectionState
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.dashboard.ConsolePanels
import com.mamadrones.gcs.presentation.dashboard.forDisplay
import com.mamadrones.gcs.presentation.dashboard.reading
import com.mamadrones.gcs.presentation.dashboard.sampleAge
import com.mamadrones.gcs.presentation.map.VehicleMap
import java.util.Locale

/** Bounded operation workspace: the map never lives inside a scrolling telemetry list. */
@Composable
fun DashboardScreen(state: VehicleState, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val displayed = state.forDisplay()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val twoMetricRows = maxHeight >= 480.dp
        if (maxWidth >= 680.dp) {
            Row(Modifier.fillMaxSize().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MapWorkspace(state, Modifier.weight(1f).fillMaxHeight(), onNavigate, showOperatorTools = true)
                TelemetryDock(displayed, true, onNavigate, Modifier.width(224.dp).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MapWorkspace(state, Modifier.fillMaxWidth().weight(1f), onNavigate)
                TelemetryDock(displayed, false, onNavigate, Modifier.fillMaxWidth(), twoMetricRows = twoMetricRows)
            }
        }
    }
}

@Composable
private fun TelemetryDock(
    state: VehicleState,
    expanded: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    twoMetricRows: Boolean = false,
) {
    val packs = state.batteries.ifEmpty { listOf(state.battery) }
    val batteryValue = if (packs.size > 1) "${packs.size} packs" else
        packs.single().percentage?.let { "$it %" } ?: "UNKNOWN"
    val metrics = listOf(
        "GROUND SPEED" to state.speedMetersPerSecond.reading("m/s"),
        "HEADING" to state.headingDegrees.reading("°"),
        "BATTERY" to batteryValue,
        "GPS FIX" to state.gps.fix.name.replace('_', ' '),
        "THROTTLE" to (state.roverHud.throttlePercent?.let { "$it %" } ?: "UNKNOWN"),
        "CLIMB" to state.roverHud.climbRateMetersPerSecond.reading("m/s"),
        "HUD SPEED" to state.roverHud.groundSpeedMetersPerSecond.reading("m/s"),
        "HUD HEADING" to state.roverHud.headingDegrees.reading("°"),
    )
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(
            if (expanded) Modifier.verticalScroll(rememberScrollState()).padding(8.dp)
            else Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (expanded) {
                Text("ROVER INSTRUMENTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                metrics.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (label, value) -> Instrument(label, value, Modifier.weight(1f)) }
                    }
                }
                TelemetryRow("Mode", state.mode ?: "UNKNOWN")
                TelemetryRow("Arming", state.armed?.let { if (it) "ARMED" else "DISARMED" } ?: "UNKNOWN")
                TelemetryRow("Direction", state.direction.name)
                TelemetryRow("Satellites", state.gps.satellites?.toString() ?: "UNKNOWN")
                TelemetryRow("HDOP", state.gps.hdop.reading(""))
                Text(sampleAge(state.position.lastUpdatedAtEpochMillis), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("FIELD EQUIPMENT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                EquipmentLink("Spray", state.spray.spraying.name) { onNavigate("spray") }
                EquipmentLink("Hydraulics", state.hydraulic.enabled.name) { onNavigate("hydraulic") }
                EquipmentLink("Motors", if (state.motors.isEmpty()) "NO DATA" else "${state.motors.size} reported") { onNavigate("motors") }
                EquipmentLink("Health", "NOT ASSESSED") { onNavigate("health") }
                OutlinedButton(onClick = { onNavigate("telemetry") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("All telemetry") }
                TextButton(onClick = { onNavigate("diagnostics") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Messages & diagnostics") }
            } else {
                if (twoMetricRows) {
                    metrics.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (label, value) -> Instrument(label, value, Modifier.weight(1f)) }
                        }
                    }
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        metrics.forEach { (label, value) -> Instrument(label, value, Modifier.widthIn(min = 112.dp)) }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All telemetry" to "telemetry", "Spray" to "spray", "Hydraulics" to "hydraulic", "Motors" to "motors", "Health" to "health").forEach { (label, route) ->
                        OutlinedButton(onClick = { onNavigate(route) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
                    }
                }
            }
        }
    }
}

@Composable
fun Instrument(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier.semantics(mergeDescendants = true) {}, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun EquipmentLink(label: String, value: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$value ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MapWorkspace(
    state: VehicleState,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit = {},
    showOperatorTools: Boolean = false,
) {
    val displayed = state.forDisplay()
    val position = displayed.position
    Surface(modifier.testTag("operation-map"), shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Box(Modifier.weight(1f).fillMaxWidth().testTag("operation-map-viewport")) {
                VehicleMap(displayed, Modifier.fillMaxSize())
                // Reserve the right edge for map controls; provider attribution stays at the bottom.
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 8.dp, end = 84.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text(
                            when (displayed.connectionStatus) {
                                VehicleConnectionState.CONNECTED -> "ROVER · LIVE TELEMETRY"
                                VehicleConnectionState.CONNECTING -> "ROVER · CONNECTING"
                                VehicleConnectionState.DEGRADED -> "ROVER · LINK DEGRADED"
                                VehicleConnectionState.ERROR -> "ROVER · LINK ERROR"
                                VehicleConnectionState.DISCONNECTED -> "ROVER · WAITING FOR LINK"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            if (position.latitude != null && position.longitude != null)
                                String.format(Locale.US, "%.7f, %.7f", position.latitude, position.longitude)
                            else "Position unavailable",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            "${displayed.mode ?: "MODE UNKNOWN"} · ${displayed.armed?.let { if (it) "ARMED" else "DISARMED" } ?: "ARM STATE UNKNOWN"}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Keep actions in their own measured row, never over map controls or attribution.
            if (showOperatorTools) {
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("operation-toolbar"),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 4.dp,
                ) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WorkspaceAction("ROUTE PLAN", "mission", onNavigate)
                        WorkspaceAction("MESSAGES", "diagnostics", onNavigate)
                        WorkspaceAction("SAFETY GATE", "control", onNavigate)
                        WorkspaceAction("SETUP", "settings", onNavigate)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceAction(label: String, destination: String, onNavigate: (String) -> Unit) {
    TextButton(onClick = { onNavigate(destination) }, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
