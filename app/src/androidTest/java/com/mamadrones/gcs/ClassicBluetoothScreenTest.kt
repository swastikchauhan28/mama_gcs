package com.mamadrones.gcs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mamadrones.gcs.data.transport.bluetooth.ClassicBluetoothPeer
import com.mamadrones.gcs.data.transport.bluetooth.ClassicLinkState
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.screens.ClassicBluetoothScreen
import com.mamadrones.gcs.presentation.screens.ClassicBluetoothUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Fake UI state only: never accesses a radio or sends a vehicle command. */
class ClassicBluetoothScreenTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()
    private val peer = ClassicBluetoothPeer("00:11:22:33:44:55", "Test HC-05")

    @Test fun selectingDeviceRequiresExplicitConfirmation() {
        var chosen: ClassicBluetoothPeer? = null
        show(ClassicBluetoothUiState(peers = listOf(peer), loaded = true), onConnect = { chosen = it })
        compose.onNodeWithTag("classic-connect-${peer.address}").performScrollTo().performClick()
        compose.onNodeWithText("Connect to this remote?").assertIsDisplayed()
        compose.runOnIdle { assertNull(chosen) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertNull(chosen) }
        compose.onNodeWithTag("classic-connect-${peer.address}").performClick()
        compose.onNodeWithText("Start receive").performClick()
        compose.runOnIdle { assertEquals(peer, chosen) }
    }

    @Test fun anotherLinkPreventsConnection() {
        show(ClassicBluetoothUiState(peers = listOf(peer), loaded = true), otherLinkOpen = true)
        compose.onNodeWithTag("classic-connect-${peer.address}").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("ANOTHER LINK IS OPEN").performScrollTo().assertIsDisplayed()
    }

    @Test fun pendingConnectionCanBeCancelled() {
        var disconnected = false
        show(ClassicBluetoothUiState(link = ClassicLinkState(peer, true,
            ConnectionState(TransportStatus.CONNECTING))), onDisconnect = { disconnected = true })
        compose.onNodeWithTag("classic-refresh").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("classic-disconnect").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(disconnected) }
    }

    @Test fun disconnectedStateDoesNotBorrowAnotherLinksHeartbeat() {
        show(ClassicBluetoothUiState(link = ClassicLinkState(peer, false,
            ConnectionState(TransportStatus.ERROR, "Remote disconnected"))),
            vehicleConnection = VehicleConnectionState.CONNECTED)
        compose.onNodeWithTag("classic-check-status").performScrollTo().assertTextEquals("BLUETOOTH ERROR")
        compose.onNodeWithText("4. Autopilot heartbeat: NOT CONFIRMED").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("classic-disconnect").assertDoesNotExist()
        compose.onNodeWithTag("classic-refresh").performScrollTo().assertIsEnabled()
    }

    @Test fun classicNtripPromptKeepsHttpAndGgaOffUntilOperatorSelectsThem() {
        var starts = 0
        show(ClassicBluetoothUiState(), onNtripStart = { _, _, _ -> starts++; "No Cube heartbeat" })
        compose.onNodeWithTag("classic-ntrip-start").performScrollTo().performClick()
        compose.onNodeWithText("NTRIP remote host").assertIsDisplayed()
        compose.onNodeWithTag("classic-ntrip-gga").assertIsOff()
        compose.onNodeWithTag("classic-ntrip-url").performTextInput("http://example.test:2101/MOUNT")
        compose.onNodeWithTag("classic-ntrip-http-opt-in").assertIsOff()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, starts) }
        compose.onNodeWithTag("classic-ntrip-status").performScrollTo().assertTextEquals("NTRIP: Stopped")
    }

    private fun show(state: ClassicBluetoothUiState, otherLinkOpen: Boolean = false,
        vehicleConnection: VehicleConnectionState = VehicleConnectionState.DISCONNECTED,
        onConnect: (ClassicBluetoothPeer) -> Unit = {}, onDisconnect: () -> Unit = {},
        onNtripStart: (String, Boolean, Boolean) -> String? = { _, _, _ -> "No Cube heartbeat" }) {
        compose.setContent {
            MaterialTheme {
                ClassicBluetoothScreen(state, {}, {}, onConnect, onDisconnect,
                    otherLinkOpen, VehicleState(connectionStatus = vehicleConnection),
                    onNtripStart = onNtripStart)
            }
        }
    }
}
