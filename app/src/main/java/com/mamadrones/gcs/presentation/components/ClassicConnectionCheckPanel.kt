package com.mamadrones.gcs.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.*

@Composable
fun ClassicConnectionCheckPanel(check: ClassicConnectionAssessment) {
    val (status, guidance) = when (check.stage) {
        ClassicCheckStage.CLOSED -> "SESSION CLOSED" to "Pair your identified HC-05 in Android settings, refresh the paired list, then choose Connect for telemetry and confirm Start receive."
        ClassicCheckStage.CONNECTING -> "OPENING BLUETOOTH" to "Keep the T12 powered and nearby. If connection fails, check that another app is not using the remote. You can cancel the connection."
        ClassicCheckStage.ERROR -> "BLUETOOTH ERROR" to "Read the serial-link error below. Check Bluetooth, remote power and other connected apps; reconnect manually when ready."
        ClassicCheckStage.WAITING_SESSION -> "WAITING FOR CURRENT SESSION" to "The socket is open, but current Classic decoder evidence is not available. Data from another link or a closed session is not used here."
        ClassicCheckStage.WAITING_BYTES -> "SOCKET OPEN · WAITING FOR BYTES" to "Bluetooth is open, but no current serial data has reached the decoder. Check T12/R12 power and radio link, and the separate R12 UART-to-Cube telemetry connection. SBUS into RCIN alone does not provide MAVLink telemetry."
        ClassicCheckStage.BYTES_STALE -> "BYTE STREAM STALE" to "No new bytes have reached the decoder within 5 seconds. Check the remote/radio link and Cube power. Old counters are not live reception."
        ClassicCheckStage.WAITING_MAVLINK -> "BYTES ARRIVING · MAVLINK NOT CONFIRMED" to "Serial bytes are arriving, but no recent supported MAVLink 1/2 message passed checksum and payload validation. Unsupported IDs are unverified candidates. Check the T12/R12 telemetry route and serial format before checking RC stream settings."
        ClassicCheckStage.WAITING_HEARTBEAT -> "BYTES ARRIVING · HEARTBEAT MISSING OR STALE" to "Bytes are arriving, but there is no fresh selected-autopilot heartbeat. Review Decoder guidance below for CRC, unsupported or rejected messages. Compare the actual radio settings with the accepted parameter baseline; do not change baud rates or signing blindly."
        ClassicCheckStage.WAITING_MEASUREMENTS -> "HEARTBEAT RECEIVED · WAITING FOR MEASUREMENTS" to "The selected autopilot is sending a fresh heartbeat, but none of the measurement groups checked here is fresh. Keep this page open and review message diagnostics. This app does not request stream rates."
        ClassicCheckStage.RECEIVING -> "LIVE RECEIVE EVIDENCE" to "Fresh heartbeat and measurement messages are arriving in this Classic session. Review their values in All telemetry. This does not independently prove every hop, sensor accuracy, trusted identity or command capability."
    }
    OutlinedCard(Modifier.fillMaxWidth().testTag("classic-connection-check")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("HC-05 connection check", style = MaterialTheme.typography.titleMedium)
            Text(status, Modifier.testTag("classic-check-status"), style = MaterialTheme.typography.titleSmall)
            Text("Expected receive route: Cube → R12 → T12 / HC-05 → phone", style = MaterialTheme.typography.bodySmall)
            Text("1. Bluetooth socket: ${if (check.socketOpen) "OPEN" else "NOT OPEN"}")
            Text("2. Recent bytes: ${if (check.freshBytes) "RECEIVED" else "NOT CONFIRMED"}")
            Text("3. Validated MAVLink: ${if (check.freshDecodedMessage) "RECEIVED" else "NOT CONFIRMED"}")
            Text("4. Autopilot heartbeat: ${if (check.freshHeartbeat) "FRESH" else "NOT CONFIRMED"}")
            Text("5. Measurement messages: ${check.measurementMessages.joinToString().ifEmpty { "NOT CONFIRMED" }}",
                Modifier.testTag("classic-check-measurements"))
            Text("6. RC_CHANNELS from selected autopilot: ${if (check.freshRcChannels) "FRESH" else "NOT CONFIRMED"}",
                Modifier.testTag("classic-check-rc-status"))
            Text(guidance, Modifier.testTag("classic-check-guidance"))
            Text("RC receipt does not prove stick movement, SBUS wiring, neutral position or safe control.",
                style = MaterialTheme.typography.bodySmall)
            Text("5-second display window · receipt is not sensor validity. Read-only check; movement and emergency-stop commands remain unavailable.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
