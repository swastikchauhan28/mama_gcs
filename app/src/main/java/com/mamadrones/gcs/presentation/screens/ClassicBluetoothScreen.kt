package com.mamadrones.gcs.presentation.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mamadrones.gcs.data.transport.bluetooth.ClassicBluetoothPeer
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.dashboard.ConsolePanels
import kotlinx.coroutines.delay

@Composable
fun ClassicBluetoothScreen(
    state: ClassicBluetoothUiState,
    onRefresh: () -> Unit,
    onPermissionDenied: () -> Unit,
    onConnect: (ClassicBluetoothPeer) -> Unit,
    onDisconnect: () -> Unit,
    otherLinkOpen: Boolean,
    vehicleConnection: VehicleConnectionState,
    diagnostics: MavlinkDiagnostics,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<ClassicBluetoothPeer?>(null) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onRefresh() else onPermissionDenied()
    }
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(1_000L); value = System.currentTimeMillis() }
    }
    ScreenBody {
        ScreenHeader("Bluetooth Classic / HC-05", "Paired serial radio · MAVLink receive only")
        Notice("CONNECT YOUR REMOTE", "Pair the identified HC-05 in Android Bluetooth settings, return here and refresh. Select its name and address. The Flipsky NRF51 module uses a separate BLE connection.")
        OutlinedButton(onClick = {
            settingsError = null
            try { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            catch (_: RuntimeException) { settingsError = "Open Bluetooth settings from Android's Settings app." }
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Android Bluetooth settings") }
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else onRefresh()
        }, enabled = !state.link.active, modifier = Modifier.testTag("classic-refresh")) { Text("Refresh paired devices") }
        settingsError?.let { Notice("SETTINGS", it) }
        state.error?.let { Notice("BLUETOOTH STATUS", it) }
        if (otherLinkOpen) Notice("ANOTHER LINK IS OPEN", "Close the active UDP or BLE telemetry link before opening Bluetooth Classic.")
        if (state.loaded && state.peers.isEmpty() && state.error == null) {
            Text("No paired Classic devices found. Pair your remote in Android settings, then refresh.")
        }
        state.peers.forEach { peer ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(peer.name, style = MaterialTheme.typography.titleMedium)
                    Text(peer.address, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { selected = peer }, enabled = !state.link.active && !otherLinkOpen,
                        modifier = Modifier.testTag("classic-connect-${peer.address}")) { Text("Connect for telemetry") }
                }
            }
        }
        if (state.link.active) {
            if (state.link.connection.status == TransportStatus.CONNECTING) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.testTag("classic-disconnect")) {
                Text(if (state.link.connection.status == TransportStatus.CONNECTING) "Cancel connection" else "Disconnect Classic")
            }
        }
        state.link.peer?.let { peer ->
            SubsystemCard(PanelSpec("Classic serial link", state.link.connection.status.name,
                listOf("Device" to peer.name, "Address" to peer.address,
                    "Received chunks" to state.link.connection.packetStatistics.receivedPackets.toString(),
                    "Command transmit" to "DISABLED",
                    "MAVLink heartbeat" to if (state.link.active) vehicleConnection.name else "SESSION CLOSED"),
                state.link.connection.detail ?: "Waiting for serial data."))
        }
        if (diagnostics.linkKind == TelemetryLinkKind.CLASSIC && diagnostics.startedAtEpochMillis != null) {
            SubsystemCard(ConsolePanels.mavlinkDiagnostics(diagnostics, now))
            Notice("DECODER GUIDANCE", ConsolePanels.mavlinkDiagnosticHints(diagnostics))
        }
        Notice("RECEPTION ONLY", "A serial connection is not proof of rover telemetry. A supported MAVLink 1 or 2 autopilot heartbeat is required. This link sends no commands, heartbeat, baud-rate settings or motor values. It closes when the app goes into the background and does not reconnect automatically.")
    }
    selected?.let { peer ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text("Connect to this remote?") },
            text = { Text("${peer.name}\n${peer.address}\n\nConfirm this is your identified MAVLink radio. Device names and Android pairing do not establish trusted rover identity.") },
            confirmButton = { TextButton(onClick = { selected = null; onConnect(peer) },
                enabled = !otherLinkOpen && !state.link.active) { Text("Start receive") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } })
    }
}
