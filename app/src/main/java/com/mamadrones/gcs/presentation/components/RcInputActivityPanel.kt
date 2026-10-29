package com.mamadrones.gcs.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.*

/** Local screen observations only. Leaving this screen discards the activity check. */
@Composable
fun RcInputActivityPanel(vehicle: VehicleState, nowElapsed: Long) {
    var snapshot by remember { mutableStateOf(RcActivitySnapshot()) }
    // Derive the current view immediately, so a stale snapshot is never drawn as observing
    // while an effect waits for the next frame. Effects only retain the bounded ranges.
    val previous = snapshot
    val current = RcInputActivity.observe(previous, vehicle, nowElapsed)
    LaunchedEffect(current) {
        // A delayed retention effect must not overwrite a newer Start/Stop/Clear action.
        if (snapshot == previous && snapshot != current) snapshot = current
    }
    val unavailable = RcInputActivity.unavailableReason(vehicle, nowElapsed)
    OutlinedCard(Modifier.fillMaxWidth().testTag("rc-activity-panel")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("RC input activity check", style = MaterialTheme.typography.titleMedium)
            Text(when (current.status) {
                RcActivityStatus.IDLE -> "NOT STARTED"
                RcActivityStatus.OBSERVING -> "OBSERVING · NOT CALIBRATION"
                RcActivityStatus.STOPPED -> "STOPPED · HISTORICAL RANGES"
                RcActivityStatus.INTERRUPTED -> "INTERRUPTED · HISTORICAL RANGES"
            }, Modifier.testTag("rc-activity-status"), style = MaterialTheme.typography.titleSmall)
            Text("Only in a hardware-team-secured setup with motor/ESC outputs physically isolated: start observation, " +
                "move one remote stick at a time, then stop observation. This app cannot stop the rover.")
            if (current.status == RcActivityStatus.IDLE) {
                Text(unavailable ?: "Fresh RC values are available for a local observation.", Modifier.testTag("rc-activity-availability"))
                OutlinedButton(onClick = { snapshot = RcInputActivity.start(vehicle, nowElapsed) }, enabled = unavailable == null,
                    modifier = Modifier.testTag("rc-activity-start").heightIn(min = 48.dp)) { Text("Start observation") }
            } else if (current.status == RcActivityStatus.OBSERVING) {
                OutlinedButton(onClick = { snapshot = RcInputActivity.stop(current, vehicle, nowElapsed) },
                    modifier = Modifier.testTag("rc-activity-stop").heightIn(min = 48.dp)) { Text("Stop observation") }
            }
            current.reason?.let { Text(it) }
            if (current.status != RcActivityStatus.IDLE) {
                Text("Observed screen-sample ranges · µs", style = MaterialTheme.typography.labelLarge)
                current.ranges.forEachIndexed { index, range ->
                    Text("RC ${index + 1}: " + (range?.let { "min ${it.minimum} · max ${it.maximum} · span ${it.span}" }
                        ?: "NO OBSERVATION"), Modifier.testTag("rc-activity-range-${index + 1}"))
                }
                if (current.status != RcActivityStatus.OBSERVING) {
                    TextButton(onClick = { snapshot = RcActivitySnapshot() },
                        modifier = Modifier.testTag("rc-activity-clear").heightIn(min = 48.dp)) { Text("Clear observation") }
                }
            }
            Text("First 18 channels only. A span shows variation, not verified stick mapping, neutral, endpoint calibration, " +
                "motor direction or a safe stop. The latest fresh sample is the starting baseline. " +
                "Screen sampling may miss changes between updates; ranges retain earlier observations of missing channels. " +
                "Ranges stop updating after link/session loss or a 5-second freshness timeout; retained ranges are historical. " +
                "No stream-rate requests, parameter writes or commands. Leaving this page discards the check.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
