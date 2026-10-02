package com.mamadrones.gcs

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.local.datastore.LocalSettingsRepository
import com.mamadrones.gcs.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real Activity/Hilt/DataStore integration; no vehicle integration or fabricated telemetry. */
class SettingsPersistenceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun themeIsSavedAndSurvivesActivityRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = LocalSettingsRepository(context)
        val previous = runBlocking { repository.preferences.first().theme }
        val previousEndpoint = runBlocking { repository.preferences.first().udpEndpoint }
        val requested = if (previous == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT
        val label = if (requested == ThemeMode.LIGHT) "Light" else "Dark"
        try {
            compose.onNodeWithTag("nav-more").performClick()
            compose.onNodeWithText("Settings").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Loading preferences…").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithText(label).performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasText(label) and isSelected()).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(requested, runBlocking { repository.preferences.first().theme })
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText(label).assertIsSelected()
        } finally {
            runBlocking { repository.setTheme(previous) }
            runBlocking { repository.setUdpEndpoint(previousEndpoint) }
        }
    }

}
