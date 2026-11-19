package com.mamadrones.gcs

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import com.mamadrones.gcs.domain.model.TcpSocketTransition
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import com.mamadrones.gcs.domain.model.TransportStatus
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.screens.DiagnosticsScreen
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File

/** In-memory fixture only; no telemetry socket, device data or vehicle commands. */
class HeartbeatTimingUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun realDiagnosticsScreenRefreshesClockWhenTcpTransitionArrives() {
        val started = SystemClock.elapsedRealtime() - 1_000L
        val transitions = mutableStateOf(emptyList<TcpSocketTransition>())
        val vehicle = VehicleState(mavlinkDiagnostics = MavlinkDiagnostics(active = true,
            linkKind = TelemetryLinkKind.TCP, startedAtElapsedMillis = started))
        compose.setContent { MamaGcsTheme { Surface {
            DiagnosticsScreen(vehicle, tcpStatus = TransportStatus.OPEN, tcpTransitions = transitions.value)
        } } }
        compose.onNodeWithText("NO EVENTS OBSERVED").assertExists()
        compose.runOnIdle {
            transitions.value = listOf(TcpSocketTransition(TransportStatus.OPEN, SystemClock.elapsedRealtime()))
        }
        compose.onNodeWithText("TCP OPEN ·", substring = true).assertExists()
    }

    @Test fun socketAndHeartbeatEventsAreOrderedOnScreen() {
        compose.setContent { LinkHealthScenarioFixture(LinkHealthScenarios.recoveredLongGap) }
        compose.onNodeWithText("Recent TCP/heartbeat timeline").assertExists()
        compose.onNodeWithText("Heartbeat recovered · 1 ms ago").assertExists()
        compose.onNodeWithText("Heartbeat exceeded 5 seconds · 1101 ms ago").assertExists()
        compose.onNodeWithText("TCP OPEN · 6051 ms ago").assertExists()
        compose.onNodeWithText("TCP OPEN · 6051 ms ago").performScrollTo()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "tcp-heartbeat-timeline-simulated.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

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

    @Test fun delayedHeartbeatRecoveryShowsCompletedGapEvidence() {
        val scenario = mutableStateOf(LinkHealthScenarios.steadyHeartbeat)
        compose.setContent { LinkHealthScenarioFixture(scenario.value) }
        compose.onNodeWithText("ACCEPTED HEARTBEAT RECENT").assertExists()

        compose.runOnIdle { scenario.value = LinkHealthScenarios.delayedHeartbeat }
        compose.onNodeWithText("HEARTBEAT GAP IN PROGRESS").assertExists()
        compose.onNodeWithText("Link lost · Setup").assertExists()

        compose.runOnIdle { scenario.value = LinkHealthScenarios.recoveredLongGap }
        compose.onNodeWithText("Recovered heartbeat after long gap · no socket, saved data, or vehicle command").assertExists()
        compose.onNodeWithText("● Connected").assertExists()
        compose.onNodeWithText("ACCEPTED HEARTBEAT RECENT").assertExists()
        compose.onNodeWithText("Completed gaps over 5 seconds").assertExists()
        compose.onNodeWithText("Completed long gaps with input before recovery").assertExists()
        compose.onNodeWithText("Recent gap 1 timing").assertExists()
        compose.onNodeWithText("input OBSERVED · selected position NOT OBSERVED").assertExists()
        compose.onAllNodesWithText("6100 ms").assertCountEquals(2)
        compose.onNodeWithText("input OBSERVED · selected position NOT OBSERVED").performScrollTo()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "heartbeat-recovery-simulated.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
