package com.mamadrones.gcs

import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
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
import java.util.concurrent.atomic.AtomicBoolean

/** Online map acceptance with synthetic positions. Never opens a vehicle transport. */
class MapAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun loadedMap(): MapLibreMap {
        var map: MapLibreMap? = null
        compose.runOnIdle { requireNotNull(findMap(compose.activity.window.decorView)).getMapAsync { map = it } }
        compose.waitUntil(45_000) {
            var ready = false
            compose.runOnIdle { ready = map?.style?.isFullyLoaded == true }
            ready
        }
        return requireNotNull(map)
    }
    private fun cameraLatitude(map: MapLibreMap): Double {
        var result = 0.0
        compose.runOnIdle { result = requireNotNull(map.cameraPosition.target).latitude }
        return result
    }
    private fun near(map: MapLibreMap, latitude: Double) = compose.waitUntil(10_000) {
        kotlin.math.abs(cameraLatitude(map) - latitude) < 0.00001
    }

    private fun afterCameraIdle(map: MapLibreMap, action: () -> Unit) {
        val idle = AtomicBoolean(false)
        val listener = MapLibreMap.OnCameraIdleListener { idle.set(true) }
        compose.runOnIdle { map.addOnCameraIdleListener(listener) }
        try {
            action()
            compose.waitUntil(10_000) { idle.get() }
        } finally {
            compose.runOnIdle { map.removeOnCameraIdleListener(listener) }
        }
    }

    // Native MapView needs real device-time touch events; Compose's virtual event clock
    // can advance ahead of the native gesture recognizer.
    private fun nativeGesture(start: Offset, end: Offset = start, duration: Long = 100) {
        val location = IntArray(2)
        compose.runOnIdle { requireNotNull(findMap(compose.activity.window.decorView)).getLocationOnScreen(location) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        fun event(action: Int, point: Offset) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                point.x + location[0], point.y + location[1], 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            // Queue DOWN without waiting for a busy UI thread: waiting here can stretch a
            // short tap into a long press before UP is even created.
            check(instrumentation.uiAutomation.injectInputEvent(event, action != MotionEvent.ACTION_DOWN))
            event.recycle()
        }
        event(MotionEvent.ACTION_DOWN, start)
        if (start == end) {
            SystemClock.sleep(duration)
        } else for (step in 1..20) {
            SystemClock.sleep(duration / 20)
            event(MotionEvent.ACTION_MOVE, start + (end - start) * (step / 20f))
        }
        event(MotionEvent.ACTION_UP, end)
        instrumentation.waitForIdleSync()
    }
    private fun mapSize(): Offset {
        var size = Offset.Zero
        compose.runOnIdle { val view = requireNotNull(findMap(compose.activity.window.decorView)); size = Offset(view.width.toFloat(), view.height.toFloat()) }
        return size
    }

    @Test fun liveMapZoomPanCenterAndFollowRespondToSyntheticTelemetry() {
        val vehicle = mutableStateOf(VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            position = GlobalPositionState(-35.3632621, 149.1652374), headingDegrees = 90.0))
        compose.setContent { MamaGcsTheme(ThemeMode.DARK) {
            VehicleMap(vehicle.value, Modifier.fillMaxSize().testTag("test-map"))
        } }
        val map = loadedMap()
        near(map, -35.3632621)
        compose.onNodeWithTag("map-center").assertIsEnabled()
        compose.onNodeWithText("Following").assertIsDisplayed()
        var zoom = 0.0
        compose.runOnIdle { zoom = map.cameraPosition.zoom }
        afterCameraIdle(map) { compose.onNodeWithContentDescription("Zoom in").performClick() }
        compose.waitUntil(10_000) {
            var changed = false
            compose.runOnIdle { changed = map.cameraPosition.zoom > zoom + 0.5 }
            changed
        }
        val size = mapSize()
        nativeGesture(Offset(size.x * 0.3f, size.y * 0.4f), Offset(size.x * 0.65f, size.y * 0.4f), 600)
        compose.onNodeWithText("Follow", substring = false).assertIsDisplayed()
        compose.runOnIdle { vehicle.value = vehicle.value.copy(position = GlobalPositionState(-35.364, 149.166)) }
        compose.onNodeWithTag("map-center").performClick()
        near(map, -35.364)
        compose.onNodeWithText("Follow", substring = false).performClick()
        compose.runOnIdle { vehicle.value = vehicle.value.copy(position = GlobalPositionState(-35.365, 149.167)) }
        near(map, -35.365)
        compose.runOnIdle { vehicle.value = VehicleState() }
        compose.onNodeWithTag("map-center").assertIsNotEnabled()
        compose.onNodeWithText("Follow", substring = true).assertIsNotEnabled()
    }

    @Test fun planningMapLongPressTapSelectionAndFitUseActualMapGestures() {
        val points = listOf(DraftWaypoint("a", -35.3632621, 149.1652374), DraftWaypoint("b", -35.364, 149.166))
        var requested: Pair<Double, Double>? = null
        var selected: String? = null
        compose.setContent { MamaGcsTheme(ThemeMode.DARK) {
            VehicleMap(VehicleState(), Modifier.fillMaxSize().testTag("test-map"),
                draftWaypoints = points, planningMode = true,
                onWaypointRequested = { lat, lon -> requested = lat to lon }, onWaypointSelected = { selected = it })
        } }
        val map = loadedMap()
        near(map, points.first().latitude)
        afterCameraIdle(map) { compose.onNodeWithTag("mission-fit").assertIsEnabled().performClick() }
        compose.waitUntil(10_000) {
            var fitted = false
            compose.runOnIdle { fitted = map.cameraPosition.target?.latitude?.let { it < points.first().latitude && it > points.last().latitude } == true }
            fitted
        }
        var offset = Offset.Zero
        compose.runOnIdle {
            val location = map.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(points.first().latitude, points.first().longitude))
            offset = Offset(location.x, location.y)
        }
        nativeGesture(offset)
        compose.waitUntil(5_000) { selected != null }
        assertEquals("a", selected)
        val size = mapSize()
        nativeGesture(Offset(size.x * 0.25f, size.y * 0.4f), duration = 1000)
        compose.waitUntil(5_000) { requested != null }
        assertTrue(requested!!.first in -90.0..90.0)
        assertTrue(requested!!.second in -180.0..180.0)
    }
}
