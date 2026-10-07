@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.ThemeMode
import com.mamadrones.gcs.domain.model.TransportStatus
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.settings.ConnectionUiState
import com.mamadrones.gcs.presentation.settings.SettingsUiState

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    connection: ConnectionUiState = ConnectionUiState(),
    onThemeSelected: (ThemeMode) -> Unit,
    onRemoteHostChanged: (String) -> Unit = {},
    onRemotePortChanged: (String) -> Unit = {},
    onLocalPortChanged: (String) -> Unit = {},
    onSaveEndpoint: () -> Unit = {},
    onOpenSocket: () -> Unit = {},
    onCloseSocket: () -> Unit = {},
    onClearEndpoint: () -> Unit = {},
    bleLinkOpen: Boolean = false,
    modifier: Modifier = Modifier
) = ScreenBody(modifier) {
    ScreenHeader("Settings", "Preferences stored on this device")
    if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading preferences…") }
    state.error?.let { Notice("PREFERENCES ERROR", it) }
    Surface(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp).selectableGroup()) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Text("Dark console, daylight, or device setting", color = MaterialTheme.colorScheme.onSurfaceVariant)
            ThemeMode.entries.forEach { theme ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                        selected = theme == state.preferences.theme, enabled = !state.loading && !state.saving,
                        role = Role.RadioButton, onClick = { onThemeSelected(theme) }
                    ), verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = theme == state.preferences.theme, onClick = null, enabled = !state.loading && !state.saving)
                    Text(when (theme) { ThemeMode.DARK -> "Dark"; ThemeMode.LIGHT -> "Light"; ThemeMode.SYSTEM -> "System" }, Modifier.padding(start = 12.dp))
                }
            }
            if (state.saving) Text("Saving…", style = MaterialTheme.typography.bodySmall)
        }
    }
    SubsystemCard(PanelSpec("Units", "METRIC", listOf("Speed" to "m/s", "Distance" to "m / km", "Temperature" to "°C", "Pressure" to "bar")))
    Text("UDP · MAVLINK RECEIVE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    SubsystemCard(
        PanelSpec(
            title = "UDP + MAVLink session",
            status = transportLabel(connection.session.connection.status),
            rows = listOf(
                "Endpoint" to (connection.savedEndpoint?.displayName ?: "NOT CONFIGURED"),
                "Local port" to (connection.savedEndpoint?.localPort?.toString() ?: "UNKNOWN"),
                "Received packets" to connection.session.connection.packetStatistics.receivedPackets.toString(),
                "Transmitted packets" to connection.session.connection.packetStatistics.transmittedPackets.toString()
            ),
            note = connection.session.connection.detail
        )
    )
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("UDP endpoint", style = MaterialTheme.typography.titleMedium)
            Text("Configure a known peer. Tapping Open UDP socket starts the receive session; vehicle liveness appears only after a valid autopilot HEARTBEAT.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = connection.remoteHostDraft,
                onValueChange = onRemoteHostChanged,
                label = { Text("Remote host") },
                singleLine = true,
                enabled = !connection.saving,
                modifier = Modifier.fillMaxWidth().testTag("udp-remote-host")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = connection.remotePortDraft,
                    onValueChange = onRemotePortChanged,
                    label = { Text("Remote port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !connection.saving,
                    modifier = Modifier.weight(1f).testTag("udp-remote-port")
                )
                OutlinedTextField(
                    value = connection.localPortDraft,
                    onValueChange = onLocalPortChanged,
                    label = { Text("Local port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !connection.saving,
                    modifier = Modifier.weight(1f).testTag("udp-local-port")
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSaveEndpoint,
                    enabled = !connection.saving,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("udp-save-endpoint")
                ) { Text("Save endpoint") }
                OutlinedButton(
                    onClick = onOpenSocket,
                    enabled = connection.savedEndpoint != null &&
                        connection.session.connection.status == TransportStatus.DISCONNECTED && !bleLinkOpen,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("udp-open-socket")
                ) { Text("Open UDP socket") }
                OutlinedButton(
                    onClick = onCloseSocket,
                    enabled = connection.session.connection.status != TransportStatus.DISCONNECTED,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("udp-close-socket")
                ) { Text("Close socket") }
                TextButton(onClick = onClearEndpoint, enabled = connection.savedEndpoint != null && !connection.saving, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear endpoint") }
            }
        }
    }
    connection.error?.let { Notice("UDP ENDPOINT ERROR", it) }
    Notice("MAVLINK SECURITY", "Only the configured UDP peer is accepted. A CRC-valid unsigned HEARTBEAT reports protocol liveness, not authenticated vehicle identity. Signed frames are rejected until signing-key verification is provisioned. This session sends no vehicle commands.")
    Notice("BLUETOOTH AND SERIAL", "Systems → BLE MAVLink can receive telemetry through a selected GATT notification characteristic. Close BLE before opening UDP. Vehicle commands and live VESC telemetry remain unavailable; Bluetooth Classic / SPP is not supported.")
    Notice("LOCAL DISPLAY PREFERENCES", "Theme is saved offline. Passwords, credentials and machine commands are not stored in display preferences.")
}

private fun transportLabel(status: TransportStatus): String = when (status) {
    TransportStatus.DISCONNECTED -> "CLOSED"
    TransportStatus.CONNECTING -> "OPENING"
    TransportStatus.OPEN -> "OPEN · AWAITING MAVLINK"
    TransportStatus.ERROR -> "SOCKET ERROR"
}
