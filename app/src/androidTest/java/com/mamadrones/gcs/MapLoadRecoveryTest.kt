package com.mamadrones.gcs

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.map.VehicleMap
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/** Actual SDK load callbacks using disposable local styles; no provider key or network required. */
class MapLoadRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()
    private val vehicle = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
        position = GlobalPositionState(-35.363, 149.165), headingDegrees = 90.0)

    @Test fun homeMarkerIsSeparateFromVehicleAndClearsWhenEvidenceExpires() {
        val file = File.createTempFile("map-home-", ".json", compose.activity.cacheDir)
        file.writeText(VALID_STYLE)
        val url = mutableStateOf<String?>(Uri.fromFile(file).toString())
        fun liveState(): VehicleState {
            val now = System.currentTimeMillis()
            return vehicle.copy(lastHeartbeatAtEpochMillis = now,
                mavlinkDiagnostics = MavlinkDiagnostics(active = true, startedAtEpochMillis = now - 100_000),
                homePosition = HomePositionState(-35.3631, 149.1651, 584.0, now - 90_000))
        }
        val state = mutableStateOf(liveState())
        try {
            compose.setContent { MamaGcsTheme { VehicleMap(state.value, Modifier.fillMaxSize(), styleUrl = url.value) } }
            val map = awaitLoaded()
            compose.runOnIdle { state.value = liveState() }
            awaitPosition(map, -35.3631, "mama-home-source") {
                state.value = state.value.copy(lastHeartbeatAtEpochMillis = System.currentTimeMillis())
                assertNotNull(HomePositionEvidence.visibleOnMap(state.value, System.currentTimeMillis()))
            }
            compose.runOnIdle { map.triggerRepaint() }
            val screenshot = requireNotNull(androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().uiAutomation.takeScreenshot())
            try {
                File(compose.activity.getExternalFilesDir(null), "phase51-home-marker.png").outputStream().use {
                    assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { screenshot.recycle() }
            awaitPosition(map, -35.363)
            compose.runOnIdle {
                assertNotNull(map.style!!.getLayer("mama-home-marker"))
                assertNotNull(map.style!!.getLayer("mama-home-label"))
                assertEquals(-35.363, map.cameraPosition.target!!.latitude, 0.000001)
            }
            // Let wall-clock time alone expire the heartbeat, without replacing the state.
            awaitPosition(map, null, "mama-home-source")
            compose.runOnIdle { state.value = liveState() }
            awaitPosition(map, -35.3631, "mama-home-source") {
                state.value = state.value.copy(lastHeartbeatAtEpochMillis = System.currentTimeMillis())
            }
            compose.runOnIdle { state.value = state.value.copy(connectionStatus = VehicleConnectionState.DISCONNECTED) }
            awaitPosition(map, null, "mama-home-source")
            compose.runOnIdle { state.value = liveState().copy(homePosition = HomePositionState(null, null, null, System.currentTimeMillis())) }
            awaitPosition(map, null, "mama-home-source")
            compose.runOnIdle { state.value = VehicleState() }
            awaitPosition(map, null, "mama-home-source")
        } finally {
            compose.runOnIdle { url.value = null }
            compose.waitForIdle()
            check(file.delete())
        }
    }

    @Test fun switchingStylesPreservesCameraAndRestoresAllOverlays() {
        val first = File.createTempFile("map-switch-a-", ".json", compose.activity.cacheDir)
        val second = File.createTempFile("map-switch-b-", ".json", compose.activity.cacheDir)
        first.writeText(VALID_STYLE)
        second.writeText(VALID_STYLE.replace("#12202c", "#ccddee"))
        val url = mutableStateOf<String?>(Uri.fromFile(first).toString())
        val points = listOf(DraftWaypoint("a", -35.363, 149.165), DraftWaypoint("b", -35.364, 149.166))
        try {
            compose.setContent { MamaGcsTheme {
                VehicleMap(vehicle, Modifier.fillMaxSize(), planningMode = true,
                    draftWaypoints = points, draftFence = points + DraftWaypoint("c", -35.365, 149.164), styleUrl = url.value)
            } }
            val map = awaitLoaded()
            awaitPosition(map, -35.363)
            compose.runOnIdle {
                map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(org.maplibre.android.geometry.LatLng(-35.37, 149.17)).zoom(14.0).bearing(30.0).build()
                url.value = Uri.fromFile(second).toString()
            }
            compose.waitUntil(10_000) {
                var switched = false
                compose.runOnIdle { switched = map.style?.uri == url.value && map.style?.isFullyLoaded == true }
                switched
            }
            awaitPosition(map, -35.363)
            compose.runOnIdle {
                assertEquals(-35.37, map.cameraPosition.target!!.latitude, 0.00001)
                assertEquals(14.0, map.cameraPosition.zoom, 0.001)
                assertEquals(30.0, map.cameraPosition.bearing, 0.001)
                for (source in listOf("mama-vehicle-source", "mama-track-source", "mama-heading-source",
                    "mama-draft-points", "mama-draft-line", "mama-draft-fence", "mama-home-source")) {
                    assertNotNull(source, map.style!!.getSource(source))
                }
            }
            compose.onNodeWithTag("mission-fit").assertIsEnabled()
        } finally {
            compose.runOnIdle { url.value = null }
            compose.waitForIdle()
            check(first.delete() && second.delete())
        }
    }

    @Test fun missingConfigurationDisablesMapControlsEvenWithPositionAndDraft() {
        compose.setContent { MamaGcsTheme(ThemeMode.DARK) {
            VehicleMap(vehicle, Modifier.fillMaxSize(), planningMode = true,
                draftWaypoints = listOf(DraftWaypoint("a", -35.363, 149.165)), styleUrl = null)
        } }
        compose.onNodeWithText("MAP SOURCE NOT CONFIGURED").assertIsDisplayed()
        compose.onNodeWithContentDescription("Zoom in").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Zoom out").assertIsNotEnabled()
        compose.onNodeWithTag("map-center").assertIsNotEnabled()
        compose.onNodeWithTag("map-follow").assertIsNotEnabled()
        compose.onNodeWithTag("mission-fit").assertIsNotEnabled()
        compose.onNodeWithText("Retry map").assertDoesNotExist()
    }

    @Test fun invalidStyleCanRetryRepeatedlyAndRecoverAtTheSameUri() {
        val file = File.createTempFile("map-recovery-", ".json", compose.activity.cacheDir)
        val url = mutableStateOf<String?>(Uri.fromFile(file).toString())
        val state = mutableStateOf(vehicle)
        val failures = AtomicInteger()
        val failureListener = MapView.OnDidFailLoadingMapListener { failures.incrementAndGet() }
        var view: MapView? = null
        try {
            file.writeText("invalid-style-secret-marker")
            compose.setContent { MamaGcsTheme(ThemeMode.DARK) {
                VehicleMap(state.value, Modifier.fillMaxSize(), styleUrl = url.value)
            } }
            awaitFailure()
            assertUnavailable()
            // Neither the raw URI nor SDK parser details belong in the operator error panel.
            compose.onNodeWithText(file.name, substring = true).assertDoesNotExist()
            compose.onNodeWithText("invalid-style-secret-marker", substring = true).assertDoesNotExist()
            compose.runOnIdle {
                view = requireNotNull(findMap(compose.activity.window.decorView))
                view!!.addOnDidFailLoadingMapListener(failureListener)
            }
            compose.onNodeWithText("Retry map").performClick()
            compose.waitUntil(10_000) { failures.get() >= 1 }
            awaitFailure()
            assertUnavailable()

            file.writeText(VALID_STYLE)
            compose.onNodeWithText("Retry map").performClick()
            val map = awaitLoaded()
            compose.onNodeWithText("MAP LOAD FAILED").assertDoesNotExist()
            compose.onNodeWithText("Retry map").assertDoesNotExist()
            compose.onNodeWithTag("map-center").assertIsEnabled()
            compose.onNodeWithTag("map-follow").assertIsEnabled()
            awaitPosition(map, -35.363)
            compose.runOnIdle {
                assertNotNull(map.style!!.getLayer("mama-vehicle-layer"))
                assertNotNull(map.style!!.getLayer("mama-track-layer"))
                assertNotNull(map.style!!.getLayer("mama-heading-layer"))
                state.value = vehicle.copy(position = GlobalPositionState(-35.364, 149.166))
            }
            awaitPosition(map, -35.364)

            // Exercise a native failure while Compose still holds the previous loaded style.
            // Unlike a URL change, this does not dispose/recreate the app's effect first.
            file.writeText("invalid native reload")
            compose.runOnIdle { map.setStyle(requireNotNull(url.value)) }
            awaitFailure()
            assertUnavailable()
            file.writeText(VALID_STYLE)
            compose.onNodeWithText("Retry map").performClick()
            awaitPosition(awaitLoaded(), -35.364)

            // Remove configuration after success, then reload the same source after another failure.
            compose.runOnIdle { url.value = null }
            compose.onNodeWithText("MAP SOURCE NOT CONFIGURED").assertIsDisplayed()
            assertUnavailable()
            file.writeText("invalid again")
            compose.runOnIdle { url.value = Uri.fromFile(file).toString() }
            awaitFailure()
            assertUnavailable()
            file.writeText(VALID_STYLE)
            compose.onNodeWithText("Retry map").performClick()
            awaitPosition(awaitLoaded(), -35.364)
            compose.onNodeWithTag("map-center").assertIsEnabled()
        } finally {
            compose.runOnIdle { view?.removeOnDidFailLoadingMapListener(failureListener); url.value = null }
            compose.waitForIdle()
            check(file.delete()) { "Could not remove test-owned map style" }
        }
    }

    private fun awaitFailure() = compose.waitUntil(10_000) {
        compose.onAllNodesWithText("MAP LOAD FAILED").fetchSemanticsNodes().isNotEmpty()
    }

    private fun assertUnavailable() {
        compose.onNodeWithTag("map-center").assertIsNotEnabled()
        compose.onNodeWithTag("map-follow").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Zoom in").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Zoom out").assertIsNotEnabled()
    }

    private fun awaitLoaded(): MapLibreMap {
        var map: MapLibreMap? = null
        compose.runOnIdle { requireNotNull(findMap(compose.activity.window.decorView)).getMapAsync { map = it } }
        compose.waitUntil(10_000) {
            var ready = false
            compose.runOnIdle { ready = map?.style?.isFullyLoaded == true }
            ready
        }
        compose.waitForIdle()
        return requireNotNull(map)
    }

    private fun awaitPosition(map: MapLibreMap, latitude: Double?, sourceId: String = "mama-vehicle-source",
        beforePoll: () -> Unit = {}) {
        // The home freshness ticker uses Compose's coroutine clock. waitUntil polls wall time
        // and does not advance a delayed effect once Compose is idle. Drive that clock explicitly;
        // eligibility still compares real receipt/heartbeat times and must expire without input.
        if (sourceId == "mama-home-source") compose.mainClock.advanceTimeBy(1_000)
        // Style JSON can finish before the native renderer exists. Source queries need a
        // completed render, not just isFullyLoaded, or the SDK may dereference a null renderer.
        val rendered = AtomicBoolean(false)
        val listener = MapView.OnDidFinishRenderingFrameListener { _, _, _ -> rendered.set(true) }
        lateinit var view: MapView
        compose.runOnIdle {
            view = requireNotNull(findMap(compose.activity.window.decorView))
            view.addOnDidFinishRenderingFrameListener(listener)
            map.triggerRepaint()
        }
        val observed = mutableSetOf<String>()
        try {
            compose.waitUntil(10_000) { rendered.get() }
            compose.waitUntil(10_000) {
                if (sourceId == "mama-home-source") compose.mainClock.advanceTimeBy(1_000)
                var found = false
                compose.runOnIdle {
                    beforePoll()
                    val features = map.style?.getSourceAs<GeoJsonSource>(sourceId)?.querySourceFeatures(null)
                    if (observed.size < 10) observed.add(features?.map { it.geometry().toString() }.toString())
                    found = if (latitude == null) features?.isEmpty() == true else features
                        ?.any { (it.geometry() as? Point)?.latitude()?.let { value -> kotlin.math.abs(value - latitude) < 0.000001 } == true } == true
                }
                found
            }
        } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            var details = ""
            compose.runOnIdle {
                details = "camera=${map.cameraPosition}; layers=${map.style?.layers?.map { it.id }}; vehicle=" +
                    map.style?.getSourceAs<GeoJsonSource>("mama-vehicle-source")?.querySourceFeatures(null)
            }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                File(compose.activity.getExternalFilesDir(null), "phase31-local-failure.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            throw AssertionError("Expected $sourceId latitude=$latitude; observed=$observed; $details", failure)
        } finally {
            compose.runOnIdle { view.removeOnDidFinishRenderingFrameListener(listener) }
        }
    }

    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findMap(view.getChildAt(index))?.let { return it }
        return null
    }

    private companion object {
        const val VALID_STYLE = """{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#12202c"}}]}"""
    }
}
