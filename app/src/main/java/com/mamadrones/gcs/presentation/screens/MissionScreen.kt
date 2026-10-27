@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mamadrones.gcs.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.MissionLibraryEntry
import com.mamadrones.gcs.domain.model.MissionDraftReview
import com.mamadrones.gcs.domain.model.MissionTravelEstimate
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.domain.repository.MissionDraftFileFormat
import com.mamadrones.gcs.presentation.components.PanelSpec
import com.mamadrones.gcs.presentation.components.SubsystemCard
import com.mamadrones.gcs.presentation.components.UnavailableActions
import com.mamadrones.gcs.presentation.dashboard.forDisplay
import com.mamadrones.gcs.presentation.map.VehicleMap
import com.mamadrones.gcs.presentation.mission.MissionPlanAction
import com.mamadrones.gcs.presentation.mission.MissionPlanUiState
import com.mamadrones.gcs.presentation.mission.MissionTimingFormat
import com.mamadrones.gcs.presentation.mission.MissionTimingPanel
import com.mamadrones.gcs.presentation.mission.MissionTimingLeg
import com.mamadrones.gcs.presentation.mission.rememberFieldCoverageState
import com.mamadrones.gcs.presentation.mission.FieldCoverageInputs
import com.mamadrones.gcs.presentation.mission.FieldCoverageResult
import com.mamadrones.gcs.presentation.mission.FieldCoverageComparisonHeader
import com.mamadrones.gcs.presentation.mission.FieldCoverageComparisonCards
import com.mamadrones.gcs.presentation.map.LocalBasemapStyle
import com.mamadrones.gcs.presentation.map.MapStyleConfig
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MissionScreen(
    state: VehicleState,
    plan: MissionPlanUiState,
    onAction: (MissionPlanAction) -> Unit,
    modifier: Modifier = Modifier,
    mapStyleUrl: String? = MapStyleConfig.styleUrlOrNull(LocalBasemapStyle.current),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri) ?: error("Could not open the selected file")
                    stream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readBounded(MAX_ROUTE_FILE_CHARS) }
                }
                onAction(MissionPlanAction.ImportContent(content))
            } catch (error: Exception) {
                onAction(MissionPlanAction.ImportFailed(error.message ?: "Could not read the selected route file."))
            }
        }
    }
    val finishExport: (android.net.Uri?) -> Unit = { uri ->
        val content = plan.exportContent
        if (uri == null || content == null) {
            onAction(MissionPlanAction.ExportFinished())
        } else scope.launch {
            val error = try {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri) ?: error("Could not create the selected file")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
                }
                null
            } catch (failure: Exception) {
                failure.message ?: "Route file could not be written."
            }
            onAction(MissionPlanAction.ExportFinished(error))
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/geo+json"), onResult = finishExport
    )
    val gpxExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml"), onResult = finishExport
    )
    LaunchedEffect(plan.exportContent, plan.exportFileName, plan.exportFormat) {
        val fileName = plan.exportFileName ?: "route.${plan.exportFormat?.extension ?: "geojson"}"
        when (plan.exportFormat) {
            MissionDraftFileFormat.GEOJSON -> exportLauncher.launch(fileName)
            MissionDraftFileFormat.GPX -> gpxExportLauncher.launch(fileName)
            null -> Unit
        }
    }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showFenceEditor by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialLatitude by rememberSaveable { mutableStateOf("") }
    var initialLongitude by rememberSaveable { mutableStateOf("") }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showNewConfirmation by rememberSaveable { mutableStateOf(false) }
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    var librarySearchQuery by rememberSaveable { mutableStateOf("") }
    var showSaveToLibrary by rememberSaveable { mutableStateOf(false) }
    var libraryOpenId by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorTab by rememberSaveable { mutableStateOf("Route") }
    var showFileActions by rememberSaveable { mutableStateOf(false) }
    var planningSpeedInput by rememberSaveable { mutableStateOf("") }
    val timingRouteAvailable = !plan.loading && !plan.loadFailed
    val coverage = rememberFieldCoverageState(plan.draft.keepInFence, timingRouteAvailable)
    val travelEstimate = remember(plan.draft, planningSpeedInput, timingRouteAvailable) {
        if (!timingRouteAvailable) null else MissionTimingFormat.parseSpeed(planningSpeedInput)?.let {
            MissionTravelEstimate.calculate(plan.draft, it)
        }
    }
    val canAdd = plan.editable && plan.draft.waypoints.size < MissionDraft.MAX_WAYPOINTS
    val canAddFenceVertex = plan.editable && plan.draft.keepInFence.size < MissionDraft.MAX_FENCE_VERTICES

    fun openEditor(id: String?, latitude: Double?, longitude: Double?) {
        editingId = id
        initialLatitude = latitude?.let { String.format(Locale.US, "%.7f", it) }.orEmpty()
        initialLongitude = longitude?.let { String.format(Locale.US, "%.7f", it) }.orEmpty()
        showEditor = true
    }
    fun openFenceEditor() {
        initialLatitude = ""
        initialLongitude = ""
        showFenceEditor = true
    }

    val mapContent: @Composable (Modifier) -> Unit = { mapModifier ->
        VehicleMap(
            state = state.forDisplay(), modifier = mapModifier.testTag("mission-map"),
            draftWaypoints = plan.draft.waypoints, draftFence = plan.draft.keepInFence, planningMode = true,
            coverageLines = coverage.mapLines,
            styleUrl = mapStyleUrl,
            onWaypointRequested = if (canAdd) { lat, lon -> openEditor(null, lat, lon) } else null,
            onWaypointSelected = if (plan.editable) { id ->
                plan.draft.waypoints.firstOrNull { it.id == id }?.let { point ->
                    openEditor(point.id, point.latitude, point.longitude)
                }
            } else null,
        )
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 680.dp
        // Leave room for the local file toolbar and editor padding in a short landscape window.
        val coverageDiagramHeight = (maxHeight - 72.dp).coerceIn(80.dp, 260.dp)
        val enlargedMobileHeader = !wide && androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.8f
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                if (enlargedMobileHeader) {
                    val draftSummary = "${plan.draft.waypoints.size} points · ${String.format(Locale.US, "%.0f", plan.draft.distanceMeters)} m · Local only"
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { showFileActions = !showFileActions },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("mission-file-menu")) {
                                Text(if (showFileActions) "Close files" else "File / Library")
                            }
                            Button(onClick = { onAction(MissionPlanAction.Save) },
                                enabled = plan.editable && (plan.dirty || plan.recoveryCleanupFailed),
                                modifier = Modifier.heightIn(min = 48.dp).testTag("mission-save")) { Text("Save draft") }
                        }
                        Column(Modifier.fillMaxWidth()) {
                            Text(plan.draft.name, style = MaterialTheme.typography.titleSmall)
                            Text(draftSummary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showFileActions = !showFileActions }, modifier = Modifier.heightIn(min = 48.dp).testTag("mission-file-menu")) {
                            Text(if (showFileActions) "Close files" else "File / Library")
                        }
                        Column(Modifier.weight(1f)) {
                            Text(plan.draft.name, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                            Text("${plan.draft.waypoints.size} points · ${String.format(Locale.US, "%.0f", plan.draft.distanceMeters)} m · Local only",
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Button(onClick = { onAction(MissionPlanAction.Save) }, enabled = plan.editable && (plan.dirty || plan.recoveryCleanupFailed),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("mission-save")) { Text("Save draft") }
                    }
                }
            }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (wide) mapContent(Modifier.weight(1f).fillMaxHeight())
            Surface(
                modifier = if (wide) Modifier.width(352.dp).fillMaxHeight() else Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("mission-editor"),
                contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Route", "Field outline", "Coverage", "Review").forEach { tab ->
                            FilterChip(selected = editorTab == tab, onClick = { editorTab = tab }, label = { Text(tab) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("mission-tab-$tab"))
                        }
                    }
                    Text(when {
                        plan.loading -> "Loading saved draft…"
                        plan.saving -> "Saving…"
                        plan.loadFailed -> "Draft unavailable"
                        plan.recovered -> "Recovered unsaved changes · review and save"
                        plan.dirty && plan.recoverySaved -> "Unsaved changes · recovery copy saved"
                        plan.dirty -> "Unsaved changes · saving recovery copy…"
                        else -> "No unsaved changes"
                    }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("mission-save-status"))
                    if (showFileActions) {
                    Text("LOCAL FILES & LIBRARY", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { showRename = true }, enabled = plan.editable) { Text("Rename") }
                        TextButton(onClick = { showSaveToLibrary = true }, enabled = plan.editable) { Text("Save to library") }
                        TextButton(onClick = { librarySearchQuery = ""; showLibrary = true }, enabled = plan.editable) { Text("Library · ${plan.library.size}") }
                        TextButton(onClick = { showNewConfirmation = true }, enabled = plan.editable) { Text("New draft") }
                        TextButton(onClick = { importLauncher.launch(arrayOf("application/geo+json", "application/json", "application/gpx+xml", "application/xml", "text/xml", "*/*")) },
                            enabled = plan.editable) { Text("Import route file") }
                        TextButton(onClick = { onAction(MissionPlanAction.Export(MissionDraftFileFormat.GEOJSON)) }, enabled = plan.editable) { Text("Export GeoJSON") }
                        TextButton(onClick = { onAction(MissionPlanAction.Export(MissionDraftFileFormat.GPX)) }, enabled = plan.editable) { Text("Export GPX") }
                    }
                    Text("GPX carries route waypoints only. Use GeoJSON to preserve the local outline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    plan.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    plan.libraryError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("mission-library-error"))
                    }
                    if (plan.loadFailed) TextButton(onClick = { onAction(MissionPlanAction.RetryLoad) }) { Text("Retry loading") }
                }
                if (!wide && (editorTab != "Coverage" || coverage.mapLines.isNotEmpty())) item(key = "planner-map") { mapContent(Modifier.fillMaxWidth().height(280.dp)) }
                if (editorTab == "Coverage") {
                    item(key = "coverage-inputs-item") { FieldCoverageInputs(coverage) }
                    item(key = "coverage-comparison-header-item") { FieldCoverageComparisonHeader(coverage.comparison) }
                    item(key = "coverage-comparison-cards-item") { FieldCoverageComparisonCards(coverage.comparison, coverage.mapEastWest, coverage.mapError, coverage::showDirectionOnMap) }
                    item(key = "coverage-result-item") { FieldCoverageResult(coverage.result, coverageDiagramHeight) }
                }
                if (editorTab == "Route") item {
                    Text("${plan.draft.waypoints.size} / ${MissionDraft.MAX_WAYPOINTS} waypoints · ${String.format(Locale.US, "%.0f", plan.draft.distanceMeters)} m",
                        style = MaterialTheme.typography.titleSmall)
                    Text("Orange points and line are the local draft. Tap a numbered point to edit it; long-press the map to add one, or enter coordinates below. Distance is straight-line; terrain and obstacles are not checked.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { openEditor(null, null, null) }, enabled = canAdd,
                        modifier = Modifier.testTag("mission-add")) { Text("Add coordinates") }
                }
                if (editorTab == "Review") {
                item {
                    val review = MissionDraftReview.inspect(plan.draft)
                    SubsystemCard(PanelSpec(
                        title = "Local route review",
                        status = if (review.hasRouteGeometry) "ROUTE SHAPE DEFINED · REVIEW REQUIRED" else "INCOMPLETE · ADD WAYPOINTS",
                        rows = listOf(
                            "Route geometry" to if (review.hasRouteGeometry) "${plan.draft.waypoints.size} POINTS" else "AT LEAST 2 POINTS NEEDED",
                            "Zero-length legs" to if (review.zeroLengthLegs.isEmpty()) "NONE FOUND" else review.zeroLengthLegs.joinToString { "${it}→${it + 1}" },
                            "Keep-in outline" to when {
                                plan.draft.keepInFence.isEmpty() -> "NOT CONFIGURED"
                                review.keepInOutlineIssue != null -> review.keepInOutlineIssue
                                else -> "${plan.draft.keepInFence.size} VERTICES · LOCAL ONLY"
                            },
                            "Route points outside outline" to when {
                                !review.hasUsableKeepInOutline -> "NOT CHECKED"
                                review.waypointsOutsideKeepIn.isEmpty() -> "NONE FOUND"
                                else -> review.waypointsOutsideKeepIn.joinToString { "WP $it" }
                            },
                            "Straight legs outside outline" to when {
                                !review.hasUsableKeepInOutline -> "NOT CHECKED"
                                review.legsOutsideKeepIn.isEmpty() -> "NONE FOUND"
                                else -> review.legsOutsideKeepIn.joinToString { "LEG $it" }
                            },
                        ),
                        note = if (review.zeroLengthLegs.isEmpty()) {
                            "A compact local-plane check flags straight legs crossing the outline. It does not model rover turns, terrain, obstacles, vehicle limits or drive safety. Verify the outline; this is not mission approval."
                        } else {
                            "Identical consecutive coordinates create a zero-length leg. Edit or remove a point. Outline checks use straight coordinate legs only, not the rover's actual trajectory or safety."
                        },
                    ), Modifier.testTag("mission-route-review"))
                }
                item {
                    MissionTimingPanel(planningSpeedInput, { planningSpeedInput = it }, travelEstimate, timingRouteAvailable)
                }
                items(travelEstimate?.legs.orEmpty(), key = { "timing-${it.fromIndex}" }) { MissionTimingLeg(it) }
                }
                if (editorTab == "Field outline") {
                item {
                    Text("Local keep-in outline · ${plan.draft.keepInFence.size}/${MissionDraft.MAX_FENCE_VERTICES} vertices",
                        style = MaterialTheme.typography.titleSmall)
                    Text("Enter at least 3 distinct coordinates to draw a planning reference on the map. It stays on this device and is never sent to the rover.",
                        style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = ::openFenceEditor, enabled = canAddFenceVertex,
                            modifier = Modifier.testTag("mission-add-fence-vertex")) { Text("Add outline coordinate") }
                        TextButton(onClick = { onAction(MissionPlanAction.ClearFence) },
                            enabled = plan.editable && plan.draft.keepInFence.isNotEmpty(),
                            modifier = Modifier.testTag("mission-clear-fence")) { Text("Clear outline") }
                    }
                }
                itemsIndexed(plan.draft.keepInFence, key = { _, point -> "fence-${point.id}" }) { index, point ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Outline vertex ${index + 1}", style = MaterialTheme.typography.titleSmall)
                                Text(String.format(Locale.US, "%.7f, %.7f", point.latitude, point.longitude), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { onAction(MissionPlanAction.RemoveFenceVertex(point.id)) },
                                enabled = plan.editable, modifier = Modifier.testTag("mission-remove-fence-$index")) { Text("Remove") }
                        }
                    }
                }
                }
                if (editorTab == "Route") {
                if (plan.draft.waypoints.isEmpty()) item {
                    Text("No waypoints yet. This draft can be prepared without a vehicle connection.")
                }
                itemsIndexed(plan.draft.waypoints, key = { _, point -> point.id }) { index, point ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Text("Waypoint ${index + 1}", style = MaterialTheme.typography.titleSmall)
                            Text(String.format(Locale.US, "%.7f, %.7f", point.latitude, point.longitude), style = MaterialTheme.typography.bodySmall)
                            FlowRow {
                                TextButton(onClick = { openEditor(point.id, point.latitude, point.longitude) }, enabled = plan.editable,
                                    modifier = Modifier.testTag("mission-edit-$index")) { Text("Edit") }
                                TextButton(onClick = { onAction(MissionPlanAction.Move(point.id, -1)) }, enabled = plan.editable && index > 0,
                                    modifier = Modifier.testTag("mission-up-$index")) { Text("Up") }
                                TextButton(onClick = { onAction(MissionPlanAction.Move(point.id, 1)) }, enabled = plan.editable && index < plan.draft.waypoints.lastIndex,
                                    modifier = Modifier.testTag("mission-down-$index")) { Text("Down") }
                                TextButton(onClick = { onAction(MissionPlanAction.Remove(point.id)) }, enabled = plan.editable,
                                    modifier = Modifier.testTag("mission-remove-$index")) { Text("Remove") }
                            }
                        }
                    }
                }
                }
                item {
                    HorizontalDivider()
                    Text("Onboard mission: unknown. Drafts are stored only on this device. Upload, download and execution are unavailable.", style = MaterialTheme.typography.bodySmall)
                    UnavailableActions("Upload", "Download", "Start mission", "Pause", "Resume")
                }
            }
            }
        }
        }
    }

    if (showEditor) WaypointDialog(
        initialLatitude, initialLongitude, editingId != null,
        enabled = plan.editable,
        onDismiss = { showEditor = false },
        onConfirm = { latitude, longitude ->
            val id = editingId
            onAction(if (id == null) MissionPlanAction.Add(latitude, longitude)
                else MissionPlanAction.Edit(DraftWaypoint(id, latitude, longitude)))
            showEditor = false
        }
    )
    if (showFenceEditor) WaypointDialog(
        initialLatitude, initialLongitude, editing = false,
        enabled = plan.editable,
        onDismiss = { showFenceEditor = false },
        onConfirm = { latitude, longitude ->
            onAction(MissionPlanAction.AddFenceVertex(latitude, longitude))
            showFenceEditor = false
        },
    )
    if (showRename) {
        var name by rememberSaveable { mutableStateOf(plan.draft.name) }
        AlertDialog(onDismissRequest = { showRename = false }, title = { Text("Name this draft") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(MissionDraft.MAX_NAME_LENGTH) },
                label = { Text("Draft name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onAction(MissionPlanAction.Rename(name)); showRename = false }, enabled = name.isNotBlank()) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } })
    }
    if (showNewConfirmation) AlertDialog(
        onDismissRequest = { showNewConfirmation = false }, title = { Text("Start a new draft?") },
        text = { Text("This clears the current editor, including unsaved changes. The saved draft is replaced only when you press Save draft.") },
        confirmButton = { TextButton(onClick = { onAction(MissionPlanAction.New); showNewConfirmation = false }) { Text("New draft") } },
        dismissButton = { TextButton(onClick = { showNewConfirmation = false }) { Text("Cancel") } }
    )
    if (showSaveToLibrary) {
        var name by rememberSaveable { mutableStateOf(plan.draft.name) }
        AlertDialog(
            onDismissRequest = { showSaveToLibrary = false },
            title = { Text("Save a library copy") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stores a separate route on this device. It will not change the active draft or upload anything to the vehicle.")
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(MissionDraft.MAX_NAME_LENGTH) },
                        label = { Text("Unique route name") },
                        singleLine = true
                    )
                    Text("${plan.library.size} / ${MissionLibraryEntry.MAX_ENTRIES} routes used")
                    if (plan.library.size >= MissionLibraryEntry.MAX_ENTRIES) {
                        Text("Library full. Delete a library route before saving another copy.")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(MissionPlanAction.SaveToLibrary(name)); showSaveToLibrary = false },
                    enabled = plan.editable && name.isNotBlank() && plan.library.size < MissionLibraryEntry.MAX_ENTRIES
                ) { Text("Save copy") }
            },
            dismissButton = { TextButton(onClick = { showSaveToLibrary = false }) { Text("Cancel") } }
        )
    }
    if (showLibrary) {
        val searchTerm = librarySearchQuery.trim()
        val matchingLibrary = if (searchTerm.isEmpty()) plan.library else
            plan.library.filter { it.draft.name.contains(searchTerm, ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { showLibrary = false },
            title = { Text("Local route library · ${plan.library.size}") },
            text = {
                val keyboardController = LocalSoftwareKeyboardController.current
                Column(
                    modifier = Modifier.heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    plan.libraryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (plan.library.isNotEmpty()) {
                        OutlinedTextField(
                            value = librarySearchQuery,
                            onValueChange = { librarySearchQuery = it.take(MissionDraft.MAX_NAME_LENGTH) },
                            label = { Text("Search route names") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                            trailingIcon = {
                                if (librarySearchQuery.isNotEmpty()) {
                                    TextButton(onClick = { librarySearchQuery = "" },
                                        modifier = Modifier.testTag("mission-library-clear-search")) { Text("Clear") }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("mission-library-search"),
                        )
                        if (searchTerm.isNotEmpty()) Text("${matchingLibrary.size} of ${plan.library.size} routes",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("mission-library-result-count"))
                    }
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (plan.library.isEmpty()) Text("No saved route copies yet. Use Save to library to keep a route separate from the active draft.")
                        else if (matchingLibrary.isEmpty()) Text("No matching routes. Clear search or try another name.",
                            modifier = Modifier.testTag("mission-library-no-matches"))
                        matchingLibrary.forEach { entry ->
                            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
                                Column(Modifier.fillMaxWidth().padding(10.dp).testTag("library-entry-${entry.id}")) {
                                    Text(entry.draft.name, style = MaterialTheme.typography.titleSmall)
                                    Text("${entry.draft.waypoints.size} waypoints · ${String.format(Locale.US, "%.0f", entry.draft.distanceMeters)} m · ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(entry.savedAtEpochMillis))}", style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { libraryOpenId = entry.id; showLibrary = false }, enabled = plan.editable) { Text("Open") }
                                        TextButton(onClick = { libraryDeleteId = entry.id; showLibrary = false }, enabled = plan.editable) { Text("Delete") }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLibrary = false }) { Text("Close") } }
        )
    }
    libraryOpenId?.let { id ->
        val entry = plan.library.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { libraryOpenId = null },
            title = { Text("Open library route?") },
            text = { Text("${entry?.draft?.name ?: "This route"} will replace the current working draft in the editor. The saved draft stays unchanged until you explicitly press Save draft. Any unsaved edits will be replaced.") },
            confirmButton = {
                TextButton(onClick = { onAction(MissionPlanAction.OpenLibraryEntry(id)); libraryOpenId = null }, enabled = entry != null && plan.editable) { Text("Open as draft") }
            },
            dismissButton = { TextButton(onClick = { libraryOpenId = null }) { Text("Cancel") } }
        )
    }
    libraryDeleteId?.let { id ->
        val entry = plan.library.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { libraryDeleteId = null },
            title = { Text("Delete library route?") },
            text = { Text("${entry?.draft?.name ?: "This route"} will be removed from this device's local library. This does not affect the active draft or any vehicle.") },
            confirmButton = {
                TextButton(onClick = { onAction(MissionPlanAction.DeleteLibraryEntry(id)); libraryDeleteId = null }, enabled = entry != null && plan.editable) { Text("Delete route") }
            },
            dismissButton = { TextButton(onClick = { libraryDeleteId = null }) { Text("Cancel") } }
        )
    }
    plan.pendingImport?.let { imported ->
        AlertDialog(
            onDismissRequest = { onAction(MissionPlanAction.CancelImport) },
            title = { Text("Import route preview") },
            text = {
                Text("${imported.name}\n${imported.waypoints.size} waypoints · ${String.format(Locale.US, "%.0f", imported.distanceMeters)} m straight-line estimate. Confirming replaces the working draft; the saved route changes only after Save draft.")
            },
            confirmButton = { TextButton(onClick = { onAction(MissionPlanAction.ConfirmImport) }) { Text("Replace working draft") } },
            dismissButton = { TextButton(onClick = { onAction(MissionPlanAction.CancelImport) }) { Text("Cancel") } }
        )
    }
}

private const val MAX_ROUTE_FILE_CHARS = 256_000
private const val MAX_COORDINATE_CHARS = 32

private fun canToggleCoordinateSign(value: String): Boolean {
    val text = value.trim()
    return text.startsWith('-') || text.startsWith('+') || text.length < MAX_COORDINATE_CHARS
}

private fun toggleCoordinateSign(value: String): String {
    val text = value.trim()
    return when {
        text.startsWith('-') -> text.drop(1)
        text.startsWith('+') -> "-${text.drop(1)}"
        text.length < MAX_COORDINATE_CHARS -> "-$text"
        // Never discard the final character merely to insert a sign.
        else -> text
    }
}

private fun java.io.Reader.readBounded(maxChars: Int): String {
    val result = StringBuilder()
    val buffer = CharArray(4096)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(result.length + count <= maxChars) { "Route file exceeds the import size limit" }
        result.append(buffer, 0, count)
    }
    return result.toString()
}

@Composable
private fun WaypointDialog(
    initialLatitude: String, initialLongitude: String, editing: Boolean,
    enabled: Boolean,
    onDismiss: () -> Unit, onConfirm: (Double, Double) -> Unit
) {
    var latitude by rememberSaveable { mutableStateOf(initialLatitude) }
    var longitude by rememberSaveable { mutableStateOf(initialLongitude) }
    val lat = latitude.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 }
    val lon = longitude.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 }
    val latitudeInvalid = latitude.isNotBlank() && lat == null
    val longitudeInvalid = longitude.isNotBlank() && lon == null
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (editing) "Edit waypoint" else "Add waypoint") },
        text = {
            // Resolve against the dialog window that owns the focused text field.
            val keyboardController = LocalSoftwareKeyboardController.current
            val longitudeRequester = remember { BringIntoViewRequester() }
            var longitudeFocused by remember { mutableStateOf(false) }
            LaunchedEffect(longitudeInvalid, longitudeFocused) {
                // Focus normally reveals the field, but its error text can remain under the IME.
                if (longitudeInvalid && longitudeFocused) longitudeRequester.bringIntoView()
            }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("WGS84 decimal degrees. Use a decimal point; tap ± for south/west.")
                OutlinedTextField(value = latitude, onValueChange = { latitude = it.take(MAX_COORDINATE_CHARS) }, singleLine = true,
                    label = { Text("Latitude (-90 to 90)") }, isError = latitudeInvalid,
                    supportingText = if (latitudeInvalid) {
                        { Text("Enter a latitude from -90 to 90.") }
                    } else null,
                    trailingIcon = {
                        IconButton(onClick = { latitude = toggleCoordinateSign(latitude) },
                            enabled = canToggleCoordinateSign(latitude),
                            modifier = Modifier.testTag("mission-latitude-sign")
                                .semantics { contentDescription = "Toggle latitude sign" }) { Text("±") }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.testTag("mission-latitude"))
                OutlinedTextField(value = longitude, onValueChange = { longitude = it.take(MAX_COORDINATE_CHARS) }, singleLine = true,
                    label = { Text("Longitude (-180 to 180)") }, isError = longitudeInvalid,
                    supportingText = if (longitudeInvalid) {
                        { Text("Enter a longitude from -180 to 180.") }
                    } else null,
                    trailingIcon = {
                        IconButton(onClick = { longitude = toggleCoordinateSign(longitude) },
                            enabled = canToggleCoordinateSign(longitude),
                            modifier = Modifier.testTag("mission-longitude-sign")
                                .semantics { contentDescription = "Toggle longitude sign" }) { Text("±") }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                    modifier = Modifier.bringIntoViewRequester(longitudeRequester)
                        .onFocusChanged { longitudeFocused = it.isFocused }.testTag("mission-longitude"))
            }
        },
        confirmButton = { TextButton(onClick = { if (enabled && lat != null && lon != null) onConfirm(lat, lon) },
            enabled = enabled && lat != null && lon != null, modifier = Modifier.testTag("mission-confirm-waypoint")) { Text(if (editing) "Apply" else "Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
