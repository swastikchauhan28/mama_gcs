package com.mamadrones.gcs

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.mamadrones.gcs.domain.model.BasemapStyle
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.presentation.map.OfflineFieldArea
import com.mamadrones.gcs.presentation.map.OfflineFieldMaps
import com.mamadrones.gcs.presentation.map.MapStyleConfig
import com.mamadrones.gcs.presentation.map.VehicleMap
import com.mamadrones.gcs.presentation.screens.MapSettingsScreen
import com.mamadrones.gcs.presentation.settings.SettingsUiState
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import android.graphics.Bitmap
import com.mamadrones.gcs.domain.model.VehicleState
import org.junit.Rule
import org.junit.Test

class OfflineFieldMapTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun fieldGeometryAndDownloadControlStayBounded() {
        assertNull(OfflineFieldArea.fromDraft(MissionDraft()))
        val point = DraftWaypoint("one", 12.9716, 77.5946)
        val draft = MissionDraft(name = "Offline field", waypoints = listOf(point))
        val area = OfflineFieldArea.fromDraft(draft)
        assertNotNull(area)
        assertEquals(true, area!!.estimatedTiles in 1..OfflineFieldArea.MAX_TILES)
        assertNull(OfflineFieldArea.fromDraft(MissionDraft(waypoints = listOf(
            DraftWaypoint("a", 12.0, 77.0), DraftWaypoint("b", 13.0, 78.0)))))
        assertNull(OfflineFieldArea.fromDraft(MissionDraft(waypoints = listOf(
            DraftWaypoint("a", 0.0, -179.9), DraftWaypoint("b", 0.0, 179.9)))))

        val currentDraft = mutableStateOf(MissionDraft())
        compose.setContent { MamaGcsTheme {
            MapSettingsScreen(SettingsUiState(loading = false), draft = currentDraft.value, onOpenMap = {})
        } }
        compose.onNodeWithTag("offline-field-download").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { currentDraft.value = draft }
        compose.waitUntil(10_000) { !com.mamadrones.gcs.presentation.map.OfflineFieldMaps.state.value.loading }
        compose.onNodeWithTag("offline-field-readiness").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("offline-field-download").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("basemap-OFFLINE_FIELD").performScrollTo().assertIsDisplayed()
    }

    @Test fun smallFieldPackCompletesAndCanBeShownWithoutNetwork() {
        val draft = MissionDraft(name = "Phase 231 offline test", waypoints = listOf(
            DraftWaypoint("one", 12.9716, 77.5946)))
        OfflineFieldMaps.refresh(compose.activity)
        compose.waitUntil(10_000) { !OfflineFieldMaps.state.value.loading }
        // A prior run can leave a synthetic pack. Remove only that test pack for a fresh download.
        OfflineFieldMaps.state.value.packs.filter { it.name == draft.name }.forEach {
            OfflineFieldMaps.delete(compose.activity, it.id)
        }
        compose.waitUntil(10_000) { OfflineFieldMaps.state.value.packs.none { it.name == draft.name } }
        OfflineFieldMaps.download(compose.activity, draft)
        compose.waitUntil(150_000) {
            val snapshot = OfflineFieldMaps.state.value
            snapshot.packs.any { it.name == draft.name && it.complete } ||
                (!snapshot.downloading && snapshot.message?.contains("ready offline") != true)
        }
        assertTrue("Offline pack: ${OfflineFieldMaps.state.value.message}; ${OfflineFieldMaps.state.value.packs.filter { it.name == draft.name }}",
            OfflineFieldMaps.state.value.packs.any { it.name == draft.name && it.complete })
        OfflineFieldMaps.refresh(compose.activity)
        compose.waitUntil(10_000) { OfflineFieldMaps.state.value.packs.any { it.name == draft.name && it.complete } }
        val showMap = mutableStateOf(false)
        compose.setContent { MamaGcsTheme {
            if (showMap.value) VehicleMap(VehicleState(), Modifier.fillMaxSize().testTag("offline-map-root"),
                draftWaypoints = draft.waypoints, planningMode = true,
                styleUrl = MapStyleConfig.OFFLINE_FIELD_STYLE_URL)
            else MapSettingsScreen(SettingsUiState(loading = false), draft = draft, onOpenMap = {})
        } }
        compose.onNodeWithTag("offline-field-readiness").performScrollTo().assertIsDisplayed()
        // Disable MapLibre's network access; this phone may still have cellular data for other apps.
        org.maplibre.android.MapLibre.setConnected(false)
        try {
            assertEquals(false, org.maplibre.android.MapLibre.isConnected())
            compose.runOnIdle { showMap.value = true }
            compose.onNodeWithTag("offline-map-root").assertIsDisplayed()
            compose.waitForIdle()
            val deadline = System.currentTimeMillis() + 45_000
            var screenshot: Bitmap
            var mapDetail: Int
            do {
                Thread.sleep(1_000)
                screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                // Sample only the upper-left map canvas: exclude the status bar, draft marker,
                // map controls and attribution. A blank MapLibre canvas has one flat colour.
                val colours = mutableSetOf<Int>()
                for (y in screenshot.height / 8 until screenshot.height * 3 / 5 step 16) {
                    for (x in screenshot.width / 20 until screenshot.width * 2 / 5 step 16) {
                        colours.add(screenshot.getPixel(x, y) and 0x00ffffff)
                    }
                }
                mapDetail = colours.size
            } while (mapDetail < 20 && System.currentTimeMillis() < deadline)
            if (mapDetail >= 20) {
                // Vector tile geometry can arrive before glyphs and labels on a slow emulator.
                Thread.sleep(8_000)
                screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            }
            File(compose.activity.getExternalFilesDir(null), "phase231-offline-field.png").outputStream().use {
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            assertTrue("Offline map canvas stayed blank ($mapDetail sampled colours)", mapDetail >= 20)
            val centreX = screenshot.width / 2
            val centreY = screenshot.height / 2
            val numeralPixels = (centreY - 12..centreY + 12).sumOf { y ->
                (centreX - 10..centreX + 10).count { x ->
                    val rgb = screenshot.getPixel(x, y)
                    android.graphics.Color.red(rgb) < 80 &&
                        android.graphics.Color.green(rgb) < 80 &&
                        android.graphics.Color.blue(rgb) < 80
                }
            }
            assertTrue("Offline waypoint number is missing", numeralPixels >= 3)
        } finally {
            org.maplibre.android.MapLibre.setConnected(true)
            OfflineFieldMaps.state.value.packs.filter { it.name == draft.name }.forEach { OfflineFieldMaps.delete(compose.activity, it.id) }
            compose.waitUntil(10_000) { OfflineFieldMaps.state.value.packs.none { it.name == draft.name } }
        }
    }
}
