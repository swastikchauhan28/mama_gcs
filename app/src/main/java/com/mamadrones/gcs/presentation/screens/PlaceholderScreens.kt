package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.dashboard.ConsolePanels
import com.mamadrones.gcs.presentation.dashboard.forDisplay

@Composable
fun MapScreen(modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Map", "Position, route and offline field maps")
    MapWorkspace(Modifier.fillMaxWidth().heightIn(min = 340.dp))
    UnavailableActions("Center vehicle", "Follow vehicle", "Offline regions")
}

@Composable
fun ControlScreen(state: VehicleState, modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Drive control", "Forward / reverse · ArduPilot Rover")
    EmergencyStopButton(Modifier.fillMaxWidth())
    Notice("CONTROL NOT IMPLEMENTED", "Commands are unavailable. Vehicle movement and stop state cannot be confirmed. Use the vehicle's physical safety system.")
    SubsystemCard(ConsolePanels.vehicle(state))
    UnavailableActions("Forward", "Reverse", "Stop", "Arm", "Disarm", "Set mode", "Set speed")
}

@Composable
fun MissionScreen(state: VehicleState, modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Mission", "Waypoint planning and execution")
    Notice("NOT IMPLEMENTED", "No mission has been downloaded. Onboard mission state is unknown.")
    SubsystemCard(PanelSpec("Mission", state.forDisplay().mission.status.name, listOf("Waypoints" to "UNKNOWN", "Current waypoint" to "UNKNOWN", "Progress" to "UNKNOWN")))
    UnavailableActions("New mission", "Upload", "Download", "Start mission", "Pause")
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
    Notice("HARDWARE INTEGRATION REQUIRED", "Controller models, motor count, CAN/UART wiring and telemetry route must be confirmed. Direct VESC communication is not configured.")
    val motors = state.forDisplay().motors
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
fun DiagnosticsScreen(state: VehicleState, modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Diagnostics", "Vehicle and communication inspection")
    CardGrid(listOf(
        PanelSpec("Communication", "NOT CONFIGURED", listOf("RX packets" to "UNKNOWN", "TX packets" to "UNKNOWN", "Parser errors" to "UNKNOWN", "Transport" to "UNSELECTED")),
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
