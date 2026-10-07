package com.mamadrones.gcs.presentation.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.mamadrones.gcs.domain.model.ThemeMode
import com.mamadrones.gcs.domain.model.VehicleConnectionState
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.screens.*
import com.mamadrones.gcs.presentation.settings.SettingsUiState
import com.mamadrones.gcs.presentation.settings.ConnectionUiState
import com.mamadrones.gcs.presentation.mission.MissionPlanUiState
import com.mamadrones.gcs.presentation.mission.MissionPlanAction
import com.mamadrones.gcs.presentation.screens.VescDiscoveryUiState
import com.mamadrones.gcs.data.transport.bluetooth.BleNotifyCharacteristic

enum class AppDestination(val route: String, val label: String, val icon: ConsoleIcon) {
    DASHBOARD("dashboard", "Operate", ConsoleIcon.DASHBOARD), MAP("map", "Map", ConsoleIcon.MAP),
    CONTROL("control", "Drive", ConsoleIcon.CONTROL), MISSION("mission", "Plan", ConsoleIcon.MISSION),
    MORE("more", "Systems", ConsoleIcon.MORE)
}

@Composable
fun MamaGcsApp(
    vehicle: VehicleState,
    settings: SettingsUiState,
    onThemeSelected: (ThemeMode) -> Unit,
    connection: ConnectionUiState = ConnectionUiState(),
    onRemoteHostChanged: (String) -> Unit = {},
    onRemotePortChanged: (String) -> Unit = {},
    onLocalPortChanged: (String) -> Unit = {},
    onSaveEndpoint: () -> Unit = {},
    onOpenSocket: () -> Unit = {},
    onCloseSocket: () -> Unit = {},
    onClearEndpoint: () -> Unit = {},
    missionPlan: MissionPlanUiState = MissionPlanUiState(),
    onMissionAction: (MissionPlanAction) -> Unit = {},
    vescDiscovery: VescDiscoveryUiState = VescDiscoveryUiState(),
    onVescScan: () -> Unit = {},
    onVescStopScan: () -> Unit = {},
    onVescPermissionDenied: () -> Unit = {},
    onBleConnectGatt: (String, String) -> Unit = { _, _ -> },
    onBleStartMavlinkReceive: (BleNotifyCharacteristic) -> Unit = {},
    onBleDisconnect: () -> Unit = {},
) {
    val navController = rememberNavController()
    var showVehicles by remember { mutableStateOf(false) }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: AppDestination.DASHBOARD.route
    val selected = AppDestination.entries.find { it.route == route } ?: AppDestination.MORE
    val navigate: (String) -> Unit = { destination ->
        navController.navigate(destination) { launchSingleTop = true }
    }
    val navigatePrimary: (AppDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            // These are flat destinations, not nested navigation graphs. Restoring the popped
            // stack can reopen Settings when the operator explicitly chooses Operate.
            popUpTo(AppDestination.DASHBOARD.route)
            launchSingleTop = true
        }
    }
    // Fill the display, but keep touch targets clear of camera cutouts and the software keyboard.
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).displayCutoutPadding().imePadding()) {
        val wide = maxWidth >= 720.dp
        Row(Modifier.fillMaxSize()) {
        if (wide) Surface(Modifier.width(72.dp).fillMaxHeight()) {
            Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AppDestination.entries.forEach { item ->
                    NavigationRailItem(selected = selected == item, onClick = { navigatePrimary(item) },
                        modifier = Modifier.height(56.dp).testTag("nav-${item.route}"),
                        icon = { MamaIcon(item.icon) }, label = { Text(item.label, style = MaterialTheme.typography.labelSmall) })
                }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Column(Modifier.widthIn(min = 72.dp, max = 104.dp).padding(start = 4.dp)) {
                            Text("MAMA GCS", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Clip)
                            Text("ROVER STATION", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        TextButton(onClick = { showVehicles = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("vehicle-selector")) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    vehicle.displayName ?: if (vehicle.connected && vehicle.systemId != null) "Rover · SYS ${vehicle.systemId}" else "No rover selected",
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    vehicle.vehicleId ?: if (vehicle.connected && vehicle.systemId != null)
                                        "Observed MAVLink system · not paired" else "Vehicle pairing unavailable",
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        TextButton(onClick = { navigate("settings") }, modifier = Modifier.heightIn(min = 48.dp).testTag("connection-shortcut")) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(connectionLabel(vehicle.connectionStatus), style = MaterialTheme.typography.labelLarge,
                                    color = when (vehicle.connectionStatus) {
                                        VehicleConnectionState.CONNECTED -> MaterialTheme.colorScheme.secondary
                                        VehicleConnectionState.DEGRADED, VehicleConnectionState.ERROR -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    })
                                Text("LINK SETUP ›", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Column {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            EmergencyStopButton(if (wide) Modifier.width(360.dp) else Modifier.weight(1f))
                            if (wide) {
                                Text("MONITORING ONLY · Vehicle controls locked", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = { navigate("control") }) { Text("Drive status") }
                            }
                        }
                    }
                    if (!wide) NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
                        AppDestination.entries.forEach { item ->
                            NavigationBarItem(selected = selected == item, onClick = { navigatePrimary(item) },
                                modifier = Modifier.testTag("nav-${item.route}"),
                                icon = { MamaIcon(item.icon) }, label = { Text(item.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                        }
                    }
                }
            }
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                Column(Modifier.weight(1f)) {
                    if (route !in AppDestination.entries.map { it.route }) {
                        TextButton(onClick = { navController.popBackStack() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("‹ Back") }
                    }
                    NavHost(navController, startDestination = AppDestination.DASHBOARD.route, modifier = Modifier.weight(1f)) {
                        composable("dashboard") { DashboardScreen(vehicle, navigate) }
                        composable("map") { MapScreen(vehicle, navigate) }
                        composable("control") { ControlScreen(vehicle) }
                        composable("mission") { MissionScreen(vehicle, missionPlan, onMissionAction) }
                        composable("more") { MoreScreen(vehicle, onNavigate = navigate) }
                        composable("telemetry") { TelemetryScreen(vehicle) }
                        composable("health") { HealthScreen(vehicle) }
                        composable("motors") { MotorScreen(vehicle) }
                        composable("vesc-discovery") {
                            VescBluetoothDiscoveryScreen(
                                state = vescDiscovery,
                                onStartScan = onVescScan,
                                onStopScan = onVescStopScan,
                                onPermissionDenied = onVescPermissionDenied,
                                onConnectGatt = onBleConnectGatt,
                                onStartMavlinkReceive = onBleStartMavlinkReceive,
                                onDisconnectGatt = onBleDisconnect,
                                udpLinkOpen = connection.session.endpoint != null || vescDiscovery.gattConnecting || vescDiscovery.gattConnected,
                            )
                        }
                        composable("spray") { SprayScreen(vehicle) }
                        composable("hydraulic") { HydraulicScreen(vehicle) }
                        composable("diagnostics") { DiagnosticsScreen(vehicle, connection = connection) }
                        composable("admin") { AdminScreen() }
                        composable("settings") {
                            SettingsScreen(
                                state = settings, connection = connection,
                                onThemeSelected = onThemeSelected,
                                onRemoteHostChanged = onRemoteHostChanged, onRemotePortChanged = onRemotePortChanged,
                                onLocalPortChanged = onLocalPortChanged, onSaveEndpoint = onSaveEndpoint,
                                onOpenSocket = onOpenSocket, onCloseSocket = onCloseSocket,
                                onClearEndpoint = onClearEndpoint,
                                bleLinkOpen = vescDiscovery.gattConnecting || vescDiscovery.gattConnected,
                            )
                        }
                    }
                }
            }
        }
        }
    }
    if (showVehicles) {
        AlertDialog(
            onDismissRequest = { showVehicles = false }, title = { Text("Vehicle selection") },
            text = {
                Text(
                    if (vehicle.connected)
                        "Receiving telemetry from system ${vehicle.systemId}, component ${vehicle.componentId}. This is an observed MAVLink identity. Secure vehicle pairing is not implemented yet."
                    else "No live vehicle is connected. Configure the telemetry link in Connection settings. Secure vehicle pairing is not implemented yet."
                )
            },
            confirmButton = { TextButton(onClick = { showVehicles = false; navigate("settings") }) { Text("Connection settings") } },
            dismissButton = { TextButton(onClick = { showVehicles = false }) { Text("Close") } },
        )
    }
}

private fun connectionLabel(state: VehicleConnectionState): String = when (state) {
    VehicleConnectionState.DISCONNECTED -> "NOT CONNECTED"
    VehicleConnectionState.CONNECTING -> "CONNECTING"
    VehicleConnectionState.CONNECTED -> "CONNECTED"
    VehicleConnectionState.DEGRADED -> "HEARTBEAT LOST"
    VehicleConnectionState.ERROR -> "CONNECTION ERROR"
}
