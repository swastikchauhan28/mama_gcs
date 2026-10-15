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

@Composable
fun BleStabilityReportControls(source: VescDiscoveryUiState, model: BleStabilityReportViewModel) {
    val context = LocalContext.current.applicationContext
    val state by model.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) model.pickerCancelled()
        else model.save { context.contentResolver.openOutputStream(uri, "wt") }
    }
    BleStabilityReportActions(state, source.canExportStabilityHistory,
        onPrepare = { model.prepare(source, System.currentTimeMillis()) },
        onDismiss = model::dismiss,
        onSave = {
            if (model.chooseDestination(source)) {
                try { launcher.launch("mama-ble-stability-report.txt") }
                catch (_: RuntimeException) { model.pickerFailed() }
            }
        })
}

@Composable
fun BleStabilityReportActions(state: TelemetryReportState, available: Boolean,
    onPrepare: () -> Unit, onDismiss: () -> Unit, onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPrepare, enabled = available && !state.writing && !state.choosingDestination,
            modifier = Modifier.heightIn(min = 48.dp).testTag("prepare-ble-stability-report")) {
            Text("Prepare stability report")
        }
        Text("Export up to 20 saved results. Stop scanning and disconnect BLE first; wait for history to finish saving. Device identity and raw error details are excluded.",
            style = MaterialTheme.typography.bodySmall)
        state.message?.let { Text(it) }
        if (state.writing) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    val preview = state.preview
    if (preview != null && !state.choosingDestination && !state.writing) {
        AlertDialog(onDismissRequest = onDismiss,
            title = { Text("Review BLE stability report") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text("Frozen saved history, not live telemetry or proof of rover safety. Opening the file picker may close other foreground telemetry links. Sharing is a separate manual action.")
                    if (!available) Text("Finish saving history and disconnect BLE before exporting.")
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(preview, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = onSave, enabled = available,
                modifier = Modifier.testTag("save-ble-stability-report")) { Text("Save text file") } },
            dismissButton = { TextButton(onClick = onDismiss,
                modifier = Modifier.testTag("close-ble-stability-report")) { Text("Close preview") } })
    }
}
