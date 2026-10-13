package com.mamadrones.gcs.presentation.mission

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.DraftLegTravelEstimate
import com.mamadrones.gcs.domain.model.MissionTravelEstimate
import java.util.Locale

@Composable
fun MissionTimingPanel(
    speedInput: String,
    onSpeedInput: (String) -> Unit,
    estimate: MissionTravelEstimate?,
    routeAvailable: Boolean,
) {
    val focus = LocalFocusManager.current
    val invalid = speedInput.isNotBlank() && MissionTimingFormat.parseSpeed(speedInput) == null
    OutlinedCard(Modifier.fillMaxWidth().testTag("mission-timing-panel")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Travel-time estimate", style = MaterialTheme.typography.titleMedium)
            Text("LOCAL CALCULATOR · NOT A VEHICLE SPEED SETTING", style = MaterialTheme.typography.labelSmall)
            OutlinedTextField(value = speedInput, onValueChange = onSpeedInput,
                label = { Text("Planning speed (km/h)") }, singleLine = true, enabled = routeAvailable,
                isError = invalid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                supportingText = { Text(if (invalid) "Enter 0.01 to 8 km/h, with at most 2 decimal places."
                    else "Calculator maximum: 8 km/h. This does not limit actual rover speed.") },
                modifier = Modifier.fillMaxWidth().testTag("mission-planning-speed"))
            Text(when {
                !routeAvailable -> "Route unavailable while loading or after a load failure."
                speedInput.isBlank() -> "Enter a planning speed to estimate travel time."
                invalid -> "Correct the speed to calculate; no previous estimate is shown."
                estimate?.totalTravelSeconds == null -> "Add at least 2 waypoints to estimate travel time."
                else -> "Total travel estimate: ${MissionTimingFormat.duration(estimate.totalTravelSeconds)}"
            }, modifier = Modifier.testTag("mission-travel-total"))
            estimate?.let {
                Text("${it.legs.size} legs · ${String.format(Locale.US, "%.1f", it.totalDistanceMeters)} m straight-line surface distance")
            }
            Text("Assumes constant speed between the listed waypoints only. Excludes travel to the first point, return travel, turns, acceleration, stops, terrain, obstacles and spray/hydraulic work. Not a completion-time promise or route-safety assessment.",
                style = MaterialTheme.typography.bodySmall)
            Text("This speed is a screen-local calculation input; it is not saved in the route, exported, read from the Cube or sent to the rover.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun MissionTimingLeg(leg: DraftLegTravelEstimate) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("mission-timing-leg-${leg.fromIndex}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("WP ${leg.fromIndex} → WP ${leg.toIndex}", style = MaterialTheme.typography.titleSmall)
            Text("${String.format(Locale.US, "%.1f", leg.distanceMeters)} m · ${MissionTimingFormat.duration(leg.travelSeconds)} estimated travel")
            if (leg.distanceMeters == 0.0) Text("Zero-length leg; review the waypoint coordinates.")
        }
    }
}
