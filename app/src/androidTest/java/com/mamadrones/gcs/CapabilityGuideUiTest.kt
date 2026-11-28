package com.mamadrones.gcs

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Disposable-emulator walkthrough; opens no telemetry link or rover command path. */
class CapabilityGuideUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun guideIsReachableAndReadableInBothOrientations() {
        val previousOrientation = compose.activity.requestedOrientation
        try {
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
            compose.openWorkspace("more")
            compose.onNodeWithTag("systems-capabilities").performScrollTo().performClick()
            compose.onNodeWithText("Capabilities & limits").assertIsDisplayed()
            compose.onNodeWithText("AVAILABLE IN THE APP").assertIsDisplayed()
            compose.onNodeWithText("Local mission planning").performScrollTo().assertIsDisplayed()
            compose.assertMonitoringOnlySafety()
            capture("phase246-capability-guide-landscape")

            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
            compose.onNodeWithText("REQUIRES LIVE PROOF").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Position and accuracy").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("UNAVAILABLE FOR ROVER CONTROL").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Mission execution").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Movement and equipment").performScrollTo().assertIsDisplayed()
            compose.assertMonitoringOnlySafety()
            capture("phase246-capability-guide-portrait-limits")

            compose.onNodeWithTag("category-more").performScrollTo().performClick()
            compose.onNodeWithText("Rover summary").assertIsDisplayed()
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = previousOrientation }
        }
    }

    private fun rotate(request: Int, expected: Int) {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = request }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == expected }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.awaitScreenshotFrame(compose.activity.window.decorView)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Compose semantics can pass underneath Android's first-use fullscreen tutorial.
        compose.waitUntil(10_000) {
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() ==
                instrumentation.targetContext.packageName
        }
        val stability = StationScreenshotStability()
        var bitmap: Bitmap? = null
        compose.waitUntil(10_000) {
            bitmap = instrumentation.uiAutomation.takeScreenshot()
            bitmap?.let {
                if (stability.accepts(it, compose.activity.window.decorView)) true
                else { it.recycle(); bitmap = null; false }
            } ?: false
        }
        val image = requireNotNull(bitmap)
        try {
            val directory = requireNotNull(instrumentation.targetContext.getExternalFilesDir("qa")).apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { image.recycle() }
    }
}
