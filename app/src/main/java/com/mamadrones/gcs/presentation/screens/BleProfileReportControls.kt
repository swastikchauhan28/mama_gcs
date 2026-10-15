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
fun BleProfileReportControls(source: VescDiscoveryUiState, competingLink: Boolean, model: BleProfileReportViewModel) {
    val context = LocalContext.current.applicationContext
    val state by model.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) model.pickerCancelled()
        else model.save { context.contentResolver.openOutputStream(uri, "wt") }
    }
    BleProfileReportActions(state, source.canCaptureBleProfile(competingLink), source.canSaveBleProfile(competingLink),
        onCapture = { model.capture(source, competingLink, System.currentTimeMillis()) },
        onReview = { model.review(source, competingLink) }, onClose = model::closePreview, onDiscard = model::discard,
        onSave = {
            if (model.chooseDestination(source, competingLink)) {
                try { launcher.launch("mama-ble-receive-profile.txt") }
                catch (_: RuntimeException) { model.pickerFailed() }
            }
        })
}

@Composable
fun BleProfileReportActions(state: BleProfileReportState, canCapture: Boolean, canSave: Boolean,
    onCapture: () -> Unit, onReview: () -> Unit, onClose: () -> Unit, onDiscard: () -> Unit, onSave: () -> Unit,
) {
    val busy = state.writing || state.choosingDestination
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("BLE receive profile", style = MaterialTheme.typography.titleMedium)
        Text("Capture discovered receive UUIDs without subscribing. Disconnect all telemetry links before reviewing and saving. Names and Bluetooth addresses are excluded; UUIDs may reveal the interface vendor.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onCapture, enabled = canCapture && !busy && !state.reviewing,
            modifier = Modifier.heightIn(min = 48.dp).testTag("capture-ble-profile")) { Text("Capture BLE profile") }
        if (state.snapshot != null) {
            OutlinedButton(onClick = onReview, enabled = canSave && !busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("review-ble-profile")) { Text("Review captured profile") }
            TextButton(onClick = onDiscard, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("discard-ble-profile")) { Text("Discard capture") }
        }
        state.message?.let { Text(it) }
        if (state.writing) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (state.reviewing && state.snapshot != null && !busy) {
        AlertDialog(onDismissRequest = onClose, title = { Text("Review BLE receive profile") },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text("Historical discovery snapshot, not live telemetry or proof of the remote's identity. Close retains it; Discard removes it from memory.")
                if (!canSave) Text("Disconnect all telemetry links and stop scanning/testing before saving.")
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(state.snapshot, style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = onSave, enabled = canSave,
                modifier = Modifier.testTag("save-ble-profile")) { Text("Save text file") } },
            dismissButton = { TextButton(onClick = onClose,
                modifier = Modifier.testTag("close-ble-profile")) { Text("Close preview") } })
    }
}
