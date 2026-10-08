package com.mamadrones.gcs

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A regression assertion for the observed landscape defect; do not weaken it to obtain green tests. */
class LandscapeOverlapTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun operatorToolbarMustNotCoverCenterControl() {
        val previous = compose.activity.requestedOrientation
        try {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            val toolbar = compose.onNodeWithTag("operation-toolbar").fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("operation-map-viewport").fetchSemanticsNode().boundsInRoot
            assertTrue("Toolbar must be outside the map, including native attribution", toolbar.top >= viewport.bottom)
            val center = compose.onNodeWithTag("map-center").fetchSemanticsNode().boundsInRoot
            assertFalse("Landscape toolbar $toolbar overlaps Center $center", toolbar.overlaps(center))
            val follow = compose.onNodeWithTag("map-follow").fetchSemanticsNode().boundsInRoot
            assertFalse("Toolbar must not cover Follow", toolbar.overlaps(follow))
            compose.onNodeWithText("ROUTE PLAN").assertIsDisplayed().performClick()
            compose.onNodeWithTag("mission-map").assertIsDisplayed()
        } finally { compose.activityRule.scenario.onActivity { it.requestedOrientation = previous } }
    }
}
