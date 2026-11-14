package com.mamadrones.gcs

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.local.datastore.LocalMissionDraftRepository
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.DraftWaypoint
import kotlinx.coroutines.runBlocking
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Uses the device-local draft store only; no vehicle socket or command is opened. */
class MissionPlanningTest {
    @get:Rule val compose = createEmptyComposeRule()

    // The host deliberately force-stops this process after the marker appears.
    // Run only on a disposable emulator: the second stage replaces the local draft.
    @Test fun phase152PrepareForProcessStop() {
        assumeTrue("Requires the disposable Phase 152 host procedure", InstrumentationRegistry.getArguments().getString("phase152") == "enabled")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = LocalMissionDraftRepository(context)
        val marker = File(context.filesDir, "phase152-ready")
        marker.delete()
        runBlocking { repository.save(MissionDraft(name = "Phase 152 baseline")) }
        val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.openWorkspace("mission")
            compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("mission-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            addPoint("-35.36326", "149.16523")
            addPoint("-35.36330", "149.16527")
            addFenceVertex("-35.36335", "149.16520")
            addFenceVertex("-35.36335", "149.16535")
            addFenceVertex("-35.36320", "149.16535")
            compose.waitUntil(15_000) { runBlocking { repository.loadRecovery()?.let { it.waypoints.size == 2 && it.keepInFence.size == 3 } == true } }
            assertEquals("Phase 152 baseline", runBlocking { repository.load().name })
            assertEquals(0, runBlocking { repository.load().waypoints.size })
            marker.writeText("ready")
            // An ordinary return would close the activity; require the host to stop us.
            Thread.sleep(180_000)
            throw AssertionError("Host did not stop the app after recovery was prepared")
        } finally { marker.delete(); scenario.close() }
    }

    @Test fun phase152VerifyAfterProcessStop() {
        assumeTrue("Requires the disposable Phase 152 host procedure", InstrumentationRegistry.getArguments().getString("phase152") == "enabled")
        val repository = LocalMissionDraftRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val expectedPoints = listOf(-35.36326 to 149.16523, -35.36330 to 149.16527)
        val expectedOutline = listOf(-35.36335 to 149.16520, -35.36335 to 149.16535, -35.36320 to 149.16535)
        assertEquals("Phase 152 baseline", runBlocking { repository.load().name })
        assertEquals(0, runBlocking { repository.load().waypoints.size })
        val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.openWorkspace("mission")
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Recovered unsaved changes · review and save").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("mission-save").assertIsEnabled()
            val recovery = requireNotNull(runBlocking { repository.loadRecovery() })
            assertEquals(expectedPoints, recovery.waypoints.map { it.latitude to it.longitude })
            assertEquals(expectedOutline, recovery.keepInFence.map { it.latitude to it.longitude })
            scrollToTag("mission-edit-0").performClick()
            compose.onNodeWithTag("mission-latitude").assertTextContains("-35.36326", substring = true)
            compose.onNodeWithTag("mission-longitude").assertTextContains("149.16523", substring = true)
            compose.onNodeWithText("Cancel").performClick()
            scrollToTag("mission-remove-fence-2").assertIsDisplayed()
            compose.onNodeWithTag("mission-save").performClick()
            compose.waitUntil(15_000) { runBlocking { repository.load().waypoints.size == 2 && repository.loadRecovery() == null } }
            val saved = runBlocking { repository.load() }
            assertEquals(expectedPoints, saved.waypoints.map { it.latitude to it.longitude })
            assertEquals(expectedOutline, saved.keepInFence.map { it.latitude to it.longitude })
        } finally { scenario.close() }
    }

    @Test fun unsavedRouteAndOutlineRecoverAfterClosingAndReopeningEditor() {
        val repository = LocalMissionDraftRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val previous = runBlocking { repository.load() }
        val previousRecovery = runBlocking { repository.loadRecovery() }
        val baseline = MissionDraft(name = "Recovery baseline")
        val points = listOf(-35.36326 to 149.16523, -35.36330 to 149.16527)
        val outline = listOf(-35.36335 to 149.16520, -35.36335 to 149.16535, -35.36320 to 149.16535)
        try {
            runBlocking { repository.save(baseline) }
            val first = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
            try {
                compose.openWorkspace("mission")
                compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("mission-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                points.forEach { (latitude, longitude) -> addPoint(latitude.toString(), longitude.toString()) }
                outline.forEach { (latitude, longitude) -> addFenceVertex(latitude.toString(), longitude.toString()) }
                compose.waitUntil(15_000) {
                    runBlocking { repository.loadRecovery()?.let { it.waypoints.size == 2 && it.keepInFence.size == 3 } == true }
                }
                val working = requireNotNull(runBlocking { repository.loadRecovery() })
                assertEquals(points, working.waypoints.map { it.latitude to it.longitude })
                assertEquals(outline, working.keepInFence.map { it.latitude to it.longitude })
                assertEquals(baseline, runBlocking { repository.load() })
            } finally { first.close() }

            val reopened = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
            try {
                compose.openWorkspace("mission")
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Recovered unsaved changes · review and save").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("mission-save").assertIsEnabled()
                assertEquals(baseline, runBlocking { repository.load() })
                capture("phase151-recovered")
                scrollToTag("mission-edit-0").performClick()
                compose.onNodeWithTag("mission-latitude").assertTextContains("-35.36326", substring = true)
                compose.onNodeWithTag("mission-longitude").assertTextContains("149.16523", substring = true)
                compose.onNodeWithText("Cancel").performClick()
                scrollToTag("mission-remove-fence-2").assertIsDisplayed()
                compose.onNodeWithText("Outline vertex 3").assertIsDisplayed()
                compose.onNodeWithTag("mission-save").performClick()
                compose.waitUntil(15_000) {
                    runBlocking { repository.load().waypoints.size == 2 && repository.loadRecovery() == null }
                }
                val saved = runBlocking { repository.load() }
                assertEquals(points, saved.waypoints.map { it.latitude to it.longitude })
                assertEquals(outline, saved.keepInFence.map { it.latitude to it.longitude })
            } finally { reopened.close() }
        } finally {
            runBlocking { repository.save(previous); previousRecovery?.let { repository.saveRecovery(it) } }
        }
    }

    @Test fun localRouteReadinessUpdatesAsWaypointsAreAdded() {
        val repository = LocalMissionDraftRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val previous = runBlocking { repository.load() }
        val previousRecovery = runBlocking { repository.loadRecovery() }
        try {
            runBlocking { repository.save(MissionDraft()) }
            val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
            try {
                compose.openWorkspace("mission")
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("mission-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                scrollToTag("mission-route-readiness").assertIsDisplayed()
                compose.onNodeWithText("NEEDS CHANGES").assertIsDisplayed()
                addPoint("-35.3632621", "149.1652374")
                addPoint("-35.3635000", "149.1660000")
                scrollToTag("mission-route-readiness").assertIsDisplayed()
                compose.onNodeWithText("READY FOR REVIEW · LOCAL ONLY").assertIsDisplayed()
                compose.onNodeWithText("NOT CHECKED · NO OUTLINE").assertIsDisplayed()
            } finally { scenario.close() }
        } finally {
            runBlocking {
                repository.save(previous)
                previousRecovery?.let { repository.saveRecovery(it) }
            }
        }
    }

    @Test fun localRouteReadinessWarningsClearAfterEdits() {
        val repository = LocalMissionDraftRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val previous = runBlocking { repository.load() }
        val previousRecovery = runBlocking { repository.loadRecovery() }
        try {
            runBlocking { repository.save(MissionDraft()) }
            val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
            try {
                compose.openWorkspace("mission")
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("mission-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                addPoint("-35.3635000", "149.1650000")
                addPoint("-35.3635000", "149.1650000")
                scrollToTag("mission-route-readiness").assertIsDisplayed()
                compose.onNodeWithText("NEEDS CHANGES").assertIsDisplayed()
                compose.onNodeWithText("Edit or remove identical consecutive points at legs 1.").assertExists()

                scrollToTag("mission-edit-1").performClick()
                compose.onNodeWithTag("mission-latitude").performTextReplacement("-35.3634000")
                compose.onNodeWithTag("mission-confirm-waypoint").performClick()
                scrollToTag("mission-route-readiness").assertIsDisplayed()
                compose.onNodeWithText("READY FOR REVIEW · LOCAL ONLY").assertIsDisplayed()

                addFenceVertex("-35.3640000", "149.1640000")
                addFenceVertex("-35.3640000", "149.1660000")
                addFenceVertex("-35.3620000", "149.1650000")
                addPoint("-35.3700000", "149.1650000")
                scrollToTag("mission-route-readiness").assertIsDisplayed()
                compose.onNodeWithText("NEEDS CHANGES").assertIsDisplayed()
                compose.onNodeWithText("Move route points inside the outline: 3.").assertExists()
                compose.onNodeWithText("Revise straight legs leaving the outline: 2.").assertExists()

                scrollToTag("mission-edit-2").performClick()
                compose.onNodeWithTag("mission-latitude").performTextReplacement("-35.3632000")
                compose.onNodeWithTag("mission-confirm-waypoint").performClick()
                scrollToTag("mission-route-readiness").assertIsDisplayed()
                compose.onNodeWithText("READY FOR REVIEW · LOCAL ONLY").assertIsDisplayed()
                compose.onNodeWithText("LOCAL CHECK PASSED").assertExists()
            } finally { scenario.close() }
        } finally {
            runBlocking {
                repository.save(previous)
                previousRecovery?.let { repository.saveRecovery(it) }
            }
        }
    }

    @Test fun editReorderAndSaveDraftSurvivesNewViewModel() {
        val repository = LocalMissionDraftRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val previous = runBlocking { repository.load() }
        val previousRecovery = runBlocking { repository.loadRecovery() }
        try {
            runBlocking { repository.save(MissionDraft()) }
            val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
            try {
                compose.openWorkspace("mission")
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("mission-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                addPoint("91", "149.1652374", invalid = true)
                scrollToTag("mission-route-review").assertIsDisplayed()
                compose.onNodeWithText("INCOMPLETE · ADD WAYPOINTS").assertIsDisplayed()
                scrollToTag("mission-route-readiness").assertIsDisplayed()
                compose.onNodeWithText("NEEDS CHANGES").assertIsDisplayed()
                addPoint("-35.3632621", "149.1652374")
                addPoint("-35.3635000", "149.1660000")
                scrollToTag("mission-route-review").assertIsDisplayed()
                compose.onNodeWithText("ROUTE SHAPE DEFINED · REVIEW REQUIRED").assertIsDisplayed()
                scrollToTag("mission-route-readiness").assertIsDisplayed()
                compose.onNodeWithText("READY FOR REVIEW · LOCAL ONLY").assertIsDisplayed()
                // Keep the synthetic outline near the route so Fit draft shows both markers.
                addFenceVertex("-35.3645000", "149.1645000")
                addFenceVertex("-35.3645000", "149.1670000")
                addFenceVertex("-35.3625000", "149.1655000")
                scrollToTag("mission-route-review").assertIsDisplayed()
                compose.onNodeWithText("3 VERTICES · LOCAL ONLY").assertIsDisplayed()
                compose.onAllNodesWithText("NONE FOUND").assertCountEquals(3)
                scrollToTag("mission-up-1").performClick()
                scrollToTag("mission-edit-0").performClick()
                compose.onNodeWithTag("mission-latitude").performTextReplacement("-35.3640000")
                compose.onNodeWithTag("mission-confirm-waypoint").performClick()
                scrollToTag("mission-save").performClick()
                compose.waitUntil(10_000) { runBlocking { repository.load().waypoints.size == 2 } }
                val saved = runBlocking { repository.load() }
                assertEquals(-35.364, saved.waypoints.first().latitude, 0.0000001)
                assertEquals(-35.3632621, saved.waypoints.last().latitude, 0.0000001)
                assertEquals(3, saved.keepInFence.size)
                capture("mission-portrait")
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                compose.waitUntil(10_000) {
                    // Rotation is window-specific; the application/test context can retain its
                    // original configuration. Check the activity that actually renders the editor.
                    var landscape = false
                    scenario.onActivity { landscape = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                    landscape
                }
                val rotatedRoot = compose.onRoot().fetchSemanticsNode().boundsInRoot
                assertTrue("Editor must actually render in landscape", rotatedRoot.width > rotatedRoot.height)
                scrollToTag("mission-add").assertIsEnabled()
                compose.onNodeWithTag("mission-map").assertIsDisplayed()
                // A map can report its Compose node before native route layers render.
                compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("mission-fit") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("mission-fit").performClick()
                captureRenderedLandscapeRoute(scenario, saved.waypoints)
                compose.onNodeWithTag("mission-editor").performScrollToNode(hasText("Start mission"))
                compose.onNodeWithText("Start mission").assertIsNotEnabled()
                scenario.close()
                val relaunched = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
                try {
                    compose.openWorkspace("mission")
                    compose.waitUntil(10_000) { compose.onAllNodesWithText("No unsaved changes").fetchSemanticsNodes().isNotEmpty() }
                    scrollToTag("mission-edit-1").assertIsDisplayed()
                    scrollToTag("mission-remove-1").performClick()
                    compose.onNodeWithTag("mission-confirm-remove-waypoint").performClick()
                    scrollToTag("mission-save").performClick()
                    compose.waitUntil(10_000) { runBlocking { repository.load().waypoints.size == 1 } }
                    assertTrue(runBlocking { repository.load().waypoints.first().id == saved.waypoints.first().id })
                } finally { relaunched.close() }
            } finally { scenario.close() }
        } finally {
            runBlocking {
                repository.save(previous)
                previousRecovery?.let { repository.saveRecovery(it) }
            }
        }
    }

    private fun addPoint(latitude: String, longitude: String, invalid: Boolean = false) {
        scrollToTag("mission-add").performClick()
        compose.onNodeWithTag("mission-latitude").performTextReplacement(latitude)
        compose.onNodeWithTag("mission-longitude").performTextReplacement(longitude)
        if (invalid) {
            compose.onNodeWithTag("mission-confirm-waypoint").assertIsNotEnabled()
            compose.onNodeWithText("Cancel").performClick()
        } else compose.onNodeWithTag("mission-confirm-waypoint").performClick()
    }

    private fun addFenceVertex(latitude: String, longitude: String) {
        scrollToTag("mission-add-fence-vertex").performClick()
        compose.onNodeWithTag("mission-latitude").performTextReplacement(latitude)
        compose.onNodeWithTag("mission-longitude").performTextReplacement(longitude)
        compose.onNodeWithTag("mission-confirm-waypoint").performClick()
    }

    private fun scrollToTag(tag: String): SemanticsNodeInteraction {
        if (tag == "mission-save") return compose.onNodeWithTag(tag)
        compose.selectMissionTab(when {
            tag == "mission-route-review" || tag == "mission-route-readiness" -> "Review"
            tag.contains("fence") -> "Field outline"
            else -> "Route"
        })
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    private fun captureRenderedLandscapeRoute(
        scenario: androidx.test.core.app.ActivityScenario<MainActivity>,
        waypoints: List<DraftWaypoint>,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var view: MapView? = null
        var map: MapLibreMap? = null
        scenario.onActivity { activity ->
            view = requireNotNull(findMap(activity.window.decorView))
            view!!.getMapAsync { map = it }
        }
        compose.waitUntil(45_000) {
            var loaded = false
            scenario.onActivity { loaded = map?.style?.isFullyLoaded == true }
            loaded
        }
        var last: Bitmap? = null
        var detail = "No rendered frame"
        try {
            compose.waitUntil(35_000) {
                val projected = mutableListOf<Pair<Int, Int>>()
                var labels = false
                scenario.onActivity {
                    val nativeView = requireNotNull(view)
                    val loadedMap = requireNotNull(map)
                    val offset = IntArray(2)
                    val visible = Rect()
                    nativeView.getLocationOnScreen(offset)
                    val shown = nativeView.isShown && nativeView.getGlobalVisibleRect(visible)
                    labels = shown && waypoints.withIndex().all { (index, waypoint) ->
                        val position = loadedMap.projection.toScreenLocation(LatLng(waypoint.latitude, waypoint.longitude))
                        val x = (offset[0] + position.x).toInt()
                        val y = (offset[1] + position.y).toInt()
                        projected.add(x to y)
                        val area = RectF(position.x - 16f, position.y - 16f, position.x + 16f, position.y + 16f)
                        visible.contains(x, y) && loadedMap.queryRenderedFeatures(area, "mama-draft-labels")
                            .any { it.getStringProperty("label") == (index + 1).toString() }
                    }
                    loadedMap.triggerRepaint()
                }
                val frame = instrumentation.uiAutomation.takeScreenshot()
                if (frame == null) false else {
                    last?.recycle()
                    last = frame
                    val orangeCounts = projected.map { (x, y) ->
                        var orange = 0
                        if (x >= 18 && y >= 18 && x + 18 < frame.width && y + 18 < frame.height) {
                            for (dy in -18..18) for (dx in -18..18) {
                                val pixel = frame.getPixel(x + dx, y + dy)
                                if (android.graphics.Color.red(pixel) >= 235 &&
                                    android.graphics.Color.green(pixel) in 90..190 &&
                                    android.graphics.Color.blue(pixel) <= 100) orange++
                            }
                        }
                        orange
                    }
                    val minimumSeparation = 28f * requireNotNull(view).resources.displayMetrics.density
                    val separated = projected.indices.all { first ->
                        (first + 1 until projected.size).all { second ->
                            val dx = (projected[first].first - projected[second].first).toFloat()
                            val dy = (projected[first].second - projected[second].second).toFloat()
                            dx * dx + dy * dy >= minimumSeparation * minimumSeparation
                        }
                    }
                    detail = "labels=$labels points=$projected orange=$orangeCounts separated=$separated"
                    labels && separated && projected.size == waypoints.size && orangeCounts.all { it >= 12 }
                }
            }
            val directory = requireNotNull(instrumentation.targetContext.getExternalFilesDir("qa")).apply { mkdirs() }
            File(directory, "mission-landscape.png").outputStream().use {
                assertTrue(requireNotNull(last).compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } catch (failure: ComposeTimeoutException) {
            last?.let { frame ->
                val directory = requireNotNull(instrumentation.targetContext.getExternalFilesDir("qa")).apply { mkdirs() }
                File(directory, "failure-mission-landscape.png").outputStream().use {
                    frame.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            throw AssertionError("Landscape route markers did not render: $detail", failure)
        } finally { last?.recycle() }
    }

    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findMap(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle()
        var screenshot: Bitmap? = null
        compose.waitUntil(10_000) {
            screenshot = instrumentation.uiAutomation.takeScreenshot()
            screenshot != null
        }
        val image = requireNotNull(screenshot)
        try {
            val directory = requireNotNull(instrumentation.targetContext.getExternalFilesDir("qa")).apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { image.recycle() }
    }
}
