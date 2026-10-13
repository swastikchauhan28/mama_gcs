package com.mamadrones.gcs

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Navigate through the visible station menu, not obsolete persistent navigation tabs. */
fun ComposeTestRule.openWorkspace(route: String) {
    if (route == "control") {
        onNodeWithTag("safety-shortcut").performClick()
    } else {
        val parent = when (route) {
            "health", "motors", "spray", "hydraulic", "parameter-review", "autopilot-info" -> "more"
            "settings", "classic-bluetooth", "vesc-discovery", "map-settings" -> "general"
            "telemetry", "rc-pwm", "message-inspector" -> "diagnostics"
            "map" -> "dashboard"
            else -> route
        }
        onNodeWithTag("station-menu").performClick()
        onNodeWithTag("menu-$parent").performClick()
        if (parent != route && route != "map") {
            onNodeWithTag("category-$route").performScrollTo().assertIsDisplayed().performClick()
        }
    }
}

/** Both layouts must explicitly state that stopping requires the physical safety system. */
fun ComposeTestRule.assertMonitoringOnlySafety() {
    onNode(
        hasText("MONITORING ONLY · App emergency stop unavailable · Use the physical safety system") or
            hasText("App stop unavailable · Physical safety required")
    ).assertIsDisplayed()
    onNodeWithTag("safety-shortcut").assertIsDisplayed().assertIsEnabled()
}

fun ComposeTestRule.openMissionFiles() {
    if (onAllNodesWithText("Close files").fetchSemanticsNodes().isEmpty()) {
        onNodeWithTag("mission-file-menu").performClick()
    }
}

fun ComposeTestRule.selectMissionTab(tab: String) {
    onNodeWithTag("mission-editor").performScrollToIndex(0)
    onNodeWithTag("mission-tab-$tab").performClick()
}
