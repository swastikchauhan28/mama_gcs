package com.mamadrones.gcs

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** A regression assertion for the observed landscape defect; do not weaken it to obtain green tests. */
class LandscapeOverlapTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun wideHeaderKeepsTitleAndConnectionOnOneRow() {
        val previous = compose.activity.requestedOrientation
        try {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            compose.onNodeWithTag("station-menu").performClick()
            compose.onNodeWithTag("menu-general").performClick()
            val title = compose.onNodeWithTag("workspace-title").assertTextEquals("Application settings")
                .fetchSemanticsNode().boundsInRoot
            val shortcut = compose.onNodeWithTag("connection-shortcut").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("vehicle-selector").assertIsDisplayed()
            val tolerance = 8 * compose.activity.resources.displayMetrics.density
            assertTrue("Wide header title and connection must share a row", abs(title.center.y - shortcut.center.y) <= tolerance)
        } finally { compose.activityRule.scenario.onActivity { it.requestedOrientation = previous } }
    }

    @Test fun actualLargeSystemFontKeepsWorkspaceTitleAndActionsReadable() {
        val scenario = compose.activityRule.scenario
        val previous = compose.activity.requestedOrientation
        try {
            scenario.onActivity { check(it.resources.configuration.fontScale >= 1.8f) { "System font scale must be >= 1.8" } }
            compose.onNodeWithTag("station-menu").performClick()
            compose.onNodeWithTag("menu-mission").performClick()
            for ((orientation, label) in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to "portrait",
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to "landscape")) {
                scenario.onActivity { it.requestedOrientation = orientation }
                val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                    Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
                compose.waitUntil(15_000) {
                    var ready = false
                    scenario.onActivity { ready = it.resources.configuration.orientation == expected && it.resources.configuration.fontScale >= 1.8f }
                    ready
                }
                assertHeader("Plan route")
                compose.onNodeWithTag("mission-map").assertIsDisplayed()
                val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                try {
                    File(compose.activity.getExternalFilesDir(null), "phase123-$label.png").outputStream().use {
                        check(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally { screenshot.recycle() }
            }
            compose.onNodeWithTag("station-menu").performClick()
            compose.onNodeWithTag("vehicle-selector").assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithText("Vehicle selection").assertIsDisplayed()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithTag("station-menu").performClick()
            compose.onNodeWithTag("menu-general").performClick()
            assertHeader("Application settings")
            compose.onNodeWithTag("connection-shortcut").performClick()
            compose.onNodeWithTag("workspace-title").assertTextEquals("Application settings")
        } finally { scenario.onActivity { it.requestedOrientation = previous } }
    }

    private fun assertHeader(expected: String) {
        val title = compose.onNodeWithTag("workspace-title").assertIsDisplayed().assertTextEquals(expected)
        val layouts = mutableListOf<TextLayoutResult>()
        title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("The workspace title must have a layout", layouts.single().lineCount > 0)
        repeat(layouts.single().lineCount) { line ->
            assertFalse("The workspace title must not be ellipsized", layouts.single().isLineEllipsized(line))
        }
        val bounds = title.fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("The complete workspace title must fit the window", bounds.left >= root.left && bounds.right <= root.right)
        compose.onNodeWithTag("station-menu").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("connection-shortcut").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("safety-shortcut").assertIsDisplayed().assertIsEnabled()
    }

    @Test fun operatorToolbarMustNotCoverCenterControl() {
        val previous = compose.activity.requestedOrientation
        try {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            val toolbar = compose.onNodeWithTag("operation-toolbar").fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("operation-map-viewport").fetchSemanticsNode().boundsInRoot
            // The QGC-inspired toolbar is now on the left of the map. Keep the native
            // provider attribution strip (bottom 32 dp) clear, not just Center/Follow.
            val attributionInset = 32 * compose.activity.resources.displayMetrics.density
            assertTrue("Toolbar must leave native attribution clear", toolbar.bottom <= viewport.bottom - attributionInset)
            val center = compose.onNodeWithTag("map-center").fetchSemanticsNode().boundsInRoot
            assertFalse("Landscape toolbar $toolbar overlaps Center $center", toolbar.overlaps(center))
            val follow = compose.onNodeWithTag("map-follow").fetchSemanticsNode().boundsInRoot
            assertFalse("Toolbar must not cover Follow", toolbar.overlaps(follow))
            compose.onNodeWithText("Plan", substring = false).assertIsDisplayed().performClick()
            compose.onNodeWithTag("mission-map").assertIsDisplayed()
        } finally { compose.activityRule.scenario.onActivity { it.requestedOrientation = previous } }
    }
}
