package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File

/** In-memory fixture only; no telemetry socket, device data or vehicle commands. */
class HeartbeatTimingUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun ongoingGapAndInputEvidenceAreVisible() {
        compose.setContent { LinkHealthScenarioFixture(LinkHealthScenarios.delayedHeartbeat) }
        compose.onNodeWithText("SIMULATED LINK-HEALTH FIXTURE").assertExists()
        compose.onNodeWithText("Delayed heartbeat with incoming bytes · no socket, saved data, or vehicle command").assertExists()
        compose.onNodeWithText("HEARTBEAT TIMING").assertExists()
        compose.onNodeWithText("HEARTBEAT GAP IN PROGRESS").assertExists()
        compose.onNodeWithText("Link lost · Setup").assertExists()
        compose.onNodeWithText("OPEN").assertExists()
        compose.onNodeWithText("Input after 5 seconds in current gap").assertExists()
        compose.onNodeWithText("OBSERVED").assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "heartbeat-timing-simulated.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun steadyHeartbeatAndSocketLossStayDistinct() {
        val scenario = mutableStateOf(LinkHealthScenarios.steadyHeartbeat)
        compose.setContent { LinkHealthScenarioFixture(scenario.value) }
        compose.onNodeWithText("Steady accepted heartbeat · no socket, saved data, or vehicle command").assertExists()
        compose.onNodeWithText("● Connected").assertExists()
        compose.onNodeWithText("ACCEPTED HEARTBEAT RECENT").assertExists()
        compose.onNodeWithText("OPEN").assertExists()

        compose.runOnIdle { scenario.value = LinkHealthScenarios.socketLoss }
        compose.onNodeWithText("Socket closed · no socket, saved data, or vehicle command").assertExists()
        compose.onNodeWithText("Disconnected · Connect").assertExists()
        compose.onNodeWithText("SESSION CLOSED OR NOT STARTED").assertExists()
    }
}
