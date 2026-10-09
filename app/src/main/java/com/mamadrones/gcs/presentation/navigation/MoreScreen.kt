package com.mamadrones.gcs.presentation.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.components.ScreenBody
import com.mamadrones.gcs.presentation.components.ScreenHeader
import com.mamadrones.gcs.presentation.components.PanelSpec
import com.mamadrones.gcs.presentation.components.SubsystemCard
import com.mamadrones.gcs.presentation.dashboard.forDisplay

private data class SystemsDestination(
    val route: String,
    val title: String,
    val detail: String,
    val status: String,
)

@Composable
fun MoreScreen(vehicle: VehicleState, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Rover summary", "Drive system, field equipment and connection evidence")
    val live = vehicle.forDisplay()
    SubsystemCard(PanelSpec("Observed vehicle", if (live.connected) "RECEIVING TELEMETRY · NOT SECURELY PAIRED" else "NO LIVE VEHICLE",
        listOf("MAVLink system" to (live.systemId?.toString() ?: "UNKNOWN"),
            "Mode" to (live.mode ?: "UNKNOWN"),
            "Arming" to (live.armed?.let { if (it) "ARMED" else "DISARMED" } ?: "UNKNOWN"),
            "App control" to "LOCKED · MONITORING ONLY"),
        "A received system ID is not an authenticated rover identity. Hardware configuration and safety readiness remain unverified."))
    val monitor = listOf(
        SystemsDestination(
            "telemetry", "Telemetry", "Position, GPS, battery, attitude and receive ages",
            if (vehicle.connected) "LIVE TELEMETRY" else "WAITING FOR LINK",
        ),
        SystemsDestination("health", "Health", "Telemetry evidence and readiness blockers", "NOT ASSESSED"),
        SystemsDestination("diagnostics", "Diagnostics", "Link counters, autopilot messages and packet ages", "INSPECT LINK"),
    )
    val equipment = listOf(
        SystemsDestination(
            "motors", "Motors", "VESC drive system · controller inventory, RPM, temperatures and faults",
            if (vehicle.motors.isEmpty()) "VESC LINK PENDING" else "${vehicle.motors.size} REPORTED",
        ),
        SystemsDestination("vesc-discovery", "BLE MAVLink", "Inspect GATT and receive rover telemetry", "READ ONLY"),
        SystemsDestination("classic-bluetooth", "HC-05 / Classic", "Receive MAVLink from a paired serial radio", "READ ONLY"),
        SystemsDestination("spray", "Spray", "Pump, nozzles, pressure, flow and fault evidence", "HARDWARE PENDING"),
        SystemsDestination("hydraulic", "Hydraulic", "Pump, valves, pressure and temperature evidence", "HARDWARE PENDING"),
    )
    val setup = listOf(
        SystemsDestination("admin", "Accounts & audit", "Local sign-in, roles, passwords and activity records", "LOCAL ACCESS"),
        SystemsDestination("general", "Application settings", "Display appearance, communication links and maps", "LOCAL SETTINGS"),
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 620.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SystemsGroup("MONITOR", monitor, columns, onNavigate)
            SystemsGroup("FIELD EQUIPMENT", equipment, columns, onNavigate)
            SystemsGroup("CONFIGURATION", setup, columns, onNavigate)
        }
    }
    Text(
        "This workspace reports available software screens, not proof that equipment is connected, safe, or ready for operation.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SystemsGroup(
    title: String,
    destinations: List<SystemsDestination>,
    columns: Int,
    onNavigate: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        destinations.chunked(columns).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { destination ->
                    ElevatedCard(
                        onClick = { onNavigate(destination.route) },
                        modifier = Modifier.weight(1f).heightIn(min = 104.dp),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(destination.title, style = MaterialTheme.typography.titleSmall)
                            Text(destination.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(destination.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
