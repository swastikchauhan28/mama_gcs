package com.mamadrones.gcs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mamadrones.gcs.data.parameters.ParameterFileReader
import com.mamadrones.gcs.presentation.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HardwareReportScreenTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()
    private val snapshot = ParameterFileReader.read("SERVO1_FUNCTION,74".byteInputStream())

    @Test(timeout = 120_000) fun summaryExposesExplicitPrepareAction() {
        var prepared = false
        compose.setContent { MaterialTheme {
            ParameterReviewScreen(ParameterReviewState(snapshot), {}, {}, { null }, onPrepareReport = { prepared = true })
        } }
        compose.onNodeWithTag("parameter-review-list").performScrollToNode(hasTestTag("parameter-hardware-summary"))
        compose.onNodeWithTag("parameter-hardware-summary").performClick()
        compose.onNodeWithTag("parameter-review-list").performScrollToNode(hasTestTag("prepare-hardware-report"))
        compose.runOnIdle { assertFalse(prepared) }
        compose.onNodeWithTag("prepare-hardware-report").performClick()
        compose.runOnIdle { assertTrue(prepared) }
    }

    @Test(timeout = 120_000) fun previewRequiresExplicitSaveAndSupportsClose() {
        var saved = false
        var closed = false
        compose.setContent { MaterialTheme {
            ParameterReviewScreen(ParameterReviewState(snapshot, report = HardwareReportState(preview = "Frozen hardware report")),
                {}, {}, { null }, onSaveReport = { saved = true }, onDismissReport = { closed = true })
        } }
        compose.onNodeWithText("Frozen hardware report").assertExists()
        compose.runOnIdle { assertFalse(saved) }
        compose.onNodeWithTag("save-hardware-report").performClick()
        compose.runOnIdle { assertTrue(saved) }
        compose.onNodeWithTag("close-hardware-report").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test(timeout = 120_000) fun choosingDestinationLocksImportAndClearAndHidesPreview() {
        compose.setContent { MaterialTheme {
            ParameterReviewScreen(ParameterReviewState(snapshot,
                report = HardwareReportState(preview = "Frozen", choosingDestination = true)), {}, {}, { null })
        } }
        compose.onNodeWithTag("parameter-review-list").performScrollToNode(hasTestTag("parameter-import"))
        compose.onNodeWithTag("parameter-import").assertIsNotEnabled()
        compose.onNodeWithTag("parameter-clear").assertIsNotEnabled()
        compose.onNodeWithTag("save-hardware-report").assertDoesNotExist()
    }
}
