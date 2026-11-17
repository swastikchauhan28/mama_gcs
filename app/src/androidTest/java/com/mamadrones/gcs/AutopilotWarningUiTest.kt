package com.mamadrones.gcs

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.VehicleConnectionState
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.domain.model.VehicleStatusText
import com.mamadrones.gcs.presentation.navigation.MamaGcsApp
import com.mamadrones.gcs.presentation.screens.AutopilotWarningBanner
import com.mamadrones.gcs.presentation.screens.DashboardScreen
import com.mamadrones.gcs.presentation.screens.DiagnosticsScreen
import com.mamadrones.gcs.presentation.settings.SettingsUiState
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic, read-only message display; no connection or Cube command is opened. */
class AutopilotWarningUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun operateWarningOpensRetainedHistoryThroughAppNavigation() {
        val receivedAt = System.currentTimeMillis()
        val state = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            statusTexts = listOf(
                VehicleStatusText(6, "Compass status normal", receivedAt),
                VehicleStatusText(2, "PreArm: Compass 1 not calibrated", receivedAt),
                VehicleStatusText(4, "GPS warning", receivedAt)))
        // The debug fixture hosts the production route without opening a transport.
        compose.setContent { MamaGcsTheme { Surface {
            MamaGcsApp(state, SettingsUiState(), onThemeSelected = {})
        } } }
        compose.onNodeWithTag("autopilot-warning-banner").assertIsDisplayed()
        compose.onNodeWithText("2 warning reports retained (last 50 messages)").assertIsDisplayed()
        compose.onNodeWithTag("autopilot-warning-banner").performClick()
        compose.onNodeWithText("Diagnostics", substring = false).assertExists()
        compose.onNodeWithText("CURRENT SESSION · 3 / 3 shown").assertExists()
        compose.onNodeWithText("PreArm: Compass 1 not calibrated", substring = true).assertExists()
        compose.onNodeWithTag("autopilot-warning-filter").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("CURRENT SESSION · 2 / 3 shown").assertExists()
        compose.onNodeWithText("Compass status normal", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("autopilot-message-search").performScrollTo().performTextInput("compass")
        compose.onNodeWithTag("autopilot-message-search").performImeAction()
        compose.onNodeWithText("CURRENT SESSION · 1 / 3 shown").assertExists()
        compose.onNodeWithText("PreArm: Compass 1 not calibrated", substring = true).assertExists()
        compose.onNodeWithText("GPS warning", substring = true).assertDoesNotExist()
        compose.onNodeWithText("CURRENT SESSION · 1 / 3 shown").performScrollTo().assertIsDisplayed()
        capture("phase193-warning-navigation")
        compose.onNodeWithText("PreArm: Compass 1 not calibrated", substring = true)
            .performScrollTo().assertIsDisplayed()
        capture("phase193-warning-result")
        compose.onNodeWithTag("autopilot-message-search-clear")
            .performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("CURRENT SESSION · 2 / 3 shown").assertExists()
    }

    @Test fun reportedCompassErrorIsVisibleAndOpensMessages() {
        val state = mutableStateOf(VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            statusTexts = listOf(VehicleStatusText(2, "PreArm: Compass 1 not calibrated", System.currentTimeMillis()))))
        var opened = 0
        compose.setContent { MamaGcsTheme { Surface { AutopilotWarningBanner(state.value) { opened++ } } } }
        compose.onNodeWithTag("autopilot-warning-banner").assertIsDisplayed()
        compose.onNodeWithText("Autopilot reported CRITICAL").assertIsDisplayed()
        compose.onNodeWithText("1 warning report retained (last 50 messages)").assertIsDisplayed()
        compose.onNodeWithText("PreArm: Compass 1 not calibrated").assertIsDisplayed()
        capture("phase188-autopilot-warning")
        compose.onNodeWithTag("autopilot-warning-banner").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
        compose.runOnIdle { state.value = state.value.copy(statusTexts = listOf(
            VehicleStatusText(4, "New warning", System.currentTimeMillis()),
            VehicleStatusText(2, "PreArm: Compass 1 not calibrated", System.currentTimeMillis()),
            VehicleStatusText(6, "Routine status", System.currentTimeMillis()),
            VehicleStatusText(2, "PreArm: Compass 1 not calibrated", System.currentTimeMillis()))) }
        compose.onNodeWithText("New warning").assertIsDisplayed()
        compose.onNodeWithText("3 warning reports retained (last 50 messages)").assertIsDisplayed()
        capture("phase192-warning-report-count")
        compose.onNodeWithText("Receive time unavailable", substring = true).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(statusTexts = emptyList()) }
        compose.onNodeWithTag("autopilot-warning-banner").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(statusTexts = listOf(
            VehicleStatusText(4, "Fresh session warning", System.currentTimeMillis()))) }
        compose.onNodeWithText("1 warning report retained (last 50 messages)").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(connectionStatus = VehicleConnectionState.DEGRADED) }
        compose.onNodeWithTag("autopilot-warning-banner").assertDoesNotExist()
    }

    @Test fun diagnosticsFilterAndHistoricalLabelKeepRetainedWarningVisible() {
        val state = mutableStateOf(VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            statusTexts = listOf(VehicleStatusText(6, "Routine status", System.currentTimeMillis()),
                VehicleStatusText(2, "PreArm: Compass 1 not calibrated", System.currentTimeMillis()))))
        compose.setContent { MamaGcsTheme { Surface { DiagnosticsScreen(state.value) } } }
        compose.onNodeWithText("CURRENT SESSION · 2 / 2 shown").assertExists()
        compose.onNodeWithText("Routine status", substring = true).assertExists()
        compose.onNodeWithTag("autopilot-warning-filter").performClick()
        compose.onNodeWithText("CURRENT SESSION · 1 / 2 shown").assertExists()
        compose.onNodeWithText("Routine status", substring = true).assertDoesNotExist()
        compose.onNodeWithText("PreArm: Compass 1 not calibrated", substring = true).assertExists()
        capture("phase189-warning-history")
        compose.runOnIdle { state.value = state.value.copy(connectionStatus = VehicleConnectionState.DISCONNECTED) }
        compose.onNodeWithText("LAST SESSION · HISTORICAL · 1 / 2 shown").assertExists()
        compose.onNodeWithText("PreArm: Compass 1 not calibrated", substring = true).assertExists()
    }

    @Test fun portraitDiagnosticsSearchAndWarningsFilterCanBeCleared() {
        val priorOrientation = compose.activity.requestedOrientation
        try {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            val state = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED, statusTexts = listOf(
                VehicleStatusText(6, "Compass status normal", System.currentTimeMillis()),
                VehicleStatusText(2, "PreArm: Compass 1 not calibrated", System.currentTimeMillis()),
                VehicleStatusText(4, "GPS warning", System.currentTimeMillis())))
            compose.setContent { MamaGcsTheme { Surface { DiagnosticsScreen(state) } } }
            compose.onNodeWithTag("autopilot-message-search").performTextInput("CoMpAsS")
            compose.onNodeWithTag("autopilot-message-search").performImeAction()
            compose.onNodeWithText("CURRENT SESSION · 2 / 3 shown").assertExists()
            compose.onNodeWithTag("autopilot-warning-filter").performClick()
            compose.onNodeWithText("CURRENT SESSION · 1 / 3 shown").assertExists()
            compose.onNodeWithText("Compass status normal", substring = true).assertDoesNotExist()
            compose.onNodeWithText("PreArm: Compass 1 not calibrated", substring = true).assertExists()
            capture("phase191-message-search-portrait")
            compose.onNodeWithTag("autopilot-message-search-clear").performClick()
            compose.onNodeWithText("CURRENT SESSION · 2 / 3 shown").assertExists()
            compose.onNodeWithTag("autopilot-message-search").performTextInput("motor")
            compose.onNodeWithText("CURRENT SESSION · 0 / 3 shown").assertExists()
            compose.onNodeWithText("No messages match the active filters.", substring = true).assertExists()
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = priorOrientation }
        }
    }

    @Test fun operatePlacesWarningBetweenMapAndInstruments() {
        val state = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            statusTexts = listOf(VehicleStatusText(2, "PreArm: Compass 1 not calibrated", System.currentTimeMillis()),
                VehicleStatusText(4, "GPS warning", System.currentTimeMillis()),
                VehicleStatusText(6, "Routine status", System.currentTimeMillis())))
        var destination: String? = null
        compose.setContent { MamaGcsTheme { Surface { DashboardScreen(state, onNavigate = { destination = it }) } } }
        assertWarningBetweenMapAndInstruments()
        compose.onNodeWithText("2 warning reports retained (last 50 messages)").assertIsDisplayed()
        capture("phase192-warning-report-count-landscape")
        compose.onNodeWithTag("autopilot-warning-banner").performClick()
        compose.runOnIdle { assertEquals("diagnostics", destination) }
    }

    @Test fun portraitOperateKeepsWarningVisibleWithoutCoveringInstruments() {
        val state = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            statusTexts = listOf(VehicleStatusText(2, "PreArm: Compass 1 not calibrated", System.currentTimeMillis()),
                VehicleStatusText(4, "GPS warning", System.currentTimeMillis()),
                VehicleStatusText(6, "Routine status", System.currentTimeMillis())))
        val priorOrientation = compose.activity.requestedOrientation
        try {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            compose.setContent { MamaGcsTheme { Surface { DashboardScreen(state, onNavigate = {}) } } }
            assertWarningBetweenMapAndInstruments()
            compose.onNodeWithText("2 warning reports retained (last 50 messages)").assertIsDisplayed()
            capture("phase192-warning-report-count-portrait")
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = priorOrientation }
        }
    }

    private fun assertWarningBetweenMapAndInstruments() {
        val map = compose.onNodeWithTag("operation-map").fetchSemanticsNode().boundsInRoot
        val banner = compose.onNodeWithTag("autopilot-warning-banner").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val instruments = compose.onNodeWithTag("rover-instrument-bar").fetchSemanticsNode().boundsInRoot
        assertTrue(map.bottom <= banner.top && banner.bottom <= instruments.top)
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
