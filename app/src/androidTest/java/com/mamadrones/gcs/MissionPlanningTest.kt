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
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Uses the device-local draft store only; no vehicle socket or command is opened. */
class MissionPlanningTest {
    @get:Rule val compose = createEmptyComposeRule()

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
                capture("mission-landscape")
                compose.onNodeWithTag("mission-editor").performScrollToNode(hasText("Start mission"))
                compose.onNodeWithText("Start mission").assertIsNotEnabled()
                scenario.close()
                val relaunched = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
                try {
                    compose.openWorkspace("mission")
                    compose.waitUntil(10_000) { compose.onAllNodesWithText("No unsaved changes").fetchSemanticsNodes().isNotEmpty() }
                    scrollToTag("mission-edit-1").assertIsDisplayed()
                    scrollToTag("mission-remove-1").performClick()
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
