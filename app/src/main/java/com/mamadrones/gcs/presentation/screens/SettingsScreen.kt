@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.ThemeMode
import com.mamadrones.gcs.domain.model.BasemapStyle
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.BuildConfig
import com.mamadrones.gcs.domain.model.TransportStatus
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.settings.ConnectionUiState
import com.mamadrones.gcs.presentation.settings.SettingsUiState
import com.mamadrones.gcs.presentation.map.OfflineFieldArea
import com.mamadrones.gcs.presentation.map.OfflineFieldMaps
import com.mamadrones.gcs.presentation.map.completePackBoundaryRect

enum class SettingsSection { ALL, GENERAL, LINK }

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
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
    Notice("BLUETOOTH AND SERIAL", "Use Bluetooth / HC-05 for a paired Classic serial radio, or Bluetooth / BLE for GATT notifications. Close the active Bluetooth link before opening UDP. Classic can send only explicitly started RTCM corrections after a fresh Cube heartbeat; vehicle commands and live VESC telemetry remain unavailable.")
    }
}

@Composable
fun MapSettingsScreen(
    state: SettingsUiState = SettingsUiState(),
    onBasemapSelected: (BasemapStyle) -> Unit = {},
    draft: MissionDraft = MissionDraft(),
    onViewSavedArea: (Long) -> Unit = {},
    onOpenMap: () -> Unit,
) = ScreenBody {
    val context = LocalContext.current
    val offline by OfflineFieldMaps.state.collectAsState()
    LaunchedEffect(Unit) { OfflineFieldMaps.refresh(context) }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    pendingDelete?.let { id ->
        AlertDialog(onDismissRequest = { pendingDelete = null },
            title = { Text("Remove field map?") },
            text = { Text("This removes downloaded map tiles from this device. The mission draft stays saved.") },
            confirmButton = { TextButton(onClick = { OfflineFieldMaps.delete(context, id); pendingDelete = null }) { Text("Remove map") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } })
    }
    ScreenHeader("Maps", "Geographic background and local route overlays")
    if (state.loading) Text("Loading preferences…")
    state.error?.let { Notice("PREFERENCES ERROR", it) }
    Surface(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp).selectableGroup()) {
            Text("Map appearance", style = MaterialTheme.typography.titleMedium)
            Text("Saved on this device for Operate, Map and Plan. No vehicle connection needed.")
            BasemapStyle.entries.forEach { basemap ->
                val enabled = !state.loading && !state.saving
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .testTag("basemap-${basemap.name}")
                    .selectable(selected = state.preferences.basemap == basemap, enabled = enabled,
                        role = Role.RadioButton, onClick = { onBasemapSelected(basemap) }),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.preferences.basemap == basemap, onClick = null, enabled = enabled)
                    Column(Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                        Text(basemap.label)
                        Text(basemap.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state.saving) Text("Saving…")
        }
    }
    val offlineSelected = state.preferences.basemap == BasemapStyle.OFFLINE_FIELD
    SubsystemCard(PanelSpec("Basemap", when { state.preferences.basemap.isGoogle -> "GOOGLE MAPS"; offlineSelected -> "MAPLIBRE · OPENFREEMAP"; else -> "MAPLIBRE · MAPTILER" }, listOf(
        "Source" to "${when { state.preferences.basemap.isGoogle -> "Google"; offlineSelected -> "OpenFreeMap"; else -> "MapTiler" }} ${state.preferences.basemap.label}", "Network" to if (offlineSelected) "Saved area works offline after download" else "Internet required",
        "Vehicle position" to "Received MAVLink coordinates", "Track" to "Current session",
    ), if (state.preferences.basemap.isGoogle) {
        if (BuildConfig.GOOGLE_MAPS_CONFIGURED) "Google Maps requires Google Play services, a restricted build-time key, enabled billing and internet. MapLibre remains available above."
        else "Google Maps key is missing from this build. Add GOOGLE_MAPS_API_KEY to ignored local.properties and rebuild, or select MapLibre."
    } else if (offlineSelected) "Download the current plan area below before field use. Outside saved bounds or zoom levels, tiles may need internet."
    else "Tile loading requires a valid build-time MapTiler key and network access. The map displays its own loading or error state."))
    val area = OfflineFieldArea.fromDraft(draft)
    val matchingPack = area?.let { field -> offline.packs.firstOrNull { it.bounds.contains(field.bounds) } }
    val ready = matchingPack?.complete == true
    Surface(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Offline field maps", style = MaterialTheme.typography.titleMedium)
            Text("Uses the current Plan outline, or route if no outline exists. OpenFreeMap vector tiles, zoom 12–18. Download on Wi-Fi before field use; no Google tiles are saved.")
            Text(when {
                offline.loading -> "Checking saved field maps…"
                area == null -> "Add a small outline or route in Plan first. Large areas cannot be downloaded."
                ready -> "READY OFFLINE for this plan area at zoom 12–18. Check it in airplane mode before field use."
                else -> "Current plan area: up to ${area.estimatedTiles} estimated tiles. Not ready offline."
            }, modifier = Modifier.testTag("offline-field-readiness"))
            Button(onClick = { OfflineFieldMaps.download(context, draft) },
                enabled = area != null && !ready && !offline.downloading && !offline.loading,
                modifier = Modifier.heightIn(min = 48.dp).testTag("offline-field-download")) {
                Text(when {
                    offline.downloading -> "Downloading…"
                    matchingPack != null && !matchingPack.complete -> "Resume current plan area"
                    else -> "Save current plan area"
                })
            }
            if (offline.downloading) {
                TextButton(onClick = { OfflineFieldMaps.cancel(context) }, enabled = !offline.cancelling,
                    modifier = Modifier.testTag("offline-field-cancel")) {
                    Text(if (offline.cancelling) "Cancelling…" else "Cancel download")
                }
            }
            TextButton(onClick = { OfflineFieldMaps.refresh(context) }, enabled = !offline.downloading,
                modifier = Modifier.testTag("offline-field-refresh")) { Text("Refresh saved maps") }
            offline.message?.let { Text(it, modifier = Modifier.testTag("offline-field-message")) }
            offline.packs.forEach { pack ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${pack.name}: ${pack.progressLabel} · ${pack.bytes / 1_000_000} MB",
                            modifier = Modifier.testTag("offline-field-pack-${pack.id}"))
                        if (!pack.complete && pack.requiredPrecise && pack.requiredResources > 0) {
                            LinearProgressIndicator(progress = {
                                (pack.resources.toFloat() / pack.requiredResources).coerceIn(0f, 1f)
                            }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (!pack.complete) {
                            TextButton(onClick = { OfflineFieldMaps.resume(context, pack.id) },
                                enabled = !offline.downloading && !offline.loading,
                                modifier = Modifier.testTag("offline-field-resume-${pack.id}")) { Text("Resume") }
                        }
                        if (completePackBoundaryRect(pack) != null) {
                            TextButton(onClick = { onViewSavedArea(pack.id) }, enabled = !offline.loading,
                                modifier = Modifier.testTag("offline-field-view-${pack.id}")) { Text("View saved area") }
                        }
                        TextButton(onClick = { pendingDelete = pack.id }, enabled = !offline.downloading) { Text("Remove") }
                    }
                }
            }
        }
    }
    Button(onClick = onOpenMap, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open map") }
    Notice("IMAGERY LIMITATIONS", "Satellite imagery is not a live camera feed and may be outdated or offset. It cannot establish obstacle clearance, boundaries or a safe driving surface. Loading depends on the selected map provider, its key and network; a saved choice does not confirm coverage.")
    Notice("OFFLINE LIMIT", "A saved field map covers only its downloaded bounds and zoom levels. Map content is geographic context, not a surveyed field boundary or proof of safe rover travel.")
    Notice("PLANNING OVERLAYS", "Waypoints and keep-in outlines are local planning references. They are not uploaded geofences, obstacle detection, or proof that a route is safe to drive.")
}

private fun transportLabel(status: TransportStatus): String = when (status) {
    TransportStatus.DISCONNECTED -> "CLOSED"
    TransportStatus.CONNECTING -> "OPENING"
    TransportStatus.OPEN -> "OPEN · AWAITING MAVLINK"
    TransportStatus.ERROR -> "SOCKET ERROR"
}
