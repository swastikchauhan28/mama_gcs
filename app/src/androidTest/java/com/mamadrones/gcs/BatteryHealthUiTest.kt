package com.mamadrones.gcs

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.screens.HealthScreen
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class BatteryHealthUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun healthDistinguishesRetainedIssueFromPerPackFreshnessAndLinkLoss() {
        val state = mutableStateOf(VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            battery = BatteryState(lastUpdatedAtEpochMillis = 1),
            batteries = listOf(BatteryState(batteryId = 1, chargeState = 3, lastUpdatedAtEpochMillis = 1),
                BatteryState(batteryId = 2, chargeState = 1, lastUpdatedAtEpochMillis = 1))))
        compose.setContent { MamaGcsTheme { HealthScreen(state.value) } }
        compose.runOnIdle {
            val now = SystemClock.elapsedRealtime()
            state.value = state.value.copy(battery = state.value.battery.copy(lastUpdatedAtElapsedMillis = now - 20_000),
                batteries = listOf(state.value.batteries[0].copy(lastUpdatedAtElapsedMillis = now - 20_000),
                    state.value.batteries[1].copy(lastUpdatedAtElapsedMillis = now)))
        }
        compose.waitUntil(6_000) { compose.onAllNodesWithText("FRESH").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Battery evidence (latest retained)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("REPORTED ISSUE").assertExists()
        compose.onNodeWithText("Battery pack 1 freshness").assert(hasText("STALE"))
        compose.onNodeWithText("Battery pack 2 freshness").assert(hasText("FRESH"))
        compose.onNodeWithText("System battery summary freshness").assert(hasText("STALE"))
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            File(compose.activity.getExternalFilesDir(null), "phase95-health-battery.png").outputStream().use {
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { screenshot.recycle() }
        compose.runOnIdle { state.value = state.value.copy(connectionStatus = VehicleConnectionState.DISCONNECTED) }
        compose.onNodeWithText("REPORTED ISSUE").assertExists()
        compose.onNodeWithText("Battery pack 1 freshness").assert(hasText("UNAVAILABLE"))
        compose.onNodeWithText("Battery pack 2 freshness").assert(hasText("UNAVAILABLE"))
        compose.onNodeWithText("System battery summary freshness").assert(hasText("UNAVAILABLE"))
        compose.runOnIdle { state.value = state.value.copy(connectionStatus = VehicleConnectionState.CONNECTED,
            batteries = listOf(state.value.batteries[0].copy(chargeState = 1, lastUpdatedAtElapsedMillis = SystemClock.elapsedRealtime()),
                state.value.batteries[1])) }
        compose.waitUntil(6_000) { compose.onAllNodesWithText("REPORTED ISSUE").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Battery evidence (latest retained)").assertExists()
        compose.waitUntil(6_000) { compose.onAllNodesWithText("FRESH").fetchSemanticsNodes().isNotEmpty() }
    }
}
