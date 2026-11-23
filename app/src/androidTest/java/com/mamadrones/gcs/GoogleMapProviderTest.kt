package com.mamadrones.gcs

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.mamadrones.gcs.domain.model.BasemapStyle
import com.mamadrones.gcs.domain.model.CoverageMapLine
import com.mamadrones.gcs.domain.model.CoverageMapPoint
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.map.LocalBasemapStyle
import com.mamadrones.gcs.presentation.map.VehicleMap
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Google tile and local-overlay acceptance on a disposable emulator; no telemetry transport. */
class GoogleMapProviderTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun googleMapLoadsDraftOverlaysAndSelectsNumberedWaypoint() {
        assertTrue("Supply the restricted debug Google Maps key for this online test", BuildConfig.GOOGLE_MAPS_CONFIGURED)
        val basemap = mutableStateOf(BasemapStyle.GOOGLE_STREETS)
        val selected = AtomicReference<String?>(null)
        val route = listOf(
            DraftWaypoint("w1", -35.3631, 149.1651),
            DraftWaypoint("w2", -35.3633, 149.1653),
        )
        val fence = listOf(
            DraftWaypoint("f1", -35.3630, 149.1650),
            DraftWaypoint("f2", -35.3630, 149.1655),
            DraftWaypoint("f3", -35.3635, 149.1655),
        )
        compose.setContent { MamaGcsTheme {
            CompositionLocalProvider(LocalBasemapStyle provides basemap.value) {
                VehicleMap(VehicleState(), Modifier.fillMaxSize(), draftWaypoints = route, draftFence = fence,
                    planningMode = true, onWaypointSelected = selected::set,
                    coverageLines = listOf(CoverageMapLine(
                        CoverageMapPoint(-35.3631, 149.16515), CoverageMapPoint(-35.3634, 149.16545))),
                    coverageGapSamples = listOf(CoverageMapPoint(-35.3632, 149.1654)))
            }
        } }
        compose.waitUntil(40_000) {
            compose.onAllNodesWithText("LOADING GOOGLE MAP").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithText("GOOGLE MAP UNAVAILABLE").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("GOOGLE MAP UNAVAILABLE").assertDoesNotExist()
        compose.onNodeWithTag("mission-fit").assertIsEnabled().performClick()
        val view = findGoogleMap(compose.activity.window.decorView)
        assertNotNull("Google MapView must be installed", view)
        val mapRef = AtomicReference<GoogleMap>()
        compose.runOnIdle { view!!.getMapAsync(mapRef::set) }
        compose.waitUntil(10_000) { mapRef.get() != null }
        compose.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull("Google map screenshot", screenshot)
        File(compose.activity.getExternalFilesDir(null), "phase229-google-map.png").outputStream().use {
            assertTrue(screenshot!!.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot!!.recycle()
        val coordinates = IntArray(2)
        var targetX = 0f
        var targetY = 0f
        compose.runOnIdle {
            val point = mapRef.get().projection.toScreenLocation(LatLng(route[1].latitude, route[1].longitude))
            view!!.getLocationOnScreen(coordinates)
            targetX = (coordinates[0] + point.x).toFloat()
            targetY = (coordinates[1] + point.y).toFloat()
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val down = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, targetX, targetY, 0))
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down + 60, MotionEvent.ACTION_UP, targetX, targetY, 0))
        compose.waitUntil(5_000) { selected.get() == "w2" }
        compose.runOnIdle { basemap.value = BasemapStyle.GOOGLE_SATELLITE }
        var satelliteSelected = false
        compose.waitUntil(10_000) {
            compose.runOnIdle { satelliteSelected = mapRef.get()?.mapType == GoogleMap.MAP_TYPE_SATELLITE }
            satelliteSelected
        }
        assertEquals("w2", selected.get())
    }

    private fun findGoogleMap(root: View): MapView? {
        if (root is MapView) return root
        if (root is ViewGroup) for (index in 0 until root.childCount) {
            findGoogleMap(root.getChildAt(index))?.let { return it }
        }
        return null
    }
}
