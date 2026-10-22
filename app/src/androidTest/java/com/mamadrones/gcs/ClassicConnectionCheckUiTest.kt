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
        compose.onNodeWithText("5-second display window · receipt is not sensor validity. Read-only check; movement and emergency-stop commands remain unavailable.")
            .performScrollTo().assertIsDisplayed()
        capture("phase46-check-light-bottom")
        compose.runOnIdle {
            check.value = ClassicConnectionAssessment(ClassicCheckStage.WAITING_MAVLINK,
                socketOpen = true, freshBytes = true,
                streamDiagnosis = ClassicStreamDiagnosis.REJECTED_FRAME_CANDIDATES)
        }
        compose.onNodeWithTag("classic-check-status").assertTextEquals("BYTES ARRIVING · MAVLINK NOT CONFIRMED")
        compose.onNodeWithTag("classic-check-guidance").assertTextContains("Unsupported", substring = true)
        compose.onNodeWithTag("classic-stream-diagnosis")
            .assertTextContains("This cannot distinguish baud mismatch", substring = true)
        compose.runOnIdle {
            theme.value = ThemeMode.DARK
            check.value = ClassicConnectionAssessment(ClassicCheckStage.RECEIVING, true, true, true,
                listOf("Attitude", "Battery"), true, true)
        }
        compose.onNodeWithTag("classic-check-status").assertTextEquals("LIVE RECEIVE EVIDENCE")
        compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: Attitude, Battery")
        compose.onNodeWithTag("classic-check-rc-status").assertTextEquals("6. RC_CHANNELS from selected autopilot: FRESH")
        compose.onNodeWithTag("classic-check-guidance").assertTextContains("does not independently prove", substring = true)
        compose.onNodeWithTag("classic-check-status").performScrollTo().assertIsDisplayed()
        capture("phase46-check-dark-top")
        compose.onNodeWithText("5-second display window · receipt is not sensor validity. Read-only check; movement and emergency-stop commands remain unavailable.")
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
        val link = mutableStateOf(ClassicBluetoothUiState(link = ClassicLinkState(peer, true,
            ConnectionState(TransportStatus.OPEN, connectedAtEpochMillis = opened))))
        val vehicle = mutableStateOf(VehicleState())
        compose.setContent { MamaGcsTheme {
            ClassicBluetoothScreen(link.value, {}, {}, {}, {}, false, vehicle.value)
        } }
        compose.onNodeWithTag("classic-check-status").performScrollTo()
        compose.runOnIdle {
            val now = System.currentTimeMillis()
            vehicle.value = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
                lastHeartbeatAtEpochMillis = now, attitude = AttitudeState(lastUpdatedAtEpochMillis = now),
                mavlinkDiagnostics = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.CLASSIC,
                    startedAtEpochMillis = opened, receivedBytes = 100, lastBytesAtEpochMillis = now,
                    decodedMessages = 1, messageObservations = listOf(MavlinkMessageObservation(
                        ObservedMavlinkMessage.HEARTBEAT, decodedV2 = 1, accepted = 1,
                        lastDecodedAtEpochMillis = now, lastAcceptedAtEpochMillis = now))))
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("LIVE RECEIVE EVIDENCE").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("BYTE STREAM STALE").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { link.value = link.value.copy(link = link.value.link.copy(active = false)) }
        compose.onNodeWithTag("classic-check-status").assertTextEquals("SESSION CLOSED")
        compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: NOT CONFIRMED")
        compose.onNodeWithTag("classic-check-rc-status").assertTextEquals("6. RC_CHANNELS from selected autopilot: NOT CONFIRMED")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
