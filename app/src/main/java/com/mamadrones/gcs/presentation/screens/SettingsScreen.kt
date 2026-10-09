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

enum class SettingsSection { ALL, GENERAL, LINK }

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
    modifier: Modifier = Modifier,
    section: SettingsSection = SettingsSection.ALL,
) = ScreenBody(modifier) {
    ScreenHeader(if (section == SettingsSection.LINK) "Communication link" else "General settings",
        if (section == SettingsSection.LINK) "Known-peer UDP · read-only MAVLink telemetry" else "Display and measurement preferences for your rover station")
    if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading preferences…") }
    state.error?.let { Notice("PREFERENCES ERROR", it) }
    if (section != SettingsSection.LINK) {
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
    Notice("ROVER WORKSPACE", "Operate shows the live map and instruments. Plan prepares local routes and field outlines. Rover configuration groups drive safety, motors, spray and hydraulics. Analyze shows received telemetry and link diagnostics.")
    Notice("LOCAL DISPLAY PREFERENCES", "Theme is saved offline. Passwords, credentials and machine commands are not stored in display preferences.")
    }
    if (section != SettingsSection.GENERAL) {
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
            if (!connection.canConfigureEndpoint) Notice("ADMIN SIGN-IN REQUIRED", "Sign in from Menu → Accounts & audit with an administrator account before changing or clearing the saved telemetry peer. A saved endpoint can still be opened for read-only monitoring.")
            OutlinedTextField(
                value = connection.remoteHostDraft,
                onValueChange = onRemoteHostChanged,
                label = { Text("Remote host") },
                singleLine = true,
                enabled = connection.canConfigureEndpoint && !connection.saving,
                modifier = Modifier.fillMaxWidth().testTag("udp-remote-host")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = connection.remotePortDraft,
                    onValueChange = onRemotePortChanged,
                    label = { Text("Remote port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = connection.canConfigureEndpoint && !connection.saving,
                    modifier = Modifier.weight(1f).testTag("udp-remote-port")
                )
                OutlinedTextField(
                    value = connection.localPortDraft,
                    onValueChange = onLocalPortChanged,
                    label = { Text("Local port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = connection.canConfigureEndpoint && !connection.saving,
                    modifier = Modifier.weight(1f).testTag("udp-local-port")
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSaveEndpoint,
                    enabled = connection.canConfigureEndpoint && !connection.saving,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("udp-save-endpoint")
                ) { Text("Save endpoint") }
                OutlinedButton(
                    onClick = onOpenSocket,
                    enabled = connection.savedEndpoint != null && !connection.saving &&
                        connection.session.connection.status == TransportStatus.DISCONNECTED && !bleLinkOpen,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("udp-open-socket")
                ) { Text("Open UDP socket") }
                OutlinedButton(
                    onClick = onCloseSocket,
                    enabled = connection.session.connection.status != TransportStatus.DISCONNECTED,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("udp-close-socket")
                ) { Text("Close socket") }
                TextButton(onClick = onClearEndpoint, enabled = connection.canConfigureEndpoint && connection.savedEndpoint != null && !connection.saving, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear endpoint") }
            }
        }
    }
    connection.error?.let { Notice("UDP ENDPOINT ERROR", it) }
    Notice("MAVLINK SECURITY", "Only the configured UDP peer is accepted. A CRC-valid unsigned HEARTBEAT reports protocol liveness, not authenticated vehicle identity. Signed frames are rejected until signing-key verification is provisioned. This session sends no vehicle commands.")
    Notice("BLUETOOTH AND SERIAL", "Use Bluetooth / HC-05 for a paired Classic serial radio, or Bluetooth / BLE for GATT notifications. Close the active Bluetooth link before opening UDP. These links receive MAVLink only; vehicle commands and live VESC telemetry remain unavailable.")
    }
}

@Composable
fun MapSettingsScreen(onOpenMap: () -> Unit) = ScreenBody {
    ScreenHeader("Maps", "Geographic background and local route overlays")
    SubsystemCard(PanelSpec("Basemap", "MAPLIBRE · MAPTILER", listOf(
        "Source" to "MapTiler Streets", "Network" to "Internet required",
        "Vehicle position" to "Received MAVLink coordinates", "Track" to "Current session",
    ), "Tile loading requires a valid build-time MapTiler key and network access. The map displays its own loading or error state."))
    Button(onClick = onOpenMap, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open map") }
    Notice("OFFLINE REGIONS", "Offline region downloads and alternative basemap selection are not implemented. Do not rely on cached tiles for offline coverage.")
    Notice("PLANNING OVERLAYS", "Waypoints and keep-in outlines are local planning references. They are not uploaded geofences, obstacle detection, or proof that a route is safe to drive.")
}

private fun transportLabel(status: TransportStatus): String = when (status) {
    TransportStatus.DISCONNECTED -> "CLOSED"
    TransportStatus.CONNECTING -> "OPENING"
    TransportStatus.OPEN -> "OPEN · AWAITING MAVLINK"
    TransportStatus.ERROR -> "SOCKET ERROR"
}
