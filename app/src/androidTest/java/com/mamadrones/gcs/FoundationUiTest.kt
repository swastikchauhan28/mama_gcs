package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.navigation.MamaGcsApp
import com.mamadrones.gcs.presentation.settings.SettingsUiState
import com.mamadrones.gcs.presentation.settings.ConnectionUiState
import com.mamadrones.gcs.data.transport.TransportSessionState
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class FoundationUiTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = requireNotNull(context.getExternalFilesDir("qa")).apply { mkdirs() }
        val file = File(directory, "$name-${context.resources.configuration.screenWidthDp}.png")
        compose.waitForIdle()
        // Native surfaces/window transitions can briefly make the system capture unavailable.
        var screenshot: Bitmap? = null
        compose.waitUntil(10_000) {
            screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            screenshot != null
        }
        val captured = requireNotNull(screenshot)
        try {
            file.outputStream().use { output -> captured.compress(Bitmap.CompressFormat.PNG, 100, output) }
        } finally {
            captured.recycle()
        }
    }
    private fun launch(vehicle: VehicleState = VehicleState(), connection: ConnectionUiState = ConnectionUiState()) {
        compose.setContent {
            var theme by remember { mutableStateOf(ThemeMode.DARK) }
            MamaGcsTheme(theme) {
                MamaGcsApp(vehicle, SettingsUiState(AppPreferences(theme), loading = false), { theme = it }, connection)
            }
        }
    }
    @Test fun connectedTelemetryDoesNotEnableCommands() {
        // Fixture only: it does not open a socket or send a vehicle command.
        launch(VehicleState(systemId = 1, componentId = 1, connectionStatus = VehicleConnectionState.CONNECTED,
            mode = "MANUAL", armed = false, speedMetersPerSecond = 0.0, headingDegrees = 352.4,
            position = GlobalPositionState(-35.3632621, 149.1652374, 584.1),
            gps = GpsState(fix = GpsFix.RTK_FIXED, satellites = 10, hdop = 1.2),
            battery = BatteryState(percentage = 100)))
        compose.onNodeWithText("Rover · SYS 1").assertIsDisplayed()
        compose.onNodeWithText("CONNECTED").assertIsDisplayed()
        compose.onNodeWithText("■  EMERGENCY STOP · UNAVAILABLE").assertIsNotEnabled()
        capture("operate-connected-fixture")
        compose.onNodeWithTag("nav-control").performClick()
        compose.onNodeWithText("Forward").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Arm", substring = false).assertIsNotEnabled()
    }

    @Test fun diagnosticsShowsSessionCounters() {
        launch(connection = ConnectionUiState(session = TransportSessionState(connection = ConnectionState(
            status = TransportStatus.OPEN, packetStatistics = PacketStatistics(receivedPackets = 1234, transmittedPackets = 0)))))
        compose.onNodeWithTag("nav-more").performClick()
        compose.onNodeWithText("Diagnostics").performScrollTo().performClick()
        compose.onNodeWithText("1234").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("NOT MEASURED").performScrollTo().assertIsDisplayed()
    }
    @Test fun initialDashboardDoesNotEnableStop() {
        launch()
        compose.onNodeWithText("NOT CONNECTED").assertIsDisplayed()
        compose.onNodeWithText("■  EMERGENCY STOP · UNAVAILABLE").assertIsNotEnabled()
        compose.onNodeWithTag("operation-map").assertIsDisplayed()
        compose.onNodeWithText("SPEED", substring = true).assertIsDisplayed()
        capture("dashboard-dark")
        compose.onNodeWithTag("vehicle-selector").performClick()
        compose.onNodeWithText("Vehicle selection").assertIsDisplayed()
    }
    @Test fun secondaryScreensAndThemeAreReachable() {
        launch()
        val destinations = listOf("Health" to "Vehicle health", "Motors" to "VESC drive system", "Spray" to "Pump, nozzles and application flow", "Hydraulic" to "Pump, valves and pressure", "Diagnostics" to "Vehicle and communication inspection", "Admin" to "Local users, pairing and configuration")
        destinations.forEach { (label, heading) ->
            compose.onNodeWithTag("nav-more").performClick()
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.onNodeWithText(heading).assertIsDisplayed()
            compose.onNodeWithText("‹ Back").performClick()
        }
        compose.onNodeWithTag("nav-more").performClick()
        compose.onNodeWithText("VESC Bluetooth").performScrollTo().performClick()
        compose.onNodeWithText("VESC Bluetooth discovery").assertIsDisplayed()
        compose.onNodeWithText("DISCOVERY ONLY · NO CONTROLLER CONNECTION").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Scan for BLE devices").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("‹ Back").performClick()
        compose.onNodeWithTag("nav-mission").performClick()
        compose.onNodeWithText("Import route file").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Export GPX").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("nav-more").performClick()
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithText("Light").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Light") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Light").assertIsSelected()
        capture("settings-light")
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Dark") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Dark").assertIsSelected()
    }
    @Test fun primaryScreensRemainNonOperational() {
        launch()
        compose.onNodeWithTag("nav-map").performClick()
        compose.onNodeWithTag("map-center").assertIsNotEnabled()
        compose.onNodeWithText("■  EMERGENCY STOP · UNAVAILABLE").assertIsDisplayed()
        capture("map-dark")
        compose.onNodeWithTag("nav-control").performClick()
        compose.onNodeWithText("Forward").performScrollTo().assertIsNotEnabled()
        capture("drive-dark")
        compose.onNodeWithTag("drive-safety-details").performScrollTo().performClick()
        compose.onNodeWithText("DRIVE SAFETY GATE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("nav-mission").performClick()
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasText("Start mission"))
        compose.onNodeWithText("Start mission").performScrollTo().assertIsNotEnabled()
    }
    @Test fun transportSettingsDoNotOpenWithoutASavedEndpoint() {
        launch()
        compose.onNodeWithTag("connection-shortcut").performClick()
        compose.onNodeWithText("UDP + MAVLINK SESSION").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("udp-open-socket").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("MAVLINK SECURITY").performScrollTo().assertIsDisplayed()
    }
}
