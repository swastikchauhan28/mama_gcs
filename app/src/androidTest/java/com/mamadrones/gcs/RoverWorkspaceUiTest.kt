package com.mamadrones.gcs

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.local.datastore.LocalSettingsRepository
import com.mamadrones.gcs.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Emulator-only UI acceptance. No transport is opened and no rover command is sent. */
class RoverWorkspaceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun landscapeDaylightWorkspaceAndCategories() {
        val repository = LocalSettingsRepository(compose.activity)
        val previousTheme = runBlocking { repository.preferences.first().theme }
        val previousOrientation = compose.activity.requestedOrientation
        try {
            runBlocking { repository.setTheme(ThemeMode.LIGHT) }
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
            compose.onNodeWithTag("operation-map").assertIsDisplayed()
            compose.onNodeWithTag("rover-heading").assertIsDisplayed()
            val tools = compose.onNodeWithTag("operation-toolbar").fetchSemanticsNode().boundsInRoot
            listOf("map-center", "map-follow", "connection-shortcut").forEach { tag ->
                assertFalse("Operator tools must not overlap $tag", tools.overlaps(compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot))
            }
            capture("station-operate-landscape-light")
            compose.onNodeWithTag("station-menu").performClick()
            capture("station-main-menu")
            compose.onNodeWithTag("menu-more").performClick()
            compose.onNodeWithText("Rover summary").assertIsDisplayed()
            compose.onNodeWithTag("category-spray").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Pump, nozzles and application flow").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Start spray").performScrollTo().assertIsNotEnabled()
            capture("station-spray-landscape")
            compose.openWorkspace("general")
            compose.onNodeWithText("Appearance").assertIsDisplayed()
            capture("station-settings-landscape-light")
            compose.onNodeWithTag("category-settings").performScrollTo().performClick()
            compose.onNodeWithTag("udp-remote-host").performScrollTo().assertExists()
            compose.onNodeWithTag("category-map-settings").performScrollTo().performClick()
            compose.onNodeWithText("OFFLINE REGIONS").performScrollTo().assertIsDisplayed()
            compose.openWorkspace("diagnostics")
            compose.onNodeWithTag("category-telemetry").performScrollTo().performClick()
            compose.onNodeWithText("Vehicle measurements and receive ages").assertIsDisplayed()
            capture("station-analyze-landscape")
        } finally {
            runBlocking { repository.setTheme(previousTheme) }
            compose.activityRule.scenario.onActivity { it.requestedOrientation = previousOrientation }
        }
    }

    @Test fun planningTabsAndFilesRemainLocal() {
        val previous = compose.activity.requestedOrientation
        try {
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
            compose.openWorkspace("mission")
            compose.onNodeWithTag("mission-map").assertIsDisplayed()
            compose.selectMissionTab("Field outline")
            compose.onNodeWithTag("mission-add-fence-vertex").performScrollTo().assertIsDisplayed()
            compose.selectMissionTab("Review")
            compose.onNodeWithTag("mission-route-review").performScrollTo().assertIsDisplayed()
            compose.selectMissionTab("Route")
            compose.openMissionFiles()
            compose.onNodeWithText("Import route file").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Export GPX").performScrollTo().assertIsDisplayed()
            capture("station-plan-files-landscape")
            compose.onNodeWithTag("mission-file-menu").performClick()
            compose.selectMissionTab("Route")
            capture("station-plan-landscape")
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasText("Start mission"))
            compose.onNodeWithText("Start mission").assertIsNotEnabled()
            compose.onNodeWithTag("exit-workspace").performClick()
            compose.onNodeWithTag("operation-map").assertIsDisplayed()
        } finally { compose.activityRule.scenario.onActivity { it.requestedOrientation = previous } }
    }

    @Test fun portraitMapAndSafetyRemainAccessible() {
        val previous = compose.activity.requestedOrientation
        try {
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
            compose.onNodeWithTag("operation-map").assertIsDisplayed()
            compose.onNodeWithTag("show-instruments").performClick()
            compose.onNodeWithText("Rover instruments").assertIsDisplayed()
            compose.onNodeWithText("Close").performClick()
            capture("station-operate-portrait")
            compose.onNodeWithTag("safety-shortcut").performClick()
            compose.onNodeWithText("■  EMERGENCY STOP · UNAVAILABLE").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("Forward").performScrollTo().assertIsNotEnabled()
            compose.openWorkspace("general")
            compose.onNodeWithText("Appearance").assertIsDisplayed()
            capture("station-settings-portrait")
        } finally { compose.activityRule.scenario.onActivity { it.requestedOrientation = previous } }
    }

    private fun rotate(request: Int, expected: Int) {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = request }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == expected }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var bitmap: Bitmap? = null
        compose.waitUntil(10_000) { bitmap = instrumentation.uiAutomation.takeScreenshot(); bitmap != null }
        val image = requireNotNull(bitmap)
        try {
            val directory = requireNotNull(instrumentation.targetContext.getExternalFilesDir("qa")).apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { image.recycle() }
    }
}
