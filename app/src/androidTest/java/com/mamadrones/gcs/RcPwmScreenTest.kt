package com.mamadrones.gcs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.screens.RcPwmContent
import com.mamadrones.gcs.presentation.screens.RcPwmScreen
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Synthetic read-only fixtures: no radio, calibration or rover commands. */
class RcPwmScreenTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun emptyMonitorExplainsMissingData() {
        compose.setContent { MaterialTheme { RcPwmContent(VehicleState(), nowElapsedMillis = 10_000) } }
        compose.onNodeWithText("RC & PWM monitor").assertExists()
        compose.onNodeWithText("NOT RECEIVED").assertExists()
        compose.onNodeWithText("PWM OUTPUTS NOT RECEIVED").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1500 µs").assertDoesNotExist()
    }

    @Test fun portSelectionDoesNotMixOutputs() {
        val state = VehicleState(systemId = 1, componentId = 1, connectionStatus = VehicleConnectionState.CONNECTED,
            lastHeartbeatAtEpochMillis = 10_000, lastHeartbeatAtElapsedMillis = 10_000,
            mavlinkDiagnostics = MavlinkDiagnostics(active = true, startedAtEpochMillis = 1_000, startedAtElapsedMillis = 1_000,
                messageObservations = listOf(MavlinkMessageObservation(ObservedMavlinkMessage.HEARTBEAT, accepted = 1, lastAcceptedAtElapsedMillis = 10_000),
                    MavlinkMessageObservation(ObservedMavlinkMessage.SERVO_OUTPUT_RAW, accepted = 1, lastAcceptedAtElapsedMillis = 10_000))),
            pwmOutputs = listOf(PwmOutputState(0, listOf(1501), 10_000, 10_000), PwmOutputState(1, listOf(1701), 10_000, 10_000)))
        compose.setContent { MaterialTheme { RcPwmContent(state, nowElapsedMillis = 10_000) } }
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
        val state = VehicleState(systemId = 1, componentId = 1, connectionStatus = VehicleConnectionState.CONNECTED,
            lastHeartbeatAtEpochMillis = 10_000, lastHeartbeatAtElapsedMillis = 10_000,
            mavlinkDiagnostics = MavlinkDiagnostics(active = true, startedAtEpochMillis = 1_000, startedAtElapsedMillis = 1_000,
                messageObservations = listOf(MavlinkMessageObservation(ObservedMavlinkMessage.HEARTBEAT, accepted = 1, lastAcceptedAtElapsedMillis = 10_000),
                    MavlinkMessageObservation(ObservedMavlinkMessage.SERVO_OUTPUT_RAW, accepted = 1, lastAcceptedAtElapsedMillis = 10_000))),
            pwmOutputs = List(4) { PwmOutputState(it, listOf(1501 + it * 100), 10_000, 10_000) })
        compose.setContent { MaterialTheme { Box(Modifier.width(200.dp)) { RcPwmContent(state, nowElapsedMillis = 10_000) } } }
        selectPort(3)
        compose.onNodeWithText("1801 µs").performScrollTo().assertIsDisplayed()
        for (value in listOf(1501, 1601, 1701)) compose.onNodeWithText("$value µs").assertDoesNotExist()
        selectPort(0)
        compose.onNodeWithText("1501 µs").performScrollTo().assertIsDisplayed()
        for (value in listOf(1601, 1701, 1801)) compose.onNodeWithText("$value µs").assertDoesNotExist()
        compose.onNodeWithTag("pwm-port-3").assertIsNotSelected()
    }

    @Test fun rcValuesExpireAndRecoverWhenElapsedTimeAdvances() {
        val elapsed = mutableStateOf(10_000L)
        val heartbeat = MavlinkMessageObservation(ObservedMavlinkMessage.HEARTBEAT, accepted = 1, lastAcceptedAtElapsedMillis = 10_000)
        val rc = MavlinkMessageObservation(ObservedMavlinkMessage.RC_CHANNELS, accepted = 1, lastAcceptedAtElapsedMillis = 10_000)
        val state = mutableStateOf(VehicleState(systemId = 1, componentId = 1, connectionStatus = VehicleConnectionState.CONNECTED,
            lastHeartbeatAtEpochMillis = 10_000, lastHeartbeatAtElapsedMillis = 10_000,
            rcInput = RcInputState(1, listOf(1500), 99, 10_000, 10_000),
            mavlinkDiagnostics = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.CLASSIC,
                startedAtEpochMillis = 1_000, startedAtElapsedMillis = 1_000, messageObservations = listOf(heartbeat, rc))))
        compose.setContent { MaterialTheme { RcPwmContent(state.value, nowElapsedMillis = elapsed.value) } }
        compose.onNodeWithText("1500 µs").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            elapsed.value = 15_001
            state.value = state.value.copy(lastHeartbeatAtElapsedMillis = 15_001,
                mavlinkDiagnostics = state.value.mavlinkDiagnostics.copy(messageObservations = listOf(
                    heartbeat.copy(lastAcceptedAtElapsedMillis = 15_001), rc)))
        }
        compose.onNodeWithText("STALE · VALUES HIDDEN").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1500 µs").assertDoesNotExist()
        capture("phase99-rc-stale")
        compose.runOnIdle {
            state.value = state.value.copy(rcInput = state.value.rcInput.copy(lastUpdatedAtElapsedMillis = 15_001),
                mavlinkDiagnostics = state.value.mavlinkDiagnostics.copy(messageObservations = listOf(
                    heartbeat.copy(lastAcceptedAtElapsedMillis = 15_001), rc.copy(lastAcceptedAtElapsedMillis = 15_001))))
        }
        compose.onNodeWithText("1500 µs").performScrollTo().assertIsDisplayed()
    }

    @Test fun realRcDisplayTimerHidesValuesWithoutNewMessages() {
        val state = mutableStateOf(VehicleState())
        compose.setContent { MaterialTheme { RcPwmScreen(state.value) } }
        fun freshSample(): VehicleState {
            val elapsed = android.os.SystemClock.elapsedRealtime()
            val wall = System.currentTimeMillis()
            return VehicleState(systemId = 1, componentId = 1, connectionStatus = VehicleConnectionState.CONNECTED,
                lastHeartbeatAtEpochMillis = wall, lastHeartbeatAtElapsedMillis = elapsed,
                rcInput = RcInputState(1, listOf(1500), 99, wall, elapsed),
                mavlinkDiagnostics = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.CLASSIC,
                    startedAtEpochMillis = wall - 1_000, startedAtElapsedMillis = elapsed - 1_000,
                    messageObservations = listOf(
                        MavlinkMessageObservation(ObservedMavlinkMessage.HEARTBEAT, accepted = 1, lastAcceptedAtElapsedMillis = elapsed),
                        MavlinkMessageObservation(ObservedMavlinkMessage.RC_CHANNELS, accepted = 1, lastAcceptedAtElapsedMillis = elapsed))))
        }
        compose.onNodeWithText("RC inputs · RC_CHANNELS", substring = true, ignoreCase = true).performScrollTo()
        compose.runOnIdle { state.value = freshSample() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("1500 µs").fetchSemanticsNodes().isNotEmpty() }
        // No state changes: the actual screen ticker must hide the old value.
        compose.waitUntil(12_000) { compose.onAllNodesWithText("1500 µs").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("1500 µs").assertDoesNotExist()
        compose.runOnIdle { state.value = freshSample() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("1500 µs").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun pwmPortFreshnessExpiresIndependentlyAndRecovers() {
        val elapsed = mutableStateOf(10_000L)
        val heartbeat = MavlinkMessageObservation(ObservedMavlinkMessage.HEARTBEAT, accepted = 1, lastAcceptedAtElapsedMillis = 10_000)
        val pwm = MavlinkMessageObservation(ObservedMavlinkMessage.SERVO_OUTPUT_RAW, accepted = 1, lastAcceptedAtElapsedMillis = 10_000)
        val state = mutableStateOf(VehicleState(systemId = 1, componentId = 1, connectionStatus = VehicleConnectionState.CONNECTED,
            lastHeartbeatAtElapsedMillis = 10_000,
            mavlinkDiagnostics = MavlinkDiagnostics(active = true, startedAtElapsedMillis = 1_000, messageObservations = listOf(heartbeat, pwm)),
            pwmOutputs = listOf(PwmOutputState(0, listOf(1501), 10_000, 10_000), PwmOutputState(1, listOf(1701), 10_000, 10_000))))
        compose.setContent { MaterialTheme { RcPwmContent(state.value, nowElapsedMillis = elapsed.value) } }
        compose.onNodeWithText("1501 µs").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            elapsed.value = 15_001
            state.value = state.value.copy(lastHeartbeatAtEpochMillis = Long.MAX_VALUE, lastHeartbeatAtElapsedMillis = 15_001,
                mavlinkDiagnostics = state.value.mavlinkDiagnostics.copy(messageObservations = listOf(heartbeat.copy(lastAcceptedAtElapsedMillis = 15_001), pwm.copy(lastAcceptedAtElapsedMillis = 15_001))),
                pwmOutputs = listOf(state.value.pwmOutputs[0], PwmOutputState(1, listOf(1701), 0, 15_001)))
        }
        compose.onNodeWithText("1501 µs").assertDoesNotExist()
        compose.onNodeWithText("STALE · VALUES HIDDEN").performScrollTo().assertIsDisplayed()
        capture("phase101-pwm-stale")
        selectPort(1)
        compose.onNodeWithText("1701 µs").performScrollTo().assertIsDisplayed()
        selectPort(0)
        compose.runOnIdle { state.value = state.value.copy(pwmOutputs = listOf(PwmOutputState(0, listOf(1502), 0, 15_001), state.value.pwmOutputs[1])) }
        compose.onNodeWithText("1502 µs").performScrollTo().assertIsDisplayed()
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
