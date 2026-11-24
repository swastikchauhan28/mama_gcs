package com.mamadrones.gcs

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.map.MapStyleConfig
import com.mamadrones.gcs.presentation.map.OfflineFieldArea
import com.mamadrones.gcs.presentation.map.OfflineFieldMaps
import com.mamadrones.gcs.presentation.map.OfflineFieldPack
import com.mamadrones.gcs.presentation.map.VehicleMap
import com.mamadrones.gcs.presentation.map.completePackBoundaryRects
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/** Run preparation online; restart the app and disable Android networking before inspection. */
class OfflineFieldUnionTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()
    private val left = MissionDraft(name = "Phase 234 west", waypoints = listOf(
        DraftWaypoint("west one", 12.97, 77.59), DraftWaypoint("west two", 12.98, 77.60)))
    private val right = MissionDraft(name = "Phase 234 east", waypoints = listOf(
        DraftWaypoint("east one", 12.97, 77.60), DraftWaypoint("east two", 12.98, 77.61)))

    @Test fun incompletePackIsExcludedFromSavedBoundaryProjection() {
        val bounds = requireNotNull(OfflineFieldArea.fromDraft(left)).bounds
        val ready = OfflineFieldPack(1, "ready", bounds, true, 1, 1, 1, true, false)
        val incomplete = ready.copy(id = 2, complete = false, name = "incomplete")
        assertEquals(1, completePackBoundaryRects(listOf(ready, incomplete)).size)
        assertEquals(emptyList<Any>(), completePackBoundaryRects(listOf(incomplete)))
    }

    @Test fun prepareTwoAdjacentPacksOnline() {
        for (draft in listOf(left, right)) {
            assertTrue(OfflineFieldArea.fromDraft(draft) != null)
            OfflineFieldMaps.refresh(compose.activity)
            compose.waitUntil(15_000) { !OfflineFieldMaps.state.value.loading }
            OfflineFieldMaps.state.value.packs.filter { it.name == draft.name }.forEach {
                OfflineFieldMaps.delete(compose.activity, it.id)
            }
            compose.waitUntil(15_000) {
                OfflineFieldMaps.state.value.packs.none { it.name == draft.name }
            }
            OfflineFieldMaps.download(compose.activity, draft)
            compose.waitUntil(180_000) {
                val state = OfflineFieldMaps.state.value
                state.packs.any { it.name == draft.name && it.complete } ||
                    (!state.downloading && !state.loading && state.message?.contains("ready offline") != true)
            }
            assertTrue("Download ${draft.name}: ${OfflineFieldMaps.state.value.message}",
                OfflineFieldMaps.state.value.packs.any { it.name == draft.name && it.complete })
        }
    }

    @Test fun adjacentPacksCoverViewportButRemovingOneRevealsGapOffline() {
        OfflineFieldMaps.refresh(compose.activity)
        compose.waitUntil(15_000) { !OfflineFieldMaps.state.value.loading }
        val packs = listOf(left.name, right.name).map { name ->
            OfflineFieldMaps.state.value.packs.single { it.name == name && it.complete }
        }
        try {
            compose.setContent { MamaGcsTheme {
                VehicleMap(VehicleState(), Modifier.fillMaxSize().testTag("union-map"),
                    styleUrl = MapStyleConfig.OFFLINE_FIELD_STYLE_URL)
            } }
            compose.onNodeWithTag("union-map").assertIsDisplayed()
            val map = loadedMap()
            compose.waitUntil(10_000) { !OfflineFieldMaps.state.value.loading }
            compose.runOnIdle {
                assertTrue(map.style?.getLayer("mama-offline-pack-bounds-line") != null)
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Saved map area · 2 ready", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("offline-pack-boundary-legend").assertIsDisplayed()
            compose.runOnIdle {
                map.cameraPosition = CameraPosition.Builder().target(LatLng(12.975, 77.60)).zoom(18.0).build()
            }
            Thread.sleep(4_000) // Let native visible bounds catch up to the camera property.
            compose.runOnIdle {
                val visible = map.projection.visibleRegion.latLngBounds
                assertFalse("West pack alone unexpectedly contains $visible", packs[0].bounds.contains(visible))
                assertFalse("East pack alone unexpectedly contains $visible", packs[1].bounds.contains(visible))
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("offline-field-outside-notice").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("offline-field-outside-notice").assertDoesNotExist()
            saveScreenshot("phase234-adjacent-covered.png")
            saveScreenshot("phase235-two-ready-pack-borders.png")

            OfflineFieldMaps.delete(compose.activity, packs[1].id)
            compose.waitUntil(15_000) {
                OfflineFieldMaps.state.value.packs.none { it.id == packs[1].id }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Saved map area · 1 ready", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("VIEW NOT FULLY SAVED OFFLINE", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("offline-field-outside-notice").assertIsDisplayed()
            saveScreenshot("phase234-gap-warning.png")
            saveScreenshot("phase235-one-ready-pack-border.png")
        } finally {
            packs.forEach { pack ->
                if (OfflineFieldMaps.state.value.packs.any { it.id == pack.id }) {
                    OfflineFieldMaps.delete(compose.activity, pack.id)
                }
            }
            compose.waitUntil(15_000) {
                packs.none { pack -> OfflineFieldMaps.state.value.packs.any { it.id == pack.id } }
            }
        }
    }

    private fun saveScreenshot(name: String) {
        Thread.sleep(2_000)
        File(compose.activity.getExternalFilesDir(null), name).outputStream().use {
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findMap(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun loadedMap(): MapLibreMap {
        var map: MapLibreMap? = null
        compose.runOnIdle {
            requireNotNull(findMap(compose.activity.window.decorView)).getMapAsync { map = it }
        }
        compose.waitUntil(45_000) {
            var ready = false
            compose.runOnIdle { ready = map?.style?.isFullyLoaded == true }
            ready
        }
        return requireNotNull(map)
    }
}
