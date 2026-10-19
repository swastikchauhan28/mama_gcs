package com.mamadrones.gcs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.components.RcInputActivityPanel
import com.mamadrones.gcs.presentation.screens.RcPwmScreen
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Synthetic fixtures only; never opens a radio or sends vehicle commands. */
class RcInputActivityUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()
    private fun vehicle(at: Long = 10_000, value: Int = 1501) = VehicleState(
        systemId = 1, componentId = 1, connectionStatus = VehicleConnectionState.CONNECTED,
        lastHeartbeatAtEpochMillis = at,
        mavlinkDiagnostics = MavlinkDiagnostics(active = true, startedAtEpochMillis = 1_000),
        rcInput = RcInputState(channelCount = 1, channelsMicroseconds = listOf(value), lastUpdatedAtEpochMillis = at))

    @Test fun missingEvidenceDisablesStart() {
        compose.setContent { MaterialTheme { RcInputActivityPanel(VehicleState(), 10_000) } }
        compose.onNodeWithTag("rc-activity-start").assertIsNotEnabled()
        compose.onNodeWithTag("rc-activity-status").assertTextEquals("NOT STARTED")
        compose.onNodeWithTag("rc-activity-range-1").assertDoesNotExist()
    }

    @Test fun narrowLargeFontPanelRemainsScrollableInBothThemes() {
        val theme = mutableStateOf(ThemeMode.LIGHT)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.3f)) {
                MamaGcsTheme(theme.value) { Surface {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        RcInputActivityPanel(vehicle(), 10_000)
                    }
                } }
            }
        }
        compose.onNodeWithTag("rc-activity-start").performScrollTo().performClick()
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            compose.runOnIdle { theme.value = mode }
            compose.onNodeWithTag("rc-activity-status").performScrollTo().assertIsDisplayed()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(compose.activity.getExternalFilesDir(null), "rc-activity-${mode.name.lowercase()}.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithTag("rc-activity-range-18").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("rc-activity-range-1").performScrollTo()
                .assertTextEquals("RC 1: min 1501 · max 1501 · span 0")
        }
        compose.onNodeWithTag("rc-activity-stop").performScrollTo().performClick()
        compose.onNodeWithTag("rc-activity-clear").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("rc-activity-start").performScrollTo().assertIsEnabled()
    }

    @Test fun observationAccumulatesStopsAndClears() {
        val state = mutableStateOf(vehicle())
        val now = mutableStateOf(10_000L)
        compose.setContent { MaterialTheme { Surface { Column(Modifier.verticalScroll(rememberScrollState())) {
            RcInputActivityPanel(state.value, now.value)
        } } } }
        compose.onNodeWithTag("rc-activity-start").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("rc-activity-range-1").assertTextEquals("RC 1: min 1501 · max 1501 · span 0")
        compose.runOnIdle { now.value = 10_001; state.value = vehicle(10_001, 1899) }
        compose.onNodeWithTag("rc-activity-range-1").assertTextEquals("RC 1: min 1501 · max 1899 · span 398")
        compose.onNodeWithTag("rc-activity-stop").performScrollTo().performClick()
        compose.runOnIdle { now.value = 10_002; state.value = vehicle(10_002, 1100) }
        compose.onNodeWithTag("rc-activity-status").assertTextEquals("STOPPED · HISTORICAL RANGES")
        compose.onNodeWithTag("rc-activity-range-1").assertTextEquals("RC 1: min 1501 · max 1899 · span 398")
        compose.onNodeWithTag("rc-activity-clear").performScrollTo().performClick()
        compose.onNodeWithTag("rc-activity-range-1").assertDoesNotExist()
        compose.onNodeWithTag("rc-activity-start").performScrollTo().assertIsEnabled()
    }

    @Test fun changedSessionInterruptsAndLeavingPageDiscardsObservation() {
        val state = mutableStateOf(vehicle())
        val visible = mutableStateOf(true)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            if (visible.value) RcInputActivityPanel(state.value, 10_000)
        } } }
        compose.onNodeWithTag("rc-activity-start").performScrollTo().performClick()
        compose.runOnIdle { state.value = vehicle().copy(systemId = 2) }
        compose.onNodeWithTag("rc-activity-status").assertTextEquals("INTERRUPTED · HISTORICAL RANGES")
        compose.onNodeWithTag("rc-activity-start").assertDoesNotExist()
        compose.runOnIdle { state.value = vehicle() }
        compose.onNodeWithTag("rc-activity-status").assertTextEquals("INTERRUPTED · HISTORICAL RANGES")
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithTag("rc-activity-status").assertTextEquals("NOT STARTED")
        compose.onNodeWithTag("rc-activity-range-1").assertDoesNotExist()
    }

    @Test fun realScreenTimerInterruptsWithoutNewPackets() {
        val state = mutableStateOf(vehicle(System.currentTimeMillis()))
        compose.setContent { MaterialTheme { RcPwmScreen(state.value) } }
        compose.runOnIdle { state.value = vehicle(System.currentTimeMillis()) }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodes(hasTestTag("rc-activity-start") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("rc-activity-start").performScrollTo().performClick()
        compose.onNodeWithTag("rc-activity-status").assertTextEquals("OBSERVING · NOT CALIBRATION")
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("INTERRUPTED · HISTORICAL RANGES").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("rc-activity-range-1").assertTextEquals("RC 1: min 1501 · max 1501 · span 0")
    }
}
