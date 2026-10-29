package com.mamadrones.gcs

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mamadrones.gcs.domain.model.ThemeMode
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.navigation.StationShell
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Repeated navigation in an in-memory shell; no app storage or transport is touched. */
class StationMenuRepeatUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun menuOpensAndNavigatesRepeatedlyInLandscape() = checkMenu(false)
    @Test fun menuOpensAndNavigatesRepeatedlyInPortrait() = checkMenu(true)

    private fun checkMenu(portrait: Boolean) {
        val previousOrientation = compose.activity.requestedOrientation
        try {
            compose.activityRule.scenario.onActivity {
                it.requestedOrientation = if (portrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            compose.waitUntil(10_000) {
                compose.activity.resources.configuration.orientation == if (portrait) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
            }
            var route by mutableStateOf("dashboard")
            var navigationCount = 0
            compose.setContent {
                MamaGcsTheme(ThemeMode.DARK) {
                    StationShell(VehicleState(), route, { route = it; navigationCount++ }, {}) {}
                }
            }
            repeat(12) { iteration ->
                val expected = if (iteration % 2 == 0) "more" else "general"
                compose.onNodeWithTag("station-menu").assertIsDisplayed().performClick()
                compose.onNodeWithTag("menu-$expected").assertIsDisplayed().performClick()
                compose.onNodeWithTag("menu-$expected").assertDoesNotExist()
                compose.runOnIdle {
                    assertEquals("Incorrect route at iteration $iteration", expected, route)
                    assertEquals("Duplicate or missing navigation at iteration $iteration", iteration + 1, navigationCount)
                }
                compose.onNodeWithTag("workspace-title").assertTextEquals(
                    if (expected == "more") "Rover configuration" else "Application settings"
                )
            }
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = previousOrientation }
        }
    }
}
