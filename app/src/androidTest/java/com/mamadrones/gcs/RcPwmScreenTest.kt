package com.mamadrones.gcs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.screens.RcPwmContent
import org.junit.Rule
import org.junit.Test

/** Synthetic read-only fixtures: no radio, calibration or rover commands. */
class RcPwmScreenTest {
    @get:Rule val compose = createComposeRule()

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
        compose.onNodeWithTag("pwm-port-1").performScrollTo().performClick()
        compose.onNodeWithText("1701 µs").assertExists()
        compose.onNodeWithText("1501 µs").assertDoesNotExist()
    }
}
