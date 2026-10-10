@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mamadrones.gcs.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamadrones.gcs.domain.model.ParameterGroup
import com.mamadrones.gcs.presentation.components.Notice
import java.io.InputStream

@Composable
fun ParameterReviewRoute(viewModel: ParameterReviewViewModel) {
    val context = LocalContext.current.applicationContext
    val state by viewModel.state.collectAsStateWithLifecycle()
    ParameterReviewScreen(state, onImport = viewModel::importFile, onClear = viewModel::clear,
        openDocument = { uri -> context.contentResolver.openInputStream(uri) })
}

@Composable
fun ParameterReviewScreen(
    state: ParameterReviewState,
    onImport: (() -> InputStream?) -> Unit,
    onClear: () -> Unit,
    openDocument: (android.net.Uri) -> InputStream?,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport { openDocument(uri) }
    }
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val group = ParameterGroup.entries.firstOrNull { it.name == category }
    val snapshot = state.snapshot
    val filtered = remember(snapshot, search, group) {
        snapshot?.entries.orEmpty().filter {
            (group == null || it.group == group) && it.name.contains(search.trim(), ignoreCase = true)
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("parameter-review-list"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Cube parameter file", style = MaterialTheme.typography.titleLarge)
            Text("READ-ONLY FILE REVIEW · Not live vehicle settings", style = MaterialTheme.typography.labelMedium)
        }
        item {
            Notice("NO ROVER CHANGES", "Importing does not send data, approve a safety profile or enable controls. File values may be partial, outdated or from another vehicle. Firmware version, wiring and physical failsafe tests must be confirmed separately.")
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launcher.launch(arrayOf("*/*")) }, enabled = !state.loading,
                    modifier = Modifier.testTag("parameter-import")) { Text("Import .param") }
                OutlinedButton(onClick = { onClear(); search = ""; category = null },
                    enabled = state.loading || snapshot != null || state.error != null,
                    modifier = Modifier.testTag("parameter-clear")) { Text("Clear review") }
            }
            Text("UTF-8, two columns: NAME,VALUE or NAME VALUE. Maximum 512 KiB / 10,000 values. Kept in memory only; leaving this workspace may discard it. The original file is never changed.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { message -> item {
            Notice("IMPORT FAILED", message + if (snapshot != null) " Previous file remains displayed below." else "")
        } }
        if (snapshot != null) {
            item {
                Text("${snapshot.entries.size} file values · Unverified source")
                Text("SHA-256: ${snapshot.sha256}", style = MaterialTheme.typography.bodySmall)
                Text("Fingerprint identifies the file, not its authenticity.", style = MaterialTheme.typography.bodySmall)
            }
            item {
                OutlinedTextField(value = search, onValueChange = { search = it.take(64) },
                    label = { Text("Search parameter name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("parameter-search"))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = group == null, onClick = { category = null }, label = { Text("All") })
                    ParameterGroup.entries.forEach { candidate ->
                        FilterChip(selected = group == candidate, onClick = { category = candidate.name },
                            label = { Text(candidate.title) })
                    }
                }
                Text(group?.guidance ?: "Grouped by parameter name only. No defaults are filled in and no safety pass/fail is inferred.")
                Text("${filtered.size} matching values", style = MaterialTheme.typography.labelMedium)
            }
            if (filtered.isEmpty()) item { Text("No matching entries in this file. Missing does not mean disabled or default.") }
            items(filtered, key = { it.name }) { entry ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.titleSmall)
                        Text("File value: ${entry.rawValue}")
                        Text("${entry.group.title} · Line ${entry.line}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
