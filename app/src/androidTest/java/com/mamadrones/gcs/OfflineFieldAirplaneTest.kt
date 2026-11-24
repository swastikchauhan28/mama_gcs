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
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.map.OfflineFieldMaps
import com.mamadrones.gcs.presentation.map.OfflineFieldArea
import com.mamadrones.gcs.presentation.map.MapStyleConfig
import com.mamadrones.gcs.presentation.map.VehicleMap
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap

/** Run the two methods separately; the host restarts the app and disables Android networking between them. */
class OfflineFieldAirplaneTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()
    private val draft = MissionDraft(name = "Phase 233 airplane test", waypoints = listOf(
        DraftWaypoint("one", 12.9662, 77.5938),
        DraftWaypoint("two", 12.9842, 77.6118)))

    @Test fun prepareSmallSavedPackOnline() {
        assertTrue("Synthetic area must fit the offline limit", OfflineFieldArea.fromDraft(draft) != null)
        OfflineFieldMaps.refresh(compose.activity)
        compose.waitUntil(15_000) { !OfflineFieldMaps.state.value.loading }
        OfflineFieldMaps.state.value.packs.filter { it.name == draft.name }.forEach {
            OfflineFieldMaps.delete(compose.activity, it.id)
        }
        compose.waitUntil(15_000) { OfflineFieldMaps.state.value.packs.none { it.name == draft.name } }
        OfflineFieldMaps.download(compose.activity, draft)
        compose.waitUntil(180_000) {
            val state = OfflineFieldMaps.state.value
            state.packs.any { it.name == draft.name && it.complete } ||
                (!state.downloading && !state.loading && state.message?.contains("ready offline") != true)
        }
        assertTrue("Download: ${OfflineFieldMaps.state.value.message}",
            OfflineFieldMaps.state.value.packs.any { it.name == draft.name && it.complete })
        // Keep this one synthetic pack so a new app process can read it with Android networking off.
    }

    @Test fun savedAreaRendersAndOutsideAreaIsMarkedOffline() {
        OfflineFieldMaps.refresh(compose.activity)
        compose.waitUntil(15_000) { !OfflineFieldMaps.state.value.loading }
        val pack = OfflineFieldMaps.state.value.packs.single { it.name == draft.name }
        assertTrue("Saved pack must be complete after process restart", pack.complete)
        try {
            compose.setContent { MamaGcsTheme {
                VehicleMap(VehicleState(), Modifier.fillMaxSize().testTag("airplane-map"),
                    draftWaypoints = draft.waypoints, planningMode = true,
                    styleUrl = MapStyleConfig.OFFLINE_FIELD_STYLE_URL)
            } }
            compose.onNodeWithTag("airplane-map").assertIsDisplayed()
            val map = loadedMap()
            compose.waitUntil(10_000) { !OfflineFieldMaps.state.value.loading }
            compose.runOnIdle {
                map.cameraPosition = CameraPosition.Builder().target(LatLng(12.9752, 77.6028)).zoom(18.0).build()
            }
            compose.waitUntil(10_000) {
                var atField = false
                compose.runOnIdle {
                    atField = map.cameraPosition.target?.let {
                        kotlin.math.abs(it.latitude - 12.9752) < 0.0001 &&
                            kotlin.math.abs(it.longitude - 77.6028) < 0.0001 &&
                            kotlin.math.abs(map.cameraPosition.zoom - 18.0) < 0.01
                    } == true
                }
                atField
            }
            Thread.sleep(4_000) // Native viewport bounds may lag the cameraPosition property.
            compose.runOnIdle {
                val visible = map.projection.visibleRegion.latLngBounds
                assertTrue("Saved bounds ${pack.bounds} did not contain viewport $visible at ${map.cameraPosition}",
                    pack.bounds.contains(visible))
            }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("offline-field-outside-notice").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("offline-field-outside-notice").assertDoesNotExist()
            val screenshot = waitForMapDetail()
            File(compose.activity.getExternalFilesDir(null), "phase233-android-offline-inside.png")
                .outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }

            // The center remains within the region, but the visible viewport crosses its edge.
            val nearEdge = LatLng(12.9859, 77.6135)
            assertTrue("Edge fixture center must be inside the saved pack", pack.bounds.contains(nearEdge))
            compose.runOnIdle {
                map.cameraPosition = CameraPosition.Builder().target(nearEdge).zoom(18.0).build()
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("VIEW NOT FULLY SAVED OFFLINE", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("VIEW NOT FULLY SAVED OFFLINE", substring = true).assertIsDisplayed()
            Thread.sleep(4_000)
            File(compose.activity.getExternalFilesDir(null), "phase233-android-offline-edge.png")
                .outputStream().use {
                    assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                        .compress(Bitmap.CompressFormat.PNG, 100, it))
                }

            compose.runOnIdle {
                map.cameraPosition = CameraPosition.Builder().target(LatLng(13.5, 78.0)).zoom(17.0).build()
            }
            compose.waitUntil(10_000) {
                var moved = false
                compose.runOnIdle { moved = map.cameraPosition.target?.latitude?.let { it > 13.4 } == true }
                moved
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("NOT SAVED OFFLINE", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("offline-field-outside-notice").assertIsDisplayed()
            compose.onNodeWithText("NOT SAVED OFFLINE", substring = true).assertIsDisplayed()
            // The Compose notice can update before the native renderer swaps its next frame.
            Thread.sleep(4_000)
            File(compose.activity.getExternalFilesDir(null), "phase233-android-offline-outside.png")
                .outputStream().use {
                    assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                        .compress(Bitmap.CompressFormat.PNG, 100, it))
                }
        } finally {
            OfflineFieldMaps.delete(compose.activity, pack.id)
            compose.waitUntil(15_000) { OfflineFieldMaps.state.value.packs.none { it.id == pack.id } }
        }
    }

    private fun waitForMapDetail(): Bitmap {
        val deadline = System.currentTimeMillis() + 45_000
        var screenshot: Bitmap
        var colours: Int
        do {
            Thread.sleep(1_000)
            screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val sampled = mutableSetOf<Int>()
            for (y in screenshot.height / 8 until screenshot.height * 3 / 5 step 16) {
                for (x in screenshot.width / 20 until screenshot.width * 2 / 5 step 16) {
                    sampled.add(screenshot.getPixel(x, y) and 0x00ffffff)
                }
            }
            colours = sampled.size
        } while (colours < 20 && System.currentTimeMillis() < deadline)
        assertTrue("Android-offline map canvas stayed blank ($colours sampled colours)", colours >= 20)
        return screenshot
    }

    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findMap(view.getChildAt(i))?.let { return it }
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
