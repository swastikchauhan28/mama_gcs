package com.mamadrones.gcs

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.map.OfflineFieldArea
import com.mamadrones.gcs.presentation.map.OfflineFieldMaps
import com.mamadrones.gcs.presentation.mission.MissionPlanAction
import com.mamadrones.gcs.presentation.mission.MissionPlanUiState
import com.mamadrones.gcs.presentation.navigation.MamaGcsApp
import com.mamadrones.gcs.presentation.settings.SettingsUiState
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.sources.GeoJsonSource

/** Prepare online, then run the UI method in a new process with emulator networking disabled. */
class SavedPackPlanOverlayTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()
    private val field = MissionDraft("Phase 245 comparison field", keepInFence = listOf(
        DraftWaypoint("nw", 12.9760, 77.6000), DraftWaypoint("ne", 12.9760, 77.6010),
        DraftWaypoint("se", 12.9750, 77.6010), DraftWaypoint("sw", 12.9750, 77.6000)))
    private val working = MissionDraft("Phase 245 working plan", waypoints = listOf(
        DraftWaypoint("inside", 12.9755, 77.6005),
        DraftWaypoint("outside", 12.9755, 77.6018)), keepInFence = listOf(
        DraftWaypoint("a", 12.9751, 77.6001),
        DraftWaypoint("b", 12.9759, 77.6009),
        DraftWaypoint("c", 12.9768, 77.6004)))

    @Test fun prepareComparisonPackOnline() {
        assertTrue("Synthetic field must fit the download budget", OfflineFieldArea.fromDraft(field) != null)
        OfflineFieldMaps.refresh(compose.activity)
        compose.waitUntil(15_000) { !OfflineFieldMaps.state.value.loading }
        OfflineFieldMaps.state.value.packs.filter { it.name == field.name }.forEach {
            OfflineFieldMaps.delete(compose.activity, it.id)
        }
        compose.waitUntil(15_000) { OfflineFieldMaps.state.value.packs.none { it.name == field.name } }
        OfflineFieldMaps.download(compose.activity, field)
        compose.waitUntil(180_000) {
            val state = OfflineFieldMaps.state.value
            state.packs.any { it.name == field.name && it.complete } ||
                (!state.downloading && !state.loading && state.message?.contains("ready offline") != true)
        }
        assertTrue("Download: ${OfflineFieldMaps.state.value.message}",
            OfflineFieldMaps.state.value.packs.any { it.name == field.name && it.complete })
    }

    @Test fun toggleWorkingPlanOnSavedMapOfflineWithoutEditingIt() {
        OfflineFieldMaps.refresh(compose.activity)
        compose.waitUntil(15_000) { !OfflineFieldMaps.state.value.loading }
        val pack = OfflineFieldMaps.state.value.packs.single { it.name == field.name }
        assertTrue("Saved comparison pack must be complete", pack.complete)
        val plan = MissionPlanUiState(draft = working, loading = false)
        var action: MissionPlanAction? = null
        try {
            compose.setContent { MamaGcsTheme {
                MamaGcsApp(vehicle = VehicleState(), settings = SettingsUiState(),
                    onThemeSelected = {}, missionPlan = plan, onMissionAction = { action = it })
            } }
            compose.onNodeWithTag("operation-browse-areas").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("area-browser-pack-${pack.id}").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("area-browser-pack-${pack.id}").performScrollTo().performClick()
            compose.onNodeWithTag("saved-pack-back").assertIsDisplayed()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("saved-pack-plan-toggle").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("saved-pack-plan-toggle").assertTextContains("Show working plan")
            compose.onNodeWithTag("saved-pack-plan-legend").assertDoesNotExist()
            val map = loadedMap()
            compose.waitUntil(20_000) {
                var framed = false
                compose.runOnIdle {
                    framed = map.projection.visibleRegion.latLngBounds.contains(pack.bounds) &&
                        map.style?.getLayer("mama-offline-pack-bounds-line") != null
                }
                framed
            }
            assertSourceCount(map, "mama-draft-points", 0)
            val targetBefore = requireNotNull(map.cameraPosition.target)
            val zoomBefore = map.cameraPosition.zoom
            compose.onNodeWithTag("saved-pack-plan-toggle").performClick()
            compose.onNodeWithTag("saved-pack-plan-legend").assertTextContains("orange route/outline", substring = true)
            compose.onNodeWithTag("saved-pack-plan-outside").assertTextContains("route points 2", substring = true)
            compose.onNodeWithTag("saved-pack-plan-outside").assertTextContains("outline vertices 3", substring = true)
            assertSourceCount(map, "mama-draft-points", 2)
            assertSourceCount(map, "mama-draft-line", 1)
            assertSourceCount(map, "mama-draft-fence", 1)
            compose.runOnIdle {
                assertTrue("Overlay changed saved-pack latitude fit",
                    kotlin.math.abs(requireNotNull(map.cameraPosition.target).latitude - targetBefore.latitude) < 0.00001)
                assertTrue("Overlay changed saved-pack longitude fit",
                    kotlin.math.abs(requireNotNull(map.cameraPosition.target).longitude - targetBefore.longitude) < 0.00001)
                assertTrue("Overlay changed saved-pack zoom", kotlin.math.abs(map.cameraPosition.zoom - zoomBefore) < 0.01)
                assertEquals(working, plan.draft)
                assertEquals(null, action)
            }
            waitForRenderedMapDetail(map)
            compose.onNodeWithTag("saved-pack-plan-toggle").performClick()
            compose.onNodeWithTag("saved-pack-plan-legend").assertDoesNotExist()
            assertSourceCount(map, "mama-draft-points", 0)
            assertSourceCount(map, "mama-draft-line", 0)
            assertSourceCount(map, "mama-draft-fence", 0)
            compose.runOnIdle { assertEquals(working, plan.draft); assertEquals(null, action) }
        } finally {
            OfflineFieldMaps.delete(compose.activity, pack.id)
            compose.waitUntil(15_000) { OfflineFieldMaps.state.value.packs.none { it.id == pack.id } }
        }
    }

    private fun assertSourceCount(map: MapLibreMap, sourceId: String, expected: Int) {
        compose.waitUntil(15_000) {
            var count = -1
            compose.runOnIdle {
                count = map.style?.getSourceAs<GeoJsonSource>(sourceId)?.querySourceFeatures(null)?.size ?: -1
            }
            count == expected
        }
    }

    private fun waitForRenderedMapDetail(map: MapLibreMap) {
        val deadline = System.currentTimeMillis() + 45_000
        var colours: Int
        var orangePixels: Int
        do {
            Thread.sleep(2_000)
            compose.runOnIdle { map.triggerRepaint() }
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val sampled = mutableSetOf<Int>()
            orangePixels = 0
            for (y in screenshot.height * 53 / 100 until screenshot.height * 82 / 100 step 12) {
                for (x in screenshot.width * 28 / 100 until screenshot.width * 68 / 100 step 12) {
                    val pixel = screenshot.getPixel(x, y) and 0x00ffffff
                    sampled.add(pixel)
                    val red = pixel shr 16 and 0xff
                    val green = pixel shr 8 and 0xff
                    val blue = pixel and 0xff
                    if (red > 235 && green in 140..180 && blue in 45..90) orangePixels++
                }
            }
            colours = sampled.size
            File(compose.activity.getExternalFilesDir(null), "phase245-saved-plan-overlay-offline.png")
                .outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            screenshot.recycle()
        } while ((colours < 20 || orangePixels < 12) && System.currentTimeMillis() < deadline)
        assertTrue("Saved map and working-plan view stayed blank ($colours colours)", colours >= 20)
        assertTrue("Orange working plan was not visibly rendered ($orangePixels sampled pixels)", orangePixels >= 12)
    }

    private fun loadedMap(): MapLibreMap {
        var map: MapLibreMap? = null
        compose.waitUntil(20_000) {
            var found = false
            compose.runOnIdle { found = findMap(compose.activity.window.decorView) != null }
            found
        }
        compose.runOnIdle { requireNotNull(findMap(compose.activity.window.decorView)).getMapAsync { map = it } }
        compose.waitUntil(45_000) {
            var ready = false
            compose.runOnIdle { ready = map?.style?.isFullyLoaded == true }
            ready
        }
        return requireNotNull(map)
    }

    private fun findMap(root: View): MapView? {
        if (root is MapView) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) {
            findMap(root.getChildAt(i))?.let { return it }
        }
        return null
    }
}
