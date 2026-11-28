package com.mamadrones.gcs

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.assertIsDisplayed
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

/** Run preparation online, then restart the app and disable emulator networking before browsing. */
class OperateSavedPackOfflineTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()
    private val field = MissionDraft(name = "Phase 244 synthetic field", keepInFence = listOf(
        DraftWaypoint("nw", 12.9760, 77.6000),
        DraftWaypoint("ne", 12.9760, 77.6010),
        DraftWaypoint("se", 12.9750, 77.6010),
        DraftWaypoint("sw", 12.9750, 77.6000)))

    @Test fun prepareSmallSavedFieldOnline() {
        assertTrue("Synthetic outline must fit the offline limit", OfflineFieldArea.fromDraft(field) != null)
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
        // The uniquely named fixture remains for a separate offline app process.
    }

    @Test fun operateOpensSavedFieldOfflineAndPreservesWorkingRoute() {
        OfflineFieldMaps.refresh(compose.activity)
        compose.waitUntil(15_000) { !OfflineFieldMaps.state.value.loading }
        val pack = OfflineFieldMaps.state.value.packs.single { it.name == field.name }
        assertTrue("Persisted field pack must be native-complete", pack.complete)
        val working = MissionDraft("Working route", listOf(DraftWaypoint("work", 28.0, 77.0)))
        val plan = MissionPlanUiState(draft = working, loading = false)
        var missionAction: MissionPlanAction? = null
        try {
            compose.setContent { MamaGcsTheme {
                MamaGcsApp(vehicle = VehicleState(), settings = SettingsUiState(),
                    onThemeSelected = {}, missionPlan = plan,
                    onMissionAction = { missionAction = it })
            } }
            compose.onNodeWithTag("operation-browse-areas").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("area-browser-pack-${pack.id}").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("area-browser-pack-${pack.id}").performScrollTo().performClick()
            compose.onNodeWithTag("saved-pack-back").assertIsDisplayed()
            compose.waitUntil(45_000) {
                compose.onAllNodesWithTag("offline-pack-boundary-legend").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("offline-pack-boundary-legend").assertIsDisplayed()
            val map = loadedMap()
            compose.waitUntil(20_000) {
                var framed = false
                compose.runOnIdle {
                    framed = map.projection.visibleRegion.latLngBounds.contains(pack.bounds) &&
                        map.style?.getLayer("mama-offline-pack-bounds-line") != null
                }
                framed
            }
            compose.runOnIdle {
                assertEquals(working, plan.draft)
                assertEquals(null, missionAction)
            }
            waitForRenderedMapDetail(map)
        } finally {
            OfflineFieldMaps.delete(compose.activity, pack.id)
            compose.waitUntil(15_000) { OfflineFieldMaps.state.value.packs.none { it.id == pack.id } }
        }
    }

    /** Camera/style readiness can precede the native SurfaceView frame; verify actual map pixels. */
    private fun waitForRenderedMapDetail(map: MapLibreMap) {
        val deadline = System.currentTimeMillis() + 45_000
        var colours = 0
        do {
            Thread.sleep(2_000)
            compose.runOnIdle { map.triggerRepaint() }
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val sampled = mutableSetOf<Int>()
            // The central map region has no controls, notice or legend in this landscape fixture.
            for (y in screenshot.height * 48 / 100 until screenshot.height * 82 / 100 step 12) {
                for (x in screenshot.width * 28 / 100 until screenshot.width * 68 / 100 step 12) {
                    sampled.add(screenshot.getPixel(x, y) and 0x00ffffff)
                }
            }
            colours = sampled.size
            File(compose.activity.getExternalFilesDir(null), "phase244-operate-saved-field-offline.png")
                .outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            screenshot.recycle()
        } while (colours < 20 && System.currentTimeMillis() < deadline)
        assertTrue("Offline saved-map canvas stayed blank ($colours sampled colours)", colours >= 20)
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
