package com.mamadrones.gcs.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.*

@Composable
fun BleConnectionCheckPanel(check: BleConnectionAssessment) {
    val (status, guidance) = when (check.stage) {
        BleCheckStage.CLOSED -> "BLE DISCONNECTED" to "Scan and connect only to your identified remote. T12_485 is the remote you identified; its name alone does not authenticate it."
        BleCheckStage.CONNECTING -> "CONNECTING / DISCOVERING SERVICES" to "Keep the remote powered and nearby. Wait for service discovery or cancel; this is not a Cube connection."
        BleCheckStage.CLOSING -> "CLOSING BLE" to "Wait for cleanup before reconnecting. Previous counters are historical, not live reception."
        BleCheckStage.ERROR -> "BLE LINK ERROR" to "Read BLE link status below. Check remote power, Bluetooth permission and competing apps. Reconnect manually when ready."
        BleCheckStage.REMOTE_ONLY -> "REMOTE CONNECTED · RECEIVE NOT STARTED" to "Phone-to-remote GATT is open. You can run the connection-only stability test without the Cube or receiver. Select a receive characteristic only after its MAVLink role is confirmed; do not try unknown characteristics."
        BleCheckStage.NO_RECEIVE_CHARACTERISTIC -> "REMOTE CONNECTED · NO RECEIVE OPTION" to "GATT is open, but discovery found no supported notify/indicate characteristic with a configuration descriptor. Connection-only testing is still possible. This does not prove a hardware fault or identify its data protocol."
        BleCheckStage.SUBSCRIBING -> "ENABLING NOTIFICATIONS" to "Waiting for notification setup to complete. Subscription is not proof that bytes or telemetry are arriving."
        BleCheckStage.WAITING_SESSION -> "WAITING FOR CURRENT BLE SESSION" to "No matching active BLE decoder session is available. Old sessions, another transport and inconsistent timestamps cannot provide live evidence here."
        BleCheckStage.WAITING_BYTES -> "SUBSCRIBED · WAITING FOR BYTES" to "No current notification bytes have reached the decoder. With only the remote present, Cube telemetry is not expected. When available, verify the R12/Cube telemetry route; SBUS alone is not MAVLink telemetry. No settings are changed by this check."
        BleCheckStage.BYTES_STALE -> "BLE BYTE STREAM STALE" to "No current byte evidence within 5 seconds at both the BLE receiver and decoder. Old totals do not prove ongoing delivery. Check the data source; this display timeout does not command a stop."
        BleCheckStage.WAITING_MAVLINK -> "BYTES ARRIVING · MAVLINK NOT CONFIRMED" to "Bytes are not necessarily MAVLink. No recent supported message was decoded. Review decoder guidance for checksum errors, unsupported or rejected frames; do not change signing or baud settings blindly."
        BleCheckStage.WAITING_HEARTBEAT -> "MAVLINK DECODED · AUTOPILOT NOT CONFIRMED" to "A supported message was decoded, but a fresh accepted heartbeat from the selected autopilot is missing. Messages from other sources do not establish rover liveness."
        BleCheckStage.RECEIVING -> "LIVE BLE HEARTBEAT EVIDENCE" to "Current BLE bytes, supported MAVLink decoding and a fresh selected-autopilot heartbeat are observed. Check All telemetry separately for sensor readings. This does not verify every radio hop, sensor accuracy, vehicle identity or control capability."
    }
    OutlinedCard(Modifier.fillMaxWidth().testTag("ble-connection-check")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("BLE connection check", style = MaterialTheme.typography.titleMedium)
            Text(status, Modifier.testTag("ble-check-status"), style = MaterialTheme.typography.titleSmall)
            Text("1. Remote GATT: ${if (check.gattOpen) "OPEN" else "NOT CONFIRMED"}")
            Text("2. Notification subscription: ${if (check.subscribed) "ACTIVE" else "NOT CONFIRMED"}")
            Text("3. Recent notification bytes: ${if (check.freshBytes) "RECEIVED" else "NOT CONFIRMED"}")
            Text("4. Recent supported MAVLink: ${if (check.freshDecodedMessage) "DECODED" else "NOT CONFIRMED"}")
            Text("5. Selected-autopilot heartbeat: ${if (check.freshHeartbeat) "FRESH" else "NOT CONFIRMED"}",
                Modifier.testTag("ble-check-heartbeat"))
            Text(guidance, Modifier.testTag("ble-check-guidance"))
            Text("5-second display window, not a safety timeout. Read-only evidence; no subscriptions, stream requests, reconnects or vehicle commands are started by this panel.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
