package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.map.OfflineFieldMapState
import com.mamadrones.gcs.presentation.map.VehicleMap
import com.mamadrones.gcs.presentation.map.completePackBoundaryRect
import com.mamadrones.gcs.presentation.mission.MissionPlanUiState

private fun MissionDraft.hasMapGeometry(): Boolean = waypoints.isNotEmpty() || keepInFence.size >= 3

/** Local navigation only. Selecting a library entry never opens it as the working draft. */
@Composable
fun MapAreaBrowserScreen(
    plan: MissionPlanUiState,
    offline: OfflineFieldMapState,
    onBack: () -> Unit,
    onViewCurrentPlan: () -> Unit,
    onViewLibraryRoute: (String) -> Unit,
    onViewSavedPack: (Long) -> Unit,
    onRefreshSavedPacks: () -> Unit,
    onMapSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val readyPacks = if (offline.loading) emptyList() else
        offline.packs.filter { completePackBoundaryRect(it) != null }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.testTag("area-browser-back")) { Text("‹ Operate") }
        Text("Browse planned areas", style = MaterialTheme.typography.headlineSmall)
        Text("Map browsing only. A saved area or route is not the rover's position; this does not upload or run a mission.",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("area-browser-notice"))

        Text("Saved field maps", style = MaterialTheme.typography.titleMedium)
        when {
            offline.loading -> Text("Checking saved field maps…", modifier = Modifier.testTag("area-browser-pack-loading"))
            readyPacks.isEmpty() -> Text("No complete saved field map is available on this device.",
                modifier = Modifier.testTag("area-browser-pack-empty"))
            else -> readyPacks.forEach { pack ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pack.name, Modifier.weight(1f).padding(top = 10.dp), maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    OutlinedButton(onClick = { onViewSavedPack(pack.id) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("area-browser-pack-${pack.id}")) {
                        Text("View area")
                    }
                }
            }
        }
        offline.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRefreshSavedPacks,
                modifier = Modifier.testTag("area-browser-refresh")) { Text("Refresh") }
            TextButton(onClick = onMapSettings,
                modifier = Modifier.testTag("area-browser-settings")) { Text("Map settings") }
        }

        Text("Planned routes", style = MaterialTheme.typography.titleMedium)
        when {
            plan.loading -> Text("Loading local plans…", modifier = Modifier.testTag("area-browser-plan-loading"))
            plan.loadFailed -> Text("Local plans could not be loaded. Open Plan to retry.",
                modifier = Modifier.testTag("area-browser-plan-error"))
            else -> {
                if (plan.draft.hasMapGeometry()) {
                    OutlinedButton(onClick = onViewCurrentPlan,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("area-browser-current")) {
                        Text("Current working plan · ${plan.draft.name}", maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                val routes = plan.library.filter { it.draft.hasMapGeometry() }
                routes.forEach { entry ->
                    OutlinedButton(onClick = { onViewLibraryRoute(entry.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("area-browser-library-${entry.id}")) {
                        Text("Saved route · ${entry.draft.name}", maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!plan.draft.hasMapGeometry() && routes.isEmpty()) {
                    Text("No route or outline to preview. Add one in Plan first.",
                        modifier = Modifier.testTag("area-browser-plan-empty"))
                }
            }
        }
    }
    }
}

/** The route geometry is supplied from local plan state and shown without a live vehicle marker. */
@Composable
fun RouteAreaPreviewScreen(draft: MissionDraft?, source: String, onBack: () -> Unit,
    modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.testTag("route-preview-back")) { Text("‹ Browse areas") }
        Text("Planned area · $source", Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("READ-ONLY PLAN PREVIEW · Rover location not shown · No mission upload",
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("route-preview-notice"),
            style = MaterialTheme.typography.bodySmall)
        if (draft == null || !draft.hasMapGeometry()) {
            Text("Planned area unavailable. Return to Browse areas.",
                Modifier.padding(24.dp).testTag("route-preview-unavailable"))
        } else {
            VehicleMap(VehicleState(), Modifier.weight(1f).fillMaxWidth(),
                draftWaypoints = draft.waypoints, draftFence = draft.keepInFence,
                planningMode = true, initialFitDraft = true, readOnlyPlan = true)
        }
    }
    }
}
