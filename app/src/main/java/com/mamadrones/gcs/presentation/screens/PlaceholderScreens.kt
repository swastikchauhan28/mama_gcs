package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import com.mamadrones.gcs.core.security.DriveSafetyGate
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.dashboard.ConsolePanels
import com.mamadrones.gcs.presentation.dashboard.forDisplay
import com.mamadrones.gcs.presentation.dashboard.reading
import com.mamadrones.gcs.presentation.dashboard.sampleAge
import com.mamadrones.gcs.presentation.map.VehicleMap
import com.mamadrones.gcs.presentation.settings.ConnectionUiState

@Composable
fun MapScreen(state: VehicleState, onNavigate: (String) -> Unit = {}, modifier: Modifier = Modifier) = Column(
    modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    MapWorkspace(state, Modifier.fillMaxWidth().weight(1f), onNavigate, showOperatorTools = true)
    Text("MapTiler · Live position & session track. Offline region downloads are not implemented.",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
}

@Composable
fun ControlScreen(state: VehicleState, modifier: Modifier = Modifier) {
    val assessment = DriveSafetyGate.assess()
    var showChecks by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wideDrive = maxWidth >= 760.dp && maxHeight >= 480.dp
        if (wideDrive) {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScreenHeader("Rover operations", "Live map and drive telemetry · commands locked")
                    Surface(Modifier.weight(1f).fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        VehicleMap(state.forDisplay(), Modifier.fillMaxSize())
                    }
                    Text("Map position follows the received rover telemetry. It is not a command or navigation target.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(
                    Modifier.widthIn(min = 300.dp, max = 380.dp).fillMaxHeight()
                        .verticalScroll(rememberScrollState()).padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DriveStatusPanel(state)
                    DriveLockPanel(assessment.blockers.size)
                    UnavailableActions("Forward", "Reverse", "Stop", "Arm", "Disarm", "Set mode", "Set speed")
                    SafetyGatePanel(assessment.checks)
                }
            }
        } else {
            ScreenBody(Modifier.fillMaxSize()) {
                ScreenHeader("Rover operations", "Forward / reverse · ArduPilot Rover")
                DriveLockPanel(assessment.blockers.size)
                UnavailableActions("Forward", "Reverse", "Stop", "Arm", "Disarm", "Set mode", "Set speed")
                TextButton(onClick = { showChecks = !showChecks }, modifier = Modifier.heightIn(min = 48.dp).testTag("drive-safety-details")) {
                    Text(if (showChecks) "Hide safety requirements" else "Why locked? · ${assessment.blockers.size} unverified requirements")
                }
                if (showChecks) SafetyGatePanel(assessment.checks)
                DriveStatusPanel(state)
                VehicleMap(state.forDisplay(), Modifier.fillMaxWidth().height(300.dp))
            }
        }
    }
}

@Composable
private fun DriveLockPanel(unverifiedRequirements: Int) {
    Notice(
        "DRIVE LOCKED · MONITORING ONLY",
        "Command transmission and deadman control are not implemented. $unverifiedRequirements drive-safety requirements remain unverified. The app cannot stop the vehicle; use its independent physical safety system.",
    )
}

@Composable
private fun DriveStatusPanel(state: VehicleState) {
    val live = state.forDisplay()
    val base = ConsolePanels.vehicle(live)
    SubsystemCard(base.copy(
        title = "Rover status",
        rows = listOf(
            "Ground speed" to live.speedMetersPerSecond.reading("m/s"),
            "Heading" to live.headingDegrees.reading("°"),
        ) + base.rows,
        note = "${if (live.connected) "Live telemetry" else "No live telemetry"} · ${sampleAge(live.kinematicsLastUpdatedAtEpochMillis)}. Observed state only; no control path is active.",
    ))
}

@Composable
private fun SafetyGatePanel(checks: List<com.mamadrones.gcs.core.security.DriveSafetyCheck>) {
    SubsystemCard(PanelSpec(
        title = "DRIVE SAFETY GATE",
        status = "LOCKED · ${checks.count { !it.satisfied }} REQUIRED",
        rows = checks.map { it.requirement.description to if (it.satisfied) "VERIFIED" else "REQUIRED" },
        note = "These checks are not user-overridable. No trusted evidence provider or command route is connected.",
    ))
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
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            kotlinx.coroutines.delay(1_000L)
            value = System.currentTimeMillis()
        }
    }
    ScreenHeader("Diagnostics", "Vehicle and communication inspection")
    val link = connection.session.connection
    SubsystemCard(ConsolePanels.mavlinkDiagnostics(state.mavlinkDiagnostics, now))
    Notice("DECODER GUIDANCE", ConsolePanels.mavlinkDiagnosticHints(state.mavlinkDiagnostics))
    CardGrid(listOf(
        PanelSpec("UDP socket counters", link.status.name, listOf(
            "RX packets" to link.packetStatistics.receivedPackets.toString(),
            "TX packets" to link.packetStatistics.transmittedPackets.toString(),
            "Last packet" to sampleAge(link.packetStatistics.lastReceivedAtEpochMillis),
            "Transport" to if (connection.savedEndpoint != null) "UDP" else "UNCONFIGURED",
        ), note = "UDP only; BLE raw counters are on the BLE MAVLink screen. Shared decoder counters are shown above. Socket counts do not prove vehicle liveness or command capability."),
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
