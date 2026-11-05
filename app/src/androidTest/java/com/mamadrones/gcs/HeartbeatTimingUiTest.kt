package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import com.mamadrones.gcs.domain.model.TransportStatus
import com.mamadrones.gcs.presentation.components.SubsystemCard
import com.mamadrones.gcs.presentation.dashboard.ConsolePanels
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File

/** In-memory fixture only; no telemetry socket, device data or vehicle commands. */
class HeartbeatTimingUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun ongoingGapAndInputEvidenceAreVisible() {
        val sample = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.TCP,
            startedAtElapsedMillis = 100, timingHeartbeatAtElapsedMillis = 100,
            lastBytesAtElapsedMillis = 5_102, completedHeartbeatGapsOverFiveSeconds = 2)
        compose.setContent { MamaGcsTheme { Surface { Column(Modifier.verticalScroll(rememberScrollState())) {
            SubsystemCard(ConsolePanels.heartbeatTiming(sample, 5_102, TransportStatus.OPEN))
        } } } }
        compose.onNodeWithText("HEARTBEAT TIMING").assertExists()
        compose.onNodeWithText("HEARTBEAT GAP IN PROGRESS").assertExists()
        compose.onNodeWithText("OPEN").assertExists()
        compose.onNodeWithText("Input after 5 seconds in current gap").assertExists()
        compose.onNodeWithText("OBSERVED").assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "heartbeat-timing-simulated.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
