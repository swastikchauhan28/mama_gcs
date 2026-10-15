package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.core.diagnostics.BleStabilityReport
import com.mamadrones.gcs.data.local.datastore.BleStabilityRun
import com.mamadrones.gcs.presentation.screens.BleStabilityReportActions
import com.mamadrones.gcs.presentation.screens.TelemetryReportState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic report UI only; does not scan, connect or command any hardware. */
class BleStabilityReportActionsTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun previewRequiresExplicitSaveAndCanBeDismissed() {
        var saves = 0
        var dismissals = 0
        compose.setContent { MaterialTheme {
            BleStabilityReportActions(TelemetryReportState(preview = "Saved test results"), true, {}, { dismissals++ }, { saves++ })
        } }
        awaitContent()
        compose.onNodeWithText("Saved test results").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, saves) }
        compose.onNodeWithTag("save-ble-stability-report").performClick()
        compose.onNodeWithTag("close-ble-stability-report").performClick()
        compose.runOnIdle { assertEquals(1, saves); assertEquals(1, dismissals) }
    }

    @Test fun unavailableHistoryDisablesPrepareAndSave() {
        val available = mutableStateOf(false)
        compose.setContent { MaterialTheme {
            BleStabilityReportActions(TelemetryReportState(preview = "Frozen report"), available.value, {}, {}, {})
        } }
        awaitContent()
        compose.onNodeWithTag("save-ble-stability-report").assertIsNotEnabled()
        compose.runOnIdle { available.value = true }
        compose.onNodeWithTag("save-ble-stability-report").assertIsEnabled()
    }

    @Test fun emptyOrBusyHistoryDoesNotOfferExport() {
        var prepares = 0
        compose.setContent { MaterialTheme {
            BleStabilityReportActions(TelemetryReportState(), false, { prepares++ }, {}, {})
        } }
        awaitContent()
        compose.onNodeWithTag("prepare-ble-stability-report").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, prepares) }
    }

    @Test fun fullReportPreviewKeepsSaveReachable() {
        val report = BleStabilityReport.capture(listOf(BleStabilityRun(1000, 601000, "PASSED", 600000, null)), 700000)
        compose.setContent { MaterialTheme {
            BleStabilityReportActions(TelemetryReportState(preview = report), true, {}, {}, {})
        } }
        awaitContent()
        compose.onNodeWithText(report).assertExists()
        compose.onNodeWithTag("save-ble-stability-report").assertIsDisplayed().assertIsEnabled()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "ble-report-preview.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { image.recycle() }
    }

    @Test fun pickerAndWriteHideDialogAndBlockRecapture() {
        val state = mutableStateOf(TelemetryReportState(preview = "Frozen report", choosingDestination = true))
        compose.setContent { MaterialTheme { BleStabilityReportActions(state.value, true, {}, {}, {}) } }
        awaitContent()
        compose.onNodeWithTag("prepare-ble-stability-report").assertIsNotEnabled()
        compose.onNodeWithTag("save-ble-stability-report").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(choosingDestination = false, writing = true) }
        compose.onNodeWithTag("prepare-ble-stability-report").assertIsNotEnabled()
        compose.onNodeWithTag("save-ble-stability-report").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(writing = false, message = "Save failed. Preview retained.") }
        compose.onNodeWithTag("save-ble-stability-report").assertIsEnabled()
    }

    private fun awaitContent() {
        compose.waitUntil(10_000) {
            runCatching { compose.onAllNodes(hasTestTag("prepare-ble-stability-report") or
                hasTestTag("save-ble-stability-report")).fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false)
        }
    }
}
