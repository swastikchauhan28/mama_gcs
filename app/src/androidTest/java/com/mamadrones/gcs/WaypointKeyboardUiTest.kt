package com.mamadrones.gcs

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.mission.MissionPlanAction
import com.mamadrones.gcs.presentation.mission.MissionPlanUiState
import com.mamadrones.gcs.presentation.screens.MissionScreen
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic Add/Edit keyboard path; callbacks do not reach storage or vehicle transport. */
class WaypointKeyboardUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun addWaypointCanDismissImeAndConfirmAtLargeText() = exerciseAdd(
        "phase71-add-ime.png", "phase71-add-after-done.png", requirePortrait = false
    )

    @Test fun addWaypointCanDismissImeInPortrait() = inPortrait {
        exerciseAdd("phase72-add-ime.png", "phase72-add-after-done.png", requirePortrait = true)
    }

    @Test fun editWaypointCanDismissImeAndConfirmAtLargeText() = exerciseEdit(
        "phase71-edit-ime.png", "phase71-edit-after-done.png", requirePortrait = false
    )

    @Test fun editWaypointCanDismissImeInPortrait() = inPortrait {
        exerciseEdit("phase72-edit-ime.png", "phase72-edit-after-done.png", requirePortrait = true)
    }

    @Test fun invalidCoordinatesExplainRangesAndRecoverInPortrait() = inPortrait {
        exerciseInvalidCoordinates(requirePortrait = true)
    }

    @Test fun invalidCoordinatesExplainRangesAndRecoverInLandscape() = exerciseInvalidCoordinates(requirePortrait = false)

    @Test fun signedDecimalControlsRemainReachableAtLargeText() {
        val actions = mutableListOf<MissionPlanAction>()
        showMission(MissionPlanUiState(loading = false), actions)
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-add"))
        compose.onNodeWithTag("mission-add").performClick()
        compose.onNodeWithTag("mission-latitude").performClick().performTextReplacement("35.36")
        compose.onNodeWithTag("mission-latitude-sign").performClick()
        compose.onNodeWithTag("mission-longitude").performClick().performTextReplacement("149.16")
        compose.onNodeWithTag("mission-longitude-sign").performClick()
        compose.onNodeWithTag("mission-longitude").performClick()
        compose.waitUntil(10_000) { imeVisible() }
        compose.onNodeWithTag("mission-latitude").assertTextContains("-35.36")
        compose.onNodeWithTag("mission-longitude").assertTextContains("-149.16")
        captureDisplay("phase75-signed-landscape-ime.png", requirePortrait = false)
        compose.onNodeWithTag("mission-longitude").performImeAction()
        compose.waitUntil(10_000) { !imeVisible() }
        compose.onNodeWithTag("mission-confirm-waypoint").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(MissionPlanAction.Add(-35.36, -149.16)), actions) }
    }

    private fun exerciseInvalidCoordinates(requirePortrait: Boolean) {
        val actions = mutableListOf<MissionPlanAction>()
        showMission(MissionPlanUiState(loading = false), actions)
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-add"))
        compose.onNodeWithTag("mission-add").performClick()
        compose.onNodeWithTag("mission-latitude").performClick().performTextReplacement("91")
        compose.onNodeWithTag("mission-longitude").performClick().performTextReplacement("181")
        compose.waitUntil(10_000) { imeVisible() }
        compose.onNodeWithText("Enter a latitude from -90 to 90.").assertIsDisplayed()
        compose.onNodeWithText("Enter a longitude from -180 to 180.").assertIsDisplayed()
        compose.onNodeWithTag("mission-confirm-waypoint").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        captureDisplay(if (requirePortrait) "phase73-invalid-ime.png" else "phase73-invalid-landscape-ime.png", requirePortrait)

        compose.onNodeWithTag("mission-latitude").performTextReplacement("-35.36")
        compose.onNodeWithText("Enter a latitude from -90 to 90.").assertDoesNotExist()
        compose.onNodeWithTag("mission-confirm-waypoint").assertIsNotEnabled()
        compose.onNodeWithTag("mission-longitude").performClick().performTextReplacement("149.16")
        compose.onNodeWithText("Enter a longitude from -180 to 180.").assertDoesNotExist()
        compose.onNodeWithTag("mission-longitude").performImeAction()
        compose.waitUntil(10_000) { !imeVisible() }
        captureDisplay(if (requirePortrait) "phase73-corrected-after-done.png" else "phase73-corrected-landscape-after-done.png", requirePortrait)
        compose.onNodeWithTag("mission-confirm-waypoint").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(MissionPlanAction.Add(-35.36, 149.16)), actions) }
    }

    private fun exerciseAdd(imeImage: String, doneImage: String, requirePortrait: Boolean) {
        val actions = mutableListOf<MissionPlanAction>()
        showMission(MissionPlanUiState(loading = false), actions)
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-add"))
        compose.onNodeWithTag("mission-add").performClick()
        compose.onNodeWithText("Add waypoint").assertIsDisplayed()
        enterCoordinatesAndDismiss("-35.36", "149.16", imeImage, doneImage, requirePortrait)
        compose.onNodeWithTag("mission-confirm-waypoint").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(MissionPlanAction.Add(-35.36, 149.16)), actions) }
    }

    private fun exerciseEdit(imeImage: String, doneImage: String, requirePortrait: Boolean) {
        val actions = mutableListOf<MissionPlanAction>()
        val waypoint = DraftWaypoint("wp-1", -35.0, 149.0)
        showMission(MissionPlanUiState(draft = MissionDraft("Test route", listOf(waypoint)), loading = false), actions)
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-edit-0"))
        compose.onNodeWithTag("mission-edit-0").performClick()
        compose.onNodeWithText("Edit waypoint").assertIsDisplayed()
        enterCoordinatesAndDismiss("-35.25", "149.75", imeImage, doneImage, requirePortrait)
        compose.onNodeWithTag("mission-confirm-waypoint").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(listOf(MissionPlanAction.Edit(DraftWaypoint("wp-1", -35.25, 149.75))), actions)
        }
    }

    private fun inPortrait(block: () -> Unit) {
        val previous = compose.activity.requestedOrientation
        try {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitUntil(10_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            }
            compose.waitForIdle()
            block()
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = previous }
        }
    }

    private fun showMission(state: MissionPlanUiState, actions: MutableList<MissionPlanAction>) {
        val screenWidthPx = (320f * compose.activity.resources.displayMetrics.density).toInt()
        compose.activity.runOnUiThread {
            compose.activity.window.setLayout(screenWidthPx, WindowManager.LayoutParams.MATCH_PARENT)
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MamaGcsTheme {
                    Box(Modifier.width(320.dp)) {
                        MissionScreen(VehicleState(), state, actions::add)
                    }
                }
            }
        }
    }

    private fun enterCoordinatesAndDismiss(
        latitude: String, longitude: String, imeImage: String, doneImage: String, requirePortrait: Boolean
    ) {
        compose.onNodeWithTag("mission-latitude").performClick().performTextReplacement(latitude)
        compose.onNodeWithTag("mission-longitude").performClick().performTextReplacement(longitude)
        compose.waitUntil(10_000) { imeVisible() }
        captureDisplay(imeImage, requirePortrait)
        compose.onNodeWithTag("mission-longitude").performImeAction()
        compose.waitUntil(10_000) { !imeVisible() }
        compose.onNodeWithTag("mission-confirm-waypoint").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        captureDisplay(doneImage, requirePortrait)
    }

    private fun imeVisible(): Boolean = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
        ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun captureDisplay(name: String, requirePortrait: Boolean) {
        compose.awaitScreenshotFrame(compose.activity.window.decorView)
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            if (requirePortrait) {
                assertTrue("$name must capture a portrait display", screenshot.height > screenshot.width)
            }
            val target = File(requireNotNull(compose.activity.getExternalFilesDir(null)), name)
            target.outputStream().use { output ->
                assertTrue("$name screenshot must be written", screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            screenshot.recycle()
        }
    }
}
