@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.dashboard.ConsolePanels

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
                    MapWorkspace(Modifier.weight(1.5f).heightIn(min = 300.dp))
                    SubsystemCard(ConsolePanels.vehicle(state), Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    MapWorkspace(Modifier.fillMaxWidth().heightIn(min = 260.dp))
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

/** Decorative planning grid. No coordinates, vehicle marker, or follow-state claim. */
@Composable
fun MapWorkspace(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier, color = colors.surface, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, colors.outlineVariant)) {
        Box {
            Canvas(Modifier.matchParentSize()) {
                val step = 32.dp.toPx()
                var x = 0f
                while (x < size.width) { drawLine(colors.outlineVariant.copy(alpha = 0.25f), Offset(x, 0f), Offset(x, size.height)); x += step }
                var y = 0f
                while (y < size.height) { drawLine(colors.outlineVariant.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y)); y += step }
            }
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("MAP WORKSPACE", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                Spacer(Modifier.height(12.dp))
                MamaIcon(ConsoleIcon.MAP, Modifier.align(Alignment.CenterHorizontally).size(40.dp))
                Text("Position unknown", style = MaterialTheme.typography.titleLarge)
                Text("Map rendering, vehicle marker and route are not implemented yet. Telemetry coordinates appear in the position panel.", color = colors.onSurfaceVariant)
                StatusBadge("MAP UNAVAILABLE")
            }
        }
    }
}
