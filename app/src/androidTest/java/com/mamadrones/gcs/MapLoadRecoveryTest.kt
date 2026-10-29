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
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiPoint
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/** Actual SDK load callbacks using disposable local styles; no provider key or network required. */
class MapLoadRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()
    private val vehicle = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
        position = GlobalPositionState(-35.363, 149.165, lastUpdatedAtEpochMillis = System.currentTimeMillis(), lastUpdatedAtElapsedMillis = android.os.SystemClock.elapsedRealtime()), headingDegrees = 90.0)

    @Test fun denseOverlayRemainsResponsiveDuringRepeatedMapInteractions() {
        val style = File.createTempFile("phase88-style-", ".json", compose.activity.cacheDir)
        style.writeText(VALID_STYLE)
        val url = mutableStateOf<String?>(Uri.fromFile(style).toString())
        fun grid(baseLatitude: Double) = List(128 * 128) { index ->
            CoverageMapPoint(baseLatitude + (index / 128) * 0.0000005, 149.165 + (index % 128) * 0.0000005)
        }
        val samples = mutableStateOf<List<CoverageMapPoint>?>(grid(-35.363))
        val lines = listOf(CoverageMapLine(CoverageMapPoint(-35.363, 149.165), CoverageMapPoint(-35.3625, 149.165)))
        try {
            compose.setContent { MamaGcsTheme { VehicleMap(VehicleState(), Modifier.fillMaxSize(), planningMode = true,
                styleUrl = url.value, coverageLines = lines, coverageGapSamples = samples.value) } }
            val map = awaitLoaded()
            lateinit var view: MapView
            compose.runOnIdle {
                view = requireNotNull(findMap(compose.activity.window.decorView))
                map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(org.maplibre.android.geometry.LatLng(-35.3627, 149.165)).zoom(17.0).build()
            }
            fun waitForRegion(min: Double?, max: Double?) {
                compose.waitUntil(30_000) {
                    var valid = false
                    compose.runOnIdle {
                        val features = map.style?.getSourceAs<GeoJsonSource>("mama-coverage-gap-samples")?.querySourceFeatures(null)
                        val points = features?.flatMap { feature -> when (val geometry = feature.geometry()) {
                            is Point -> listOf(geometry)
                            is MultiPoint -> geometry.coordinates()
                            else -> emptyList()
                        } }
                        valid = if (min == null || max == null) points?.isEmpty() == true
                            else points?.isNotEmpty() == true && points.all { it.latitude() in min..max }
                    }
                    valid
                }
            }
            fun timed(label: String, action: () -> Unit) {
                val start = android.os.SystemClock.elapsedRealtime()
                action()
                println("Phase88 $label ms=${android.os.SystemClock.elapsedRealtime() - start}")
            }
            waitForRegion(-35.36301, -35.36292)
            repeat(3) { cycle ->
                timed("zoom-in-$cycle") {
                    var before = 0.0
                    compose.runOnIdle { before = map.cameraPosition.zoom }
                    compose.onNodeWithContentDescription("Zoom in").assertIsEnabled().performClick()
                    compose.waitUntil(20_000) { var zoom = before; compose.runOnIdle { zoom = map.cameraPosition.zoom }; zoom > before + 0.5 }
                }
                timed("pan-$cycle") {
                    var before = 0.0
                    compose.runOnIdle { before = map.cameraPosition.target!!.longitude }
                    // MapLibre is a native AndroidView; inject screen-level events into its view hierarchy.
                    val location = IntArray(2)
                    var startX = 0f; var y = 0f; var distance = 0f
                    compose.runOnIdle {
                        view.getLocationOnScreen(location)
                        startX = location[0] + view.width * 0.35f
                        y = location[1] + view.height * 0.5f
                        distance = view.width * 0.12f
                    }
                    val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    val downTime = android.os.SystemClock.uptimeMillis()
                    fun send(action: Int, x: Float) {
                        val event = android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(), action, x, y, 0)
                        try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
                    }
                    send(android.view.MotionEvent.ACTION_DOWN, startX)
                    for (step in 1..8) {
                        android.os.SystemClock.sleep(35)
                        send(android.view.MotionEvent.ACTION_MOVE, startX + distance * step / 8)
                    }
                    send(android.view.MotionEvent.ACTION_UP, startX + distance)
                    compose.waitUntil(20_000) { var longitude = before; compose.runOnIdle { longitude = map.cameraPosition.target!!.longitude }; kotlin.math.abs(longitude - before) > 0.000001 }
                }
                timed("zoom-out-$cycle") {
                    var before = 0.0
                    compose.runOnIdle { before = map.cameraPosition.zoom }
                    compose.onNodeWithContentDescription("Zoom out").assertIsEnabled().performClick()
                    compose.waitUntil(20_000) { var zoom = before; compose.runOnIdle { zoom = map.cameraPosition.zoom }; zoom < before - 0.5 }
                }
                val first = cycle % 2 == 1
                timed("switch-$cycle") {
                    compose.runOnIdle { samples.value = grid(if (first) -35.363 else -35.3625) }
                    if (first) waitForRegion(-35.36301, -35.36292) else waitForRegion(-35.36251, -35.36242)
                }
            }
            compose.awaitScreenshotFrame(view)
            val screenshot = requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            try {
                File(compose.activity.getExternalFilesDir(null), "phase88-dense-map.png").outputStream().use {
                    assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { screenshot.recycle() }
            timed("clear") {
                compose.runOnIdle { samples.value = emptyList() }
                waitForRegion(null, null)
                compose.onNodeWithText("No sampled gaps detected", substring = true).assertIsDisplayed()
            }
            compose.onNodeWithContentDescription("Zoom in").assertIsEnabled()
            compose.onNodeWithContentDescription("Zoom out").assertIsEnabled()
        } finally {
            compose.runOnIdle { url.value = null }; compose.waitForIdle()
            check(style.delete())
        }
    }
    @Test fun denseSampledGapSourceSwitchesAndClears() {
        val style = File.createTempFile("phase87-style-", ".json", compose.activity.cacheDir)
        style.writeText(VALID_STYLE)
        val url = mutableStateOf<String?>(Uri.fromFile(style).toString())
        fun grid(baseLatitude: Double) = List(128 * 128) { index ->
            CoverageMapPoint(baseLatitude + (index / 128) * 0.0000005, 149.165 + (index % 128) * 0.0000005)
        }
        val samples = mutableStateOf<List<CoverageMapPoint>?>(grid(-35.363))
        val lines = listOf(CoverageMapLine(CoverageMapPoint(-35.363, 149.165), CoverageMapPoint(-35.3625, 149.165)))
        try {
            compose.setContent { MamaGcsTheme { VehicleMap(VehicleState(), Modifier.fillMaxSize(), planningMode = true,
                styleUrl = url.value, coverageLines = lines, coverageGapSamples = samples.value) } }
            val map = awaitLoaded()
            compose.runOnIdle {
                map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(org.maplibre.android.geometry.LatLng(-35.3627, 149.165)).zoom(16.0).build()
                map.triggerRepaint()
            }
            fun awaitGrid(minLatitude: Double?, maxLatitude: Double?) {
                val started = android.os.SystemClock.elapsedRealtime()
                compose.waitUntil(30_000) {
                    var valid = false
                    compose.runOnIdle {
                        val features = map.style?.getSourceAs<GeoJsonSource>("mama-coverage-gap-samples")?.querySourceFeatures(null)
                        val points = features?.flatMap { feature -> when (val geometry = feature.geometry()) {
                            is Point -> listOf(geometry)
                            is MultiPoint -> geometry.coordinates()
                            else -> emptyList()
                        } }
                        valid = if (minLatitude == null || maxLatitude == null) points?.isEmpty() == true
                            else points?.isNotEmpty() == true && points.all { it.latitude() in minLatitude..maxLatitude }
                    }
                    valid
                }
                println("Phase87 source update ms=${android.os.SystemClock.elapsedRealtime() - started} target=$minLatitude")
            }
            awaitGrid(-35.36301, -35.36292)
            compose.onNodeWithText("magenta: sampled gap centers", substring = true).assertIsDisplayed()
            compose.runOnIdle { samples.value = grid(-35.3625) }
            awaitGrid(-35.36251, -35.36242)
            compose.runOnIdle { samples.value = emptyList() }
            awaitGrid(null, null)
            compose.onNodeWithText("No sampled gaps detected", substring = true).assertIsDisplayed()
            compose.runOnIdle { samples.value = null }
            awaitGrid(null, null)
            compose.onNodeWithText("Gap estimate unavailable", substring = true).assertIsDisplayed()
        } finally {
            compose.runOnIdle { url.value = null }; compose.waitForIdle()
            check(style.delete())
        }
    }
    @Test fun sampledGapSourceSwitchesClearsAndReloads() {
        val first = File.createTempFile("phase86-a-", ".json", compose.activity.cacheDir)
        val second = File.createTempFile("phase86-b-", ".json", compose.activity.cacheDir)
        first.writeText(VALID_STYLE); second.writeText(VALID_STYLE.replace("#12202c", "#243444"))
        val url = mutableStateOf<String?>(Uri.fromFile(first).toString())
        val samples = mutableStateOf<List<CoverageMapPoint>?>(listOf(CoverageMapPoint(-35.363, 149.165)))
        val lines = listOf(CoverageMapLine(CoverageMapPoint(-35.36305, 149.165), CoverageMapPoint(-35.36295, 149.165)))
        try {
            compose.setContent { MamaGcsTheme { VehicleMap(VehicleState(), Modifier.fillMaxSize(), planningMode = true,
                styleUrl = url.value, coverageLines = lines, coverageGapSamples = samples.value) } }
            val map = awaitLoaded()
            val frame = AtomicBoolean(false)
            val listener = MapView.OnDidFinishRenderingFrameListener { _, _, _ -> frame.set(true) }
            lateinit var view: MapView
            compose.runOnIdle {
                view = requireNotNull(findMap(compose.activity.window.decorView)); view.addOnDidFinishRenderingFrameListener(listener)
                map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder().target(org.maplibre.android.geometry.LatLng(-35.363, 149.165)).zoom(18.0).build()
                map.triggerRepaint()
            }
            try {
                compose.waitUntil(15_000) { frame.get() }
                fun awaitSample(expected: CoverageMapPoint?) {
                    compose.waitUntil(20_000) {
                        var valid = false
                        compose.runOnIdle {
                            val features = map.style?.getSourceAs<GeoJsonSource>("mama-coverage-gap-samples")?.querySourceFeatures(null)
                            val points = features?.flatMap { feature -> when (val geometry = feature.geometry()) {
                                is Point -> listOf(geometry)
                                is MultiPoint -> geometry.coordinates()
                                else -> emptyList()
                            } }
                            valid = if (expected == null) points?.isEmpty() == true else points?.isNotEmpty() == true && points.all {
                                kotlin.math.abs(it.latitude() - expected.latitude) < 0.000001 && kotlin.math.abs(it.longitude() - expected.longitude) < 0.000001
                            }
                        }
                        valid
                    }
                }
                awaitSample(samples.value!!.first())
                compose.onNodeWithText("magenta: sampled gap centers", substring = true).assertIsDisplayed()
                compose.runOnIdle { samples.value = listOf(CoverageMapPoint(-35.36302, 149.16502)) }
                awaitSample(samples.value!!.first())
                compose.runOnIdle { url.value = Uri.fromFile(second).toString() }
                compose.waitUntil(20_000) { var loaded = false; compose.runOnIdle { loaded = map.style?.uri == url.value && map.style?.isFullyLoaded == true }; loaded }
                awaitSample(samples.value!!.first())
                compose.runOnIdle { samples.value = emptyList() }
                awaitSample(null)
                compose.onNodeWithText("No sampled gaps detected", substring = true).assertIsDisplayed()
                compose.runOnIdle { samples.value = null }
                awaitSample(null)
                compose.onNodeWithText("Gap estimate unavailable", substring = true).assertIsDisplayed()
            } finally { compose.runOnIdle { view.removeOnDidFinishRenderingFrameListener(listener) } }
        } finally {
            compose.runOnIdle { url.value = null }; compose.waitForIdle()
            check(first.delete() && second.delete())
        }
    }

    @Test fun coverageLinesRenderSwitchClearAndSurviveStyleReload() {
        val first = File.createTempFile("coverage-style-a-", ".json", compose.activity.cacheDir)
        val second = File.createTempFile("coverage-style-b-", ".json", compose.activity.cacheDir)
        first.writeText(VALID_STYLE)
        second.writeText(VALID_STYLE.replace("#12202c", "#243444"))
        val url = mutableStateOf<String?>(Uri.fromFile(first).toString())
        val north = CoverageMapLine(CoverageMapPoint(-35.36305, 149.165), CoverageMapPoint(-35.36295, 149.165))
        val east = CoverageMapLine(CoverageMapPoint(-35.363, 149.16495), CoverageMapPoint(-35.363, 149.16505))
        val lines = mutableStateOf(listOf(north))
        val fence = listOf(DraftWaypoint("a", -35.36305, 149.16495), DraftWaypoint("b", -35.36305, 149.16505),
            DraftWaypoint("c", -35.36295, 149.16505), DraftWaypoint("d", -35.36295, 149.16495))
        try {
            compose.setContent { MamaGcsTheme { VehicleMap(VehicleState(), Modifier.fillMaxSize(), planningMode = true,
                draftFence = fence, styleUrl = url.value, coverageLines = lines.value) } }
            val map = awaitLoaded()
            val rendered = AtomicBoolean(false)
            val listener = MapView.OnDidFinishRenderingFrameListener { _, _, _ -> rendered.set(true) }
            lateinit var view: MapView
            compose.runOnIdle {
                view = requireNotNull(findMap(compose.activity.window.decorView))
                view.addOnDidFinishRenderingFrameListener(listener)
                map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(org.maplibre.android.geometry.LatLng(-35.363, 149.165)).zoom(18.0).build()
                map.triggerRepaint()
            }
            try {
                compose.waitUntil(10_000) { rendered.get() }
                fun awaitLine(expected: CoverageMapLine?) {
                    compose.waitUntil(15_000) {
                        var found = false
                        compose.runOnIdle {
                            val features = map.style?.getSourceAs<GeoJsonSource>("mama-coverage-preview")?.querySourceFeatures(null)
                            found = if (expected == null) features?.isEmpty() == true else features?.any { feature ->
                                val coords = (feature.geometry() as? LineString)?.coordinates() ?: emptyList()
                                coords.size >= 2 && coords.all { point ->
                                    if (expected.start.longitude == expected.end.longitude) kotlin.math.abs(point.longitude() - expected.start.longitude) < 0.000001
                                    else kotlin.math.abs(point.latitude() - expected.start.latitude) < 0.000001
                                }
                            } == true
                        }
                        found
                    }
                }
                awaitLine(north)
                compose.onNodeWithTag("coverage-map-legend").assertIsDisplayed()
                compose.runOnIdle {
                    assertNotNull(map.style!!.getLayer("mama-coverage-preview-layer"))
                    assertNotNull(map.style!!.getSource("mama-draft-fence"))
                    map.triggerRepaint()
                }
                compose.awaitScreenshotFrame(view)
                val screenshot = requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                try {
                    File(compose.activity.getExternalFilesDir(null), "phase81-map-overlay.png").outputStream().use {
                        assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                    }
                    var screenX = 0; var screenY = 0
                    compose.runOnIdle {
                        val center = map.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(-35.363, 149.165))
                        val offset = IntArray(2); view.getLocationOnScreen(offset)
                        screenX = (offset[0] + center.x).roundToInt(); screenY = (offset[1] + center.y).roundToInt()
                    }
                    var cyan = 0
                    for (dy in -6..6) for (dx in -6..6) {
                        val pixel = screenshot.getPixel(screenX + dx, screenY + dy)
                        if (android.graphics.Color.red(pixel) < 60 && android.graphics.Color.green(pixel) in 120..190 &&
                            android.graphics.Color.blue(pixel) in 130..205) cyan++
                    }
                    assertTrue("Rendered pass line not visible at projected center: $cyan cyan pixels", cyan >= 3)
                } finally { screenshot.recycle() }
                compose.runOnIdle { lines.value = listOf(east) }
                awaitLine(east)
                compose.runOnIdle { url.value = Uri.fromFile(second).toString() }
                compose.waitUntil(15_000) { var loaded = false; compose.runOnIdle { loaded = map.style?.uri == url.value && map.style?.isFullyLoaded == true }; loaded }
                awaitLine(east)
                compose.runOnIdle { lines.value = emptyList() }
                awaitLine(null)
                compose.onNodeWithTag("coverage-map-legend").assertDoesNotExist()
            } finally { compose.runOnIdle { view.removeOnDidFinishRenderingFrameListener(listener) } }
        } finally {
            compose.runOnIdle { url.value = null }
            compose.waitForIdle()
            check(first.delete() && second.delete())
        }
    }

    @Test fun mapWorkspaceLabelsStaleAndFreshPosition() {
        val now = System.currentTimeMillis()
        val state = mutableStateOf(VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            lastHeartbeatAtEpochMillis = now,
            position = GlobalPositionState(-35.363, 149.165, lastUpdatedAtEpochMillis = now - 10_000, lastUpdatedAtElapsedMillis = android.os.SystemClock.elapsedRealtime() - 10_000)))
        compose.setContent { MamaGcsTheme {
            com.mamadrones.gcs.presentation.screens.MapWorkspace(state.value, Modifier.fillMaxSize(), mapStyleUrl = null)
        } }
        compose.onNodeWithTag("operation-position-status").assertTextContains("Position stale", substring = true)
        val screenshot = requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            File(compose.activity.getExternalFilesDir(null), "phase92-stale.png").outputStream().use {
                assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { screenshot.recycle() }
        compose.runOnIdle { state.value = state.value.copy(position = state.value.position.copy(lastUpdatedAtEpochMillis = 1L, lastUpdatedAtElapsedMillis = android.os.SystemClock.elapsedRealtime())) }
        compose.onNodeWithTag("operation-position-status").assertTextContains("-35.3630000", substring = true)
        compose.runOnIdle { state.value = state.value.copy(connectionStatus = VehicleConnectionState.DISCONNECTED) }
        compose.onNodeWithTag("operation-position-status").assertTextContains("Position unavailable", substring = true)
    }
    @Test fun stalePositionClearsMapDespiteOngoingHeartbeatAndReturnsWhenUpdated() {
        val file = File.createTempFile("phase92-style-", ".json", compose.activity.cacheDir)
        file.writeText(VALID_STYLE)
        val url = mutableStateOf<String?>(Uri.fromFile(file).toString())
        val now = System.currentTimeMillis()
        val state = mutableStateOf(VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            lastHeartbeatAtEpochMillis = now, headingDegrees = 90.0,
            position = GlobalPositionState(-35.363, 149.165, lastUpdatedAtEpochMillis = now, lastUpdatedAtElapsedMillis = android.os.SystemClock.elapsedRealtime()),
            positionTrack = listOf(GeoTrackPoint(-35.363, 149.165, now), GeoTrackPoint(-35.36301, 149.16501, now))))
        try {
            compose.setContent { MamaGcsTheme { VehicleMap(state.value, Modifier.fillMaxSize(), styleUrl = url.value) } }
            val map = awaitLoaded()
            compose.runOnIdle { state.value = state.value.copy(position = state.value.position.copy(lastUpdatedAtEpochMillis = System.currentTimeMillis(), lastUpdatedAtElapsedMillis = android.os.SystemClock.elapsedRealtime())) }
            awaitPosition(map, -35.363)
            compose.onNodeWithTag("map-center").assertIsEnabled()
            compose.waitUntil(15_000) {
                compose.mainClock.advanceTimeBy(1_000)
                var cleared = false
                compose.runOnIdle {
                    state.value = state.value.copy(lastHeartbeatAtEpochMillis = System.currentTimeMillis())
                    cleared = listOf("mama-vehicle-source", "mama-heading-source", "mama-track-source").all { id ->
                        map.style?.getSourceAs<GeoJsonSource>(id)?.querySourceFeatures(null)?.isEmpty() == true
                    }
                }
                cleared
            }
            compose.onNodeWithTag("map-center").assertIsNotEnabled()
            compose.onNodeWithTag("map-follow").assertIsNotEnabled()
            compose.runOnIdle {
                val fresh = System.currentTimeMillis()
                state.value = state.value.copy(lastHeartbeatAtEpochMillis = fresh,
                    position = GlobalPositionState(-35.364, 149.166, lastUpdatedAtEpochMillis = fresh, lastUpdatedAtElapsedMillis = android.os.SystemClock.elapsedRealtime()))
            }
            awaitPosition(map, -35.364)
            compose.onNodeWithTag("map-center").assertIsEnabled()
        } finally {
            compose.runOnIdle { url.value = null }; compose.waitForIdle()
            check(file.delete())
        }
    }
    @Test fun homeMarkerIsSeparateFromVehicleAndClearsWhenEvidenceExpires() {
        val file = File.createTempFile("map-home-", ".json", compose.activity.cacheDir)
        file.writeText(VALID_STYLE)
        val url = mutableStateOf<String?>(Uri.fromFile(file).toString())
        fun liveState(): VehicleState {
            val now = System.currentTimeMillis()
            return vehicle.copy(position = vehicle.position.copy(lastUpdatedAtEpochMillis = now, lastUpdatedAtElapsedMillis = android.os.SystemClock.elapsedRealtime()), lastHeartbeatAtEpochMillis = now,
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
            val renderedFrames = AtomicInteger()
            val frameListener = MapView.OnDidFinishRenderingFrameListener { _, _, _ -> renderedFrames.incrementAndGet() }
            lateinit var mapView: MapView
            compose.runOnIdle {
                mapView = requireNotNull(findMap(compose.activity.window.decorView))
                mapView.addOnDidFinishRenderingFrameListener(frameListener)
                map.triggerRepaint()
            }
            try {
                compose.waitUntil(5_000) { renderedFrames.get() >= 1 }
                compose.runOnIdle { map.triggerRepaint() }
                compose.waitUntil(5_000) { renderedFrames.get() >= 2 }
            } finally {
                compose.runOnIdle { mapView.removeOnDidFinishRenderingFrameListener(frameListener) }
            }
            var centerX = 0
            var centerY = 0
            compose.runOnIdle {
                val view = requireNotNull(findMap(compose.activity.window.decorView))
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                val home = map.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(-35.3631, 149.1651))
                centerX = (location[0] + home.x).roundToInt()
                centerY = (location[1] + home.y).roundToInt()
            }
            var screenshot: android.graphics.Bitmap? = null
            var whiteCenterPixels = 0
            var purpleCenterPixels = 0
            try {
                // Symbol layers can still be fading after the first rendered frames. Preserve
                // the strict white-H and purple-center checks while waiting for full rendering.
                compose.waitUntil(10_000) {
                    compose.runOnIdle { map.triggerRepaint() }
                    val next = androidx.test.platform.app.InstrumentationRegistry
                        .getInstrumentation().uiAutomation.takeScreenshot()
                    if (next == null) false else {
                        screenshot?.recycle()
                        screenshot = next
                        whiteCenterPixels = 0
                        purpleCenterPixels = 0
                        if (centerX < 7 || centerY < 7 || centerX + 7 >= next.width || centerY + 7 >= next.height) {
                            false
                        } else {
                            for (dy in -7..7) for (dx in -7..7) {
                                val pixel = next.getPixel(centerX + dx, centerY + dy)
                                if (android.graphics.Color.red(pixel) >= 220 &&
                                    android.graphics.Color.green(pixel) >= 220 &&
                                    android.graphics.Color.blue(pixel) >= 220) whiteCenterPixels++
                                if (kotlin.math.abs(android.graphics.Color.red(pixel) - 114) <= 20 &&
                                    kotlin.math.abs(android.graphics.Color.green(pixel) - 74) <= 20 &&
                                    kotlin.math.abs(android.graphics.Color.blue(pixel) - 200) <= 20) purpleCenterPixels++
                            }
                            whiteCenterPixels >= 3 && purpleCenterPixels >= 10
                        }
                    }
                }
                val captured = requireNotNull(screenshot)
                File(compose.activity.getExternalFilesDir(null), "phase113-home-marker.png").outputStream().use {
                    assertTrue(captured.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
                assertTrue("Projected home is not the purple marker: $purpleCenterPixels purple pixels at $centerX,$centerY",
                    purpleCenterPixels >= 10)
                assertTrue("Home H is not visible at $centerX,$centerY: $whiteCenterPixels white pixels",
                    whiteCenterPixels >= 3)
            } finally { screenshot?.recycle() }
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
                state.value = vehicle.copy(position = GlobalPositionState(-35.364, 149.166, lastUpdatedAtEpochMillis = System.currentTimeMillis(), lastUpdatedAtElapsedMillis = android.os.SystemClock.elapsedRealtime()))
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
