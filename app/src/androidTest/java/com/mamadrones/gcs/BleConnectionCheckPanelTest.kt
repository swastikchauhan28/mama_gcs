package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.components.BleConnectionCheckPanel
import com.mamadrones.gcs.presentation.components.ScreenBody
import com.mamadrones.gcs.presentation.screens.VescBluetoothDiscoveryScreen
import com.mamadrones.gcs.presentation.screens.VescDiscoveryUiState
import com.mamadrones.gcs.data.transport.bluetooth.BleNotifyCharacteristic
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Synthetic states; no scanner, GATT connection or subscription is opened. */
class BleConnectionCheckPanelTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun remoteOnlyScreenExplainsWhatCanBeTestedWithoutCube() {
        compose.setContent { MaterialTheme {
            VescBluetoothDiscoveryScreen(state = VescDiscoveryUiState(gattConnected = true,
                notifyCharacteristics = listOf(BleNotifyCharacteristic("service", "characteristic", false))))
        } }
        awaitPanel()
        compose.onNodeWithTag("ble-check-status").performScrollTo().assertTextEquals("REMOTE CONNECTED · RECEIVE NOT STARTED")
        compose.onNodeWithTag("ble-check-heartbeat").performScrollTo().assertTextContains("NOT CONFIRMED", substring = true)
        compose.onNodeWithTag("ble-check-guidance").performScrollTo().assertTextContains("without the Cube or receiver", substring = true)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = compose.awaitSystemScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "ble-connection-check.png").outputStream().use {
                org.junit.Assert.assertTrue("BLE connection screenshot could not be encoded",
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }

    @Test fun disconnectRemovesFreshHeartbeatClaim() {
        val state = mutableStateOf(BleConnectionAssessment(BleCheckStage.RECEIVING, true, true, true, true, true))
        compose.setContent { MaterialTheme { ScreenBody { BleConnectionCheckPanel(state.value) } } }
        awaitPanel()
        compose.onNodeWithTag("ble-check-heartbeat").performScrollTo().assertTextContains("FRESH", substring = true)
        compose.runOnIdle { state.value = BleConnectionAssessment(BleCheckStage.CLOSED) }
        compose.onNodeWithTag("ble-check-heartbeat").performScrollTo().assertTextContains("NOT CONFIRMED", substring = true)
        compose.onNodeWithTag("ble-check-status").performScrollTo().assertTextEquals("BLE DISCONNECTED")
    }

    @Test fun everyStageHasGuidanceAndNoAutomaticAction() {
        val state = mutableStateOf(BleConnectionAssessment(BleCheckStage.CLOSED))
        compose.setContent { MaterialTheme { ScreenBody { BleConnectionCheckPanel(state.value) } } }
        awaitPanel()
        BleCheckStage.entries.forEach { stage ->
            compose.runOnIdle { state.value = BleConnectionAssessment(stage) }
            compose.onNodeWithTag("ble-check-guidance").performScrollTo().assertIsDisplayed()
            compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        }
    }

    private fun awaitPanel() {
        compose.waitUntil(10_000) {
            runCatching { compose.onAllNodesWithTag("ble-connection-check").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }
}
