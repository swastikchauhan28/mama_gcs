package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.dashboard.MavlinkInspectorPanels
import kotlinx.coroutines.delay

@Composable
fun MavlinkInspectorScreen(diagnostics: MavlinkDiagnostics) {
    var query by rememberSaveable { mutableStateOf("") }
    var observedOnly by rememberSaveable { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(1_000L); value = System.currentTimeMillis() }
    }
    ScreenBody {
        ScreenHeader("MAVLink message inspector", "Receive-only message counts · supported telemetry subset")
        Text(when {
            diagnostics.startedAtEpochMillis == null -> "NO RECEIVE SESSION"
            diagnostics.active -> "${diagnostics.linkKind.name} · CURRENT SESSION COUNTS"
            else -> "${diagnostics.linkKind.name} · LAST SESSION HISTORY"
        }, Modifier.testTag("inspector-session"), style = MaterialTheme.typography.titleSmall)
        Notice("OBSERVATIONS, NOT CAPABILITIES", "The list is what this app can decode, not what your Cube must send. Counts reset on a new receive session; disconnected counts are historical. Unsupported IDs, bad checksums and rejected signed packets are excluded here; see Link & messages. No requests or commands are sent.")
        OutlinedTextField(value = query, onValueChange = { query = it.take(64) },
            label = { Text("Search message name or ID") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().testTag("inspector-search"))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Only observed messages", Modifier.weight(1f))
            Checkbox(checked = observedOnly, onCheckedChange = { observedOnly = it },
                modifier = Modifier.testTag("inspector-observed-only").semantics {
                    contentDescription = "Only observed messages"
                })
        }
        val types = MavlinkInspectorPanels.visibleTypes(diagnostics, query, observedOnly)
        Text("Message types shown: ${types.size}", Modifier.testTag("inspector-result-count"))
        if (types.isEmpty()) Text("No matching observations. Clear the search or turn off Only observed messages.")
        types.forEach { type ->
            key(type) { SubsystemCard(MavlinkInspectorPanels.panel(type, diagnostics, now),
                Modifier.testTag("inspector-message-${type.messageId}")) }
        }
        Notice("LOCAL DISPLAY ONLY", "The inspector keeps no raw payloads, names, addresses or coordinates. Counters stay in memory and are not included in the telemetry report export. Recency uses a 5-second display window; this is not a link-health or operational-readiness verdict.")
    }
}
