package com.mamadrones.gcs.presentation.parameters

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.presentation.screens.HardwareReportState

@Composable
fun HardwareReportPreview(state: HardwareReportState, onDismiss: () -> Unit, onSave: () -> Unit) {
    val preview = state.preview ?: return
    if (state.busy) return
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Review hardware report") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text("Contains selected configuration values and a file fingerprint. Review before sharing. " +
                    "This is a frozen copy, not live settings. Opening the save picker may close foreground telemetry.")
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(preview, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onSave, modifier = Modifier.testTag("save-hardware-report")) { Text("Save text file") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("close-hardware-report")) { Text("Close preview") } })
}
