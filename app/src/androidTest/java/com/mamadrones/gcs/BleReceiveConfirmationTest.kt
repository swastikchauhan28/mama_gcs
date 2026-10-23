package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.transport.bluetooth.BleNotifyCharacteristic
import com.mamadrones.gcs.data.transport.bluetooth.BleReceiveSelection
import com.mamadrones.gcs.domain.model.ThemeMode
import com.mamadrones.gcs.presentation.screens.VescBluetoothDiscoveryScreen
import com.mamadrones.gcs.presentation.screens.VescDiscoveryUiState
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Fake callbacks only: never connects to a device or writes a notification descriptor. */
class BleReceiveConfirmationTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()
    private val characteristic = BleNotifyCharacteristic("000018f0-0000-1000-8000-00805f9b34fb",
        "0000fff1-0000-1000-8000-00805f9b34fb", false, 2, 3)
    private val connected = VescDiscoveryUiState(gattConnected = true, successfulConnections = 4,
        selectedDeviceName = "Synthetic test radio", notifyCharacteristics = listOf(characteristic))

    @Test fun selectingAndCancellingNeverStartsReceive() {
        var calls = 0
        compose.setContent { MamaGcsTheme(ThemeMode.DARK) {
            VescBluetoothDiscoveryScreen(state = connected, onStartMavlinkReceive = { calls++ })
        } }
        openSelection()
        compose.onNodeWithText("Confirm BLE telemetry receive").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithTag("ble-cancel-receive").performClick()
        compose.onNodeWithTag("ble-confirm-receive").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun explicitConfirmationDispatchesExactConnectionAndCharacteristicOnce() {
        val requests = mutableListOf<BleReceiveSelection>()
        compose.setContent { MamaGcsTheme(ThemeMode.DARK) {
            VescBluetoothDiscoveryScreen(state = connected, onStartMavlinkReceive = { requests.add(it) })
        } }
        openSelection()
        compose.onNodeWithText("Instances: 2/3").assertExists()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "ble-receive-confirmation.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
        compose.onNodeWithTag("ble-confirm-receive").assertIsDisplayed().performClick()
        compose.onNodeWithTag("ble-confirm-receive").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(BleReceiveSelection(characteristic, 4)), requests) }
    }

    @Test fun linkChangesAndBusyStateDiscardPendingConfirmation() {
        val state = mutableStateOf(connected)
        val competing = mutableStateOf(false)
        var calls = 0
        compose.setContent { MamaGcsTheme(ThemeMode.DARK) {
            VescBluetoothDiscoveryScreen(state = state.value, onStartMavlinkReceive = { calls++ }, udpLinkOpen = competing.value)
        } }
        listOf(connected.copy(gattConnected = false), connected.copy(successfulConnections = 5),
            connected.copy(notifyCharacteristics = emptyList()), connected.copy(subscribing = true),
            connected.copy(receivingFrom = characteristic), connected.copy(closing = true),
            connected.copy(gattConnecting = true)).forEach { changed ->
            compose.runOnIdle { state.value = connected }
            openSelection()
            compose.runOnIdle { state.value = changed }
            compose.onNodeWithTag("ble-confirm-receive").assertDoesNotExist()
            compose.runOnIdle { state.value = connected }
            compose.onNodeWithTag("ble-confirm-receive").assertDoesNotExist()
        }
        openSelection()
        compose.runOnIdle { competing.value = true }
        compose.onNodeWithTag("ble-confirm-receive").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun leavingForegroundDiscardsPendingSelection() {
        var calls = 0
        compose.setContent { MamaGcsTheme(ThemeMode.DARK) {
            VescBluetoothDiscoveryScreen(state = connected, onStartMavlinkReceive = { calls++ })
        } }
        openSelection()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("ble-confirm-receive").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun unknownConnectionNumberCannotSelectReceive() {
        compose.setContent { MamaGcsTheme(ThemeMode.DARK) {
            VescBluetoothDiscoveryScreen(state = connected.copy(successfulConnections = 0))
        } }
        awaitScreen()
        compose.onNodeWithTag("ble-receive-${characteristic.characteristicUuid}").performScrollTo().assertIsNotEnabled()
    }

    private fun openSelection() {
        awaitScreen()
        compose.onNodeWithTag("ble-receive-${characteristic.characteristicUuid}").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("ble-confirm-receive").assertExists()
    }

    private fun awaitScreen() {
        compose.waitUntil(10_000) {
            runCatching { compose.onAllNodesWithText("BLE MAVLink link").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }
}
