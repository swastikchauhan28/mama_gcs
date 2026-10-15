package com.mamadrones.gcs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mamadrones.gcs.data.local.datastore.BleStabilityRun
import com.mamadrones.gcs.presentation.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic screen states only; these tests never open a Bluetooth connection. */
class BleStabilityScreenTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun startRequiresConnectionRecoveredHistoryAndCompletedSave() {
        val state = mutableStateOf(VescDiscoveryUiState())
        var starts = 0
        compose.setContent { MaterialTheme {
            VescBluetoothDiscoveryScreen(state.value, onStartStabilityTrial = { starts++ })
        } }
        awaitContent()
        compose.onNodeWithTag("ble-stability-start").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(gattConnected = true) }
        compose.onNodeWithTag("ble-stability-start").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(stabilityHistoryReady = true, stabilityHistorySaving = true) }
        compose.onNodeWithTag("ble-stability-start").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(stabilityHistorySaving = false) }
        compose.onNodeWithTag("ble-stability-start").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, starts) }
    }

    @Test fun runningTrialOffersStopAndShowsRemainingTime() {
        var stopped = false
        compose.setContent { MaterialTheme {
            VescBluetoothDiscoveryScreen(VescDiscoveryUiState(stabilityTrial = BleStabilityTrialUiState(
                status = BleStabilityTrialStatus.RUNNING, elapsedMillis = 60_000)),
                onStopStabilityTrial = { stopped = true })
        } }
        awaitContent()
        compose.onNodeWithText("STABILITY TEST RUNNING · 09:00 LEFT").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("ble-stability-start").assertDoesNotExist()
        compose.onNodeWithTag("ble-stability-stop").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(stopped) }
    }

    @Test fun terminalStatesOfferResetAndStorageFailuresAreVisible() {
        val state = mutableStateOf(VescDiscoveryUiState())
        var resets = 0
        compose.setContent { MaterialTheme {
            VescBluetoothDiscoveryScreen(state.value, onResetStabilityTrial = { resets++ })
        } }
        awaitContent()
        for (status in listOf(BleStabilityTrialStatus.PASSED, BleStabilityTrialStatus.FAILED, BleStabilityTrialStatus.STOPPED)) {
            compose.runOnIdle { state.value = state.value.copy(stabilityTrial = BleStabilityTrialUiState(status)) }
            compose.onNodeWithTag("ble-stability-reset").performScrollTo().performClick()
        }
        compose.runOnIdle {
            assertEquals(3, resets)
            state.value = VescDiscoveryUiState(gattConnected = true, stabilityHistoryError = "Cannot save test result.")
        }
        compose.onNodeWithText("Cannot save test result.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("ble-stability-start").performScrollTo().assertIsNotEnabled()
    }

    @Test fun historyShowsOnlyLatestFiveIncludingInterruptedRun() {
        val runs = (1L..7L).map { BleStabilityRun(it, it + 1000, if (it == 7L) "INTERRUPTED" else "PASSED", 1000, "run-$it") }
        compose.setContent { MaterialTheme { VescBluetoothDiscoveryScreen(VescDiscoveryUiState(savedStabilityRuns = runs)) } }
        awaitContent()
        compose.onNodeWithText("run-7", substring = true).performScrollTo().assertTextContains("INTERRUPTED", substring = true)
        compose.onNodeWithText("run-3", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("run-2", substring = true).assertDoesNotExist()
    }

    private fun awaitContent() {
        compose.waitUntil(10_000) {
            runCatching { compose.onAllNodesWithText("BLE MAVLink link").fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false)
        }
    }
}
