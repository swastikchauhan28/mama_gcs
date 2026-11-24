package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.map.OfflineFieldArea
import com.mamadrones.gcs.presentation.map.OfflineFieldMaps
import com.mamadrones.gcs.presentation.map.MapStyleConfig
import com.mamadrones.gcs.presentation.map.VehicleMap
import com.mamadrones.gcs.presentation.screens.MapSettingsScreen
import com.mamadrones.gcs.presentation.settings.SettingsUiState
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre

/** Run interrupt/start and resume methods in separate instrumentation invocations. */
class OfflineFieldRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    private val cancelDraft = MissionDraft(name = "Phase 232 cancel test", waypoints = listOf(
        DraftWaypoint("a", 28.6100, 77.2000), DraftWaypoint("b", 28.6440, 77.2340)))
    private val interruptedDraft = MissionDraft(name = "Phase 232 interrupted test", waypoints = listOf(
        DraftWaypoint("a", 17.3850, 78.4860), DraftWaypoint("b", 17.4190, 78.5200)))

    @Test fun cancelKeepsAnIncompletePack() {
        assertTrue(OfflineFieldArea.fromDraft(cancelDraft) != null)
        removePriorTestPack(cancelDraft.name)
        compose.setContent { MamaGcsTheme {
            MapSettingsScreen(SettingsUiState(loading = false), draft = cancelDraft, onOpenMap = {})
        } }
        compose.waitUntil(10_000) { !OfflineFieldMaps.state.value.loading }
        compose.onNodeWithTag("offline-field-download").performScrollTo().performClick()
        compose.waitUntil(5_000) { OfflineFieldMaps.state.value.downloading }
        compose.onNodeWithTag("offline-field-cancel").performScrollTo().performClick()
        compose.waitUntil(20_000) {
            val state = OfflineFieldMaps.state.value
            !state.downloading && !state.loading && state.packs.any { it.name == cancelDraft.name }
        }
        val pack = OfflineFieldMaps.state.value.packs.single { it.name == cancelDraft.name }
        assertFalse(pack.complete)
        compose.onNodeWithTag("offline-field-resume-${pack.id}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("offline-field-view-${pack.id}").assertDoesNotExist()
        File(compose.activity.getExternalFilesDir(null), "phase232-incomplete-settings.png").outputStream().use {
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        OfflineFieldMaps.delete(compose.activity, pack.id)
        compose.waitUntil(10_000) { OfflineFieldMaps.state.value.packs.none { it.id == pack.id } }
    }

    @Test fun startDownloadForProcessInterruption() {
        val area = OfflineFieldArea.fromDraft(interruptedDraft)
        assertTrue("Synthetic area must remain bounded", area != null && area.estimatedTiles > 100)
        removePriorTestPack(interruptedDraft.name)
        OfflineFieldMaps.download(compose.activity, interruptedDraft)
        compose.waitUntil(20_000) {
            OfflineFieldMaps.state.value.packs.any { it.name == interruptedDraft.name }
        }
        val state = OfflineFieldMaps.state.value
        assertTrue("Download must still be active before process interruption", state.downloading)
        assertFalse(state.packs.single { it.name == interruptedDraft.name }.complete)
        // Simulate network loss before the host closes this test process. The next process
        // starts with MapLibre networking restored and must explicitly resume this region.
        MapLibre.setConnected(false)
        Thread.sleep(1_000)
        assertFalse(OfflineFieldMaps.state.value.packs.single { it.name == interruptedDraft.name }.complete)
        // The host ends this instrumentation process and force-stops only the disposable emulator app.
        // A separate invocation below must discover the persisted incomplete native region.
    }

    @Test fun resumeAfterProcessRestartAndRenderOffline() {
        OfflineFieldMaps.refresh(compose.activity)
        compose.waitUntil(15_000) { !OfflineFieldMaps.state.value.loading }
        val saved = OfflineFieldMaps.state.value.packs.single { it.name == interruptedDraft.name }
        assertFalse("Interrupted pack must not be marked ready", saved.complete)
        assertFalse(OfflineFieldMaps.state.value.downloading)
        val showMap = mutableStateOf(false)
        compose.setContent { MamaGcsTheme {
            if (showMap.value) VehicleMap(VehicleState(), Modifier.fillMaxSize().testTag("recovered-offline-map"),
                draftWaypoints = interruptedDraft.waypoints, planningMode = true,
                styleUrl = MapStyleConfig.OFFLINE_FIELD_STYLE_URL)
            else MapSettingsScreen(SettingsUiState(loading = false), draft = interruptedDraft, onOpenMap = {})
        } }
        compose.waitUntil(15_000) { !OfflineFieldMaps.state.value.loading }
        compose.onNodeWithTag("offline-field-resume-${saved.id}").performScrollTo().performClick()
        compose.waitUntil(300_000) {
            val state = OfflineFieldMaps.state.value
            (!state.downloading && !state.loading && state.packs.any { it.id == saved.id && it.complete }) ||
                (!state.downloading && !state.loading && state.message?.contains("ready offline") != true)
        }
        assertTrue("Resume result: ${OfflineFieldMaps.state.value.message}",
            OfflineFieldMaps.state.value.packs.any { it.id == saved.id && it.complete })
        MapLibre.setConnected(false)
        try {
            compose.runOnIdle { showMap.value = true }
            compose.onNodeWithTag("recovered-offline-map").assertIsDisplayed()
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
            File(compose.activity.getExternalFilesDir(null), "phase232-resumed-offline-map.png").outputStream().use {
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            assertTrue("Resumed offline map stayed blank ($colours sampled colours)", colours >= 20)
        } finally {
            MapLibre.setConnected(true)
            OfflineFieldMaps.state.value.packs.filter { it.name == interruptedDraft.name }.forEach {
                OfflineFieldMaps.delete(compose.activity, it.id)
            }
            compose.waitUntil(15_000) {
                OfflineFieldMaps.state.value.packs.none { it.name == interruptedDraft.name }
            }
        }
    }

    private fun removePriorTestPack(name: String) {
        OfflineFieldMaps.refresh(compose.activity)
        compose.waitUntil(15_000) { !OfflineFieldMaps.state.value.loading }
        OfflineFieldMaps.state.value.packs.filter { it.name == name }.forEach {
            OfflineFieldMaps.delete(compose.activity, it.id)
        }
        compose.waitUntil(15_000) { OfflineFieldMaps.state.value.packs.none { it.name == name } }
    }
}
