package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.core.security.DriveSafetyGate
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.dashboard.ConsolePanels
import com.mamadrones.gcs.presentation.dashboard.forDisplay
import com.mamadrones.gcs.presentation.dashboard.sampleAge
import com.mamadrones.gcs.presentation.settings.ConnectionUiState

@Composable
fun MapScreen(state: VehicleState, modifier: Modifier = Modifier) = Column(
    modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    MapWorkspace(state, Modifier.fillMaxWidth().weight(1f))
    Text("MapTiler · Live position & session track. Offline region downloads are not implemented.",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
}

@Composable
fun ControlScreen(state: VehicleState, modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Drive control", "Forward / reverse · ArduPilot Rover")
    val assessment = DriveSafetyGate.assess()
    var showChecks by rememberSaveable { mutableStateOf(false) }
    Notice(
        "DRIVE LOCKED · MONITORING ONLY",
        "Command transmission and deadman control are not implemented. The app cannot stop the vehicle. Use its independent physical safety system.",
    )
    UnavailableActions("Forward", "Reverse", "Stop", "Arm", "Disarm", "Set mode", "Set speed")
    TextButton(onClick = { showChecks = !showChecks }, modifier = Modifier.heightIn(min = 48.dp).testTag("drive-safety-details")) {
        Text(if (showChecks) "Hide safety requirements" else "Why locked? · ${assessment.blockers.size} unverified requirements")
    }
    if (showChecks) SubsystemCard(PanelSpec(
        title = "Drive safety gate", status = "LOCKED",
        rows = assessment.checks.map { it.requirement.description to if (it.satisfied) "VERIFIED" else "REQUIRED" },
        note = "These are evidence checks, not user-overridable switches. No command route or verified safety evidence is connected.",
    ))
    SubsystemCard(ConsolePanels.vehicle(state))
}

@Composable
fun HealthScreen(state: VehicleState, modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Vehicle health", "Telemetry evidence and readiness gate")
    CardGrid(listOf(ConsolePanels.health(state), ConsolePanels.healthBlockers(state), ConsolePanels.gps(state), ConsolePanels.battery(state), ConsolePanels.systemStatus(state)))
    Notice("READINESS NOT ASSESSED", "Received MAVLink data is shown as evidence only. Hardware-specific limits, expected telemetry rates, and validated subsystem routes are required before health or safety readiness can be assessed.")
}

@Composable
fun MotorScreen(state: VehicleState, modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Motors", "VESC drive system")
    Notice("HARDWARE INTEGRATION REQUIRED", "A read-only VESC telemetry reducer is ready, but controller models, identities, wiring, protocol and trusted telemetry route must be confirmed. No VESC input or motor commands are connected.")
    // VESC may use an independent route; MAVLink heartbeat loss must not rewrite its status.
    val motors = state.motors
    CardGrid(if (motors.isEmpty()) listOf(ConsolePanels.motors(state)) else motors.map { ConsolePanels.motor(it) })
}

@Composable
fun SprayScreen(state: VehicleState, modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Spray", "Pump, nozzles and application flow")
    SubsystemCard(ConsolePanels.spray(state))
    Notice("HARDWARE INTEGRATION REQUIRED", "Pump output mapping, nozzle addressing, pressure/flow sensors and safety interlocks are unconfirmed. Spray controls are unavailable.")
    UnavailableActions("Start spray", "Stop spray", "Nozzle control")
}

@Composable
fun HydraulicScreen(state: VehicleState, modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Hydraulic", "Pump, valves and pressure")
    SubsystemCard(ConsolePanels.hydraulic(state))
    Notice("HARDWARE INTEGRATION REQUIRED", "Controller interface, valve mapping, sensors and mechanical interlocks are unconfirmed. Hydraulic controls are unavailable.")
    UnavailableActions("Enable hydraulic", "Disable hydraulic", "Valve control")
}

@Composable
fun DiagnosticsScreen(state: VehicleState, modifier: Modifier = Modifier, connection: ConnectionUiState = ConnectionUiState()) = ScreenBody(modifier) {
    ScreenHeader("Diagnostics", "Vehicle and communication inspection")
    val link = connection.session.connection
    CardGrid(listOf(
        PanelSpec("Communication", link.status.name, listOf(
            "RX packets" to link.packetStatistics.receivedPackets.toString(),
            "TX packets" to link.packetStatistics.transmittedPackets.toString(),
            "Last packet" to sampleAge(link.packetStatistics.lastReceivedAtEpochMillis),
            "Parser errors" to "NOT MEASURED",
            "Transport" to if (connection.savedEndpoint != null) "UDP" else "UNCONFIGURED",
        ), note = "Socket state and packet counts do not prove vehicle liveness or command capability."),
        ConsolePanels.vehicle(state), ConsolePanels.gps(state), ConsolePanels.position(state),
        ConsolePanels.battery(state), ConsolePanels.attitude(state), ConsolePanels.systemStatus(state),
        ConsolePanels.statusTexts(state)
    ))
    Notice("PERSISTENT LOGGING NOT IMPLEMENTED", "The latest in-memory STATUSTEXT messages and telemetry receive ages are shown above. They are not saved or exported, and do not constitute a health assessment.")
    UnavailableActions("Export logs")
}

@Composable
fun AdminScreen(modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Admin", "Local users, pairing and configuration")
    Notice("AUTHENTICATION NOT IMPLEMENTED", "No authenticated user session exists. Administration and vehicle configuration are unavailable.")
    CardGrid(listOf(
        PanelSpec("Access", "SIGNED OUT", listOf("Role" to "NONE", "Configuration access" to "DENIED")),
        PanelSpec("Vehicle pairing", "NO VEHICLES", listOf("Application identity" to "UNASSIGNED", "Transport profile" to "NOT CONFIGURED"))
    ))
    UnavailableActions("Manage users", "Pair vehicle", "Parameters", "Maximum speed")
}
