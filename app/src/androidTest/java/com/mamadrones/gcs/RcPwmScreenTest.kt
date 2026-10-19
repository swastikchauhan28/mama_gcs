package com.mamadrones.gcs

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.screens.RcPwmContent
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Synthetic read-only fixtures: no radio, calibration or rover commands. */
class RcPwmScreenTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun emptyMonitorExplainsMissingData() {
        compose.setContent { MaterialTheme { RcPwmContent(VehicleState(), 10_000) } }
        compose.onNodeWithText("RC & PWM monitor").assertExists()
        compose.onNodeWithText("NOT RECEIVED").assertExists()
        compose.onNodeWithText("PWM OUTPUTS NOT RECEIVED").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1500 µs").assertDoesNotExist()
    }

    @Test fun portSelectionDoesNotMixOutputs() {
        val state = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            lastHeartbeatAtEpochMillis = 10_000,
            mavlinkDiagnostics = MavlinkDiagnostics(active = true, startedAtEpochMillis = 1_000),
            pwmOutputs = listOf(PwmOutputState(0, listOf(1501), 10_000), PwmOutputState(1, listOf(1701), 10_000)))
        compose.setContent { MaterialTheme { RcPwmContent(state, 10_000) } }
        compose.onNodeWithText("1501 µs").assertExists()
        val target = compose.onNodeWithTag("pwm-port-1").performScrollTo()
        // Preserve evidence of the old single-axis scroll, which can leave the row offscreen.
        capture("pwm-before-page-scroll")
        selectPort(1)
        capture("pwm-port-1-selected")
        compose.onNodeWithText("1701 µs").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1501 µs").assertDoesNotExist()
        compose.onNodeWithTag("pwm-port-0").assertIsNotSelected()
        selectPort(0)
        compose.onNodeWithText("1501 µs").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1701 µs").assertDoesNotExist()
        target.assertIsNotSelected()
    }

    @Test fun narrowSelectorReachesLastPortAndSwitchesBackWithoutMixingValues() {
        val state = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            lastHeartbeatAtEpochMillis = 10_000,
            mavlinkDiagnostics = MavlinkDiagnostics(active = true, startedAtEpochMillis = 1_000),
            pwmOutputs = List(4) { PwmOutputState(it, listOf(1501 + it * 100), 10_000) })
        compose.setContent { MaterialTheme { Box(Modifier.width(200.dp)) { RcPwmContent(state, 10_000) } } }
        selectPort(3)
        compose.onNodeWithText("1801 µs").performScrollTo().assertIsDisplayed()
        for (value in listOf(1501, 1601, 1701)) compose.onNodeWithText("$value µs").assertDoesNotExist()
        selectPort(0)
        compose.onNodeWithText("1501 µs").performScrollTo().assertIsDisplayed()
        for (value in listOf(1601, 1701, 1801)) compose.onNodeWithText("$value µs").assertDoesNotExist()
        compose.onNodeWithTag("pwm-port-3").assertIsNotSelected()
    }

    private fun selectPort(port: Int) {
        val chip = compose.onNodeWithTag("pwm-port-$port")
        // performScrollTo handles the nearest scroll parent only. Bring the horizontal
        // selector row into the vertical page viewport before scrolling within the row.
        chip.onParent().performScrollTo().assertIsDisplayed()
        chip.performScrollTo().assertIsDisplayed().assertIsEnabled().performClick().assertIsSelected()
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
