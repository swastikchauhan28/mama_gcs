@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.dashboard.ConsolePanels
import com.mamadrones.gcs.presentation.dashboard.forDisplay
import com.mamadrones.gcs.presentation.dashboard.sampleAge
import com.mamadrones.gcs.presentation.map.VehicleMap
import java.util.Locale

@Composable
fun DashboardScreen(state: VehicleState, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    ScreenBody(modifier) {
        ScreenHeader("Field overview", "AGRICULTURAL UGV  /  OPERATIONS")
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EmergencyStopButton()
        }
        BoxWithConstraints {
            if (maxWidth >= 860.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MapWorkspace(state, Modifier.weight(1.5f).heightIn(min = 300.dp))
                    SubsystemCard(ConsolePanels.vehicle(state), Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    MapWorkspace(state, Modifier.fillMaxWidth().heightIn(min = 260.dp))
                    SubsystemCard(ConsolePanels.vehicle(state))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Control" to "control", "Mission" to "mission", "Diagnostics" to "diagnostics", "Admin" to "admin").forEach { (label, route) ->
                OutlinedButton(onClick = { onNavigate(route) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
            }
        }
        Text("SUBSYSTEMS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        CardGrid(listOf(
            ConsolePanels.health(state), ConsolePanels.gps(state), ConsolePanels.position(state),
            ConsolePanels.battery(state), ConsolePanels.attitude(state), ConsolePanels.motors(state),
            ConsolePanels.spray(state), ConsolePanels.hydraulic(state)
        ))
    }
}

/** MapTiler vector basemap with live MapLibre GeoJSON overlays from validated telemetry. */
@Composable
fun MapWorkspace(state: VehicleState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val displayed = state.forDisplay()
    val position = displayed.position
    val track = displayed.positionTrack
    val hasPosition = position.latitude != null && position.longitude != null

    Surface(
        modifier = modifier,
        color = colors.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Box {
            VehicleMap(
                state = displayed,
                modifier = Modifier.matchParentSize(),
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                color = colors.surface.copy(alpha = 0.94f),
                shape = MaterialTheme.shapes.small,
                tonalElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        "MAPTILER VECTOR MAP · NORTH UP",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                    )
                    StatusBadge(if (hasPosition) "POSITION RECEIVED" else "POSITION UNKNOWN")
                    if (hasPosition) {
                        Text(
                            String.format(
                                Locale.US,
                                "%.7f, %.7f",
                                position.latitude,
                                position.longitude,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Heading ${displayed.headingDegrees?.let { String.format(Locale.US, "%.1f°", it) } ?: "UNKNOWN"}" +
                                " · Track ${track.size} points · ${sampleAge(position.lastUpdatedAtEpochMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Waiting for a fresh GLOBAL_POSITION_INT sample",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
