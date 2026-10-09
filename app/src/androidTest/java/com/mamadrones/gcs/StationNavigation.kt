package com.mamadrones.gcs

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Navigate through the visible station menu, not obsolete persistent navigation tabs. */
fun ComposeTestRule.openWorkspace(route: String) {
    if (route == "control") {
        onNodeWithTag("safety-shortcut").performClick()
    } else {
        onNodeWithTag("station-menu").performClick()
        onNodeWithTag("menu-${if (route == "map") "dashboard" else route}").performClick()
    }
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
