@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mamadrones.gcs.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.MissionLibraryEntry
import com.mamadrones.gcs.domain.model.MissionDraftReview
import com.mamadrones.gcs.domain.model.MissionReviewDestination
import com.mamadrones.gcs.domain.model.MissionReviewIssue
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
import com.mamadrones.gcs.presentation.mission.CoverageSettingsComparisonPanel
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
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
    var editingFenceId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialLatitude by rememberSaveable { mutableStateOf("") }
    var initialLongitude by rememberSaveable { mutableStateOf("") }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showNewConfirmation by rememberSaveable { mutableStateOf(false) }
    var pendingOutlineClear by remember { mutableStateOf<MissionDraft?>(null) }
    // A stable vertex ID alone is insufficient when the surrounding outline changes before confirmation.
    var pendingFenceRemoval by remember { mutableStateOf<Pair<MissionDraft, String>?>(null) }
    var pendingWaypointRemovalId by rememberSaveable { mutableStateOf<String?>(null) }
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    var librarySearchQuery by rememberSaveable { mutableStateOf("") }
    var showSaveToLibrary by rememberSaveable { mutableStateOf(false) }
    var libraryOpenId by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorTab by rememberSaveable { mutableStateOf("Route") }
    var showFileActions by rememberSaveable { mutableStateOf(false) }
    var planningSpeedInput by rememberSaveable { mutableStateOf("") }
    var jumpWaypointInput by rememberSaveable { mutableStateOf("") }
    var jumpFenceInput by rememberSaveable { mutableStateOf("") }
    var jumpedWaypointId by remember(plan.draft) { mutableStateOf<String?>(null) }
    // Coordinate/status refreshes should not clear a jump; a changed vertex list does.
    var jumpedFenceId by remember(plan.draft.keepInFence.map { it.id }) { mutableStateOf<String?>(null) }
    var pendingRouteFocusNumber by remember { mutableStateOf<Int?>(null) }
    var pendingReviewIssue by remember(plan.draft) { mutableStateOf<MissionReviewIssue?>(null) }
    val routeListState = rememberLazyListState()
    val jumpWaypointNumber = jumpWaypointInput.toIntOrNull()
    val waypointCount = plan.draft.waypoints.size
    val jumpFenceNumber = jumpFenceInput.toIntOrNull()
    val fenceVertexCount = plan.draft.keepInFence.size
    val canJumpToFence = !plan.loading && !plan.loadFailed &&
        jumpFenceNumber != null && jumpFenceNumber in 1..fenceVertexCount
    val draftReview = remember(plan.draft) { MissionDraftReview.inspect(plan.draft) }
    val canJumpToWaypoint = !plan.loading && !plan.loadFailed &&
        jumpWaypointNumber != null && jumpWaypointNumber in 1..waypointCount
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
        initialLatitude = latitude?.let { if (id == null) String.format(Locale.US, "%.7f", it) else formatEditableCoordinate(it) }.orEmpty()
        initialLongitude = longitude?.let { if (id == null) String.format(Locale.US, "%.7f", it) else formatEditableCoordinate(it) }.orEmpty()
        showEditor = true
    }
    fun openFenceEditor(point: DraftWaypoint? = null) {
        editingFenceId = point?.id
        initialLatitude = point?.latitude?.let(::formatEditableCoordinate).orEmpty()
        initialLongitude = point?.longitude?.let(::formatEditableCoordinate).orEmpty()
        showFenceEditor = true
    }
    fun focusReviewWaypoint(number: Int?) {
        if (number != null && number !in 1..waypointCount) return
        jumpedWaypointId = number?.let { plan.draft.waypoints[it - 1].id }
        pendingRouteFocusNumber = number ?: 0
        editorTab = "Route"
    }

    val mapContent: @Composable (Modifier, Boolean) -> Unit = { mapModifier, compactControls ->
        VehicleMap(
            state = state.forDisplay(), modifier = mapModifier.testTag("mission-map"),
            draftWaypoints = plan.draft.waypoints, draftFence = plan.draft.keepInFence, planningMode = true,
            coverageLines = coverage.mapLines,
            coverageGapSamples = coverage.mapGapSamples,
            compactPlanningControls = compactControls,
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
        val enlargedMobileHeader = !wide && androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.8f
        val shortLandscape = enlargedMobileHeader && maxHeight < 400.dp &&
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val fileItemCount = if (shortLandscape && showFileActions) 9 else 0
        val shortTabItemCount = if (shortLandscape) 1 else 0
        val routeJumpItemCount = if (shortLandscape && waypointCount > 0) 3 else 0
        val fenceJumpItemCount = if (shortLandscape && fenceVertexCount > 0) 3 else 0
        // Route items follow one shared header, an optional mobile map, and the route introduction.
        val firstWaypointItemIndex = (if (wide) 2 else 3) + shortTabItemCount + fileItemCount + routeJumpItemCount
        val firstFenceItemIndex = (if (wide) 2 else 3) + shortTabItemCount + fileItemCount + fenceJumpItemCount
        LaunchedEffect(editorTab, pendingRouteFocusNumber, wide, waypointCount, shortTabItemCount, fileItemCount, routeJumpItemCount) {
            val number = pendingRouteFocusNumber
            if (editorTab == "Route" && number != null) {
                // Wait for Route content to replace Review content before scrolling its keyed rows.
                val itemIndex = if (number in 1..waypointCount) firstWaypointItemIndex + number - 1
                else firstWaypointItemIndex - 1
                routeListState.scrollToItem(itemIndex)
                pendingRouteFocusNumber = null
            }
        }
        fun jumpToWaypoint() {
            val number = jumpWaypointNumber ?: return
            if (!canJumpToWaypoint) return
            jumpedWaypointId = plan.draft.waypoints[number - 1].id
            focusManager.clearFocus()
            keyboardController?.hide()
            scope.launch { routeListState.scrollToItem(firstWaypointItemIndex + number - 1) }
        }
        fun jumpToFenceVertex() {
            // Lazy-list callbacks can outlive the composition that calculated validation flags.
            val number = jumpFenceInput.toIntOrNull() ?: return
            if (plan.loading || plan.loadFailed || number !in 1..plan.draft.keepInFence.size) return
            jumpedFenceId = plan.draft.keepInFence[number - 1].id
            focusManager.clearFocus()
            keyboardController?.hide()
            scope.launch { routeListState.scrollToItem(firstFenceItemIndex + number - 1) }
        }
        // Leave room for the local file toolbar and editor padding in a short landscape window.
        val coverageDiagramHeight = (maxHeight - 72.dp).coerceIn(80.dp, 260.dp)
        Column(Modifier.fillMaxSize()) {
            if (!shortLandscape) Surface(color = MaterialTheme.colorScheme.surface) {
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
            if (wide) mapContent(Modifier.weight(1f).fillMaxHeight(), false)
            Surface(
                modifier = if (wide) Modifier.width(352.dp).fillMaxHeight() else Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("mission-editor"),
                state = routeListState,
                contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    if (shortLandscape) {
                        // Keep the route controls reachable without consuming the entire short viewport.
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showFileActions = !showFileActions },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("mission-file-menu")) {
                                Text(if (showFileActions) "Close files" else "File / Library")
                            }
                            Button(onClick = { onAction(MissionPlanAction.Save) },
                                enabled = plan.editable && (plan.dirty || plan.recoveryCleanupFailed),
                                modifier = Modifier.heightIn(min = 48.dp).testTag("mission-save")) { Text("Save draft") }
                        }
                        Text(plan.draft.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall)
                        Text("${plan.draft.waypoints.size} points · ${String.format(Locale.US, "%.0f", plan.draft.distanceMeters)} m · Local only",
                            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                    }
                    if (!shortLandscape) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    if (showFileActions && !shortLandscape) {
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
                if (shortLandscape) item(key = "planner-tabs") {
                    // Keep navigation reachable even when the header exceeds the short viewport.
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Route", "Field outline", "Coverage", "Review").forEach { tab ->
                            FilterChip(selected = editorTab == tab, onClick = { editorTab = tab }, label = { Text(tab) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("mission-tab-$tab"))
                        }
                    }
                }
                if (showFileActions && shortLandscape) {
                    // Separate lazy rows let each control scroll fully into a short 2× landscape viewport.
                    item(key = "files-heading") { Text("LOCAL FILES & LIBRARY", style = MaterialTheme.typography.labelLarge) }
                    item(key = "files-rename") { TextButton(onClick = { showRename = true }, enabled = plan.editable) { Text("Rename") } }
                    item(key = "files-save-library") { TextButton(onClick = { showSaveToLibrary = true }, enabled = plan.editable) { Text("Save to library") } }
                    item(key = "files-library") { TextButton(onClick = { librarySearchQuery = ""; showLibrary = true }, enabled = plan.editable) { Text("Library · ${plan.library.size}") } }
                    item(key = "files-new") { TextButton(onClick = { showNewConfirmation = true }, enabled = plan.editable) { Text("New draft") } }
                    item(key = "files-import") { TextButton(onClick = { importLauncher.launch(arrayOf("application/geo+json", "application/json", "application/gpx+xml", "application/xml", "text/xml", "*/*")) }, enabled = plan.editable) { Text("Import route file") } }
                    item(key = "files-geojson") { TextButton(onClick = { onAction(MissionPlanAction.Export(MissionDraftFileFormat.GEOJSON)) }, enabled = plan.editable) { Text("Export GeoJSON") } }
                    item(key = "files-gpx") { TextButton(onClick = { onAction(MissionPlanAction.Export(MissionDraftFileFormat.GPX)) }, enabled = plan.editable) { Text("Export GPX") } }
                    item(key = "files-notice") { Text("GPX carries route waypoints only. Use GeoJSON to preserve the local outline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (!wide && (editorTab != "Coverage" || coverage.mapLines.isNotEmpty())) item(key = "planner-map") {
                    val compactCoverage = editorTab == "Coverage"
                    // Leave a usable map canvas beneath the sampled-gap legend at larger text sizes.
                    val mapHeight = if (!compactCoverage) 280.dp else if (enlargedMobileHeader) 600.dp else 400.dp
                    mapContent(Modifier.fillMaxWidth().height(mapHeight), compactCoverage)
                }
                if (editorTab == "Coverage") {
                    item(key = "coverage-inputs-item") { FieldCoverageInputs(coverage) }
                    item(key = "coverage-options-item") { CoverageSettingsComparisonPanel(plan.draft.keepInFence, coverage) }
                    item(key = "coverage-comparison-header-item") { FieldCoverageComparisonHeader(coverage.comparison, coverage.sidewaysEstimate, coverage.requiredOverlap) }
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
                    if (waypointCount > 0 && !shortLandscape) {
                        Text("Jump to waypoint", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = jumpWaypointInput,
                            onValueChange = { jumpWaypointInput = it.take(MissionDraft.MAX_WAYPOINTS.toString().length) },
                            label = { Text("Waypoint number") },
                            singleLine = true,
                            isError = jumpWaypointInput.isNotEmpty() && !canJumpToWaypoint,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { jumpToWaypoint() }),
                            supportingText = {
                                Text(
                                    if (jumpWaypointInput.isNotEmpty() && !canJumpToWaypoint) "Enter a number from 1 to $waypointCount."
                                    else "1 to $waypointCount · local draft only",
                                    modifier = Modifier.testTag("mission-jump-help"),
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("mission-jump-input"),
                        )
                        TextButton(
                            onClick = { jumpToWaypoint() },
                            enabled = canJumpToWaypoint,
                            modifier = Modifier.testTag("mission-jump-go"),
                        ) { Text("Go to waypoint") }
                    }
                }
                if (editorTab == "Route" && shortLandscape && waypointCount > 0) {
                    // Each control must be independently scrollable in the short 2× viewport.
                    item(key = "route-jump-heading") { Text("Jump to waypoint", style = MaterialTheme.typography.titleSmall) }
                    item(key = "route-jump-input") {
                        OutlinedTextField(
                            value = jumpWaypointInput,
                            onValueChange = { jumpWaypointInput = it.take(MissionDraft.MAX_WAYPOINTS.toString().length) },
                            label = { Text("Waypoint number") },
                            singleLine = true,
                            isError = jumpWaypointInput.isNotEmpty() && !canJumpToWaypoint,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { jumpToWaypoint() }),
                            supportingText = {
                                Text(
                                    if (jumpWaypointInput.isNotEmpty() && !canJumpToWaypoint) "Enter a number from 1 to $waypointCount."
                                    else "1 to $waypointCount · local draft only",
                                    modifier = Modifier.testTag("mission-jump-help"),
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("mission-jump-input"),
                        )
                    }
                    item(key = "route-jump-go") {
                        TextButton(onClick = { jumpToWaypoint() }, enabled = canJumpToWaypoint,
                            modifier = Modifier.testTag("mission-jump-go")) { Text("Go to waypoint") }
                    }
                }
                if (editorTab == "Review") {
                item {
                    val review = draftReview
                    val issues = review.issues
                    SubsystemCard(PanelSpec(
                        title = "Local route readiness",
                        status = if (issues.isEmpty()) "READY FOR REVIEW · LOCAL ONLY" else "NEEDS CHANGES",
                        rows = if (issues.isEmpty()) listOf(
                            "Route structure" to "LOCAL CHECKS PASSED",
                            "Outline containment" to if (plan.draft.keepInFence.isEmpty()) "NOT CHECKED · NO OUTLINE" else "LOCAL CHECK PASSED",
                        ) else issues.mapIndexed { index, issue -> "Fix ${index + 1}" to issue.message },
                        note = "This is a local drafting check. Vehicle mission settings, Cube acceptance, turns, obstacles and execution safety are not checked. Mission upload is unavailable.",
                    ), Modifier.testTag("mission-route-readiness"))
                    if (issues.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            issues.forEachIndexed { index, issue ->
                                TextButton(onClick = {
                                    if (issue.destination == MissionReviewDestination.ROUTE) {
                                        if (issue.targets.size > 1) pendingReviewIssue = issue
                                        else focusReviewWaypoint(issue.waypointNumber)
                                    } else editorTab = "Field outline"
                                }, modifier = Modifier.testTag("mission-fix-issue-$index")) {
                                    val destination = when {
                                        issue.destination == MissionReviewDestination.FIELD_OUTLINE -> "field outline"
                                        issue.waypointNumber == null -> "add point"
                                        else -> "waypoint ${issue.waypointNumber}"
                                    }
                                    Text(if (issue.targets.size > 1) "Fix ${index + 1}: Choose affected item (${issue.targets.size})"
                                        else "Fix ${index + 1}: Go to $destination")
                                }
                            }
                        }
                    }
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
                    draftReview.keepInOutlineIssue?.let { issue ->
                        Text("Local outline issue: $issue. Review the vertices; this check cannot identify one cause.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("mission-outline-issue"))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { openFenceEditor() }, enabled = canAddFenceVertex,
                            modifier = Modifier.testTag("mission-add-fence-vertex")) { Text("Add outline coordinate") }
                        TextButton(onClick = { pendingOutlineClear = plan.draft },
                            enabled = plan.editable && plan.draft.keepInFence.isNotEmpty(),
                            modifier = Modifier.testTag("mission-clear-fence")) { Text("Clear outline") }
                    }
                    if (fenceVertexCount > 0 && !shortLandscape) {
                        Text("Jump to outline vertex", style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = jumpFenceInput,
                                onValueChange = { jumpFenceInput = it.take(MissionDraft.MAX_FENCE_VERTICES.toString().length) },
                                label = { Text("Vertex number") },
                                singleLine = true,
                                isError = jumpFenceInput.isNotEmpty() && !canJumpToFence,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { jumpToFenceVertex() }),
                                supportingText = {
                                    Text(if (jumpFenceInput.isNotEmpty() && !canJumpToFence) "Enter a number from 1 to $fenceVertexCount."
                                        else "1 to $fenceVertexCount · local outline only")
                                },
                                modifier = Modifier.weight(1f).testTag("mission-fence-jump-input"),
                            )
                            TextButton(onClick = { jumpToFenceVertex() }, enabled = canJumpToFence,
                                modifier = Modifier.testTag("mission-fence-jump-go")) { Text("Go to vertex") }
                        }
                    }
                }
                if (shortLandscape && fenceVertexCount > 0) {
                    item(key = "fence-jump-heading") { Text("Jump to outline vertex", style = MaterialTheme.typography.titleSmall) }
                    item(key = "fence-jump-input") {
                        OutlinedTextField(
                            value = jumpFenceInput,
                            onValueChange = { jumpFenceInput = it.take(MissionDraft.MAX_FENCE_VERTICES.toString().length) },
                            label = { Text("Vertex number") },
                            singleLine = true,
                            isError = jumpFenceInput.isNotEmpty() && !canJumpToFence,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { jumpToFenceVertex() }),
                            supportingText = {
                                Text(if (jumpFenceInput.isNotEmpty() && !canJumpToFence) "Enter a number from 1 to $fenceVertexCount."
                                    else "1 to $fenceVertexCount · local outline only")
                            },
                            modifier = Modifier.fillMaxWidth().testTag("mission-fence-jump-input"),
                        )
                    }
                    item(key = "fence-jump-go") {
                        TextButton(onClick = { jumpToFenceVertex() }, enabled = canJumpToFence,
                            modifier = Modifier.testTag("mission-fence-jump-go")) { Text("Go to vertex") }
                    }
                }
                itemsIndexed(plan.draft.keepInFence, key = { _, point -> "fence-${point.id}" }) { index, point ->
                    val jumpTarget = jumpedFenceId == point.id
                    Surface(color = if (jumpTarget) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.small) {
                        Column(Modifier.fillMaxWidth().padding(10.dp).testTag("mission-fence-row-$index")) {
                            Text("Outline vertex ${index + 1}", style = MaterialTheme.typography.titleSmall)
                            if (jumpTarget) Text("Jump target", style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.testTag("mission-fence-jump-target"))
                            Text(String.format(Locale.US, "%.7f, %.7f", point.latitude, point.longitude), style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { openFenceEditor(point) }, enabled = plan.editable,
                                    modifier = Modifier.testTag("mission-edit-fence-$index").semantics { contentDescription = "Edit outline vertex ${index + 1}" }) { Text("Edit") }
                                TextButton(onClick = { onAction(MissionPlanAction.MoveFenceVertex(point.id, -1)) },
                                    enabled = plan.editable && index > 0,
                                    modifier = Modifier.testTag("mission-up-fence-$index").semantics { contentDescription = "Move outline vertex ${index + 1} up" }) { Text("Move up") }
                                TextButton(onClick = { onAction(MissionPlanAction.MoveFenceVertex(point.id, 1)) },
                                    enabled = plan.editable && index < plan.draft.keepInFence.lastIndex,
                                    modifier = Modifier.testTag("mission-down-fence-$index").semantics { contentDescription = "Move outline vertex ${index + 1} down" }) { Text("Move down") }
                                TextButton(onClick = { pendingFenceRemoval = plan.draft to point.id },
                                    enabled = plan.editable,
                                    modifier = Modifier.testTag("mission-remove-fence-$index")
                                        .semantics { contentDescription = "Remove outline vertex ${index + 1}" }) { Text("Remove") }
                            }
                        }
                    }
                }
                }
                if (editorTab == "Route") {
                if (plan.draft.waypoints.isEmpty()) item {
                    Text("No waypoints yet. This draft can be prepared without a vehicle connection.")
                }
                itemsIndexed(plan.draft.waypoints, key = { _, point -> point.id }) { index, point ->
                    val jumpTarget = jumpedWaypointId == point.id
                    Surface(color = if (jumpTarget) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.small) {
                        Column(Modifier.fillMaxWidth().padding(10.dp).testTag("mission-waypoint-row-$index")) {
                            Text("Waypoint ${index + 1}", style = MaterialTheme.typography.titleSmall)
                            if (jumpTarget) Text("Jump target", style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.testTag("mission-jump-target"))
                            Text(String.format(Locale.US, "%.7f, %.7f", point.latitude, point.longitude), style = MaterialTheme.typography.bodySmall)
                            FlowRow {
                                TextButton(onClick = { openEditor(point.id, point.latitude, point.longitude) }, enabled = plan.editable,
                                    modifier = Modifier.testTag("mission-edit-$index")
                                        .semantics { contentDescription = "Edit waypoint ${index + 1}" }) { Text("Edit") }
                                TextButton(onClick = { onAction(MissionPlanAction.Move(point.id, -1)) }, enabled = plan.editable && index > 0,
                                    modifier = Modifier.testTag("mission-up-$index")
                                        .semantics { contentDescription = "Move waypoint ${index + 1} up" }) { Text("Up") }
                                TextButton(onClick = { onAction(MissionPlanAction.Move(point.id, 1)) }, enabled = plan.editable && index < plan.draft.waypoints.lastIndex,
                                    modifier = Modifier.testTag("mission-down-$index")
                                        .semantics { contentDescription = "Move waypoint ${index + 1} down" }) { Text("Down") }
                                TextButton(onClick = { pendingWaypointRemovalId = point.id }, enabled = plan.editable,
                                    modifier = Modifier.testTag("mission-remove-$index")
                                        .semantics { contentDescription = "Remove waypoint ${index + 1}" }) { Text("Remove") }
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

    pendingReviewIssue?.let { issue ->
        AlertDialog(
            onDismissRequest = { pendingReviewIssue = null },
            title = { Text("Choose affected item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(issue.message, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium)
                    LazyColumn(Modifier.heightIn(max = 320.dp).testTag("mission-fix-target-list")) {
                        items(issue.targets, key = { it.waypointNumber }) { target ->
                            TextButton(
                                onClick = {
                                    pendingReviewIssue = null
                                    focusReviewWaypoint(target.waypointNumber)
                                },
                                modifier = Modifier.fillMaxWidth().testTag("mission-fix-target-${target.waypointNumber}"),
                            ) { Text(target.label) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pendingReviewIssue = null }) { Text("Cancel") } },
        )
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
    if (showFenceEditor) {
        val id = editingFenceId
        val original = if (id != null) {
            val lat = initialLatitude.toDoubleOrNull()
            val lon = initialLongitude.toDoubleOrNull()
            if (lat != null && lon != null && lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0)
                DraftWaypoint(id, lat, lon) else null
        } else null
        val stale = id != null && (original == null || plan.draft.keepInFence.firstOrNull { it.id == id } != original)
        WaypointDialog(
            initialLatitude, initialLongitude, editing = id != null,
            enabled = plan.editable && !stale, outline = true, stale = stale,
            onDismiss = { showFenceEditor = false },
            onConfirm = { latitude, longitude ->
                if (id == null) onAction(MissionPlanAction.AddFenceVertex(latitude, longitude))
                else if (original != null) onAction(MissionPlanAction.EditFenceVertex(original, latitude, longitude))
                showFenceEditor = false
            },
        )
    }
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
    pendingOutlineClear?.let { requestedDraft ->
        val stillCurrent = plan.editable && plan.draft == requestedDraft && requestedDraft.keepInFence.isNotEmpty()
        AlertDialog(
            onDismissRequest = { pendingOutlineClear = null },
            title = { Text("Clear field outline?") },
            text = { Text(if (stillCurrent)
                "Remove all ${requestedDraft.keepInFence.size} outline vertices from the working draft? Route waypoints stay. The saved draft changes only after Save draft, and the coverage preview will clear."
            else "The working draft changed. Close this dialog and review the outline before trying again.") },
            confirmButton = { TextButton(
                onClick = { if (plan.editable && plan.draft == requestedDraft) onAction(MissionPlanAction.ClearFence); pendingOutlineClear = null },
                enabled = stillCurrent, modifier = Modifier.testTag("mission-confirm-clear-fence")
            ) { Text("Clear outline") } },
            dismissButton = { TextButton(onClick = { pendingOutlineClear = null },
                modifier = Modifier.testTag("mission-cancel-clear-fence")) { Text("Cancel") } },
        )
    }
    pendingFenceRemoval?.let { (requestedDraft, id) ->
        val index = plan.draft.keepInFence.indexOfFirst { it.id == id }
        val vertex = plan.draft.keepInFence.getOrNull(index)
        val stillCurrent = plan.editable && plan.draft == requestedDraft && vertex != null
        AlertDialog(
            onDismissRequest = { pendingFenceRemoval = null },
            title = { Text("Remove outline vertex?") },
            text = { Text(if (stillCurrent)
                "Remove outline vertex ${index + 1} (${String.format(Locale.US, "%.7f, %.7f", vertex!!.latitude, vertex.longitude)}) from the working draft? The saved draft changes only after Save draft. Coverage preview will clear."
            else "The working draft changed. Close this dialog and review the outline before trying again.") },
            confirmButton = { TextButton(
                onClick = {
                    if (plan.editable && plan.draft == requestedDraft && plan.draft.keepInFence.any { it.id == id })
                        onAction(MissionPlanAction.RemoveFenceVertex(id))
                    pendingFenceRemoval = null
                },
                enabled = stillCurrent,
                modifier = Modifier.testTag("mission-confirm-remove-fence"),
            ) { Text("Remove vertex") } },
            dismissButton = { TextButton(onClick = { pendingFenceRemoval = null },
                modifier = Modifier.testTag("mission-cancel-remove-fence")) { Text("Cancel") } },
        )
    }
    pendingWaypointRemovalId?.let { id ->
        val index = plan.draft.waypoints.indexOfFirst { it.id == id }
        val waypoint = plan.draft.waypoints.getOrNull(index)
        AlertDialog(
            onDismissRequest = { pendingWaypointRemovalId = null },
            title = { Text("Remove waypoint?") },
            text = {
                Text(if (waypoint == null) "This waypoint is no longer in the working draft."
                    else "Waypoint ${index + 1} (${String.format(Locale.US, "%.7f, %.7f", waypoint.latitude, waypoint.longitude)}) will be removed from the working draft. The saved route changes only after Save draft.")
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(MissionPlanAction.Remove(id)); pendingWaypointRemovalId = null },
                    enabled = waypoint != null && plan.editable,
                    modifier = Modifier.testTag("mission-confirm-remove-waypoint"),
                ) { Text("Remove waypoint") }
            },
            dismissButton = { TextButton(onClick = { pendingWaypointRemovalId = null }) { Text("Cancel") } },
        )
    }
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
        val landscapePreview = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val outlineSummary = if (imported.keepInFence.isEmpty()) {
            "No field outline"
        } else {
            "Field outline: ${imported.keepInFence.size} vertices"
        }
        AlertDialog(
            onDismissRequest = { onAction(MissionPlanAction.CancelImport) },
            // Wider landscape space keeps the title and actions from consuming the entire short window.
            modifier = if (landscapePreview) Modifier.fillMaxWidth(0.9f) else Modifier,
            properties = DialogProperties(usePlatformDefaultWidth = !landscapePreview),
            title = { Text("Import route preview") },
            text = {
                // Keep the preview details reachable when large text leaves little space above the actions.
                Column(Modifier.verticalScroll(rememberScrollState()).testTag("import-preview-scroll")) {
                    Text(imported.name)
                    Text("${imported.waypoints.size} waypoints · ${String.format(Locale.US, "%.0f", imported.distanceMeters)} m straight-line estimate.")
                    Text(outlineSummary)
                    Text("Saved route changes only after Save draft.")
                }
            },
            confirmButton = { TextButton(onClick = { onAction(MissionPlanAction.ConfirmImport) }) { Text("Replace working draft") } },
            dismissButton = { TextButton(onClick = { onAction(MissionPlanAction.CancelImport) }) { Text("Cancel") } }
        )
    }
}

private const val MAX_ROUTE_FILE_CHARS = 256_000
private const val MAX_COORDINATE_CHARS = 32

private fun formatEditableCoordinate(value: Double): String {
    val canonical = value.toString()
    if ('E' !in canonical && 'e' !in canonical) return canonical
    val plain = java.math.BigDecimal.valueOf(value).toPlainString()
    // An extreme coordinate may need more characters than the input allows; never truncate it.
    return plain.takeIf { it.length <= MAX_COORDINATE_CHARS } ?: canonical
}

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
    enabled: Boolean, outline: Boolean = false, stale: Boolean = false,
    onDismiss: () -> Unit, onConfirm: (Double, Double) -> Unit
) {
    var latitude by rememberSaveable { mutableStateOf(initialLatitude) }
    var longitude by rememberSaveable { mutableStateOf(initialLongitude) }
    val lat = latitude.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 }
    val lon = longitude.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 }
    val latitudeInvalid = latitude.isNotBlank() && lat == null
    val longitudeInvalid = longitude.isNotBlank() && lon == null
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(when {
            outline && editing -> "Edit outline vertex"
            outline -> "Add outline vertex"
            editing -> "Edit waypoint"
            else -> "Add waypoint"
        }) },
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
                if (stale) Text("This outline vertex changed. Cancel and reopen it.")
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
