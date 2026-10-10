package com.mamadrones.gcs.presentation.screens

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamadrones.gcs.domain.model.VehicleState

@Composable
fun TelemetryReportControls(vehicle: VehicleState, model: TelemetryReportViewModel) {
    val context = LocalContext.current.applicationContext
    val state by model.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) model.pickerCancelled()
        else model.save { context.contentResolver.openOutputStream(uri, "wt") }
    }
    TelemetryReportActions(state,
        onPrepare = { model.prepare(vehicle, System.currentTimeMillis()) },
        onDismiss = model::dismiss,
        onSave = {
            if (model.chooseDestination()) {
                try { launcher.launch("mama-telemetry-report.txt") }
                catch (_: RuntimeException) { model.pickerFailed() }
            }
        })
}

@Composable
fun TelemetryReportActions(state: TelemetryReportState, onPrepare: () -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPrepare, enabled = !state.writing && !state.choosingDestination,
            modifier = Modifier.testTag("prepare-telemetry-report")) { Text("Prepare telemetry report") }
        Text("Preview a frozen text snapshot before saving. Coordinates, device addresses and raw messages are excluded.",
            style = MaterialTheme.typography.bodySmall)
        state.message?.let { Text(it) }
        if (state.writing) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    val preview = state.preview
    if (preview != null && !state.choosingDestination && !state.writing) {
        AlertDialog(onDismissRequest = onDismiss,
            title = { Text("Review telemetry report") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text("Saving opens Android's file picker and may close the telemetry link. This snapshot will not update while you choose a destination.")
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(preview, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = onSave, modifier = Modifier.testTag("save-telemetry-report")) { Text("Save text file") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Close preview") } })
    }
}
