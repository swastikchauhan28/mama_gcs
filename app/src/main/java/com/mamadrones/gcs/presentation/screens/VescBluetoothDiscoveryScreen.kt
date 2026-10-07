package com.mamadrones.gcs.presentation.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.mamadrones.gcs.data.transport.bluetooth.BleNotifyCharacteristic
import com.mamadrones.gcs.domain.model.VehicleConnectionState
import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import com.mamadrones.gcs.presentation.components.SubsystemCard
import com.mamadrones.gcs.presentation.dashboard.ConsolePanels
import kotlinx.coroutines.delay

@Composable
fun VescBluetoothDiscoveryScreen(
    state: VescDiscoveryUiState = VescDiscoveryUiState(),
    onStartScan: () -> Unit = {},
    onStopScan: () -> Unit = {},
    onPermissionDenied: () -> Unit = {},
    onConnectGatt: (String, String) -> Unit = { _, _ -> },
    onStartMavlinkReceive: (BleNotifyCharacteristic) -> Unit = {},
    onDisconnectGatt: () -> Unit = {},
    udpLinkOpen: Boolean = false,
    vehicleConnection: VehicleConnectionState = VehicleConnectionState.DISCONNECTED,
    diagnostics: MavlinkDiagnostics = MavlinkDiagnostics(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
            if (event == Lifecycle.Event.ON_STOP) onStopScan()
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
        if (udpLinkOpen) Notice("CLOSE THE OTHER LINK FIRST", "Close the active UDP connection in Link Setup before opening BLE. Only one telemetry session should feed the rover display at a time.")
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
                        modifier = Modifier.heightIn(min = 48.dp),
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
                    onClick = { onStartMavlinkReceive(characteristic) },
                    enabled = !udpLinkOpen && !state.subscribing && state.receivingFrom == null && !state.closing,
                    modifier = Modifier.fillMaxWidth().testTag("ble-receive-${characteristic.characteristicUuid}"),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${if (selected) "SUBSCRIBED · " else "RECEIVE · "}${if (characteristic.supportsIndication) "INDICATE" else "NOTIFY"}", style = MaterialTheme.typography.titleSmall)
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
}

private fun requiredScanPermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    listOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

private const val SCAN_DURATION_MILLIS = 12_000L
