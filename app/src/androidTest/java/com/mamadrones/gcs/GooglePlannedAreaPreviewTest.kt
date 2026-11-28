package com.mamadrones.gcs

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.mamadrones.gcs.domain.model.BasemapStyle
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.presentation.map.LocalBasemapStyle
import com.mamadrones.gcs.presentation.screens.RouteAreaPreviewScreen
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic route and a disposable Google APIs emulator; no vehicle transport or saved draft. */
class GooglePlannedAreaPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun googlePreviewAutomaticallyFitsNumberedRouteWithoutRoverPosition() {
        assertTrue("A restricted debug Maps key is required for this online check",
            BuildConfig.GOOGLE_MAPS_CONFIGURED)
        val north = DraftWaypoint("north", -35.3630, 149.1650)
        val south = DraftWaypoint("south", -35.3660, 149.1700)
        val draft = MissionDraft("Synthetic route", listOf(north, south))
        compose.setContent { MamaGcsTheme {
            CompositionLocalProvider(LocalBasemapStyle provides BasemapStyle.GOOGLE_STREETS) {
                RouteAreaPreviewScreen(draft, "Saved route", onBack = {})
            }
        } }

        compose.onNodeWithTag("route-preview-notice")
            .assertTextContains("Rover location not shown", substring = true)
        val mapView = findGoogleMap(compose.activity.window.decorView)
        assertNotNull("Google MapView must be installed", mapView)
        val map = AtomicReference<GoogleMap>()
        compose.runOnIdle { mapView!!.getMapAsync(map::set) }
        compose.waitUntil(10_000) { map.get() != null }

        // Initial camera starts at waypoint 1. Requiring the midpoint and both visible points
        // specifically verifies the automatic whole-route fit, without tapping Fit area.
        try {
            compose.waitUntil(45_000) {
                var fitted = false
                compose.runOnIdle {
                    val ready = map.get() ?: return@runOnIdle
                    val target = ready.cameraPosition.target
                    val bounds = ready.projection.visibleRegion.latLngBounds
                    fitted = target.latitude in -35.3650..-35.3640 &&
                        target.longitude in 149.1670..149.1680 &&
                        bounds.contains(LatLng(north.latitude, north.longitude)) &&
                        bounds.contains(LatLng(south.latitude, south.longitude))
                }
                fitted
            }
        } finally {
            // Preserve the rendered provider state if automatic fit times out.
            captureScreenshot("phase243-google-load-check.png")
        }
        compose.onNodeWithText("GOOGLE MAP UNAVAILABLE").assertDoesNotExist()
        compose.onNodeWithText("GOOGLE MAP TIMED OUT").assertDoesNotExist()
        compose.waitForIdle()
        captureScreenshot("phase243-google-planned-area.png")
    }

    private fun captureScreenshot(name: String) {
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull("Google preview screenshot", screenshot)
        File(compose.activity.getExternalFilesDir(null), name)
            .outputStream().use { assertTrue(screenshot!!.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot!!.recycle()
    }
    private fun findGoogleMap(root: View): MapView? {
        if (root is MapView) return root
        if (root is ViewGroup) for (index in 0 until root.childCount) {
            findGoogleMap(root.getChildAt(index))?.let { return it }
        }
        return null
    }
}
