package com.mamadrones.gcs

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertFalse
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
            val first = compose.onNodeWithText("ROUTE PLAN").fetchSemanticsNode().boundsInRoot
            val last = compose.onNodeWithText("SETUP").fetchSemanticsNode().boundsInRoot
            val toolbar = Rect(first.left, minOf(first.top, last.top), last.right, maxOf(first.bottom, last.bottom))
            val center = compose.onNodeWithTag("map-center").fetchSemanticsNode().boundsInRoot
            assertFalse("Landscape toolbar $toolbar overlaps Center $center", toolbar.overlaps(center))
        } finally { compose.activityRule.scenario.onActivity { it.requestedOrientation = previous } }
    }
}
