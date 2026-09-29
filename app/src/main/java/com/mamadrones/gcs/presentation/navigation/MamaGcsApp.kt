@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mamadrones.gcs.presentation.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.mamadrones.gcs.domain.model.ThemeMode
import com.mamadrones.gcs.domain.model.VehicleConnectionState
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.screens.*
import com.mamadrones.gcs.presentation.settings.SettingsUiState
import com.mamadrones.gcs.presentation.settings.ConnectionUiState

enum class AppDestination(val route: String, val label: String, val icon: ConsoleIcon) {
    DASHBOARD("dashboard", "Home", ConsoleIcon.DASHBOARD), MAP("map", "Map", ConsoleIcon.MAP),
    CONTROL("control", "Control", ConsoleIcon.CONTROL), MISSION("mission", "Mission", ConsoleIcon.MISSION),
    MORE("more", "More", ConsoleIcon.MORE)
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
    onClearEndpoint: () -> Unit = {}
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
            popUpTo(AppDestination.DASHBOARD.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    FlowRow(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("MAMA GCS", style = MaterialTheme.typography.titleLarge)
                        StatusBadge(connectionLabel(vehicle.connectionStatus), vehicle.connectionStatus in setOf(VehicleConnectionState.ERROR, VehicleConnectionState.DEGRADED))
                        OutlinedButton(onClick = { showVehicles = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(vehicle.displayName ?: "Select vehicle · none paired")
                        }
                    }
                }
            },
            bottomBar = {
                if (!wide) NavigationBar {
                    AppDestination.entries.forEach { item ->
                        NavigationBarItem(selected = selected == item, onClick = { navigatePrimary(item) },
                            icon = { MamaIcon(item.icon) }, label = { Text(item.label) })
                    }
                }
            }
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (wide) Surface(Modifier.width(194.dp).fillMaxHeight()) {
                    Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("AGRICULTURAL UGV", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        AppDestination.entries.forEach { item ->
                            NavigationDrawerItem(label = { Text(item.label) }, icon = { MamaIcon(item.icon) }, selected = selected == item, onClick = { navigatePrimary(item) })
                        }
                        HorizontalDivider()
                        Text("LOCAL OPERATIONS", style = MaterialTheme.typography.labelSmall)
                        Text("Vehicle control unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(1f)) {
                    if (route !in AppDestination.entries.map { it.route }) {
                        TextButton(onClick = { navController.popBackStack() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("‹ Back") }
                    }
                    NavHost(navController, startDestination = AppDestination.DASHBOARD.route, modifier = Modifier.weight(1f)) {
                        composable("dashboard") { DashboardScreen(vehicle, navigate) }
                        composable("map") { MapScreen() }
                        composable("control") { ControlScreen(vehicle) }
                        composable("mission") { MissionScreen(vehicle) }
                        composable("more") { MoreScreen(onNavigate = navigate) }
                        composable("health") { HealthScreen(vehicle) }
                        composable("motors") { MotorScreen(vehicle) }
                        composable("spray") { SprayScreen(vehicle) }
                        composable("hydraulic") { HydraulicScreen(vehicle) }
                        composable("diagnostics") { DiagnosticsScreen(vehicle) }
                        composable("admin") { AdminScreen() }
                        composable("settings") {
                            SettingsScreen(
                                state = settings,
                                connection = connection,
                                onThemeSelected = onThemeSelected,
                                onRemoteHostChanged = onRemoteHostChanged,
                                onRemotePortChanged = onRemotePortChanged,
                                onLocalPortChanged = onLocalPortChanged,
                                onSaveEndpoint = onSaveEndpoint,
                                onOpenSocket = onOpenSocket,
                                onCloseSocket = onCloseSocket,
                                onClearEndpoint = onClearEndpoint
                            )
                        }
                    }
                }
            }
        }
    }
    if (showVehicles) {
        AlertDialog(
            onDismissRequest = { showVehicles = false }, title = { Text("Vehicle selection") },
            text = { Text("No vehicles are paired. Pairing is not implemented yet. A vehicle identity and connection profile must be provisioned before operation.") },
            confirmButton = { TextButton(onClick = { showVehicles = false }) { Text("Close") } }
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
