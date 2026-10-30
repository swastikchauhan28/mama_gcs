package com.mamadrones.gcs

import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.map.VehicleMap
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.geojson.Point
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** Synthetic, constrained map areas and 2x text on the disposable emulator. */
class SmallMapFitAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()
    private val points = listOf(DraftWaypoint("a", -35.363, 149.165),
        DraftWaypoint("b", -35.364, 149.166), DraftWaypoint("c", -35.365, 149.1655))

    @Test fun standardControlsKeepOneTwoAndThreePointsVisible() = verify(compact = false)
    @Test fun compactCoverageControlsKeepOneTwoAndThreePointsVisible() = verify(compact = true)

    private fun verify(compact: Boolean) {
        val route = mutableStateOf(points.take(1))
        val portraitShape = mutableStateOf(true)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MamaGcsTheme {
                    Box(Modifier.size(if (portraitShape.value) 300.dp else 350.dp,
                        if (portraitShape.value) 320.dp else 230.dp)) {
                        VehicleMap(VehicleState(), Modifier.size(if (portraitShape.value) 300.dp else 350.dp,
                            if (portraitShape.value) 320.dp else 230.dp), planningMode = true,
                            draftWaypoints = route.value, compactPlanningControls = compact)
                    }
                }
            }
        }
        var map: MapLibreMap? = null
        lateinit var view: MapView
        compose.runOnIdle {
            view = requireNotNull(findMap(compose.activity.window.decorView))
            view.getMapAsync { map = it }
        }
        compose.waitUntil(45_000) {
            var ready = false
            compose.runOnIdle { ready = map?.style?.isFullyLoaded == true }
            ready
        }
        val loaded = requireNotNull(map)
        val rendered = AtomicBoolean(false)
        val listener = MapView.OnDidFinishRenderingFrameListener { _, _, _ -> rendered.set(true) }
        compose.runOnIdle { view.addOnDidFinishRenderingFrameListener(listener); loaded.triggerRepaint() }
        try {
            compose.waitUntil(30_000) { rendered.get() }
            for (portrait in listOf(true, false)) {
                compose.runOnIdle { portraitShape.value = portrait }
                compose.waitForIdle()
                for (count in 1..3) {
                    compose.runOnIdle { route.value = points.take(count) }
                    compose.onNodeWithTag("coverage-map-controls").assertIsDisplayed().performClick()
                    compose.onNodeWithTag("mission-fit").assertIsDisplayed().assertIsEnabled().performClick()
                    compose.onNodeWithTag("coverage-map-controls").assertTextContains("Map controls")
                    compose.onAllNodesWithTag("mission-fit").assertCountEquals(0)
                    val name = "phase121-${if (compact) "compact" else "standard"}-${if (portrait) "portrait" else "landscape"}-$count.png"
                    assertVisible(loaded, view, route.value, name)
                }
            }
        } finally { compose.runOnIdle { view.removeOnDidFinishRenderingFrameListener(listener) } }
    }

    private fun assertVisible(map: MapLibreMap, view: MapView, expected: List<DraftWaypoint>, name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var last: android.graphics.Bitmap? = null
        var detail = "not rendered"
        try {
            compose.waitUntil(35_000) {
                val positions = mutableListOf<Pair<Int, Int>>()
                var labels = false
                compose.runOnIdle {
                    val origin = IntArray(2); view.getLocationOnScreen(origin)
                    val visible = Rect(); val shown = view.isShown && view.getGlobalVisibleRect(visible)
                    labels = expected.withIndex().all { (index, point) ->
                        val pos = map.projection.toScreenLocation(LatLng(point.latitude, point.longitude))
                        val x = (origin[0] + pos.x).toInt(); val y = (origin[1] + pos.y).toInt()
                        positions.add(x to y)
                        val area = RectF(pos.x - 16, pos.y - 16, pos.x + 16, pos.y + 16)
                        shown && visible.contains(x, y) && map.queryRenderedFeatures(area, "mama-draft-labels").any { feature ->
                            val p = feature.geometry() as? Point
                            feature.getStringProperty("label") == (index + 1).toString() && p != null &&
                                abs(p.latitude() - point.latitude) < 0.00002 && abs(p.longitude() - point.longitude) < 0.00002
                        }
                    }
                    map.triggerRepaint()
                }
                val frame = instrumentation.uiAutomation.takeScreenshot()
                last?.recycle(); last = frame
                if (frame == null) false else {
                    val counts = positions.map { (x, y) ->
                        var orange = 0; var dark = 0
                        if (x >= 18 && y >= 18 && x + 18 < frame.width && y + 18 < frame.height) {
                            for (dy in -18..18) for (dx in -18..18) {
                                val pixel = frame.getPixel(x + dx, y + dy)
                                val r = android.graphics.Color.red(pixel); val g = android.graphics.Color.green(pixel); val b = android.graphics.Color.blue(pixel)
                                if (r >= 235 && g in 90..190 && b <= 100) orange++
                                if (dx * dx + dy * dy <= 49 && r < 80 && g < 80 && b < 80) dark++
                            }
                        }
                        orange to dark
                    }
                    detail = "labels=$labels positions=$positions pixels=$counts"
                    labels && positions.size == expected.size && counts.all { (orange, dark) -> orange >= 12 && dark >= 3 }
                }
            }
            File(compose.activity.getExternalFilesDir(null), name).outputStream().use {
                check(requireNotNull(last).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
        } catch (failure: ComposeTimeoutException) {
            last?.let { frame -> File(compose.activity.getExternalFilesDir(null), "failure-$name").outputStream().use {
                frame.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            } }
            throw AssertionError("Small-map fit did not show every numbered point: $detail", failure)
        } finally { last?.recycle() }
    }

    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findMap(view.getChildAt(index))?.let { return it }
        return null
    }
}
