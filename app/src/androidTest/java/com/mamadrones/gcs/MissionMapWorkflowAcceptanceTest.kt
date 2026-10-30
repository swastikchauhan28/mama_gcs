package com.mamadrones.gcs

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.local.datastore.LocalMissionDraftRepository
import com.mamadrones.gcs.data.local.datastore.LocalSettingsRepository
import com.mamadrones.gcs.domain.model.BasemapStyle
import com.mamadrones.gcs.domain.model.MissionDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.geojson.Point
import java.io.File
import kotlin.math.abs

/** Real mission editor and map, on the disposable emulator only. Never opens a vehicle link. */
class MissionMapWorkflowAcceptanceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun editorActionsKeepNumberedMapMarkersInSyncAcrossRotationAndStyle() {
        val drafts = LocalMissionDraftRepository(instrumentation.targetContext)
        val settings = LocalSettingsRepository(instrumentation.targetContext)
        val previous = runBlocking { drafts.load() }
        val recovery = runBlocking { drafts.loadRecovery() }
        val basemap = runBlocking { settings.preferences.first().basemap }
        var activity: ActivityScenario<MainActivity>? = null
        try {
            runBlocking { drafts.save(MissionDraft()); settings.setBasemap(BasemapStyle.STREETS) }
            val scenario = ActivityScenario.launch(MainActivity::class.java).also { activity = it }
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            awaitOrientation(scenario, Configuration.ORIENTATION_PORTRAIT)
            compose.openWorkspace("mission")
            compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("mission-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            val a = -35.363 to 149.165
            val b = -35.364 to 149.166
            val c = -35.365 to 149.1655
            val edited = -35.3644 to 149.166
            add(a); add(b)
            assertMap(scenario, listOf(a, b), "phase120-portrait-add.png")
            add(c)
            assertMap(scenario, listOf(a, b, c), "phase120-portrait-three.png")
            scrollToRoute("mission-edit-1").performClick()
            compose.onNodeWithTag("mission-latitude").performTextReplacement(edited.first.toString())
            compose.onNodeWithTag("mission-confirm-waypoint").performClick()
            assertMap(scenario, listOf(a, edited, c), "phase120-portrait-edit.png")
            scrollToRoute("mission-up-2").performClick()
            assertMap(scenario, listOf(a, c, edited), "phase120-portrait-reorder.png")
            scrollToRoute("mission-remove-1").performClick()
            assertMap(scenario, listOf(a, edited), "phase120-portrait-remove.png")
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            awaitOrientation(scenario, Configuration.ORIENTATION_LANDSCAPE)
            assertMap(scenario, listOf(a, edited), "phase120-landscape.png")
            runBlocking { settings.setBasemap(BasemapStyle.HYBRID) }
            compose.waitUntil(45_000) {
                var ready = false
                scenario.onActivity { current ->
                    findMap(current.window.decorView)?.getMapAsync { map ->
                        ready = map.style?.isFullyLoaded == true && map.style?.uri?.contains("/maps/${BasemapStyle.HYBRID.mapTilerId}/") == true
                    }
                }
                ready
            }
            assertMap(scenario, listOf(a, edited), "phase120-landscape-hybrid.png")
        } finally {
            activity?.close()
            runBlocking {
                drafts.save(previous)
                recovery?.let { drafts.saveRecovery(it) }
                settings.setBasemap(basemap)
            }
        }
    }

    private fun awaitOrientation(scenario: ActivityScenario<MainActivity>, orientation: Int) {
        compose.waitUntil(15_000) {
            var matched = false
            scenario.onActivity { matched = it.resources.configuration.orientation == orientation }
            matched
        }
    }

    private fun scrollToRoute(tag: String): SemanticsNodeInteraction {
        compose.selectMissionTab("Route")
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    private fun add(point: Pair<Double, Double>) {
        scrollToRoute("mission-add").performClick()
        compose.onNodeWithTag("mission-latitude").performTextReplacement(point.first.toString())
        compose.onNodeWithTag("mission-longitude").performTextReplacement(point.second.toString())
        compose.onNodeWithTag("mission-confirm-waypoint").performClick()
    }

    private fun assertMap(scenario: ActivityScenario<MainActivity>, expected: List<Pair<Double, Double>>, imageName: String) {
        var portrait = false
        scenario.onActivity { portrait = it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        if (portrait) compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-map"))
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("mission-fit") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        var view: MapView? = null
        var map: MapLibreMap? = null
        scenario.onActivity { current ->
            view = requireNotNull(findMap(current.window.decorView))
            view!!.getMapAsync { map = it }
        }
        compose.waitUntil(45_000) {
            var ready = false
            scenario.onActivity { ready = map?.style?.isFullyLoaded == true }
            ready
        }
        val loaded = requireNotNull(map)
        val rendered = java.util.concurrent.atomic.AtomicBoolean(false)
        val frameListener = MapView.OnDidFinishRenderingFrameListener { _, _, _ -> rendered.set(true) }
        scenario.onActivity {
            requireNotNull(view).addOnDidFinishRenderingFrameListener(frameListener)
            loaded.triggerRepaint()
        }
        var detail = "not rendered"
        var last: android.graphics.Bitmap? = null
        try {
            // Style loading can finish before MapLibre creates its native renderer.
            compose.waitUntil(20_000) { rendered.get() }
            compose.onNodeWithTag("mission-fit").performClick()
            compose.waitUntil(35_000) {
                val projected = mutableListOf<Pair<Int, Int>>()
                val mapBounds = Rect()
                var labels = false
                scenario.onActivity {
                    val nativeView = requireNotNull(view)
                    val offset = IntArray(2)
                    nativeView.getLocationOnScreen(offset)
                    val visible = Rect()
                    val shown = nativeView.isShown && nativeView.getGlobalVisibleRect(visible)
                    mapBounds.set(visible)
                    labels = expected.withIndex().all { (index, point) ->
                        val pos = loaded.projection.toScreenLocation(LatLng(point.first, point.second))
                        val x = (offset[0] + pos.x).toInt(); val y = (offset[1] + pos.y).toInt()
                        projected.add(x to y)
                        val area = RectF(pos.x - 16, pos.y - 16, pos.x + 16, pos.y + 16)
                        shown && visible.contains(x, y) && loaded.queryRenderedFeatures(area, "mama-draft-labels").any { feature ->
                            val coordinate = feature.geometry() as? Point
                            feature.getStringProperty("label") == (index + 1).toString() && coordinate != null &&
                                abs(coordinate.latitude() - point.first) < 0.00002 && abs(coordinate.longitude() - point.second) < 0.00002
                        }
                    }
                    loaded.triggerRepaint()
                }
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                last?.recycle(); last = bitmap
                if (bitmap == null) false else {
                    val counts = projected.map { (x, y) ->
                        var orange = 0; var dark = 0
                        if (x >= 18 && y >= 18 && x + 18 < bitmap.width && y + 18 < bitmap.height) {
                            for (dy in -18..18) for (dx in -18..18) {
                                val px = bitmap.getPixel(x + dx, y + dy)
                                val red = android.graphics.Color.red(px); val green = android.graphics.Color.green(px); val blue = android.graphics.Color.blue(px)
                                if (red >= 235 && green in 90..190 && blue <= 100) orange++
                                if (dx * dx + dy * dy <= 49 && red < 80 && green < 80 && blue < 80) dark++
                            }
                        }
                        orange to dark
                    }
                    var nonBlack = 0
                    var samples = 0
                    val colours = mutableSetOf<Int>()
                    if (imageName.contains("hybrid")) {
                        val left = mapBounds.left.coerceAtLeast(0)
                        val top = mapBounds.top.coerceAtLeast(0)
                        val right = mapBounds.right.coerceAtMost(bitmap.width)
                        val bottom = mapBounds.bottom.coerceAtMost(bitmap.height)
                        for (y in top until bottom step 8) for (x in left until right step 8) {
                            val colour = bitmap.getPixel(x, y) and 0x00ffffff
                            colours.add(colour)
                            if (colour != 0) nonBlack++
                            samples++
                        }
                    }
                    val imageryVisible = !imageName.contains("hybrid") ||
                        (samples > 0 && nonBlack > samples * 0.9 && colours.size > 32)
                    detail = "labels=$labels points=$projected pixels=$counts nonBlack=$nonBlack/$samples colours=${colours.size}"
                    labels && imageryVisible && projected.size == expected.size &&
                        counts.all { (orange, dark) -> orange >= 12 && dark >= 3 }
                }
            }
            File(instrumentation.targetContext.getExternalFilesDir(null), imageName).outputStream().use {
                check(requireNotNull(last).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
        } catch (failure: ComposeTimeoutException) {
            last?.let { frame -> File(instrumentation.targetContext.getExternalFilesDir(null), "failure-$imageName").outputStream().use {
                frame.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            } }
            throw AssertionError("Mission map did not match editor after fit: $detail", failure)
        } finally {
            last?.recycle()
            scenario.onActivity { view?.removeOnDidFinishRenderingFrameListener(frameListener) }
        }
    }

    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findMap(view.getChildAt(index))?.let { return it }
        return null
    }
}
