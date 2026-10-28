package com.mamadrones.gcs

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.mission.*
import com.mamadrones.gcs.presentation.screens.MissionScreen
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun coverageTestOutline(triangle: Boolean = false): List<DraftWaypoint> {
    val xy = if (triangle) listOf(0.0 to 0.0, 12.0 to 0.0, 0.0 to 12.0)
        else listOf(0.0 to 0.0, 10.0 to 0.0, 10.0 to 20.0, 0.0 to 20.0)
    return xy.mapIndexed { i, (x, y) -> DraftWaypoint("f$i", Math.toDegrees(y / 6_371_000.0), Math.toDegrees(x / 6_371_000.0)) }
}

/** Synthetic geometry only; callbacks are captured in memory and never reach storage or a vehicle. */
class FieldCoverageUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()
    private val actions = mutableListOf<MissionPlanAction>()

    @Test fun selectedCoverageReachesOfflineMapInPortrait() = selectedCoverageReachesOfflineMap(false)

    @Test fun selectedCoverageReachesOfflineMapInLandscape() = selectedCoverageReachesOfflineMap(true)

    @Test fun triangleCoverageReachesOfflineMap() = verifyIrregularCoverage(false, false)

    @Test fun concaveCoverageReachesOfflineMap() = verifyIrregularCoverage(true, false)

    @Test fun enlargedTextCoverageMapControlsRemainUsable() = verifyIrregularCoverage(false, true)

    private fun verifyIrregularCoverage(concave: Boolean, largeText: Boolean) {
        val style = File.createTempFile("phase84-style-", ".json", compose.activity.cacheDir)
        style.writeText("""{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#12202c"}}]}""")
        val xy = if (concave) listOf(0.0 to 0.0, 12.0 to 0.0, 12.0 to 12.0, 8.0 to 12.0, 8.0 to 4.0, 4.0 to 4.0, 4.0 to 12.0, 0.0 to 12.0)
            else listOf(0.0 to 0.0, 12.0 to 0.0, 0.0 to 12.0)
        val outline = xy.mapIndexed { i, (x, y) -> DraftWaypoint("phase84-$i", 0.123456 + originalMetres(y), 149.165 + originalMetres(x)) }
        val draft = mutableStateOf(MissionDraft(keepInFence = outline))
        val original = draft.value
        try {
            show(largeText = largeText, draft = draft, mapStyleUrl = Uri.fromFile(style).toString())
            node("coverage-width").performTextReplacement("2")
            node("coverage-overlap").apply { performTextReplacement("0"); performImeAction() }
            compose.onNodeWithTag("coverage-close-settings").performClick()
            node("coverage-compare").performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("coverage-comparison").fetchSemanticsNodes().isNotEmpty() }
            for (eastWest in listOf(false, true)) {
                val calculation = FieldCoveragePlanner.calculate(outline, 2.0, 0.0, eastWest)
                assertTrue("Synthetic field should yield a preview: $calculation", calculation is CoverageCalculation.Ready)
                val preview = (calculation as CoverageCalculation.Ready).preview
                val projected = CoverageMapProjection.project(preview, outline) as CoverageMapProjectionResult.Ready
                if (concave && eastWest) {
                    assertTrue("Concave fixture must contain split passes", preview.passes.groupBy { (it.start.y * 1000).roundToInt() }.values.any { it.size > 1 })
                }
                node(if (eastWest) "coverage-comparison-east-map" else "coverage-comparison-north-map").performClick()
                verifyIrregularMap(projected.lines, concave, eastWest, largeText)
            }
            node("coverage-overlap").apply { performTextReplacement("10"); performImeAction() }
            compose.onNodeWithTag("coverage-close-settings").performClick()
            compose.onAllNodesWithTag("coverage-map-status").assertCountEquals(0)
            compose.onAllNodesWithTag("coverage-map-legend").assertCountEquals(0)
            compose.onAllNodesWithTag("mission-map").assertCountEquals(0)
            assertEquals(original, draft.value)
            assertTrue("Coverage preview must not dispatch a mission action", actions.isEmpty())
        } finally {
            compose.runOnIdle { }
            check(style.delete()) { "Could not remove test-owned map style" }
        }
    }

    private fun verifyIrregularMap(expected: List<CoverageMapLine>, concave: Boolean, eastWest: Boolean, largeText: Boolean) {
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-map"))
        compose.onNodeWithTag("mission-map").assertIsDisplayed()
        var map: MapLibreMap? = null
        lateinit var view: MapView
        compose.runOnIdle { view = requireNotNull(findMap(compose.activity.window.decorView)); view.getMapAsync { map = it } }
        compose.waitUntil(30_000) { var loaded = false; compose.runOnIdle { loaded = map?.style?.isFullyLoaded == true }; loaded }
        val rendered = AtomicBoolean(false)
        val listener = MapView.OnDidFinishRenderingFrameListener { _, _, _ -> rendered.set(true) }
        compose.runOnIdle { view.addOnDidFinishRenderingFrameListener(listener); map!!.triggerRepaint() }
        try {
            compose.waitUntil(30_000) { rendered.get() }
            compose.onNodeWithTag("coverage-map-controls").assertIsDisplayed().performClick()
            compose.onNodeWithTag("mission-fit").assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(30_000) { var zoom = 0.0; compose.runOnIdle { zoom = map!!.cameraPosition.zoom }; zoom > 18.0 }
            compose.onNodeWithTag("coverage-map-controls").assertIsDisplayed().performClick()
            compose.onAllNodesWithTag("mission-fit").assertCountEquals(0)
            var lines = emptyList<LineString>()
            try {
                compose.waitUntil(30_000) {
                    compose.runOnIdle { lines = map!!.style?.getSourceAs<GeoJsonSource>("mama-coverage-preview")?.querySourceFeatures(null)?.mapNotNull { it.geometry() as? LineString }?.distinct() ?: emptyList() }
                    lines.size == expected.size
                }
            } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
                throw AssertionError("Expected ${expected.size} unique segments, observed ${lines.size}: $lines", failure)
            }
            val tolerance = 1e-7 // About 1.1 cm at this latitude, for native tile quantization.
            fun close(a: Double, b: Double) = abs(a - b) <= tolerance
            lines.forEach { line ->
                val p = line.coordinates(); assertEquals(2, p.size)
                assertTrue("Map segment must match a planned segment", expected.any { e ->
                    (close(p[0].latitude(), e.start.latitude) && close(p[0].longitude(), e.start.longitude) && close(p[1].latitude(), e.end.latitude) && close(p[1].longitude(), e.end.longitude)) ||
                    (close(p[1].latitude(), e.start.latitude) && close(p[1].longitude(), e.start.longitude) && close(p[0].latitude(), e.end.latitude) && close(p[0].longitude(), e.end.longitude))
                })
                for (step in 0..10) {
                    val t = step / 10.0
                    val x = Math.toRadians(p[0].longitude() * (1 - t) + p[1].longitude() * t - 149.165) * 6_371_000.0
                    val y = Math.toRadians(p[0].latitude() * (1 - t) + p[1].latitude() * t - 0.123456) * 6_371_000.0
                    assertTrue("Segment must remain in the synthetic field: $x,$y", x >= -0.02 && y >= -0.02 && x <= 12.02 && y <= 12.02 &&
                        if (concave) !(x > 4.02 && x < 7.98 && y > 4.02) else x + y <= 12.02)
                }
            }
            compose.onNodeWithTag("coverage-map-legend").assertIsDisplayed()
            compose.runOnIdle { map!!.triggerRepaint() }
            compose.awaitScreenshotFrame(view)
            val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            try {
                val name = "phase84-${if (largeText) "large" else if (concave) "concave" else "triangle"}-${if (eastWest) "east" else "north"}.png"
                File(compose.activity.getExternalFilesDir(null), name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                val p = lines[lines.size / 2].coordinates(); var x = 0; var y = 0
                compose.runOnIdle {
                    val center = map!!.projection.toScreenLocation(org.maplibre.android.geometry.LatLng((p[0].latitude() + p[1].latitude()) / 2, (p[0].longitude() + p[1].longitude()) / 2))
                    val offset = IntArray(2); view.getLocationOnScreen(offset); x = (offset[0] + center.x).roundToInt(); y = (offset[1] + center.y).roundToInt()
                }
                assertTrue("Projected segment midpoint must be on screen", x in 6 until bitmap.width - 6 && y in 6 until bitmap.height - 6)
                var cyan = 0
                for (dy in -6..6) for (dx in -6..6) {
                    val color = bitmap.getPixel(x + dx, y + dy)
                    if (android.graphics.Color.red(color) < 60 && android.graphics.Color.green(color) in 120..190 && android.graphics.Color.blue(color) in 130..205) cyan++
                }
                assertTrue("A planned segment must render at its projected midpoint: $cyan cyan pixels", cyan >= 3)
            } finally { bitmap.recycle() }
        } finally { compose.runOnIdle { view.removeOnDidFinishRenderingFrameListener(listener) } }
    }

    private fun selectedCoverageReachesOfflineMap(landscape: Boolean) {
        val style = File.createTempFile("phase82-style-", ".json", compose.activity.cacheDir)
        style.writeText("""{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#12202c"}}]}""")
        // Avoid placing this tiny synthetic field exactly on a native map tile boundary.
        val draft = mutableStateOf(MissionDraft(keepInFence = coverageTestOutline().map {
            it.copy(latitude = it.latitude + 0.123456, longitude = it.longitude + 149.165)
        }))
        val original = draft.value
        try {
            show(draft = draft, mapStyleUrl = Uri.fromFile(style).toString(), landscape = landscape)
            node("coverage-width").performTextReplacement("2")
            node("coverage-overlap").apply { performTextReplacement("0"); performImeAction() }
            compose.onNodeWithTag("coverage-close-settings").performClick()
            node("coverage-compare").performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("coverage-comparison").fetchSemanticsNodes().isNotEmpty() }
            node("coverage-comparison-north-map").performClick()
            verifySelectedMap(landscape, eastWest = false, expectedCount = 5)
            node("coverage-comparison-east-map").performClick()
            node("coverage-settings-summary").assertTextContains("Single preview direction: North / south", substring = true)
            node("coverage-map-status").assertTextContains("East / west preview selected for the map", substring = true)
            verifySelectedMap(landscape, eastWest = true, expectedCount = 10)
            node("coverage-overlap").apply { performTextReplacement("10"); performImeAction() }
            compose.onNodeWithTag("coverage-close-settings").performClick()
            compose.onAllNodesWithTag("coverage-map-status").assertCountEquals(0)
            compose.onAllNodesWithTag("coverage-map-legend").assertCountEquals(0)
            if (landscape) {
                var current: MapLibreMap? = null
                compose.runOnIdle { requireNotNull(findMap(compose.activity.window.decorView)).getMapAsync { current = it } }
                compose.waitUntil(30_000) {
                    var empty = false
                    compose.runOnIdle { empty = current?.style?.getSourceAs<GeoJsonSource>("mama-coverage-preview")?.querySourceFeatures(null)?.isEmpty() == true }
                    empty
                }
            } else compose.onAllNodesWithTag("mission-map").assertCountEquals(0)
            assertEquals(original, draft.value)
            assertTrue("Preview must not dispatch a mission action", actions.isEmpty())
        } finally {
            compose.runOnIdle { }
            check(style.delete()) { "Could not remove test-owned map style" }
        }
    }

    private fun verifySelectedMap(landscape: Boolean, eastWest: Boolean, expectedCount: Int) {
        if (!landscape) compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-map"))
        compose.onNodeWithTag("mission-map").assertIsDisplayed()
        var map: MapLibreMap? = null
        lateinit var view: MapView
        compose.runOnIdle {
            view = requireNotNull(findMap(compose.activity.window.decorView))
            view.getMapAsync { map = it }
        }
        compose.waitUntil(30_000) { var loaded = false; compose.runOnIdle { loaded = map?.style?.isFullyLoaded == true }; loaded }
        val rendered = AtomicBoolean(false)
        val listener = MapView.OnDidFinishRenderingFrameListener { _, _, _ -> rendered.set(true) }
        compose.runOnIdle { view.addOnDidFinishRenderingFrameListener(listener); map!!.triggerRepaint() }
        try {
            compose.waitUntil(30_000) { rendered.get() }
            if (!landscape) {
                compose.onNodeWithTag("coverage-map-controls").assertTextContains("Map controls").performClick()
            }
            compose.onNodeWithTag("mission-fit").assertIsEnabled().performClick()
            compose.waitUntil(30_000) {
                var zoom = 0.0
                compose.runOnIdle { zoom = map!!.cameraPosition.zoom }
                zoom > 18.0
            }
            if (!landscape) {
                compose.onNodeWithTag("coverage-map-controls").assertTextContains("Hide controls").performClick()
                compose.onAllNodesWithTag("mission-fit").assertCountEquals(0)
            }
            var lines = emptyList<LineString>()
            try {
                compose.waitUntil(30_000) {
                    compose.runOnIdle {
                        lines = map!!.style?.getSourceAs<GeoJsonSource>("mama-coverage-preview")
                            ?.querySourceFeatures(null)?.mapNotNull { it.geometry() as? LineString }?.distinct() ?: emptyList()
                    }
                    lines.size == expectedCount
                }
            } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
                throw AssertionError("Expected $expectedCount ${if (eastWest) "east/west" else "north/south"} lines; received ${lines.size}: $lines", failure)
            }
            val minLat = 0.123456; val minLon = 149.165
            val maxLat = minLat + originalMetres(20.0)
            val maxLon = minLon + originalMetres(10.0)
            lines.forEach { line ->
                val points = line.coordinates()
                assertEquals(2, points.size)
                points.forEach { point ->
                    // Native tile geometry is quantized; allow about 1.1 cm at this latitude.
                    assertTrue("Line must stay in the synthetic field", point.latitude() in minLat - 1e-7..maxLat + 1e-7 && point.longitude() in minLon - 1e-7..maxLon + 1e-7)
                }
                assertTrue("Line must match selected direction", if (eastWest) abs(points[0].latitude() - points[1].latitude()) < 1e-9 else abs(points[0].longitude() - points[1].longitude()) < 1e-9)
            }
            compose.onNodeWithTag("coverage-map-legend").assertIsDisplayed()
            compose.waitForIdle()
            compose.runOnIdle { map!!.triggerRepaint() }
            compose.awaitScreenshotFrame(view)
            val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            try {
                File(compose.activity.getExternalFilesDir(null), "phase83-${if (landscape) "landscape" else "portrait"}-${if (eastWest) "east" else "north"}.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                val p = lines[lines.size / 2].coordinates()
                var x = 0; var y = 0
                compose.runOnIdle {
                    val center = map!!.projection.toScreenLocation(org.maplibre.android.geometry.LatLng((p[0].latitude() + p[1].latitude()) / 2, (p[0].longitude() + p[1].longitude()) / 2))
                    val offset = IntArray(2); view.getLocationOnScreen(offset)
                    x = (offset[0] + center.x).roundToInt(); y = (offset[1] + center.y).roundToInt()
                }
                assertTrue("Projected line center must be visible", x in 6 until bitmap.width - 6 && y in 6 until bitmap.height - 6)
                var cyan = 0
                for (dy in -6..6) for (dx in -6..6) {
                    val pixel = bitmap.getPixel(x + dx, y + dy)
                    if (android.graphics.Color.red(pixel) < 60 && android.graphics.Color.green(pixel) in 120..190 && android.graphics.Color.blue(pixel) in 130..205) cyan++
                }
                assertTrue("Selected line must render at its geographic position: $cyan pixels", cyan >= 3)
            } finally { bitmap.recycle() }
        } finally { compose.runOnIdle { view.removeOnDidFinishRenderingFrameListener(listener) } }
    }

    private fun originalMetres(metres: Double) = Math.toDegrees(metres / 6_371_000.0)
    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
        return null
    }

    @Test fun settingsRegeneratePreviewAndNeverReplaceRoute() {
        show()
        node("coverage-generate").assertIsNotEnabled()
        node("coverage-width").performTextReplacement("2")
        node("coverage-overlap").apply { performTextReplacement("0"); performImeAction() }
        generate()
        node("coverage-pass-count").assertTextEquals("5 working passes · preview only")
        node("coverage-spacing").assertTextEquals("Maximum pass spacing: 2.00 m")
        node("coverage-area-headland").assertTextEquals("Estimated reserved headland: none (0 m width)")
        node("coverage-area-gap").assertTextEquals("Estimated unplanned main area: none detected")
        node("coverage-range-headland").assertTextContains("0.0–0.0", substring = true)
        node("coverage-range-gap").assertTextContains("Modeled unplanned main range", substring = true)
        node("coverage-range-warning").assertTextContains("not actual soil", substring = true)
        node("coverage-area-resolution").assertTextContains("Estimates can be inaccurate", substring = true)
        node("coverage-area-resolution").assertTextContains("Narrow gaps may be missed", substring = true)
        node("coverage-overlap").apply { performTextReplacement("10"); performImeAction() }
        compose.onNodeWithTag("coverage-close-settings").performClick()
        node("coverage-empty").assertIsDisplayed()
        generate()
        node("coverage-pass-count").assertTextEquals("6 working passes · preview only")
        node("coverage-spacing").assertTextEquals("Maximum pass spacing: 1.80 m")
        node("coverage-east-west").performClick()
        compose.onNodeWithTag("coverage-close-settings").performClick()
        node("coverage-empty").assertIsDisplayed()
        generate()
        node("coverage-pass-count").assertTextEquals("11 working passes · preview only")
        node("coverage-overlap").apply { performTextReplacement("100"); performImeAction() }
        node("coverage-generate").assertIsNotEnabled()
        node("coverage-input-error").assertTextEquals("Enter overlap from 0 to 80 percent.")
        compose.onNodeWithTag("coverage-close-settings").performClick()
        node("coverage-empty").assertIsDisplayed()
        assertTrue(actions.isEmpty())
    }

    @Test fun missingAndChangedOutlineCannotLeaveAnOldPreview() {
        val draft = mutableStateOf(MissionDraft(keepInFence = coverageTestOutline()))
        show(draft = draft)
        node("coverage-width").performTextReplacement("2")
        generate()
        node("coverage-pass-count").assertIsDisplayed()
        compose.runOnIdle { draft.value = MissionDraft() }
        node("coverage-empty").assertIsDisplayed()
        node("coverage-width").performTextReplacement("2")
        generate(expectError = true)
        node("coverage-error").assertTextEquals("Add 3 to 64 field-outline vertices first.")
        assertTrue(actions.isEmpty())
    }

    @Test fun trianglePreviewAndControlsRemainReachableAtLargeText() {
        show(largeText = true, draft = mutableStateOf(MissionDraft(keepInFence = coverageTestOutline(true))))
        node("coverage-width").performTextReplacement("2")
        node("coverage-overlap").apply { performTextReplacement("10"); performImeAction() }
        node("coverage-generate").assertIsDisplayed()
        capture("phase76-large-inputs.png")
        generate()
        node("coverage-diagram").assertIsDisplayed()
        capture("phase76-large-triangle.png")
        node("coverage-compare").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("coverage-comparison").fetchSemanticsNodes().isNotEmpty() }
        node("coverage-comparison-north-passes")
        capture("phase80-large-observe.png")
        node("coverage-comparison-north-passes").assertIsDisplayed()
        node("coverage-comparison-east-passes").assertIsDisplayed()
        capture("phase80-large-comparison.png")
        assertTrue(actions.isEmpty())
    }

    @Test fun headlandInvalidatesAndPreviewsRectangleTriangleAndConcaveFields() {
        val metres = 6_371_000.0
        fun field(xy: List<Pair<Double, Double>>) = xy.mapIndexed { i, (x, y) -> DraftWaypoint("h$i", Math.toDegrees(y / metres), Math.toDegrees(x / metres)) }
        val draft = mutableStateOf(MissionDraft(keepInFence = coverageTestOutline()))
        show(draft = draft)
        node("coverage-width").performTextReplacement("1")
        node("coverage-headland").apply { performTextReplacement("0.5"); performImeAction() }
        generate()
        node("coverage-headland-result").assertTextEquals("Reserved headland: 0.50 m")
        node("coverage-area-headland").assertTextContains("≈", substring = true)
        node("coverage-area-gap").assertTextContains("≈", substring = true)
        node("coverage-range-headland").assertTextContains("Modeled headland range", substring = true)
        node("coverage-range-gap").assertTextContains("Modeled unplanned main range", substring = true)
        node("coverage-range-warning").assertTextContains("not actual soil", substring = true)
        capture("phase79-ranges.png")
        node("coverage-area-resolution").assertTextContains("Narrow gaps may be missed", substring = true)
        capture("phase78-estimates.png")
        node("coverage-diagram").assertIsDisplayed()
        capture("phase77-rectangle.png")
        val shapes = listOf(coverageTestOutline(true), field(listOf(0.0 to 0.0, 12.0 to 0.0, 12.0 to 12.0, 8.0 to 12.0, 8.0 to 4.0, 4.0 to 4.0, 4.0 to 12.0, 0.0 to 12.0)))
        for ((index, outline) in shapes.withIndex()) {
            compose.runOnIdle { draft.value = MissionDraft(keepInFence = outline) }
            node("coverage-empty").assertIsDisplayed()
            node("coverage-width").performTextReplacement("1")
            node("coverage-headland").apply { performTextReplacement("0.5"); performImeAction() }
            generate()
            node("coverage-headland-result").assertTextEquals("Reserved headland: 0.50 m")
            node("coverage-diagram").assertIsDisplayed()
            capture(if (index == 0) "phase77-triangle.png" else "phase77-concave.png")
        }
        node("coverage-headland").apply { performTextReplacement("101"); performImeAction() }
        node("coverage-generate").assertIsNotEnabled()
        node("coverage-input-error").assertTextEquals("Enter a headland width from 0 to 100 metres.")
        assertTrue(actions.isEmpty())
    }

    @Test fun compareDirectionsShowsIndependentCardsAndClearsAfterChanges() {
        val metres = 6_371_000.0
        fun field(xy: List<Pair<Double, Double>>) = xy.mapIndexed { i, (x, y) -> DraftWaypoint("d$i", Math.toDegrees(y / metres), Math.toDegrees(x / metres)) }
        val draft = mutableStateOf(MissionDraft(keepInFence = coverageTestOutline()))
        show(draft = draft)
        node("coverage-compare").assertIsNotEnabled()
        node("coverage-width").performTextReplacement("2")
        node("coverage-overlap").apply { performTextReplacement("0"); performImeAction() }
        generate()
        node("coverage-compare").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("coverage-comparison").fetchSemanticsNodes().isNotEmpty() }
        node("coverage-comparison-warning").assertTextContains("Overlapping ranges do not show", substring = true)
        node("coverage-comparison-north-passes").assertTextEquals("5 working passes")
        node("coverage-comparison-north-length").assertTextEquals("Working length: 100.0 m")
        node("coverage-comparison-north-range").assertTextContains("Modeled unplanned range:", substring = true)
        node("coverage-comparison-east-passes").assertTextEquals("10 working passes")
        node("coverage-comparison-east-length").assertTextEquals("Working length: 100.0 m")
        node("coverage-comparison-east-range").assertTextContains("Modeled unplanned range:", substring = true)
        capture("phase80-metrics.png")
        node("coverage-comparison-east-diagram").assertIsDisplayed()
        capture("phase80-comparison.png")
        node("coverage-comparison-north-map").performClick()
        node("coverage-map-status").assertTextContains("North / south preview selected", substring = true)
        node("coverage-comparison-east-map").performClick()
        node("coverage-map-status").assertTextContains("East / west preview selected", substring = true)
        node("coverage-map-hide").performClick()
        compose.onAllNodesWithTag("coverage-map-status").assertCountEquals(0)
        node("coverage-comparison-north-map").performClick()
        node("coverage-map-status").assertTextContains("North / south preview selected", substring = true)
        node("coverage-pass-count").assertTextEquals("5 working passes · preview only")
        node("coverage-overlap").apply { performTextReplacement("10"); performImeAction() }
        compose.onNodeWithTag("coverage-close-settings").performClick()
        node("coverage-compare").assertIsEnabled()
        compose.onAllNodesWithTag("coverage-map-status").assertCountEquals(0)
        compose.onAllNodesWithTag("coverage-comparison").assertCountEquals(0)
        node("coverage-empty").assertIsDisplayed()
        val shapes = listOf(coverageTestOutline(true), field(listOf(0.0 to 0.0, 12.0 to 0.0, 12.0 to 12.0, 8.0 to 12.0, 8.0 to 4.0, 4.0 to 4.0, 4.0 to 12.0, 0.0 to 12.0)))
        for (shape in shapes) {
            compose.runOnIdle { draft.value = MissionDraft(keepInFence = shape) }
            node("coverage-width").performTextReplacement("1")
            node("coverage-headland").apply { performTextReplacement("0.5"); performImeAction() }
            compose.onNodeWithTag("coverage-close-settings").performClick()
            node("coverage-compare").performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("coverage-comparison").fetchSemanticsNodes().isNotEmpty() }
            node("coverage-comparison-north-passes").assertIsDisplayed()
            node("coverage-comparison-east-passes").assertIsDisplayed()
            node("coverage-comparison-east-map").performClick()
            node("coverage-map-status").assertTextContains("East / west preview selected", substring = true)
        }
        assertTrue(actions.isEmpty())
    }

    @Test fun comparisonShowsIndependentDirectionErrors() {
        compose.setContent {
            MamaGcsTheme {
                FieldCoverageComparisonPanel(CoverageDirectionComparison(
                    CoverageCalculation.Invalid("North problem"), CoverageCalculation.Invalid("East problem")))
            }
        }
        compose.onNodeWithTag("coverage-comparison-north-error").performScrollTo().assertTextEquals("Unavailable: North problem")
        compose.onNodeWithTag("coverage-comparison-east-error").performScrollTo().assertTextEquals("Unavailable: East problem")
        assertTrue(actions.isEmpty())
    }

    private fun show(largeText: Boolean = false, draft: MutableState<MissionDraft> = mutableStateOf(MissionDraft(keepInFence = coverageTestOutline())), mapStyleUrl: String? = null, landscape: Boolean = false) {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (largeText) it.window.setLayout((320 * it.resources.displayMetrics.density).toInt(), WindowManager.LayoutParams.MATCH_PARENT)
        }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, if (largeText) 2f else 1f)) {
                MamaGcsTheme {
                    Box(if (largeText) Modifier.width(320.dp) else Modifier) {
                        MissionScreen(VehicleState(), MissionPlanUiState(draft = draft.value, loading = false), actions::add, mapStyleUrl = mapStyleUrl)
                    }
                }
            }
        }
        compose.onNodeWithTag("mission-tab-Coverage").performScrollTo().performClick()
    }

    private fun node(tag: String): SemanticsNodeInteraction {
        if (tag in setOf("coverage-width", "coverage-overlap", "coverage-headland", "coverage-generate", "coverage-input-error", "coverage-east-west", "coverage-north-south")) {
            if (compose.onAllNodesWithTag("coverage-generate").fetchSemanticsNodes().isEmpty()) node("coverage-settings").performClick()
            val target = compose.onNodeWithTag(tag)
            return if (tag == "coverage-generate") target else target.performScrollTo()
        }
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag(tag))
        val target = compose.onNodeWithTag(tag).performScrollTo()
        if (tag.startsWith("coverage-comparison-") && tag != "coverage-comparison-warning") {
            repeat(12) {
                val viewport = compose.onNodeWithTag("mission-editor").getUnclippedBoundsInRoot()
                val bounds = target.getUnclippedBoundsInRoot()
                if (target.isDisplayed() && bounds.top >= viewport.top && bounds.bottom <= viewport.bottom) return target
                compose.onNodeWithTag("mission-editor").performTouchInput {
                    if (bounds.top < viewport.top) swipeDown(startY = height * 0.25f, endY = height * 0.45f)
                    else swipeUp(startY = height * 0.75f, endY = height * 0.55f)
                }
            }
            val viewport = compose.onNodeWithTag("mission-editor").getUnclippedBoundsInRoot()
            val bounds = target.getUnclippedBoundsInRoot()
            assertTrue("Comparison item must fit vertically in editor: $tag $bounds / $viewport",
                target.isDisplayed() && bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
        }
        return target
    }

    private fun generate(expectError: Boolean = false) {
        node("coverage-generate").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("coverage-settings") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        node(if (expectError) "coverage-error" else "coverage-pass-count").assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.awaitScreenshotFrame(compose.activity.window.decorView)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            File(compose.activity.getExternalFilesDir(null), name).outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }
}
