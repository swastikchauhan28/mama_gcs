package com.mamadrones.gcs

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.local.datastore.LocalMissionDraftRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real app navigation on a disposable emulator; Cancel leaves the existing local draft untouched. */
class MissionCoordinateEntryUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun signedDecimalEntryWorksInMainActivityWithoutSaving() {
        val previousOrientation = compose.activity.requestedOrientation
        try {
            compose.activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            compose.waitUntil(10_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            }
            compose.openWorkspace("mission")
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasTestTag("mission-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }

            val repository = LocalMissionDraftRepository(compose.activity.applicationContext)
            val beforeDraft = runBlocking { repository.load() }
            val beforeRecovery = runBlocking { repository.loadRecovery() }
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-add"))
            compose.onNodeWithTag("mission-add").performClick()
            compose.onNodeWithTag("mission-latitude").performClick().performTextReplacement("35.36")
            compose.onNodeWithTag("mission-latitude-sign").performClick()
            compose.onNodeWithTag("mission-latitude").assertTextContains("-35.36")
            compose.onNodeWithTag("mission-longitude").performClick().performTextReplacement("149.16")
            compose.onNodeWithTag("mission-longitude-sign").performClick()
            compose.onNodeWithTag("mission-longitude").assertTextContains("-149.16")
            compose.onNodeWithTag("mission-longitude").performClick()
            compose.waitUntil(10_000) { imeVisible() }
            compose.onNodeWithTag("mission-confirm-waypoint").assertIsEnabled()
            capturePortraitDisplay("phase75-signed-decimal-ime.png")

            compose.onNodeWithTag("mission-latitude-sign").performClick()
            compose.onNodeWithTag("mission-longitude-sign").performClick()
            compose.onNodeWithTag("mission-latitude").assertTextContains("35.36")
            compose.onNodeWithTag("mission-longitude").assertTextContains("149.16")
            compose.onNodeWithTag("mission-latitude").performTextReplacement("1".repeat(32))
            compose.onNodeWithTag("mission-latitude-sign").assertIsNotEnabled()
            compose.onNodeWithTag("mission-latitude").assertTextContains("1".repeat(32))
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(beforeDraft, runBlocking { repository.load() })
            assertEquals(beforeRecovery, runBlocking { repository.loadRecovery() })
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = previousOrientation }
        }
    }

    @Test fun invalidCoordinateGuidanceAndDoneAreReachableInMainActivity() {
        val previousOrientation = compose.activity.requestedOrientation
        try {
            compose.activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            compose.waitUntil(10_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            }
            compose.openWorkspace("mission")
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasTestTag("mission-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }

            val repository = LocalMissionDraftRepository(compose.activity.applicationContext)
            val beforeDraft = runBlocking { repository.load() }
            val beforeRecovery = runBlocking { repository.loadRecovery() }
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-add"))
            compose.onNodeWithTag("mission-add").performClick()
            compose.onNodeWithTag("mission-latitude").performClick().performTextReplacement("91")
            compose.onNodeWithTag("mission-longitude").performClick().performTextReplacement("181")
            compose.waitUntil(10_000) { imeVisible() }
            compose.onNodeWithText("Enter a latitude from -90 to 90.").assertIsDisplayed()
            compose.onNodeWithText("Enter a longitude from -180 to 180.").assertIsDisplayed()
            compose.onNodeWithTag("mission-confirm-waypoint").assertIsNotEnabled()
            capturePortraitDisplay("phase74-main-invalid-ime.png")

            compose.onNodeWithTag("mission-latitude").performTextReplacement("-35.36")
            compose.onNodeWithTag("mission-longitude").performClick().performTextReplacement("149.16")
            compose.onNodeWithText("Enter a latitude from -90 to 90.").assertDoesNotExist()
            compose.onNodeWithText("Enter a longitude from -180 to 180.").assertDoesNotExist()
            compose.onNodeWithTag("mission-longitude").performImeAction()
            compose.waitUntil(10_000) { !imeVisible() }
            compose.onNodeWithTag("mission-confirm-waypoint").assertIsEnabled()
            capturePortraitDisplay("phase74-main-corrected-after-done.png")
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Add waypoint").assertDoesNotExist()
            assertEquals(beforeDraft, runBlocking { repository.load() })
            assertEquals(beforeRecovery, runBlocking { repository.loadRecovery() })
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = previousOrientation }
        }
    }

    private fun imeVisible(): Boolean = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
        ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun capturePortraitDisplay(name: String) {
        compose.awaitScreenshotFrame(compose.activity.window.decorView)
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            assertTrue("$name must show the portrait display", screenshot.height > screenshot.width)
            val target = File(requireNotNull(compose.activity.getExternalFilesDir(null)), name)
            target.outputStream().use { output ->
                assertTrue("$name screenshot must be written", screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            screenshot.recycle()
        }
    }
}
