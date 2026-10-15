package com.mamadrones.gcs.presentation.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mamadrones.gcs.presentation.components.Notice
import com.mamadrones.gcs.presentation.components.ScreenBody
import com.mamadrones.gcs.presentation.components.ScreenHeader
import com.mamadrones.gcs.data.transport.bluetooth.BleReceiveSelection
import com.mamadrones.gcs.data.local.datastore.BleStabilityRun
import com.mamadrones.gcs.domain.model.VehicleConnectionState
import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import com.mamadrones.gcs.presentation.components.SubsystemCard
import com.mamadrones.gcs.presentation.components.BleConnectionCheckPanel
import com.mamadrones.gcs.domain.model.BleConnectionCheck
import com.mamadrones.gcs.domain.model.BleReceiveEvidence
import com.mamadrones.gcs.presentation.dashboard.ConsolePanels
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun VescBluetoothDiscoveryScreen(
    state: VescDiscoveryUiState = VescDiscoveryUiState(),
    onStartScan: () -> Unit = {},
    onStopScan: () -> Unit = {},
    onPermissionDenied: () -> Unit = {},
    onConnectGatt: (String, String) -> Unit = { _, _ -> },
    onStartMavlinkReceive: (BleReceiveSelection) -> Unit = {},
    onDisconnectGatt: () -> Unit = {},
    onStartStabilityTrial: () -> Unit = {},
    onStopStabilityTrial: () -> Unit = {},
    onResetStabilityTrial: () -> Unit = {},
    udpLinkOpen: Boolean = false,
    vehicleConnection: VehicleConnectionState = VehicleConnectionState.DISCONNECTED,
    diagnostics: MavlinkDiagnostics = MavlinkDiagnostics(),
    modifier: Modifier = Modifier,
    reportControls: @Composable () -> Unit = {},
    lastHeartbeatAtEpochMillis: Long? = null,
) {
    val context = LocalContext.current
    var pendingReceive by remember { mutableStateOf<BleReceiveSelection?>(null) }
    val receiveBusy = udpLinkOpen || state.gattConnecting || state.subscribing || state.closing || state.receivingFrom != null
    val selectionCurrent = pendingReceive?.isCurrent(state.successfulConnections, state.gattConnected,
        receiveBusy, state.notifyCharacteristics) == true
    LaunchedEffect(selectionCurrent) {
        if (!selectionCurrent) pendingReceive = null
    }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000L)
            value = System.currentTimeMillis()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissions = remember { requiredScanPermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.all { it }) onStartScan() else onPermissionDenied()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                pendingReceive = null
                onStopScan()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onStopScan()
        }
    }
    LaunchedEffect(state.scanning) {
        if (state.scanning) {
            delay(SCAN_DURATION_MILLIS)
            onStopScan()
        }
    }

    ScreenBody(modifier) {
        ScreenHeader("BLE MAVLink link", "Inspect a rover radio and receive telemetry")
        BleConnectionCheckPanel(BleConnectionCheck.assess(
            BleReceiveEvidence(connected = state.gattConnected, connecting = state.gattConnecting,
                closing = state.closing, hasError = state.gattError != null,
                receiveOptionAvailable = state.notifyCharacteristics.isNotEmpty(),
                subscribing = state.subscribing, subscribed = state.receivingFrom != null,
                connectedAtEpochMillis = state.connectedAtEpochMillis,
                notifications = state.receivedNotifications, bytes = state.receivedBytes,
                lastBytesAtEpochMillis = state.lastReceivedAtEpochMillis),
            diagnostics, vehicleConnection, lastHeartbeatAtEpochMillis, now))
        Notice(
            "READ-ONLY · NO VEHICLE COMMANDS",
            "Connect to a BLE device, inspect its GATT notification characteristics, then select the one carrying MAVLink bytes. The app only receives telemetry in this phase; it does not send commands or VESC data.",
        )
        Notice(
            "BLE VS CLASSIC",
            "This scan finds BLE advertisements only. A Bluetooth Classic / SPP module will not appear here. Close VESC Tool before scanning if it is connected; some modules stop advertising during an active connection. Android may also omit some beacon-style advertisements because location is not used.",
        )
        Notice(
            "SCAN PERMISSION",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                "Android will ask for Nearby devices access. On Android 11 and earlier, the system requires Location permission for BLE scanning; Mama GCS does not read or save your location."
            else "Android will ask for Location permission because this version requires it for BLE scanning; Mama GCS does not read or save your location.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val missing = permissions.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isEmpty()) onStartScan() else permissionLauncher.launch(missing.toTypedArray())
                },
                enabled = !state.scanning && state.bleSupported,
                modifier = Modifier.heightIn(min = 48.dp).testTag("vesc-ble-scan"),
            ) { Text(if (state.finished) "Scan again" else "Scan for BLE devices") }
            if (state.scanning) OutlinedButton(
                onClick = onStopScan,
                modifier = Modifier.heightIn(min = 48.dp).testTag("vesc-ble-stop"),
            ) { Text("Stop scan") }
        }
        if (!state.bleSupported) {
            Notice("BLE UNAVAILABLE", "This phone does not report BLE support. Bluetooth Classic devices cannot be scanned here.")
        } else if (state.scanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Scanning for up to 12 seconds…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.error?.let { Notice("SCAN STATUS", it) }
        if (udpLinkOpen) Notice("CLOSE THE OTHER LINK FIRST", "Close the active UDP or Bluetooth Classic connection before opening BLE. Only one telemetry session can feed the rover display at a time.")
        when {
            state.scanning && state.advertisements.isEmpty() -> Text("No BLE advertisements found yet.")
            state.finished && state.advertisements.isEmpty() && state.error == null -> Text(
                "No BLE advertisements found. This does not rule out a Bluetooth Classic module or a BLE module that is connected, asleep, or not advertising."
            )
        }
        state.advertisements.forEach { result ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(result.name, style = MaterialTheme.typography.titleMedium)
                    Text("Signal ${result.rssiDbm} dBm", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (result.serviceUuids.isEmpty()) "No service UUID advertised"
                        else "Advertised services: ${result.serviceUuids.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { onConnectGatt(result.key, result.name) },
                        enabled = !udpLinkOpen && !state.gattConnecting && !state.gattConnected && !state.closing,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("ble-connect-${result.key}"),
                    ) { Text("Connect and inspect GATT") }
                }
            }
        }
        if (state.gattConnecting) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Connecting and discovering BLE services…")
        }
        if (state.gattConnecting || state.gattConnected || state.closing) {
            OutlinedButton(onClick = onDisconnectGatt, enabled = !state.closing,
                modifier = Modifier.heightIn(min = 48.dp).testTag("ble-disconnect")) {
                Text(if (state.closing) "Closing BLE…" else if (state.gattConnecting) "Cancel connection" else "Disconnect BLE")
            }
        }
        state.gattError?.let { Notice("BLE LINK STATUS", it) }
        if (state.selectedDeviceName != null || state.successfulConnections > 0) {
            val connectedFor = state.connectedAtElapsedMillis?.let { SystemClock.elapsedRealtime() - it }
            val stabilityDetails = buildList {
                add(connectedFor?.let { "Current connection: ${formatConnectionDuration(it)}" }
                    ?: "Current connection: not connected")
                add("Successful connections this app run: ${state.successfulConnections}")
                add("Unexpected disconnects: ${state.unexpectedDisconnects}")
                state.lastSessionDurationMillis?.let { add("Previous session: ${formatConnectionDuration(it)}") }
                state.lastDisconnectReason?.let { add("Last disconnect: $it") }
            }.joinToString(" · ")
            Notice("BLE CONNECTION STABILITY", stabilityDetails)
        }
        when (state.stabilityTrial.status) {
            BleStabilityTrialStatus.IDLE -> Notice(
                "10-MINUTE STABILITY TEST",
                "Start while T12_485 is GATT connected. The test watches the foreground connection and ends if the link drops or the app is backgrounded.",
            )
            BleStabilityTrialStatus.RUNNING -> {
                val remaining = (BLE_STABILITY_TARGET_MILLIS - state.stabilityTrial.elapsedMillis).coerceAtLeast(0L)
                Notice("STABILITY TEST RUNNING · ${formatConnectionDuration(remaining)} LEFT",
                    "Keep Mama GCS open and the phone unlocked. An unexpected GATT disconnect marks this run failed.")
                LinearProgressIndicator(
                    progress = { (state.stabilityTrial.elapsedMillis.toFloat() / BLE_STABILITY_TARGET_MILLIS).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = onStopStabilityTrial, modifier = Modifier.heightIn(min = 48.dp)
                    .testTag("ble-stability-stop")) { Text("Stop stability test") }
            }
            BleStabilityTrialStatus.PASSED -> {
                Notice("STABILITY TEST PASSED · 10 MINUTES",
                    "The T12 GATT connection stayed open for the full foreground test. This checks connection continuity only.")
                OutlinedButton(onClick = onResetStabilityTrial, modifier = Modifier.heightIn(min = 48.dp)
                    .testTag("ble-stability-reset")) { Text("Run test again") }
            }
            BleStabilityTrialStatus.FAILED -> {
                Notice("STABILITY TEST FAILED · LINK DROPPED",
                    state.lastDisconnectReason ?: state.gattError ?: "The T12 GATT connection ended before ten minutes.")
                OutlinedButton(onClick = onResetStabilityTrial, modifier = Modifier.heightIn(min = 48.dp)
                    .testTag("ble-stability-reset")) { Text("Retry test") }
            }
            BleStabilityTrialStatus.STOPPED -> {
                Notice("STABILITY TEST STOPPED",
                    "The test ended before ten minutes. Connect to T12_485 and start again when ready.")
                OutlinedButton(onClick = onResetStabilityTrial, modifier = Modifier.heightIn(min = 48.dp)
                    .testTag("ble-stability-reset")) { Text("Run test again") }
            }
        }
        state.stabilityHistoryError?.let { Notice("STABILITY HISTORY", it) }
        if (state.stabilityHistorySaving) Text("Saving stability test…")
        if (state.stabilityTrial.status == BleStabilityTrialStatus.IDLE) {
            Button(
                onClick = onStartStabilityTrial,
                enabled = state.gattConnected && state.stabilityHistoryReady && !state.stabilityHistorySaving && !state.closing && !udpLinkOpen,
                modifier = Modifier.heightIn(min = 48.dp).testTag("ble-stability-start"),
            ) { Text("Start 10-minute stability test") }
        }
        if (state.savedStabilityRuns.isNotEmpty()) {
            Notice("SAVED STABILITY TESTS", "The latest ${state.savedStabilityRuns.size.coerceAtMost(5)} test results are stored on this phone.")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.savedStabilityRuns.takeLast(5).asReversed().forEach { run ->
                    Text(formatRunSummary(run), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        reportControls()
        if (state.gattConnected) {
            Notice(
                when {
                    state.subscribing -> "ENABLING NOTIFICATIONS"
                    state.receivingFrom == null -> "GATT CONNECTED · TELEMETRY NOT STARTED"
                    state.receivedNotifications == 0L -> "SUBSCRIBED · WAITING FOR BYTES"
                    else -> "BLE BYTES RECEIVED · CHECK MAVLINK STATUS"
                },
                "${state.selectedDeviceName ?: "BLE device"}. Select a notify/indicate characteristic only if the hardware team confirms it carries MAVLink serial data. A GATT connection alone does not prove vehicle identity or valid telemetry.",
            )
            if (state.subscribing) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.notifyCharacteristics.forEach { characteristic ->
                val selected = state.receivingFrom == characteristic
                OutlinedCard(
                    onClick = { pendingReceive = BleReceiveSelection(characteristic, state.successfulConnections) },
                    enabled = !receiveBusy && state.successfulConnections > 0,
                    modifier = Modifier.fillMaxWidth().testTag("ble-receive-${characteristic.characteristicUuid}"),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${if (selected) "SUBSCRIBED · " else "REVIEW RECEIVE · "}${if (characteristic.supportsIndication) "INDICATE" else "NOTIFY"}", style = MaterialTheme.typography.titleSmall)
                        Text("Service ${characteristic.serviceUuid}", style = MaterialTheme.typography.bodySmall)
                        Text("Characteristic ${characteristic.characteristicUuid}", style = MaterialTheme.typography.bodySmall)
                        Text("Instances ${characteristic.serviceInstanceId}/${characteristic.characteristicInstanceId}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state.notifyCharacteristics.isEmpty()) {
                Notice("NO RECEIVE CHARACTERISTIC", "This BLE device exposes no notify/indicate characteristic with a notification configuration descriptor. Ask the hardware team whether its link uses BLE notifications, Bluetooth Classic/SPP, or another route.")
            }
        }
        if (state.selectedDeviceName != null) {
            Notice("BLE RECEIVE COUNTERS",
                "Notifications: ${state.receivedNotifications} · Bytes: ${state.receivedBytes}. These are BLE chunks, not MAVLink message counts. Transmit is disabled.")
            Text("Last bytes: ${com.mamadrones.gcs.presentation.dashboard.sampleAge(state.lastReceivedAtEpochMillis, now)}")
            Notice("MAVLINK HEARTBEAT",
                if (state.receivingFrom != null || state.subscribing) "Status: ${vehicleConnection.name}. Only a valid autopilot heartbeat establishes telemetry liveness; it does not authenticate the rover."
                else "Telemetry receive is not active. Reconnect and select the confirmed characteristic to retry.")
        }
        if (diagnostics.linkKind == TelemetryLinkKind.BLE && diagnostics.startedAtEpochMillis != null) {
            SubsystemCard(ConsolePanels.mavlinkDiagnostics(diagnostics, now))
            Notice("DECODER GUIDANCE", ConsolePanels.mavlinkDiagnosticHints(diagnostics))
        }
        Notice(
            "NEXT HARDWARE EVIDENCE",
            "If telemetry does not appear, send the selected device name, service UUID, characteristic UUID, Cube telemetry-port wiring, baud rate, and ArduPilot Rover version to the hardware team. VESC telemetry still needs its own confirmed route and protocol.",
        )
    }
    val selection = pendingReceive
    if (selection != null && selectionCurrent) {
        AlertDialog(onDismissRequest = { pendingReceive = null },
            title = { Text("Confirm BLE telemetry receive") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Device: ${state.selectedDeviceName ?: "Selected BLE device"}")
                    Text("Service: ${selection.characteristic.serviceUuid}")
                    Text("Characteristic: ${selection.characteristic.characteristicUuid}")
                    Text("Instances: ${selection.characteristic.serviceInstanceId}/${selection.characteristic.characteristicInstanceId}")
                    Text("Receive method: ${if (selection.characteristic.supportsIndication) "INDICATE" else "NOTIFY"}")
                    Text("Start only if the hardware team has confirmed this characteristic carries MAVLink. UUIDs and device names do not verify its protocol or identity. For remote-only stability testing, cancel; no subscription is needed.")
                    Text("Starting enables Android notifications and writes the BLE notification configuration descriptor. No MAVLink, motor or vehicle command is sent. This confirmation expires when the connection changes or the app leaves the foreground.")
                }
            },
            confirmButton = { TextButton(onClick = {
                val request = pendingReceive
                pendingReceive = null
                if (request != null && request.isCurrent(state.successfulConnections, state.gattConnected,
                        receiveBusy, state.notifyCharacteristics)) onStartMavlinkReceive(request)
            }, modifier = Modifier.testTag("ble-confirm-receive")) { Text("Start receive") } },
            dismissButton = { TextButton(onClick = { pendingReceive = null },
                modifier = Modifier.testTag("ble-cancel-receive")) { Text("Cancel") } })
    }
}

private fun requiredScanPermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    listOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

private fun formatConnectionDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private fun formatRunSummary(run: BleStabilityRun): String {
    val time = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(run.finishedAtEpochMillis))
    val reason = run.disconnectReason?.let { " · $it" }.orEmpty()
    return "$time · ${run.outcome} · ${formatConnectionDuration(run.durationMillis)}$reason"
}

private const val SCAN_DURATION_MILLIS = 12_000L
private const val BLE_STABILITY_TARGET_MILLIS = 10 * 60 * 1_000L
