package com.mamadrones.gcs

import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.screens.TelemetryScreen
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import android.graphics.Bitmap
import java.io.File

class BatteryFreshnessUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun stalePackAndAggregateDoNotHideFreshPackAndRecover() {
        val now = SystemClock.elapsedRealtime()
        val state = mutableStateOf(VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            battery = BatteryState(percentage = 70, lastUpdatedAtElapsedMillis = now - 20_000),
            batteries = listOf(BatteryState(percentage = 80, batteryId = 1, lastUpdatedAtElapsedMillis = now - 20_000),
                BatteryState(percentage = 65, batteryId = 2, lastUpdatedAtElapsedMillis = now))))
        compose.setContent { MamaGcsTheme { TelemetryScreen(state.value) } }
        compose.onNodeWithText("Pack 2 (BATTERY_STATUS) remaining").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("STALE").assertCountEquals(2)
        compose.onAllNodesWithText("FRESH").assertCountEquals(1)
        compose.onNodeWithText("65 %").assertExists()
        compose.onNodeWithText("80 %").assertDoesNotExist()
        compose.onNodeWithText("70 %").assertDoesNotExist()
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            File(compose.activity.getExternalFilesDir(null), "phase94-battery-freshness.png").outputStream().use {
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { screenshot.recycle() }
        compose.runOnIdle { state.value = state.value.copy(batteries = listOf(
            state.value.batteries[0].copy(lastUpdatedAtElapsedMillis = SystemClock.elapsedRealtime()), state.value.batteries[1])) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("80 %").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("70 %").assertDoesNotExist()
    }
    @Test fun displayedPackExpiresWithoutAnotherVehicleUpdate() {
        val state = mutableStateOf(VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            batteries = listOf(BatteryState(percentage = 80, batteryId = 1))))
        compose.setContent { MamaGcsTheme { TelemetryScreen(state.value) } }
        // Set the sample after the screen is ready so slow emulator startup cannot consume its window.
        compose.runOnIdle { state.value = state.value.copy(batteries = listOf(
            state.value.batteries.single().copy(lastUpdatedAtElapsedMillis = SystemClock.elapsedRealtime() - 7_000))) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("80 %").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("STALE").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("80 %").assertDoesNotExist()
    }

}
