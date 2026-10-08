package com.mamadrones.gcs

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
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

/** Actual SDK load callbacks using disposable local styles; no provider key or network required. */
class MapLoadRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val vehicle = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
        position = GlobalPositionState(-35.363, 149.165), headingDegrees = 90.0)

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

    private fun awaitPosition(map: MapLibreMap, latitude: Double) = compose.waitUntil(10_000) {
        var found = false
        compose.runOnIdle {
            found = map.style?.getSourceAs<GeoJsonSource>("mama-vehicle-source")?.querySourceFeatures(null)
                ?.any { (it.geometry() as? Point)?.latitude()?.let { value -> kotlin.math.abs(value - latitude) < 0.000001 } == true } == true
        }
        found
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
