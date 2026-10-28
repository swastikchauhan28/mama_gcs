package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.screens.TelemetryReportActions
import com.mamadrones.gcs.presentation.screens.TelemetryReportViewModel
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class TelemetryBatteryReportUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun previewShowsFrozenPerPackFreshnessWithoutSaving() {
        val vehicle = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            battery = BatteryState(percentage = 77, lastUpdatedAtElapsedMillis = 19_000),
            batteries = listOf(BatteryState(batteryId = 1, percentage = 83, lastUpdatedAtElapsedMillis = 10_000),
                BatteryState(batteryId = 2, percentage = 42, lastUpdatedAtElapsedMillis = 19_000)),
            mavlinkDiagnostics = MavlinkDiagnostics(active = true, startedAtEpochMillis = 1_000))
        val model = TelemetryReportViewModel(Dispatchers.Unconfined)
        var saves = 0
        compose.setContent { MaterialTheme {
            val state by model.state.collectAsState()
            TelemetryReportActions(state, { model.prepare(vehicle, 2_000, 20_001) }, model::dismiss, { saves++ })
        } }
        compose.onNodeWithTag("prepare-telemetry-report").performClick()
        compose.onNodeWithText("BATTERY RECEIPT FRESHNESS AT CAPTURE", substring = true).assertExists()
        compose.onNodeWithText("Pack 1 (BATTERY_STATUS): STALE", substring = true).assertExists()
        compose.onNodeWithText("Pack 2 (BATTERY_STATUS): FRESH", substring = true).assertExists()
        compose.onNodeWithTag("save-telemetry-report").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { assertEquals(0, saves) }
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            File(compose.activity.getExternalFilesDir(null), "phase96-report-preview.png").outputStream().use {
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { screenshot.recycle() }
        compose.onNodeWithText("Close preview").performClick()
        compose.runOnIdle { assertEquals(0, saves) }
    }
}
