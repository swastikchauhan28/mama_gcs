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
import com.mamadrones.gcs.data.parameters.ParameterDifferenceKind
import com.mamadrones.gcs.presentation.components.Notice
import com.mamadrones.gcs.presentation.parameters.HardwareFileSectionCard
import com.mamadrones.gcs.presentation.parameters.ParameterHardwareSummary
import java.io.InputStream

@Composable
fun ParameterReviewRoute(viewModel: ParameterReviewViewModel) {
    val context = LocalContext.current.applicationContext
    val state by viewModel.state.collectAsStateWithLifecycle()
    ParameterReviewScreen(state, onImport = viewModel::importFile, onClear = viewModel::clear,
        openDocument = { uri -> context.contentResolver.openInputStream(uri) },
        onImportReference = viewModel::importReference, onClearReference = viewModel::clearReference,
        onPickerFailure = viewModel::pickerFailed)
}

@Composable
fun ParameterReviewScreen(
    state: ParameterReviewState,
    onImport: (() -> InputStream?) -> Unit,
    onClear: () -> Unit,
    openDocument: (android.net.Uri) -> InputStream?,
    onImportReference: (() -> InputStream?) -> Unit = {},
    onClearReference: () -> Unit = {},
    onPickerFailure: (Boolean) -> Unit = {},
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport { openDocument(uri) }
    }
    val referenceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportReference { openDocument(uri) }
    }
    var search by rememberSaveable { mutableStateOf("") }
    var showUnchanged by rememberSaveable { mutableStateOf(false) }
    var showHardware by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val group = ParameterGroup.entries.firstOrNull { it.name == category }
    val snapshot = state.snapshot
    val comparison = state.comparison
    val hardwareSections = remember(snapshot) { snapshot?.let(ParameterHardwareSummary::sections).orEmpty() }
    val filtered = remember(snapshot, search, group) {
        snapshot?.entries.orEmpty().filter {
            (group == null || it.group == group) && it.name.contains(search.trim(), ignoreCase = true)
        }
    }
    val filteredDifferences = remember(comparison, search, group, showUnchanged) {
        comparison?.differences.orEmpty().filter {
            (showUnchanged || it.kind != ParameterDifferenceKind.UNCHANGED) &&
                (group == null || ParameterGroup.forName(it.name) == group) &&
                it.name.contains(search.trim(), ignoreCase = true)
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
                Button(onClick = {
                    try { launcher.launch(arrayOf("*/*")) } catch (_: RuntimeException) { onPickerFailure(false) }
                }, enabled = !state.loading,
                    modifier = Modifier.testTag("parameter-import")) { Text("Import .param") }
                OutlinedButton(onClick = { onClear(); search = ""; category = null; showUnchanged = false; showHardware = false },
                    enabled = state.loading || snapshot != null || state.reference != null || state.error != null,
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
                Text("Current file SHA-256: ${snapshot.sha256}", style = MaterialTheme.typography.bodySmall)
                Text("Fingerprint identifies the file, not its authenticity.", style = MaterialTheme.typography.bodySmall)
                FilterChip(selected = showHardware, onClick = { showHardware = !showHardware },
                    label = { Text("Hardware summary") }, modifier = Modifier.testTag("parameter-hardware-summary"))
            }
            if (showHardware) {
                item {
                    Notice("CURRENT FILE ONLY · HARDWARE UNVERIFIED", "This summary ignores reference-file and search filters. " +
                        "Limited ArduPilot reference labels are not matched to an installed firmware version. Missing means NOT IN FILE, not disabled or default. " +
                        "Shows SERVO1–32 and SERIAL0–9 when present, plus selected input, motor, speed and failsafe fields. Other parameters remain in the raw review. " +
                        "Confirm file date/source, exact firmware, wiring, speed limit and physical emergency-stop/disconnect behavior with the hardware team. No controls are unlocked.")
                }
                items(hardwareSections, key = { "hardware-${it.id}" }) { HardwareFileSectionCard(it) }
            } else {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        try { referenceLauncher.launch(arrayOf("*/*")) } catch (_: RuntimeException) { onPickerFailure(true) }
                    }, enabled = !state.loading, modifier = Modifier.testTag("parameter-reference-import")) {
                        Text(if (state.reference == null) "Compare with reference file" else "Replace reference file")
                    }
                    if (state.reference != null) TextButton(onClick = onClearReference, enabled = !state.loading,
                        modifier = Modifier.testTag("parameter-reference-clear")) { Text("Remove reference") }
                }
                Text("Reference = comparison baseline. Current = the file imported with Import .param. Neither is live vehicle configuration.",
                    style = MaterialTheme.typography.bodySmall)
                state.reference?.let { Text("Reference file SHA-256: ${it.sha256}", style = MaterialTheme.typography.bodySmall) }
            }
            if (comparison != null) item {
                Notice("FILE COMPARISON ONLY", "Compares exact value text, not numeric equivalence: 1, 1.0 and 0x1 differ. " +
                    "Missing entries do not mean disabled or default. Differences are not safety findings and cannot be applied to the Cube.")
                Text(ParameterDifferenceKind.entries.joinToString(" · ") { "${it.label}: ${comparison.counts[it] ?: 0}" },
                    modifier = Modifier.testTag("parameter-comparison-summary"), style = MaterialTheme.typography.bodySmall)
                FilterChip(selected = showUnchanged, onClick = { showUnchanged = !showUnchanged },
                    label = { Text("Include unchanged") }, modifier = Modifier.testTag("parameter-show-unchanged"))
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
                Text(if (comparison == null) "${filtered.size} matching values" else "${filteredDifferences.size} matching comparison entries",
                    style = MaterialTheme.typography.labelMedium)
            }
            if (comparison != null) {
                if (filteredDifferences.isEmpty()) item {
                    Text("No comparison entries match the current filters. Unchanged entries are hidden unless enabled; this is not safety approval.")
                }
                items(filteredDifferences, key = { it.name }) { difference ->
                    OutlinedCard(Modifier.fillMaxWidth().testTag("parameter-difference-${difference.name}")) {
                        Column(Modifier.padding(12.dp)) {
                            Text(difference.name, style = MaterialTheme.typography.titleSmall)
                            Text(difference.kind.label, style = MaterialTheme.typography.labelMedium)
                            Text("Reference: ${difference.reference?.rawValue ?: "NOT IN FILE"}")
                            Text("Current: ${difference.current?.rawValue ?: "NOT IN FILE"}")
                            Text("${ParameterGroup.forName(difference.name).title} · Reference line ${difference.reference?.line ?: "—"} · Current line ${difference.current?.line ?: "—"}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
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
    }
}
