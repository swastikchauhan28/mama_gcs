package com.mamadrones.gcs

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.MissionLibraryEntry
import com.mamadrones.gcs.domain.model.BasemapStyle
import com.mamadrones.gcs.presentation.navigation.MamaGcsApp
import androidx.compose.runtime.CompositionLocalProvider
import com.mamadrones.gcs.presentation.map.OfflineFieldMapState
import com.mamadrones.gcs.presentation.map.OfflineFieldPack
import com.mamadrones.gcs.presentation.map.LocalBasemapStyle
import com.mamadrones.gcs.presentation.mission.MissionPlanUiState
import com.mamadrones.gcs.presentation.mission.MissionPlanAction
import com.mamadrones.gcs.presentation.screens.MapAreaBrowserScreen
import com.mamadrones.gcs.presentation.screens.RouteAreaPreviewScreen
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import com.mamadrones.gcs.presentation.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic local data only; no network download, user draft write or vehicle link. */
class MapAreaBrowserUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun operateEntryOpensSavedRoutePreviewWithoutEditingDraft() {
        val working = MissionDraft("Working", listOf(DraftWaypoint("w", -35.363, 149.165)))
        val saved = MissionDraft("Saved north", listOf(DraftWaypoint("s", -35.365, 149.168)))
        val plan = MissionPlanUiState(draft = working, loading = false,
            library = listOf(MissionLibraryEntry("route-1", saved, 1L)))
        var missionAction: MissionPlanAction? = null
        compose.setContent { MamaGcsTheme {
            MamaGcsApp(vehicle = com.mamadrones.gcs.domain.model.VehicleState(),
                settings = SettingsUiState(), onThemeSelected = {}, missionPlan = plan,
                onMissionAction = { missionAction = it })
        } }
        compose.onNodeWithTag("operation-browse-areas").performClick()
        compose.onNodeWithTag("area-browser-notice").assertExists()
        compose.onNodeWithTag("area-browser-library-route-1").performScrollTo().performClick()
        compose.onNodeWithTag("route-preview-notice").assertExists()
        compose.runOnIdle {
            assertEquals(working, plan.draft)
            assertEquals(null, missionAction)
        }
    }

    @Test fun browseReadyPackAndLibraryRouteWithoutChangingWorkingPlan() {
        val working = MissionDraft("Working", listOf(DraftWaypoint("working", 28.0, 77.0)))
        val saved = MissionDraft("Saved north", listOf(DraftWaypoint("saved", 29.0, 78.0)))
        val plan = MissionPlanUiState(draft = working, loading = false,
            library = listOf(MissionLibraryEntry("route-1", saved, 1L)))
        val bounds = LatLngBounds.from(29.001, 78.001, 28.999, 77.999)
        fun pack(id: Long, complete: Boolean) = OfflineFieldPack(id, "Field $id", bounds,
            complete, 20, 1_000, 20, true, false)
        var selectedPack: Long? = null
        var selectedRoute: String? = null
        var currentSelected = false
        compose.setContent { MamaGcsTheme {
            MapAreaBrowserScreen(plan, OfflineFieldMapState(listOf(pack(7, true), pack(8, false)), loading = false),
                onBack = {}, onViewCurrentPlan = { currentSelected = true },
                onViewLibraryRoute = { selectedRoute = it },
                onViewSavedPack = { selectedPack = it }, onRefreshSavedPacks = {}, onMapSettings = {})
        } }
        compose.onNodeWithTag("area-browser-notice").assertTextContains("not the rover's position", substring = true)
        compose.onNodeWithTag("area-browser-pack-7").assertExists()
        compose.onNodeWithTag("area-browser-pack-8").assertDoesNotExist()
        compose.onNodeWithTag("area-browser-pack-7").performScrollTo().performClick()
        compose.onNodeWithTag("area-browser-current").performScrollTo().performClick()
        compose.onNodeWithTag("area-browser-library-route-1").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(7L, selectedPack)
            assertEquals("route-1", selectedRoute)
            assertEquals(true, currentSelected)
            assertEquals(working, plan.draft)
        }
        captureScreenshot("phase242-area-browser.png")
    }

    @Test fun emptyAndMissingRouteShowReadOnlyGuidance() {
        compose.setContent { MamaGcsTheme {
            MapAreaBrowserScreen(MissionPlanUiState(loading = false), OfflineFieldMapState(loading = false),
                onBack = {}, onViewCurrentPlan = {}, onViewLibraryRoute = {},
                onViewSavedPack = {}, onRefreshSavedPacks = {}, onMapSettings = {})
        } }
        compose.onNodeWithTag("area-browser-pack-empty").assertExists()
        compose.onNodeWithTag("area-browser-plan-empty").assertExists()
        compose.onNodeWithTag("area-browser-current").assertDoesNotExist()
    }

    @Test fun removedLibraryRouteCannotDisplayStaleGeometry() {
        compose.setContent { MamaGcsTheme {
            RouteAreaPreviewScreen(null, "Saved route", onBack = {})
        } }
        compose.onNodeWithTag("route-preview-notice").assertTextContains("Rover location not shown", substring = true)
        compose.onNodeWithTag("route-preview-unavailable").assertExists()
    }

    @Test fun routePreviewFitsItsGeometryWithoutAReportedVehicle() {
        val draft = MissionDraft("Synthetic field", listOf(
            DraftWaypoint("north", -35.363, 149.165),
            DraftWaypoint("south", -35.365, 149.168)))
        var map: MapLibreMap? = null
        val fullFrames = AtomicInteger()
        compose.setContent { MamaGcsTheme {
            CompositionLocalProvider(LocalBasemapStyle provides BasemapStyle.STREETS) {
                RouteAreaPreviewScreen(draft, "Saved route", onBack = {})
            }
        } }
        compose.onNodeWithTag("route-preview-notice")
            .assertTextContains("Rover location not shown", substring = true)
        compose.waitUntil(20_000) {
            var found = false
            compose.runOnIdle { found = findMap(compose.activity.window.decorView) != null }
            found
        }
        compose.runOnIdle {
            val view = findMap(compose.activity.window.decorView)!!
            view.addOnDidFinishRenderingFrameListener { fully, _, _ ->
                if (fully) fullFrames.incrementAndGet()
            }
            view.getMapAsync { map = it }
        }
        compose.waitUntil(45_000) {
            var ready = false
            compose.runOnIdle { ready = map?.style?.isFullyLoaded == true }
            ready
        }
        compose.waitUntil(20_000) {
            var fitted = false
            compose.runOnIdle {
                val target = map?.cameraPosition?.target
                fitted = target != null && target.latitude in -35.365..-35.363 &&
                    target.longitude in 149.165..149.168
            }
            fitted
        }
        compose.onNodeWithTag("mission-fit").assertIsEnabled().assertTextContains("Fit area")
        compose.waitUntil(30_000) {
            if (fullFrames.get() < 3) compose.runOnIdle { map?.triggerRepaint() }
            fullFrames.get() >= 3
        }
        compose.waitForIdle()
        captureScreenshot("phase242-route-preview.png")
    }

    private fun captureScreenshot(name: String) {
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File(compose.activity.getExternalFilesDir(null), name)
        file.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

    private fun findMap(root: View): MapView? {
        if (root is MapView) return root
        if (root is ViewGroup) for (index in 0 until root.childCount) {
            findMap(root.getChildAt(index))?.let { return it }
        }
        return null
    }
}
