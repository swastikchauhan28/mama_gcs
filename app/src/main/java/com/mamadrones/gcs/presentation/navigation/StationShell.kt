package com.mamadrones.gcs.presentation.navigation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.dashboard.forDisplay
import com.mamadrones.gcs.presentation.components.ConsoleIcon
import com.mamadrones.gcs.presentation.components.MamaIcon

private data class WorkspacePage(val route: String, val title: String)
private val vehiclePages = listOf(
    WorkspacePage("more", "Summary"), WorkspacePage("control", "Drive & safety"),
    WorkspacePage("health", "Health"), WorkspacePage("motors", "Drive motors"),
    WorkspacePage("parameter-review", "Parameter file"),
    WorkspacePage("spray", "Spray system"), WorkspacePage("hydraulic", "Hydraulics"),
)
private val analyzePages = listOf(WorkspacePage("telemetry", "Instruments"), WorkspacePage("diagnostics", "Link & messages"))
private val settingsPages = listOf(
    WorkspacePage("general", "General"), WorkspacePage("settings", "UDP link"),
    WorkspacePage("classic-bluetooth", "Bluetooth / HC-05"),
    WorkspacePage("vesc-discovery", "Bluetooth / BLE"), WorkspacePage("map-settings", "Maps"),
    WorkspacePage("admin", "Accounts & audit"),
)

/** A map-first shell. Configuration categories never permanently consume the operating map. */
@Composable
fun StationShell(
    vehicle: VehicleState,
    route: String,
    onNavigate: (String) -> Unit,
    onVehicleDetails: () -> Unit,
    content: @Composable () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val live = vehicle.forDisplay()
    val pages = when {
        vehiclePages.any { it.route == route } -> vehiclePages
        analyzePages.any { it.route == route } -> analyzePages
        settingsPages.any { it.route == route } -> settingsPages
        else -> emptyList()
    }
    val workspace = when (pages) {
        vehiclePages -> "Rover configuration"
        analyzePages -> "Analyze telemetry"
        settingsPages -> "Application settings"
        else -> if (route == "mission") "Plan route" else "Operate"
    }
    BoxWithConstraints(Modifier.fillMaxSize().displayCutoutPadding().imePadding()) {
        val wide = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize()) {
            Surface(shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(onClick = { menuOpen = true }, modifier = Modifier.heightIn(min = 52.dp).testTag("station-menu")) {
                            MamaIcon(ConsoleIcon.DASHBOARD)
                            if (wide) Text("  MAMA GCS", style = MaterialTheme.typography.titleSmall)
                            else Text(" Menu", style = MaterialTheme.typography.labelMedium)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            Text("ROVER GROUND STATION", Modifier.padding(16.dp), style = MaterialTheme.typography.labelSmall)
                            listOf(
                                WorkspacePage("dashboard", "Operate rover"), WorkspacePage("mission", "Plan route"),
                                WorkspacePage("more", "Rover configuration"), WorkspacePage("diagnostics", "Analyze tools"),
                                WorkspacePage("general", "Application settings"), WorkspacePage("admin", "Accounts & audit"),
                            ).forEach { page ->
                                DropdownMenuItem(text = { Text(page.title) }, onClick = {
                                    menuOpen = false; onNavigate(page.route)
                                }, modifier = Modifier.testTag("menu-${page.route}"))
                            }
                        }
                    }
                    if (wide) TextButton(onClick = onVehicleDetails, modifier = Modifier.testTag("vehicle-selector")) {
                        Text(vehicle.displayName ?: vehicle.systemId?.let { "Rover · SYS $it" } ?: "No rover selected",
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(workspace, Modifier.weight(1f).padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { onNavigate("settings") }, modifier = Modifier.heightIn(min = 48.dp).testTag("connection-shortcut")) {
                        Text(when (live.connectionStatus) {
                            com.mamadrones.gcs.domain.model.VehicleConnectionState.CONNECTED -> "● Connected"
                            com.mamadrones.gcs.domain.model.VehicleConnectionState.CONNECTING -> "Connecting…"
                            com.mamadrones.gcs.domain.model.VehicleConnectionState.DEGRADED -> "Link lost · Setup"
                            com.mamadrones.gcs.domain.model.VehicleConnectionState.ERROR -> "Link error · Setup"
                            else -> "Disconnected · Connect"
                        }, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (pages.isNotEmpty() || route == "mission") {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onNavigate("dashboard") }, modifier = Modifier.heightIn(min = 48.dp).testTag("exit-workspace")) { Text("‹ Operate") }
                        if (!wide && pages.isNotEmpty()) Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                            pages.forEach { page ->
                                FilterChip(selected = route == page.route, onClick = { onNavigate(page.route) },
                                    label = { Text(page.title) }, modifier = Modifier.padding(end = 6.dp).heightIn(min = 48.dp))
                            }
                        } else Text(if (route == "mission") "LOCAL PLAN · Not uploaded to rover" else "${pages.firstOrNull { it.route == route }?.title.orEmpty()} · Rover station",
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (wide && pages.isNotEmpty()) Surface(Modifier.width(184.dp).fillMaxHeight()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        pages.forEach { page ->
                            NavigationDrawerItem(selected = route == page.route, onClick = { onNavigate(page.route) },
                                label = { Text(page.title, style = MaterialTheme.typography.bodyMedium) },
                                shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("category-${page.route}"))
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
            // Persistent, truthful safety status, not a simulated emergency-stop control.
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (wide) "MONITORING ONLY · App emergency stop unavailable · Use the physical safety system"
                        else "App stop unavailable · Physical safety required",
                        Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { onNavigate("control") }, modifier = Modifier.heightIn(min = 48.dp).testTag("safety-shortcut")) {
                        Text("Safety ›", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}
