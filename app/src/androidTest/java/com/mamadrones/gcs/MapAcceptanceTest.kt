package com.mamadrones.gcs

import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.map.VehicleMap
import com.mamadrones.gcs.presentation.map.LocalBasemapStyle
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Online map acceptance with synthetic positions. Never opens a vehicle transport. */
class MapAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun allBasemapsRenderThroughTheSharedPreferenceAndKeepDraftLayers() {
        val selected = mutableStateOf(BasemapStyle.STREETS)
        val homeState = mutableStateOf(VehicleState())
        val renderedFrames = AtomicInteger()
        var observedMap: MapLibreMap? = null
        val listener = MapView.OnDidFinishRenderingFrameListener { fully, _, _ ->
            if (fully && observedMap?.style?.uri?.contains("/maps/${selected.value.mapTilerId}/") == true &&
                observedMap?.style?.isFullyLoaded == true) {
                // The style URI can change before the renderer displays its first new frame.
                // Request subsequent frames rather than treating one queued callback as proof.
                if (renderedFrames.incrementAndGet() < 3) observedMap?.triggerRepaint()
            }
        }
        var view: MapView? = null
        try {
            compose.setContent { MamaGcsTheme {
                CompositionLocalProvider(LocalBasemapStyle provides selected.value) {
                    VehicleMap(homeState.value, Modifier.fillMaxSize(), planningMode = true,
                        draftWaypoints = listOf(DraftWaypoint("a", -35.3632621, 149.1652374),
                            DraftWaypoint("b", -35.364, 149.166)))
                }
            } }
            val map = loadedMap()
            compose.runOnIdle {
                observedMap = map
                view = requireNotNull(findMap(compose.activity.window.decorView))
                view!!.addOnDidFinishRenderingFrameListener(listener)
                map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(org.maplibre.android.geometry.LatLng(-35.3635, 149.1655)).zoom(16.0).build()
            }
            for (style in listOf(BasemapStyle.SATELLITE, BasemapStyle.HYBRID, BasemapStyle.STREETS)) {
                compose.runOnIdle { renderedFrames.set(0); selected.value = style }
                compose.waitUntil(45_000) {
                    var ready = false
                    compose.runOnIdle { ready = map.style?.uri?.contains("/maps/${style.mapTilerId}/") == true &&
                        map.style?.isFullyLoaded == true && renderedFrames.get() >= 3 }
                    ready
                }
                compose.onNodeWithText("MAP LOAD FAILED").assertDoesNotExist()
                compose.onNodeWithTag("mission-fit").assertIsEnabled()
                compose.runOnIdle {
                    assertNotNull(map.style!!.getLayer("mama-draft-waypoints"))
                    assertNotNull(map.style!!.getLayer("mama-draft-labels"))
                    val now = System.currentTimeMillis()
                    homeState.value = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
                        lastHeartbeatAtEpochMillis = now,
                        mavlinkDiagnostics = MavlinkDiagnostics(active = true, startedAtEpochMillis = now - 1_000),
                        homePosition = HomePositionState(-35.3635, 149.1652, 584.0, now))
                }
                compose.waitUntil(5_000) {
                    var present = false
                    compose.runOnIdle {
                        homeState.value = homeState.value.copy(lastHeartbeatAtEpochMillis = System.currentTimeMillis())
                        present = map.style?.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>("mama-home-source")
                            ?.querySourceFeatures(null)?.isNotEmpty() == true
                    }
                    present
                }
                // Capture the displayed compositor output, not the SDK snapshot callback's
                // potentially stale framebuffer. Sample the map centre, away from controls/logo.
                var captured: android.graphics.Bitmap? = null
                compose.waitUntil(10_000) {
                    val marker = IntArray(2)
                    compose.runOnIdle {
                        // Model continuing telemetry while checking rendering, not a dead link.
                        // HOME_POSITION itself stays unchanged (it is session metadata).
                        homeState.value = homeState.value.copy(lastHeartbeatAtEpochMillis = System.currentTimeMillis())
                        val point = map.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(-35.3635, 149.1652))
                        view!!.getLocationOnScreen(marker)
                        marker[0] += point.x.toInt()
                        marker[1] += point.y.toInt()
                        map.triggerRepaint()
                    }
                    compose.waitForIdle()
                    captured?.recycle()
                    captured = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    val bitmap = captured
                    if (bitmap == null) false else {
                        val colours = mutableSetOf<Int>()
                        var nonBlack = 0
                        var samples = 0
                        for (x in bitmap.width / 4 until bitmap.width * 3 / 4 step 8) {
                            for (y in bitmap.height / 4 until bitmap.height * 3 / 4 step 8) {
                                val pixel = bitmap.getPixel(x, y) and 0x00ffffff
                                colours.add(pixel)
                                if (pixel != 0) nonBlack++
                                samples++
                            }
                        }
                        // Source data alone does not prove the marker reached the framebuffer.
                        var homePixels = 0
                        for (x in maxOf(0, marker[0] - 30)..minOf(bitmap.width - 1, marker[0] + 30)) {
                            for (y in maxOf(0, marker[1] - 30)..minOf(bitmap.height - 1, marker[1] + 30)) {
                                val pixel = bitmap.getPixel(x, y)
                                if (kotlin.math.abs(android.graphics.Color.red(pixel) - 114) < 10 &&
                                    kotlin.math.abs(android.graphics.Color.green(pixel) - 74) < 10 &&
                                    kotlin.math.abs(android.graphics.Color.blue(pixel) - 200) < 10) homePixels++
                            }
                        }
                        colours.size > 32 && nonBlack > samples * 0.9 && homePixels > 30
                    }
                }
                requireNotNull(captured).let { bitmap ->
                    java.io.File(compose.activity.getExternalFilesDir(null), "phase31-${style.name}.png").outputStream().use {
                        check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                    }
                    bitmap.recycle()
                }
            }
        } finally {
            compose.runOnIdle { view?.removeOnDidFinishRenderingFrameListener(listener) }
        }
    }
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
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var view: MapView
        compose.runOnIdle { view = requireNotNull(findMap(compose.activity.window.decorView)) }
        compose.waitUntil(10_000) {
            var focused = false
            instrumentation.runOnMainSync { focused = view.isShown && view.hasWindowFocus() }
            focused
        }
        compose.runOnIdle { view.getLocationOnScreen(location) }
        if (start != end || duration < android.view.ViewConfiguration.getLongPressTimeout()) {
            // Drive the real native recognizer, not app callbacks. Keep short taps
            // in one main-thread turn; deliver drag moves directly to this view
            // so cross-process input scheduling cannot turn them into long presses.
            val downTime = SystemClock.uptimeMillis()
            fun dispatch(action: Int, point: Offset, time: Long = SystemClock.uptimeMillis()) {
                val event = MotionEvent.obtain(downTime, time, action, point.x, point.y, 0)
                try {
                    event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    assertTrue("Native map must accept gesture", view.dispatchTouchEvent(event))
                } finally { event.recycle() }
            }
            if (start == end) instrumentation.runOnMainSync {
                dispatch(MotionEvent.ACTION_DOWN, start, downTime)
                dispatch(MotionEvent.ACTION_UP, end, downTime + duration)
            } else {
                instrumentation.runOnMainSync { dispatch(MotionEvent.ACTION_DOWN, start) }
                for (step in 1..20) {
                    SystemClock.sleep(duration / 20)
                    instrumentation.runOnMainSync {
                        dispatch(MotionEvent.ACTION_MOVE, start + (end - start) * (step / 20f))
                    }
                }
                instrumentation.runOnMainSync { dispatch(MotionEvent.ACTION_UP, end) }
            }
            compose.waitForIdle()
            return
        }
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
        SystemClock.sleep(duration)
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
            position = GlobalPositionState(-35.3632621, 149.1652374, lastUpdatedAtEpochMillis = System.currentTimeMillis(), lastUpdatedAtElapsedMillis = android.os.SystemClock.elapsedRealtime()), headingDegrees = 90.0))
        compose.setContent { MamaGcsTheme(ThemeMode.DARK) {
            VehicleMap(vehicle.value, Modifier.fillMaxSize().testTag("test-map"))
        } }
        val map = loadedMap()
        compose.runOnIdle { vehicle.value = vehicle.value.copy(position = vehicle.value.position.copy(lastUpdatedAtEpochMillis = System.currentTimeMillis(), lastUpdatedAtElapsedMillis = android.os.SystemClock.elapsedRealtime())) }
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
        compose.runOnIdle { vehicle.value = vehicle.value.copy(position = GlobalPositionState(-35.364, 149.166, lastUpdatedAtEpochMillis = System.currentTimeMillis(), lastUpdatedAtElapsedMillis = android.os.SystemClock.elapsedRealtime())) }
        compose.onNodeWithTag("map-center").performClick()
        near(map, -35.364)
        compose.onNodeWithText("Follow", substring = false).performClick()
        compose.runOnIdle { vehicle.value = vehicle.value.copy(position = GlobalPositionState(-35.365, 149.167, lastUpdatedAtEpochMillis = System.currentTimeMillis(), lastUpdatedAtElapsedMillis = android.os.SystemClock.elapsedRealtime())) }
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
