package com.mamadrones.gcs

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.local.datastore.LocalMissionDraftRepository
import com.mamadrones.gcs.domain.model.MissionDraft
import kotlinx.coroutines.runBlocking
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
                addPoint("-35.3632621", "149.1652374")
                addPoint("-35.3635000", "149.1660000")
                scrollToTag("mission-route-review").assertIsDisplayed()
                compose.onNodeWithText("ROUTE SHAPE DEFINED · REVIEW REQUIRED").assertIsDisplayed()
                addFenceVertex("-35.3700000", "149.1500000")
                addFenceVertex("-35.3700000", "149.1800000")
                addFenceVertex("-35.3500000", "149.1650000")
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
                captureRenderedLandscapeRoute()
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
            tag == "mission-route-review" -> "Review"
            tag.contains("fence") -> "Field outline"
            else -> "Route"
        })
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    private fun captureRenderedLandscapeRoute() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bounds = compose.onNodeWithTag("mission-map").fetchSemanticsNode().boundsInRoot
        var captured: Bitmap? = null
        try {
            // Exclude the right-side map controls, whose gold labels are not route evidence.
            compose.waitUntil(20_000) {
                val frame = instrumentation.uiAutomation.takeScreenshot()
                if (frame == null) false else {
                    var orange = 0
                    val right = (bounds.left + bounds.width * 0.65f).toInt().coerceAtMost(frame.width)
                    val bottom = bounds.bottom.toInt().coerceAtMost(frame.height)
                    for (y in bounds.top.toInt().coerceAtLeast(0) until bottom step 3) {
                        for (x in bounds.left.toInt().coerceAtLeast(0) until right step 3) {
                            val pixel = frame.getPixel(x, y)
                            if (android.graphics.Color.red(pixel) >= 235 && android.graphics.Color.green(pixel) in 90..190 &&
                                android.graphics.Color.blue(pixel) <= 100) orange++
                        }
                    }
                    if (orange >= 12) { captured = frame; true } else { frame.recycle(); false }
                }
            }
            val directory = requireNotNull(instrumentation.targetContext.getExternalFilesDir("qa")).apply { mkdirs() }
            File(directory, "mission-landscape.png").outputStream().use {
                assertTrue(requireNotNull(captured).compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { captured?.recycle() }
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
