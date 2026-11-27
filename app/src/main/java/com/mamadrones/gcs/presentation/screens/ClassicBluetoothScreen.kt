package com.mamadrones.gcs.presentation.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mamadrones.gcs.data.transport.bluetooth.ClassicBluetoothPeer
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.dashboard.ConsolePanels
import kotlinx.coroutines.delay

/** The UI tick triggers idle refreshes; current receipts need a clock sampled at composition. */
internal fun classicChecklistElapsedNow(uiTickElapsed: Long, currentElapsed: Long): Long =
    maxOf(uiTickElapsed, currentElapsed)

/** Decoder evidence can supersede the socket-opening detail without mutating transport state. */
internal fun classicSerialLinkDetail(connection: ConnectionState, check: ClassicConnectionAssessment): String = when {
    connection.status != TransportStatus.OPEN -> connection.detail ?: "Classic serial session not open."
    check.freshHeartbeat -> "Fresh autopilot heartbeat received in this Classic session. Receive-only evidence."
    check.stage == ClassicCheckStage.BYTES_STALE -> "No recent serial bytes in this Classic session; previous telemetry may be stale."
    check.stage == ClassicCheckStage.WAITING_HEARTBEAT -> "Autopilot heartbeat not confirmed from current serial data."
    else -> connection.detail ?: "Waiting for serial data."
}

@Composable
fun ClassicBluetoothScreen(
    state: ClassicBluetoothUiState,
    onRefresh: () -> Unit,
    onPermissionDenied: () -> Unit,
    onConnect: (ClassicBluetoothPeer) -> Unit,
    onDisconnect: () -> Unit,
    otherLinkOpen: Boolean,
    vehicle: VehicleState,
    evidenceControls: @Composable () -> Unit = {},
    onNtripStart: (String, Boolean, Boolean) -> String? = { _, _, _ -> "NTRIP is unavailable." },
    onNtripStop: () -> Unit = {},
) {
    val diagnostics = vehicle.mavlinkDiagnostics
    val context = LocalContext.current
    var selected by remember { mutableStateOf<ClassicBluetoothPeer?>(null) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var showNtripPrompt by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onRefresh() else onPermissionDenied()
    }
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(1_000L); value = System.currentTimeMillis() }
    }
    val nowElapsed by produceState(android.os.SystemClock.elapsedRealtime()) {
        while (true) { delay(1_000L); value = android.os.SystemClock.elapsedRealtime() }
    }
    // New packets can be newer than the one-second UI tick. Keep strict future-receipt rejection
    // in ClassicConnectionCheck, but evaluate it against a fresh monotonic sample.
    val check = ClassicConnectionCheck.assess(state.link.active, state.link.connection, vehicle,
        now, classicChecklistElapsedNow(nowElapsed, android.os.SystemClock.elapsedRealtime()))
    ScreenBody {
        ScreenHeader("Bluetooth Classic / HC-05", "Paired serial radio · MAVLink telemetry and optional RTK corrections")
        Notice("CONNECT YOUR REMOTE", "Pair the identified T12 or HC-05 serial radio in Android Bluetooth settings, return here and refresh. Select its name and address. The Flipsky NRF51 module uses a separate BLE connection.")
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
        ClassicConnectionCheckPanel(check)
        evidenceControls()
        state.link.peer?.let { peer ->
            SubsystemCard(PanelSpec("Classic serial link", state.link.connection.status.name,
                listOf("Device" to peer.name, "Address" to peer.address,
                    "Received chunks" to state.link.connection.packetStatistics.receivedPackets.toString(),
                    "Vehicle commands" to "DISABLED",
                    "MAVLink heartbeat" to if (check.freshHeartbeat) "FRESH" else "NOT CONFIRMED"),
                classicSerialLinkDetail(state.link.connection, check)))
        }
        if (diagnostics.linkKind == TelemetryLinkKind.CLASSIC && diagnostics.startedAtEpochMillis != null) {
            SubsystemCard(ConsolePanels.mavlinkDiagnostics(diagnostics, now))
            Notice("DECODER GUIDANCE", ConsolePanels.mavlinkDiagnosticHints(diagnostics))
        }
        HorizontalDivider()
        Text("RTK / GPS inject · NTRIP", style = MaterialTheme.typography.titleMedium)
        Notice("CORRECTIONS", "Enter one caster URL when prompted. Validated RTCM is sent only as MAVLink GPS_RTCM_DATA to this Bluetooth session after a fresh Cube heartbeat. Sent does not mean accepted or RTK fixed.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showNtripPrompt = true }, enabled = !state.ntrip.active,
                modifier = Modifier.testTag("classic-ntrip-start")) { Text("Connect NTRIP") }
            OutlinedButton(onClick = onNtripStop, enabled = state.ntrip.active,
                modifier = Modifier.testTag("classic-ntrip-stop")) { Text("Stop") }
        }
        state.ntrip.target?.let { Text("Caster: $it", modifier = Modifier.testTag("classic-ntrip-target")) }
        if (state.ntrip.active) Text(if (state.ntrip.sendsGga) "VRS GGA: on" else "VRS GGA: off")
        Text("NTRIP: ${state.ntrip.status}", modifier = Modifier.testTag("classic-ntrip-status"))
        Text("Validated RTCM sent: ${state.ntrip.sent} · Rejected: ${state.ntrip.rejected}",
            modifier = Modifier.testTag("classic-ntrip-counters"))
        Text("Cube-reported GPS fix: ${vehicle.gps.fix.name.replace('_', ' ')}",
            modifier = Modifier.testTag("classic-ntrip-gps-fix"))
        Notice("SESSION SAFETY", "A paired radio and heartbeat do not authenticate the Cube or prove a reverse radio path. Vehicle commands remain unavailable. NTRIP and Bluetooth close when the app goes into the background and do not reconnect automatically.")
    }
    selected?.let { peer ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text("Connect to this remote?") },
            text = { Text("${peer.name}\n${peer.address}\n\nConfirm this is your identified MAVLink radio. Device names and Android pairing do not establish trusted rover identity.") },
            confirmButton = { TextButton(onClick = { selected = null; onConnect(peer) },
                enabled = !otherLinkOpen && !state.link.active) { Text("Start receive") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } })
    }
    if (showNtripPrompt) {
        var remoteUrl by remember { mutableStateOf("") }
        var allowHttp by remember { mutableStateOf(false) }
        var sendGga by remember { mutableStateOf(false) }
        var promptError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showNtripPrompt = false },
            title = { Text("NTRIP remote host") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the caster URL, including username, password, host, port and mountpoint. The URL is used for this session only.")
                    OutlinedTextField(remoteUrl, { if (it.length <= 512) { remoteUrl = it; promptError = null } },
                        label = { Text("NTRIP URL") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("classic-ntrip-url"))
                    if (remoteUrl.startsWith("http://", ignoreCase = true)) {
                        Text("HTTP is unencrypted. Anyone on the network path can read the caster password and any GGA position you choose to send.",
                            color = MaterialTheme.colorScheme.error)
                        Row { Checkbox(allowHttp, { allowHttp = it }, modifier = Modifier.testTag("classic-ntrip-http-opt-in"))
                            Text("Allow unencrypted HTTP for this session") }
                    }
                    Row { Checkbox(sendGga, { sendGga = it }, modifier = Modifier.testTag("classic-ntrip-gga"))
                        Text("Send fresh Cube GPS position as VRS GGA") }
                    Text("GGA shares rover position with the caster. Requires a fresh 3D or better Cube GPS fix.")
                    promptError?.let { Text(it, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("classic-ntrip-prompt-error")) }
                }
            },
            confirmButton = { TextButton(onClick = {
                val error = onNtripStart(remoteUrl, allowHttp, sendGga)
                if (error == null) showNtripPrompt = false else promptError = error
            }, modifier = Modifier.testTag("classic-ntrip-confirm")) { Text("Connect") } },
            dismissButton = { TextButton(onClick = { showNtripPrompt = false }) { Text("Cancel") } },
        )
    }
}
