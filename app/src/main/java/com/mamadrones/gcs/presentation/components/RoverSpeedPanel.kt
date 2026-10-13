package com.mamadrones.gcs.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mamadrones.gcs.domain.model.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

/** Timer expires old readings even when no new vehicle message causes recomposition. */
@Composable
fun RoverSpeedMonitorPanel(state: VehicleState, compact: Boolean = false) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(1_000L); value = System.currentTimeMillis() }
    }
    RoverSpeedPanel(RoverSpeedMonitor.assess(state, now), compact)
}

@Composable
fun RoverSpeedPanel(assessment: RoverSpeedAssessment, compact: Boolean = false) {
    val exceeded = assessment.observation == SpeedObservation.ABOVE_LIMIT
    val status = when (assessment.observation) {
        SpeedObservation.UNAVAILABLE -> "SPEED UNAVAILABLE"
        SpeedObservation.BELOW_LIMIT -> "BELOW 8 km/h · OBSERVED ONLY"
        SpeedObservation.AT_LIMIT -> "AT 8 km/h · OBSERVED ONLY"
        SpeedObservation.ABOVE_LIMIT -> "ABOVE 8 km/h · WARNING"
    }
    Surface(
        modifier = (if (compact) Modifier.width(220.dp) else Modifier.fillMaxWidth()).testTag("rover-speed-monitor"),
        color = if (exceeded) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (exceeded) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(status, style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                modifier = Modifier.testTag("rover-speed-status"))
            Text(assessment.speedKmh?.let { String.format(Locale.US, "%.2f km/h", it) } ?: "UNKNOWN",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("rover-speed-value"))
            if (!compact) assessment.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(if (compact) "Display only · no speed control" else "Warning only · no automatic braking or speed limiting",
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall)
            if (!compact) Text("Operator maximum: 8 km/h. Source: GLOBAL_POSITION_INT horizontal velocity. " +
                "Heartbeat and speed must each be no older than 5 seconds; checked about once per second while this screen is open. " +
                "Below-limit readings do not mean the rover is safe. Values are rounded for display; comparison uses the unrounded speed. " +
                "This does not change the Cube's cruise setting or verify stop behaviour.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
