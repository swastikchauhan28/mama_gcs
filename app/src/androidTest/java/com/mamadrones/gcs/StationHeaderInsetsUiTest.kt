package com.mamadrones.gcs

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.ThemeMode
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.navigation.StationShell
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Test-only window comparison: no preference, route, telemetry or hardware writes. */
class StationHeaderInsetsUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun landscapeHeaderWithAndWithoutSystemBars() = checkHeader(false)
    @Test fun portraitHeaderWithAndWithoutSystemBars() = checkHeader(true)

    private fun checkHeader(portrait: Boolean) {
        val previousOrientation = compose.activity.requestedOrientation
        try {
            compose.activityRule.scenario.onActivity {
                it.requestedOrientation = if (portrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == if (portrait) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE }
            var theme by mutableStateOf(ThemeMode.DARK)
            compose.setContent { MamaGcsTheme(theme) { StationShell(VehicleState(), "general", {}, {}) {} } }
            for (mode in listOf(ThemeMode.DARK, ThemeMode.LIGHT)) {
                compose.runOnIdle { theme = mode }
                for (visible in listOf(true, false)) {
                    compose.runOnIdle {
                        val controller = WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
                        if (visible) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
                    }
                    compose.waitUntil(10_000) {
                        ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.statusBars()) == visible
                    }
                    compose.onNodeWithTag("workspace-title").assertIsDisplayed().assertTextEquals("Application settings")
                    compose.onNodeWithTag("connection-shortcut").assertIsDisplayed()
                    compose.awaitScreenshotFrame(compose.activity.window.decorView)
                    val stability = StationScreenshotStability()
                    var screenshot: Bitmap? = null
                    try {
                        compose.waitUntil(10_000) {
                            val next = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                            if (next == null) false else if (stability.accepts(next, compose.activity.window.decorView)) {
                                screenshot = next; true
                            } else { next.recycle(); false }
                        }
                        val name = "phase114-${if (portrait) "portrait" else "landscape"}-${mode.name.lowercase()}-${if (visible) "bars" else "immersive"}.png"
                        File(compose.activity.getExternalFilesDir(null), name).outputStream().use {
                            assertTrue(requireNotNull(screenshot).compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                    } finally { screenshot?.recycle() }
                }
            }
        } finally {
            compose.activityRule.scenario.onActivity {
                WindowCompat.getInsetsController(it.window, it.window.decorView).show(WindowInsetsCompat.Type.systemBars())
                it.requestedOrientation = previousOrientation
            }
        }
    }
}
