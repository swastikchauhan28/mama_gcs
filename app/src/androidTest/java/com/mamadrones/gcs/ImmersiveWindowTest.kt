package com.mamadrones.gcs

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real activity/window checks: Compose-only hosts do not exercise immersive system-bar policy. */
class ImmersiveWindowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun assertImmersive() {
        compose.waitUntil(10_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.let {
                !it.isVisible(WindowInsetsCompat.Type.statusBars()) &&
                    !it.isVisible(WindowInsetsCompat.Type.navigationBars())
            } == true
        }
        compose.runOnIdle {
            assertEquals(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
                WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).systemBarsBehavior,
            )
        }
    }

    @Test fun systemBarsHiddenAfterLaunchAndActivityRecreation() {
        assertImmersive()
        compose.onNodeWithTag("connection-shortcut").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        assertImmersive()
        compose.onNodeWithTag("connection-shortcut").assertIsDisplayed()
    }

    @Test fun connectionSettingsRemainAccessibleInFullscreen() {
        assertImmersive()
        compose.onNodeWithTag("connection-shortcut").performClick()
        compose.onNodeWithTag("exit-workspace").assertIsDisplayed().performClick()
        try {
            compose.waitUntil(10_000) { compose.onNodeWithTag("operation-map").isDisplayed() }
        } catch (failure: AssertionError) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val output = File(instrumentation.targetContext.getExternalFilesDir("qa"), "fullscreen-navigation.png")
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            throw AssertionError(compose.onRoot().printToString(maxDepth = 3), failure)
        }
        compose.onNodeWithTag("operation-map").assertIsDisplayed()
        assertImmersive()
    }

    @Test fun landscapeKeepsMapAndNavigationVisible() {
        val previousOrientation = compose.activity.requestedOrientation
        try {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            assertImmersive()
            compose.onNodeWithTag("operation-map").assertIsDisplayed()
            compose.onNodeWithTag("station-menu").assertIsDisplayed()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val directory = requireNotNull(instrumentation.targetContext.getExternalFilesDir("qa")).apply { mkdirs() }
            var screenshot: Bitmap? = null
            compose.waitUntil(10_000) {
                screenshot = instrumentation.uiAutomation.takeScreenshot()
                screenshot != null
            }
            requireNotNull(screenshot).let { bitmap ->
                File(directory, "fullscreen-landscape.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = previousOrientation }
        }
    }
}
