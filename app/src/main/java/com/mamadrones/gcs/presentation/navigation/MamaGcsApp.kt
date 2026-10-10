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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.mamadrones.gcs.presentation.security.AccessViewModel
import com.mamadrones.gcs.data.transport.bluetooth.BleNotifyCharacteristic
import com.mamadrones.gcs.data.transport.bluetooth.ClassicBluetoothPeer

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
    classicBluetooth: ClassicBluetoothUiState = ClassicBluetoothUiState(),
    onClassicRefresh: () -> Unit = {},
    onClassicPermissionDenied: () -> Unit = {},
    onClassicConnect: (ClassicBluetoothPeer) -> Unit = {},
    onClassicDisconnect: () -> Unit = {},
) {
    val navController = rememberNavController()
    var showVehicles by remember { mutableStateOf(false) }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: AppDestination.DASHBOARD.route
    val navigate: (String) -> Unit = { destination ->
        navController.navigate(destination) {
            popUpTo(AppDestination.DASHBOARD.route)
            launchSingleTop = true
        }
    }
    StationShell(vehicle, route, navigate, onVehicleDetails = { showVehicles = true }) {
                    NavHost(navController, startDestination = AppDestination.DASHBOARD.route, modifier = Modifier.fillMaxSize()) {
                        composable("dashboard") { DashboardScreen(vehicle, navigate) }
                        composable("map") { MapScreen(vehicle, navigate) }
                        composable("control") { ControlScreen(vehicle) }
                        composable("mission") { MissionScreen(vehicle, missionPlan, onMissionAction) }
                        composable("more") { MoreScreen(vehicle, onNavigate = navigate) }
                        composable("telemetry") { TelemetryScreen(vehicle) }
                        composable("health") { HealthScreen(vehicle) }
                        composable("parameter-review") { ParameterReviewRoute(hiltViewModel()) }
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
                                udpLinkOpen = connection.session.endpoint != null || classicBluetooth.link.active,
                                vehicleConnection = vehicle.connectionStatus,
                                diagnostics = vehicle.mavlinkDiagnostics,
                            )
                        }
                        composable("classic-bluetooth") {
                            ClassicBluetoothScreen(
                                state = classicBluetooth,
                                onRefresh = onClassicRefresh,
                                onPermissionDenied = onClassicPermissionDenied,
                                onConnect = onClassicConnect,
                                onDisconnect = onClassicDisconnect,
                                otherLinkOpen = connection.session.endpoint != null || vescDiscovery.gattConnecting ||
                                    vescDiscovery.gattConnected || vescDiscovery.closing,
                                vehicleConnection = vehicle.connectionStatus,
                                diagnostics = vehicle.mavlinkDiagnostics,
                            )
                        }
                        composable("spray") { SprayScreen(vehicle) }
                        composable("hydraulic") { HydraulicScreen(vehicle) }
                        composable("diagnostics") {
                            val reportViewModel: TelemetryReportViewModel = hiltViewModel()
                            DiagnosticsScreen(vehicle, connection = connection,
                                reportControls = { TelemetryReportControls(vehicle, reportViewModel) })
                        }
                        composable("admin") {
                            val accessViewModel: AccessViewModel = hiltViewModel()
                            val access = accessViewModel.state.collectAsStateWithLifecycle().value
                            AdminScreen(
                                access = access,
                                onInitializeAdmin = accessViewModel::initializeAdministrator,
                                onSignIn = accessViewModel::signIn,
                                onSignOut = accessViewModel::signOut,
                                onChangePassword = accessViewModel::changePassword,
                                onCreateAccount = accessViewModel::createAccount,
                                onAccountEnabled = accessViewModel::setAccountEnabled,
                                onClearMessage = accessViewModel::clearMessage,
                            )
                        }
                        listOf("settings", "general").forEach { settingsRoute -> composable(settingsRoute) {
                            SettingsScreen(
                                section = if (settingsRoute == "general") SettingsSection.GENERAL else SettingsSection.LINK,
                                state = settings, connection = connection,
                                onThemeSelected = onThemeSelected,
                                onRemoteHostChanged = onRemoteHostChanged, onRemotePortChanged = onRemotePortChanged,
                                onLocalPortChanged = onLocalPortChanged, onSaveEndpoint = onSaveEndpoint,
                                onOpenSocket = onOpenSocket, onCloseSocket = onCloseSocket,
                                onClearEndpoint = onClearEndpoint,
                                bleLinkOpen = vescDiscovery.gattConnecting || vescDiscovery.gattConnected || vescDiscovery.closing || classicBluetooth.link.active,
                            )
                        } }
                        composable("map-settings") { MapSettingsScreen { navigate("map") } }
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
