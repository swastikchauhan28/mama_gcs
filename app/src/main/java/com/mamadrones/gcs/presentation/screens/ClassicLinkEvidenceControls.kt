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
import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.VehicleState

@Composable
fun ClassicLinkEvidenceControls(
    linkActive: Boolean,
    competingLink: Boolean,
    connection: ConnectionState,
    vehicle: VehicleState,
    model: ClassicLinkEvidenceViewModel,
) {
    val context = LocalContext.current.applicationContext
    val state by model.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) model.pickerCancelled()
        else model.save { context.contentResolver.openOutputStream(uri, "wt") }
    }
    ClassicLinkEvidenceActions(
        state = state,
        canCapture = model.canCapture(linkActive, competingLink, connection),
        canExport = model.canExport(linkActive, competingLink),
        onCapture = { model.capture(linkActive, competingLink, connection, vehicle, System.currentTimeMillis()) },
        onReview = { model.review(linkActive, competingLink) },
        onClose = model::closePreview,
        onDiscard = model::discard,
        onSave = {
            if (model.chooseDestination(linkActive, competingLink)) {
                try { launcher.launch("mama-classic-link-evidence.txt") }
                catch (_: RuntimeException) { model.pickerFailed() }
            }
        },
    )
}

@Composable
fun ClassicLinkEvidenceActions(
    state: ClassicLinkEvidenceState,
    canCapture: Boolean,
    canExport: Boolean,
    onCapture: () -> Unit,
    onReview: () -> Unit,
    onClose: () -> Unit,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
) {
    val busy = state.choosingDestination || state.writing
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Classic link evidence", style = MaterialTheme.typography.titleMedium)
        Text("Capture the current HC-05 receive checklist without sending data. Disconnect every telemetry link before reviewing or saving. Paired device names, addresses and MAVLink contents are excluded.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onCapture, enabled = canCapture && !busy && !state.reviewing,
            modifier = Modifier.heightIn(min = 48.dp).testTag("capture-classic-evidence")) { Text("Capture Classic evidence") }
        if (state.snapshot != null) {
            OutlinedButton(onClick = onReview, enabled = canExport && !busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("review-classic-evidence")) { Text("Review captured evidence") }
            TextButton(onClick = onDiscard, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("discard-classic-evidence")) { Text("Discard capture") }
        }
        state.message?.let { Text(it) }
        if (state.writing) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (state.reviewing && state.snapshot != null && !busy) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Review Classic link evidence") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text("Historical receive observation, not a live status, trusted identity or proof of rover control. Close retains it; Discard removes it from memory.")
                    if (!canExport) Text("Disconnect all telemetry links before saving.")
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(state.snapshot, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = onSave, enabled = canExport,
                modifier = Modifier.testTag("save-classic-evidence")) { Text("Save text file") } },
            dismissButton = { TextButton(onClick = onClose,
                modifier = Modifier.testTag("close-classic-evidence")) { Text("Close preview") } },
        )
    }
}
