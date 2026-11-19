package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.core.diagnostics.TelemetryEvidenceReport
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.screens.TelemetryReportActions
import com.mamadrones.gcs.presentation.screens.TelemetryReportState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class TelemetryReportActionsTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun parserCandidateExcerptIsReadableBeforeExplicitSave() {
        val vehicle = VehicleState(mavlinkDiagnostics = MavlinkDiagnostics(active = true,
            linkKind = TelemetryLinkKind.CLASSIC, startedAtEpochMillis = 1_000,
            checksumFailures = 5, overlappingRescanChecksumFailures = 3,
            pendingIncompleteCandidateBytes = 8))
        val report = TelemetryEvidenceReport.capture(vehicle, 2_000)
        val decoder = "DECODER TOTALS" + report.substringAfter("DECODER TOTALS")
            .substringBefore("SAMPLE RECEIPT TIMES")
        var saves = 0
        compose.setContent { MaterialTheme {
            TelemetryReportActions(TelemetryReportState(preview = decoder), {}, {}, { saves++ })
        } }
        compose.onNodeWithText("Checksum candidates inside rescan span: 3", substring = true).assertExists()
        compose.onNodeWithText("Checksum candidates outside rescan span: 2", substring = true).assertExists()
        compose.onNodeWithText("Pending incomplete candidate bytes at capture: 8", substring = true).assertExists()
        compose.onNodeWithText("Rescan-span overlap is parser provenance", substring = true).assertExists()
        compose.runOnIdle { assertEquals(0, saves) }
        val preview = compose.onNodeWithText("DECODER TOTALS", substring = true)
        for (name in listOf("phase205-parser-total.png", "phase205-parser-split.png")) {
            preview.performTouchInput { swipeUp() }
            val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            try {
                File(compose.activity.getExternalFilesDir(null), name).outputStream().use {
                    assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { screenshot.recycle() }
        }
    }

    @Test fun tcpTimelineExcerptIsReadableInExplicitPreview() {
        val vehicle = VehicleState(mavlinkDiagnostics = MavlinkDiagnostics(active = true,
            linkKind = TelemetryLinkKind.TCP, startedAtEpochMillis = 1_000,
            startedAtElapsedMillis = 100, timingHeartbeatAtElapsedMillis = 6_200,
            recentHeartbeatGaps = listOf(HeartbeatGapEvidence(200, 6_200, true, true))))
        val report = TelemetryEvidenceReport.capture(vehicle, 2_000, 6_201,
            listOf(TcpSocketTransition(TransportStatus.OPEN, 150)))
        val timeline = "TCP / HEARTBEAT EVENT TIMELINE AT CAPTURE" +
            report.substringAfter("TCP / HEARTBEAT EVENT TIMELINE AT CAPTURE").substringBefore("DECODER TOTALS")
        var saves = 0
        compose.setContent { MaterialTheme {
            TelemetryReportActions(TelemetryReportState(preview = timeline), {}, {}, { saves++ })
        } }
        compose.onNodeWithText("Heartbeat recovered: 1 ms before capture", substring = true).assertExists()
        compose.onNodeWithText("TCP OPEN: 6051 ms before capture", substring = true).assertExists()
        compose.runOnIdle { assertEquals(0, saves) }
        compose.onNodeWithText("TCP / HEARTBEAT EVENT TIMELINE AT CAPTURE", substring = true)
            .performTouchInput { swipeUp() }
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            File(compose.activity.getExternalFilesDir(null), "phase202-tcp-report-preview.png").outputStream().use {
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { screenshot.recycle() }
    }

    @Test fun previewRequiresExplicitSaveAction() {
        var saved = false
        compose.setContent { MaterialTheme {
            TelemetryReportActions(TelemetryReportState(preview = "Frozen test report"), {}, {}, { saved = true })
        } }
        compose.onNodeWithText("Frozen test report").assertIsDisplayed()
        compose.runOnIdle { assertFalse(saved) }
        compose.onNodeWithTag("save-telemetry-report").performClick()
        compose.runOnIdle { assertTrue(saved) }
    }

    @Test fun writingPreventsRecaptureAndHidesSaveDialog() {
        compose.setContent { MaterialTheme {
            TelemetryReportActions(TelemetryReportState(preview = "Frozen test report", writing = true), {}, {}, {})
        } }
        compose.onNodeWithTag("prepare-telemetry-report").assertIsNotEnabled()
        compose.onNodeWithTag("save-telemetry-report").assertDoesNotExist()
    }
}
