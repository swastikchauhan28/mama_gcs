package com.mamadrones.gcs.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.presentation.components.ScreenBody
import com.mamadrones.gcs.presentation.components.ScreenHeader

/** Static feature inventory. It must never be interpreted as a live vehicle readiness verdict. */
@Composable
fun CapabilityGuideScreen() = ScreenBody {
    ScreenHeader("Capabilities & limits", "Software guide · reviewed 28 Nov 2026")
    Text(
        "These are app features, not a check of the connected rover. Live measurements and physical readiness must be verified separately.",
        style = MaterialTheme.typography.bodyMedium,
    )
    CapabilitySection("AVAILABLE IN THE APP", listOf(
        "Telemetry and diagnostics" to "Receive selected MAVLink messages over configured TCP, UDP, Bluetooth Classic or BLE links; inspect ages, link counters and reported warnings.",
        "Local mission planning" to "Edit and save routes and field outlines, exchange GeoJSON/GPX files, and preview coverage using tool width and overlap. No route is uploaded to the rover.",
        "Map backgrounds" to "Use configured online MapLibre or Google Maps. Save selected OpenFreeMap field packs for offline viewing; Google Maps is not the offline source.",
        "Optional RTK corrections" to "Forward validated NTRIP RTCM corrections on an active TCP or Bluetooth Classic link when explicitly started. Counters show attempted delivery, not an RTK fix.",
    ))
    CapabilitySection("REQUIRES LIVE PROOF", listOf(
        "Position and accuracy" to "A real GPS fix, correct field location and RTK accuracy depend on the installed Cube, antenna, correction path and field conditions.",
        "Link and equipment" to "Radio return path, correction acceptance, VESC telemetry, spray and hydraulic hardware still need vehicle-specific checks.",
        "Release readiness" to "Signed vehicle identity, safety profile, physical stop, failsafe and operator acceptance are not established by this guide or simulated tests.",
    ))
    CapabilitySection("UNAVAILABLE FOR ROVER CONTROL", listOf(
        "Mission execution" to "The app does not upload or start a mission on the Cube.",
        "Movement and equipment" to "Drive, arm, mode, motor, spray and hydraulic commands are disabled. The app cannot provide an emergency stop.",
    ))
    Text(
        "For current validation evidence and open work, consult the project handover before a release decision.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CapabilitySection(title: String, entries: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        entries.forEach { (label, detail) ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(detail, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
