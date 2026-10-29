package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.transport.bluetooth.ClassicBluetoothPeer
import com.mamadrones.gcs.data.transport.bluetooth.ClassicLinkState
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.components.ClassicConnectionCheckPanel
import com.mamadrones.gcs.presentation.components.SubsystemCard
import com.mamadrones.gcs.presentation.dashboard.ConsolePanels
import com.mamadrones.gcs.presentation.screens.ClassicBluetoothScreen
import com.mamadrones.gcs.presentation.screens.ClassicBluetoothUiState
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic UI evidence only: no Bluetooth access or commands. */
class ClassicConnectionCheckUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun validatedRadioOnlyDoesNotClaimCubeTelemetry() {
        compose.setContent { MamaGcsTheme {
            Surface(Modifier.verticalScroll(rememberScrollState())) {
                ClassicConnectionCheckPanel(ClassicConnectionAssessment(
                    ClassicCheckStage.WAITING_MAVLINK, socketOpen = true, freshBytes = true,
                    streamDiagnosis = ClassicStreamDiagnosis.VALIDATED_RADIO_ONLY))
            }
        } }
        compose.onNodeWithTag("classic-check-status")
            .assertTextEquals("BYTES ARRIVING · CUBE MAVLINK NOT CONFIRMED")
        compose.onNodeWithText("VALIDATED RADIO STATUS ONLY").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("4. Autopilot heartbeat: NOT CONFIRMED").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("classic-check-rc-status").performScrollTo()
            .assertTextContains("NOT CONFIRMED", substring = true)
        capture("phase50-radio-only")
        compose.onNodeWithText("A checksum-valid RADIO_STATUS frame appeared", substring = true)
            .performScrollTo().assertIsDisplayed()
        capture("phase50-radio-only-detail")
    }

    @Test fun radioDecoderPanelLabelsRawValuesAsRadioOnly() {
        val sample = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.CLASSIC,
            startedAtEpochMillis = 100, receivedBytes = 18, validatedRadioStatusFrames = 1,
            latestRadioStatus = RadioStatusObservation(68, 68, 3, 4, 95, 100, 80, 1, 2, 1_000))
        compose.setContent { MamaGcsTheme {
            Surface(Modifier.verticalScroll(rememberScrollState())) {
                SubsystemCard(ConsolePanels.mavlinkDiagnostics(sample, 2_000))
            }
        } }
        compose.onNodeWithText("Validated RADIO_STATUS (radio only)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Radio RSSI / remote RSSI (raw)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("RADIO_STATUS is checksum-validated radio-only evidence", substring = true)
            .performScrollTo().assertIsDisplayed()
        capture("phase50-radio-decoder")
    }

    @Test fun stagesAndGuidanceRemainReadableInBothThemes() {
        val check = mutableStateOf(ClassicConnectionAssessment(ClassicCheckStage.WAITING_BYTES, socketOpen = true))
        val theme = mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { MamaGcsTheme(theme.value) {
            Surface(Modifier.verticalScroll(rememberScrollState())) { ClassicConnectionCheckPanel(check.value) }
        } }
        compose.onNodeWithTag("classic-check-status").assertTextEquals("SOCKET OPEN · WAITING FOR BYTES")
        compose.onNodeWithTag("classic-check-guidance").assertTextContains("SBUS into RCIN alone", substring = true)
        compose.onNodeWithTag("classic-check-rc-status").assertTextContains("NOT CONFIRMED", substring = true)
        capture("phase46-check-light-top")
        compose.onNodeWithText("5-second display window. Bytes, heartbeat, RC, GPS, position and attitude checks use elapsed time; other measurement checks use calendar time and may be affected by clock changes. Receipt is not sensor validity. Read-only check; movement and emergency-stop commands remain unavailable.")
            .performScrollTo().assertIsDisplayed()
        capture("phase46-check-light-bottom")
        compose.runOnIdle {
            check.value = ClassicConnectionAssessment(ClassicCheckStage.WAITING_MAVLINK,
                socketOpen = true, freshBytes = true,
                streamDiagnosis = ClassicStreamDiagnosis.REJECTED_FRAME_CANDIDATES)
        }
        compose.onNodeWithTag("classic-check-status").assertTextEquals("BYTES ARRIVING · CUBE MAVLINK NOT CONFIRMED")
        compose.onNodeWithTag("classic-check-guidance").assertTextContains("Radio-only status", substring = true)
        compose.onNodeWithTag("classic-stream-diagnosis").assertExists()
        compose.onNode(
            hasText("This cannot distinguish baud mismatch", substring = true) and
                hasAnyAncestor(hasTestTag("classic-stream-diagnosis")),
        ).performScrollTo().assertIsDisplayed()
        capture("phase49-diagnosis-light")
        compose.runOnIdle { theme.value = ThemeMode.DARK }
        compose.onNodeWithText("This cannot distinguish baud mismatch", substring = true)
            .performScrollTo().assertIsDisplayed()
        capture("phase49-diagnosis-dark")
        compose.runOnIdle {
            check.value = ClassicConnectionAssessment(ClassicCheckStage.RECEIVING, true, true, true,
                listOf("Attitude", "Battery"), true, true)
        }
        compose.onNodeWithTag("classic-check-status").assertTextEquals("LIVE RECEIVE EVIDENCE")
        compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: Attitude, Battery")
        compose.onNodeWithTag("classic-check-rc-status").assertTextEquals("6. RC_CHANNELS from selected autopilot: FRESH")
        compose.onNodeWithTag("classic-check-guidance").assertTextContains("does not independently prove", substring = true)
        compose.onNodeWithTag("classic-check-status").performScrollTo().assertIsDisplayed()
        capture("phase46-check-dark-top")
        compose.onNodeWithText("5-second display window. Bytes, heartbeat, RC, GPS, position and attitude checks use elapsed time; other measurement checks use calendar time and may be affected by clock changes. Receipt is not sensor validity. Read-only check; movement and emergency-stop commands remain unavailable.")
            .performScrollTo().assertIsDisplayed()
        capture("phase46-check-dark-bottom")
    }

    @Test fun heartbeatOnlyDoesNotClaimMeasurementReception() {
        compose.setContent { MamaGcsTheme {
            ClassicConnectionCheckPanel(ClassicConnectionAssessment(ClassicCheckStage.WAITING_MEASUREMENTS,
                true, true, true, freshDecodedMessage = true))
        } }
        compose.onNodeWithTag("classic-check-status").assertTextEquals("HEARTBEAT RECEIVED · WAITING FOR MEASUREMENTS")
        compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: NOT CONFIRMED")
        compose.onNodeWithTag("classic-check-rc-status").assertTextEquals("6. RC_CHANNELS from selected autopilot: NOT CONFIRMED")
    }

    @Test fun screenExpiresEvidenceWithoutIncomingUpdatesAndDisconnectClearsIt() {
        val peer = ClassicBluetoothPeer("00:11:22:33:44:55", "Synthetic HC-05")
        val opened = System.currentTimeMillis()
        val openedElapsed = android.os.SystemClock.elapsedRealtime()
        val link = mutableStateOf(ClassicBluetoothUiState(link = ClassicLinkState(peer, true,
            ConnectionState(TransportStatus.OPEN, connectedAtEpochMillis = opened, connectedAtElapsedMillis = openedElapsed))))
        val vehicle = mutableStateOf(VehicleState())
        compose.setContent { MamaGcsTheme {
            ClassicBluetoothScreen(link.value, {}, {}, {}, {}, false, vehicle.value)
        } }
        compose.onNodeWithTag("classic-check-status").performScrollTo()
        compose.runOnIdle {
            val now = System.currentTimeMillis()
            val elapsed = android.os.SystemClock.elapsedRealtime()
            vehicle.value = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
                lastHeartbeatAtEpochMillis = now, lastHeartbeatAtElapsedMillis = elapsed, attitude = AttitudeState(lastUpdatedAtEpochMillis = now, lastUpdatedAtElapsedMillis = elapsed),
                mavlinkDiagnostics = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.CLASSIC,
                    startedAtEpochMillis = opened, startedAtElapsedMillis = openedElapsed, receivedBytes = 100, lastBytesAtEpochMillis = now, lastBytesAtElapsedMillis = elapsed,
                    decodedMessages = 1, messageObservations = listOf(MavlinkMessageObservation(
                        ObservedMavlinkMessage.HEARTBEAT, decodedV2 = 1, accepted = 1,
                        lastDecodedAtEpochMillis = now, lastAcceptedAtEpochMillis = now,
                        lastDecodedAtElapsedMillis = elapsed, lastAcceptedAtElapsedMillis = elapsed),
                        MavlinkMessageObservation(ObservedMavlinkMessage.ATTITUDE, decodedV2 = 1, accepted = 1,
                            lastDecodedAtElapsedMillis = elapsed, lastAcceptedAtElapsedMillis = elapsed))))
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("LIVE RECEIVE EVIDENCE").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("BYTE STREAM STALE").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("other measurement checks use calendar time", substring = true).performScrollTo().assertIsDisplayed()
        capture("phase98-classic-clock-limits")
        compose.runOnIdle { link.value = link.value.copy(link = link.value.link.copy(active = false)) }
        compose.onNodeWithTag("classic-check-status").assertTextEquals("SESSION CLOSED")
        compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: NOT CONFIRMED")
        compose.onNodeWithTag("classic-check-rc-status").assertTextEquals("6. RC_CHANNELS from selected autopilot: NOT CONFIRMED")
    }

    @Test fun rcReceiptExpiresIndependentlyAndRecovers() {
        val peer = ClassicBluetoothPeer("00:11:22:33:44:55", "Synthetic HC-05")
        val opened = System.currentTimeMillis() - 10_000L
        val openedElapsed = android.os.SystemClock.elapsedRealtime() - 10_000L
        val link = ClassicBluetoothUiState(link = ClassicLinkState(peer, true,
            ConnectionState(TransportStatus.OPEN, connectedAtEpochMillis = opened, connectedAtElapsedMillis = openedElapsed)))
        val vehicle = mutableStateOf(VehicleState())
        compose.setContent { MamaGcsTheme { ClassicBluetoothScreen(link, {}, {}, {}, {}, false, vehicle.value) } }
        compose.onNodeWithTag("classic-check-status").performScrollTo()
        fun sample(rcAge: Long): VehicleState {
            val wall = System.currentTimeMillis()
            val elapsed = android.os.SystemClock.elapsedRealtime()
            val rcTime = elapsed - rcAge
            return VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
                lastHeartbeatAtEpochMillis = wall, lastHeartbeatAtElapsedMillis = elapsed,
                rcInput = RcInputState(lastUpdatedAtEpochMillis = wall, lastUpdatedAtElapsedMillis = rcTime),
                mavlinkDiagnostics = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.CLASSIC,
                    startedAtEpochMillis = opened, startedAtElapsedMillis = openedElapsed,
                    receivedBytes = 100, lastBytesAtElapsedMillis = elapsed, decodedMessages = 2,
                    messageObservations = listOf(
                        MavlinkMessageObservation(ObservedMavlinkMessage.HEARTBEAT, decodedV2 = 1, accepted = 1,
                            lastDecodedAtElapsedMillis = elapsed, lastAcceptedAtElapsedMillis = elapsed),
                        MavlinkMessageObservation(ObservedMavlinkMessage.RC_CHANNELS, decodedV2 = 1, accepted = 1,
                            lastDecodedAtElapsedMillis = rcTime, lastAcceptedAtElapsedMillis = rcTime))))
        }
        compose.runOnIdle { vehicle.value = sample(2_000L) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("6. RC_CHANNELS from selected autopilot: FRESH").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: RC input")
        compose.waitUntil(6_000) { compose.onAllNodesWithText("6. RC_CHANNELS from selected autopilot: NOT CONFIRMED").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("classic-check-rc-status").performScrollTo().assertIsDisplayed()
        capture("phase98-rc-expired")
        compose.runOnIdle { vehicle.value = sample(0L) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("6. RC_CHANNELS from selected autopilot: FRESH").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: RC input")
    }

    @Test fun positionReceiptUsesElapsedTimeAndExpiresIndependently() {
        val wall = mutableStateOf(20_000L)
        val elapsed = mutableStateOf(20_000L)
        val link = ConnectionState(TransportStatus.OPEN, connectedAtEpochMillis = 10_000, connectedAtElapsedMillis = 10_000)
        val heartbeat = MavlinkMessageObservation(ObservedMavlinkMessage.HEARTBEAT, decodedV2 = 1, accepted = 1,
            lastDecodedAtElapsedMillis = 20_000, lastAcceptedAtElapsedMillis = 20_000)
        val position = MavlinkMessageObservation(ObservedMavlinkMessage.GLOBAL_POSITION_INT, decodedV2 = 1, accepted = 1,
            lastDecodedAtElapsedMillis = 20_000, lastAcceptedAtElapsedMillis = 20_000)
        val state = mutableStateOf(VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            lastHeartbeatAtElapsedMillis = 20_000,
            position = GlobalPositionState(lastUpdatedAtEpochMillis = 20_000, lastUpdatedAtElapsedMillis = 20_000),
            mavlinkDiagnostics = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.CLASSIC,
                startedAtEpochMillis = 9_000, startedAtElapsedMillis = 9_000, receivedBytes = 10,
                lastBytesAtElapsedMillis = 20_000, decodedMessages = 2, messageObservations = listOf(heartbeat, position))))
        compose.setContent { MamaGcsTheme { Surface(Modifier.verticalScroll(rememberScrollState())) {
            ClassicConnectionCheckPanel(ClassicConnectionCheck.assess(true, link, state.value, wall.value, elapsed.value))
        } } }
        compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: Position")
        compose.runOnIdle { wall.value = Long.MAX_VALUE }
        compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: Position")
        compose.runOnIdle {
            elapsed.value = 25_001
            state.value = state.value.copy(lastHeartbeatAtElapsedMillis = 25_001,
                mavlinkDiagnostics = state.value.mavlinkDiagnostics.copy(lastBytesAtElapsedMillis = 25_001,
                    messageObservations = listOf(heartbeat.copy(lastDecodedAtElapsedMillis = 25_001, lastAcceptedAtElapsedMillis = 25_001), position)))
        }
        compose.onNodeWithTag("classic-check-measurements").performScrollTo().assertTextEquals("5. Measurement messages: NOT CONFIRMED")
        capture("phase102-position-stale")
        compose.runOnIdle {
            state.value = state.value.copy(position = state.value.position.copy(lastUpdatedAtElapsedMillis = 25_001),
                mavlinkDiagnostics = state.value.mavlinkDiagnostics.copy(messageObservations = listOf(
                    heartbeat.copy(lastDecodedAtElapsedMillis = 25_001, lastAcceptedAtElapsedMillis = 25_001),
                    position.copy(lastDecodedAtElapsedMillis = 25_001, lastAcceptedAtElapsedMillis = 25_001))))
        }
        compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: Position")
    }

    @Test fun attitudeReceiptUsesElapsedTimeAndExpiresIndependently() {
        val wall = mutableStateOf(20_000L)
        val elapsed = mutableStateOf(20_000L)
        val link = ConnectionState(TransportStatus.OPEN, connectedAtEpochMillis = 10_000, connectedAtElapsedMillis = 10_000)
        val heartbeat = MavlinkMessageObservation(ObservedMavlinkMessage.HEARTBEAT, decodedV2 = 1, accepted = 1,
            lastDecodedAtElapsedMillis = 20_000, lastAcceptedAtElapsedMillis = 20_000)
        val attitude = MavlinkMessageObservation(ObservedMavlinkMessage.ATTITUDE, decodedV2 = 1, accepted = 1,
            lastDecodedAtElapsedMillis = 20_000, lastAcceptedAtElapsedMillis = 20_000)
        val state = mutableStateOf(VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            lastHeartbeatAtElapsedMillis = 20_000,
            attitude = AttitudeState(lastUpdatedAtEpochMillis = 20_000, lastUpdatedAtElapsedMillis = 20_000),
            mavlinkDiagnostics = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.CLASSIC,
                startedAtEpochMillis = 9_000, startedAtElapsedMillis = 9_000, receivedBytes = 10,
                lastBytesAtElapsedMillis = 20_000, decodedMessages = 2, messageObservations = listOf(heartbeat, attitude))))
        compose.setContent { MamaGcsTheme { Surface(Modifier.verticalScroll(rememberScrollState())) {
            ClassicConnectionCheckPanel(ClassicConnectionCheck.assess(true, link, state.value, wall.value, elapsed.value))
        } } }
        compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: Attitude")
        compose.runOnIdle { wall.value = Long.MAX_VALUE }
        compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: Attitude")
        compose.runOnIdle {
            elapsed.value = 25_001
            state.value = state.value.copy(lastHeartbeatAtElapsedMillis = 25_001,
                mavlinkDiagnostics = state.value.mavlinkDiagnostics.copy(lastBytesAtElapsedMillis = 25_001,
                    messageObservations = listOf(heartbeat.copy(lastDecodedAtElapsedMillis = 25_001, lastAcceptedAtElapsedMillis = 25_001), attitude)))
        }
        compose.onNodeWithTag("classic-check-measurements").performScrollTo().assertTextEquals("5. Measurement messages: NOT CONFIRMED")
        capture("phase103-attitude-stale")
        compose.runOnIdle {
            state.value = state.value.copy(attitude = state.value.attitude.copy(lastUpdatedAtElapsedMillis = 25_001),
                mavlinkDiagnostics = state.value.mavlinkDiagnostics.copy(messageObservations = listOf(
                    heartbeat.copy(lastDecodedAtElapsedMillis = 25_001, lastAcceptedAtElapsedMillis = 25_001),
                    attitude.copy(lastDecodedAtElapsedMillis = 25_001, lastAcceptedAtElapsedMillis = 25_001))))
        }
        compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: Attitude")
    }

    @Test fun gpsReceiptUsesElapsedTimeAndExpiresEvenWithoutFix() {
        val wall = mutableStateOf(20_000L)
        val elapsed = mutableStateOf(20_000L)
        val link = ConnectionState(TransportStatus.OPEN, connectedAtEpochMillis = 10_000, connectedAtElapsedMillis = 10_000)
        val heartbeat = MavlinkMessageObservation(ObservedMavlinkMessage.HEARTBEAT, decodedV2 = 1, accepted = 1,
            lastDecodedAtElapsedMillis = 20_000, lastAcceptedAtElapsedMillis = 20_000)
        val gps = MavlinkMessageObservation(ObservedMavlinkMessage.GPS_RAW_INT, decodedV2 = 1, accepted = 1,
            lastDecodedAtElapsedMillis = 20_000, lastAcceptedAtElapsedMillis = 20_000)
        val state = mutableStateOf(VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            lastHeartbeatAtElapsedMillis = 20_000,
            gps = GpsState(fix = GpsFix.NO_FIX, lastUpdatedAtEpochMillis = 20_000, lastUpdatedAtElapsedMillis = 20_000),
            mavlinkDiagnostics = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.CLASSIC,
                startedAtEpochMillis = 9_000, startedAtElapsedMillis = 9_000, receivedBytes = 10,
                lastBytesAtElapsedMillis = 20_000, decodedMessages = 2, messageObservations = listOf(heartbeat, gps))))
        compose.setContent { MamaGcsTheme { Surface(Modifier.verticalScroll(rememberScrollState())) {
            ClassicConnectionCheckPanel(ClassicConnectionCheck.assess(true, link, state.value, wall.value, elapsed.value))
        } } }
        compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: GPS")
        compose.runOnIdle { wall.value = Long.MAX_VALUE }
        compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: GPS")
        compose.runOnIdle {
            elapsed.value = 25_001
            state.value = state.value.copy(lastHeartbeatAtElapsedMillis = 25_001,
                mavlinkDiagnostics = state.value.mavlinkDiagnostics.copy(lastBytesAtElapsedMillis = 25_001,
                    messageObservations = listOf(heartbeat.copy(lastDecodedAtElapsedMillis = 25_001, lastAcceptedAtElapsedMillis = 25_001), gps)))
        }
        compose.onNodeWithTag("classic-check-measurements").performScrollTo().assertTextEquals("5. Measurement messages: NOT CONFIRMED")
        capture("phase104-gps-stale")
        compose.runOnIdle {
            state.value = state.value.copy(gps = state.value.gps.copy(lastUpdatedAtElapsedMillis = 25_001),
                mavlinkDiagnostics = state.value.mavlinkDiagnostics.copy(messageObservations = listOf(
                    heartbeat.copy(lastDecodedAtElapsedMillis = 25_001, lastAcceptedAtElapsedMillis = 25_001),
                    gps.copy(lastDecodedAtElapsedMillis = 25_001, lastAcceptedAtElapsedMillis = 25_001))))
        }
        compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: GPS")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
