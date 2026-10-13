package com.mamadrones.gcs

import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.components.RoverSpeedMonitorPanel
import com.mamadrones.gcs.presentation.components.RoverSpeedPanel
import com.mamadrones.gcs.presentation.screens.ControlScreen
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File

/** Synthetic read-only samples; never opens a radio or transmits a command. */
class RoverSpeedMonitorUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    private fun sample(speed: Double = 4.0, now: Long = System.currentTimeMillis()) = VehicleState(
        connectionStatus = VehicleConnectionState.CONNECTED,
        speedMetersPerSecond = speed, lastHeartbeatAtEpochMillis = now,
        kinematicsLastUpdatedAtEpochMillis = now)

    @Test fun belowAtAndAboveStatesAreExplicitWithoutSafeOrStoppedClaims() {
        val assessment = mutableStateOf(RoverSpeedMonitor.assess(sample(1.0, 10000), 10000))
        val theme = mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { MamaGcsTheme(theme.value) { Surface { RoverSpeedPanel(assessment.value) } } }
        compose.onNodeWithTag("rover-speed-status").assertTextEquals("BELOW 8 km/h · OBSERVED ONLY")
        compose.onNodeWithTag("rover-speed-value").assertTextEquals("3.60 km/h")
        compose.runOnIdle { assessment.value = RoverSpeedMonitor.assess(sample(8.0 / 3.6, 10000), 10000) }
        compose.onNodeWithTag("rover-speed-status").assertTextEquals("AT 8 km/h · OBSERVED ONLY")
        compose.runOnIdle { assessment.value = RoverSpeedMonitor.assess(sample(4.0, 10000), 10000) }
        compose.onNodeWithTag("rover-speed-status").assertTextEquals("ABOVE 8 km/h · WARNING")
        compose.onNodeWithTag("rover-speed-value").assertTextEquals("14.40 km/h")
        compose.onNodeWithText("Warning only · no automatic braking or speed limiting").assertExists()
        capture("phase27-speed-light")
        compose.runOnIdle { theme.value = ThemeMode.DARK }
        compose.onNodeWithTag("rover-speed-status").assertTextEquals("ABOVE 8 km/h · WARNING")
        capture("phase27-speed-dark")
    }

    @Test fun disconnectHidesSpeedAndDoesNotLeaveBelowLimitClaim() {
        val state = mutableStateOf(sample(1.0, 10000))
        compose.setContent { MamaGcsTheme { RoverSpeedPanel(RoverSpeedMonitor.assess(state.value, 10000), compact = true) } }
        compose.onNodeWithTag("rover-speed-value").assertTextEquals("3.60 km/h")
        compose.runOnIdle { state.value = state.value.copy(connectionStatus = VehicleConnectionState.DISCONNECTED) }
        compose.onNodeWithTag("rover-speed-status").assertTextEquals("SPEED UNAVAILABLE")
        compose.onNodeWithTag("rover-speed-value").assertTextEquals("UNKNOWN")
    }

    @Test fun timerExpiresReadingWithoutNewTelemetry() {
        val state = mutableStateOf(VehicleState())
        compose.setContent { MamaGcsTheme { RoverSpeedMonitorPanel(state.value, compact = true) } }
        compose.runOnIdle { state.value = sample() }
        // The clock is wall time, not the Compose animation clock.
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("ABOVE 8 km/h · WARNING").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("SPEED UNAVAILABLE").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("rover-speed-value").assertTextEquals("UNKNOWN")
    }

    @Test fun speedObservationDoesNotEnableDriveActions() {
        compose.setContent { MamaGcsTheme { ControlScreen(sample()) } }
        listOf("Forward", "Reverse", "Stop", "Arm", "Disarm", "Set mode", "Set speed").forEach {
            compose.onNodeWithText(it).assertIsNotEnabled()
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
