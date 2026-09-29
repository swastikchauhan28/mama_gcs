package com.mamadrones.gcs.presentation.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.presentation.components.*

@Composable
fun MoreScreen(modifier: Modifier = Modifier, onNavigate: (String) -> Unit) = ScreenBody(modifier) {
    ScreenHeader("Systems", "Subsystems, inspection and local setup")
    listOf(
        Triple("health", "Health", "Vehicle condition and telemetry availability"),
        Triple("motors", "Motors", "VESC temperature, RPM, current and faults"),
        Triple("spray", "Spray", "Pump, nozzles, pressure and flow"),
        Triple("hydraulic", "Hydraulic", "Pump, valves and sensor status"),
        Triple("diagnostics", "Diagnostics", "Communication and vehicle inspection"),
        Triple("admin", "Admin", "Local access, pairing and configuration"),
        Triple("settings", "Settings", "Appearance and device preferences")
    ).forEach { (route, label, detail) ->
        OutlinedCard(onClick = { onNavigate(route) }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
