package com.mamadrones.gcs.presentation.screens

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.dashboard.AutopilotVersionPanel
import kotlinx.coroutines.delay

@Composable
fun AutopilotVersionScreen(state: VehicleState, modifier: Modifier = Modifier) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(1_000); value = System.currentTimeMillis() }
    }
    AutopilotVersionContent(state, now, modifier)
}

@Composable
fun AutopilotVersionContent(state: VehicleState, now: Long, modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Autopilot information", "Read-only · AUTOPILOT_VERSION")
    SubsystemCard(AutopilotVersionPanel.panel(state, now))
    Notice("PASSIVE RECEPTION", "Version information appears only if the selected autopilot sends AUTOPILOT_VERSION after its heartbeat. " +
        "The app does not request this message; not received does not mean unsupported firmware. " +
        "Reconnect starts a new review. Disconnected data is labeled as a last observation.")
    Notice("NOT HARDWARE VERIFICATION", "Reported version and release labels are not authentication, compatibility approval or safety evidence. " +
        "No firmware installation, parameter write or vehicle command is available here. Hardware UIDs and capability flags are not retained.")
}
