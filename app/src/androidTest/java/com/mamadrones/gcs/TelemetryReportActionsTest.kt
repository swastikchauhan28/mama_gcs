package com.mamadrones.gcs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mamadrones.gcs.presentation.screens.TelemetryReportActions
import com.mamadrones.gcs.presentation.screens.TelemetryReportState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TelemetryReportActionsTest {
    @get:Rule val compose = createComposeRule()

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
