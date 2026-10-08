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
import com.mamadrones.gcs.data.transport.bluetooth.BleNotifyCharacteristic
import com.mamadrones.gcs.presentation.screens.VescDiscoveryUiState
import com.mamadrones.gcs.presentation.screens.VescBluetoothDiscoveryScreen
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class FoundationUiTest {
    @get:Rule val compose = createComposeRule()
    private val bleCharacteristic = BleNotifyCharacteristic("service", "characteristic", false)

    @Test fun libraryFailureAndCapacityAreExplainedWithoutHiddenActions() {
        compose.setContent {
            MamaGcsTheme(ThemeMode.DARK) {
                com.mamadrones.gcs.presentation.screens.MissionScreen(VehicleState(),
                    com.mamadrones.gcs.presentation.mission.MissionPlanUiState(loading = false,
                        libraryError = "That library route could not be deleted. It has been kept on this device.",
                        library = (1..MissionLibraryEntry.MAX_ENTRIES).map {
                            MissionLibraryEntry("route-$it", MissionDraft("Route $it"), it.toLong())
                        }), onAction = {})
            }
        }
        compose.onNodeWithTag("mission-library-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save to library").performScrollTo().performClick()
        compose.onNodeWithText("Library full. Delete a library route before saving another copy.").assertIsDisplayed()
        compose.onNodeWithText("Save copy").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("mission-library-error").performScrollTo().assertIsDisplayed()
    }

    @Test fun openWaypointDialogCannotConfirmWhileDraftIsSaving() {
        val plan = mutableStateOf(com.mamadrones.gcs.presentation.mission.MissionPlanUiState(loading = false))
        var dispatched = false
        compose.setContent {
            MamaGcsTheme(ThemeMode.DARK) {
                com.mamadrones.gcs.presentation.screens.MissionScreen(VehicleState(), plan.value,
                    onAction = { dispatched = true })
            }
        }
        compose.onNodeWithTag("mission-add").performScrollTo().performClick()
        compose.onNodeWithTag("mission-latitude").performTextReplacement("-35.36")
        compose.onNodeWithTag("mission-longitude").performTextReplacement("149.16")
        compose.onNodeWithTag("mission-confirm-waypoint").assertIsEnabled()
        compose.runOnIdle { plan.value = plan.value.copy(saving = true) }
        compose.onNodeWithTag("mission-confirm-waypoint").assertIsNotEnabled()
        compose.runOnIdle { org.junit.Assert.assertFalse(dispatched) }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("mission-confirm-waypoint").assertDoesNotExist()
    }

    @Test fun recoveryCleanupFailureAllowsSavingAnUnchangedDraft() {
        var action: com.mamadrones.gcs.presentation.mission.MissionPlanAction? = null
        compose.setContent {
            MamaGcsTheme(ThemeMode.DARK) {
                com.mamadrones.gcs.presentation.screens.MissionScreen(
                    VehicleState(),
                    com.mamadrones.gcs.presentation.mission.MissionPlanUiState(
                        loading = false, dirty = false, recoveryCleanupFailed = true),
                    onAction = { action = it })
            }
        }
        compose.onNodeWithTag("mission-save").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(
            com.mamadrones.gcs.presentation.mission.MissionPlanAction.Save, action) }
    }

    @Test fun connectedBleCanStartReceiveThroughAppNavigation() {
        var selected: BleNotifyCharacteristic? = null
        compose.setContent {
            MamaGcsTheme(ThemeMode.DARK) {
                MamaGcsApp(VehicleState(), SettingsUiState(), {},
                    vescDiscovery = VescDiscoveryUiState(gattConnected = true,
                        selectedDeviceName = "Test radio", notifyCharacteristics = listOf(bleCharacteristic)),
                    onBleStartMavlinkReceive = { selected = it })
            }
        }
        compose.onNodeWithTag("nav-more").performClick()
        compose.onNodeWithText("BLE MAVLink").performScrollTo().performClick()
        compose.onNodeWithTag("ble-receive-characteristic").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(bleCharacteristic, selected) }
    }

    @Test fun bleSubscriptionDoesNotClaimBytesOrEnableAnotherSubscription() {
        compose.setContent {
            MamaGcsTheme(ThemeMode.DARK) {
                VescBluetoothDiscoveryScreen(VescDiscoveryUiState(gattConnected = true,
                    selectedDeviceName = "Test radio", receivingFrom = bleCharacteristic,
                    notifyCharacteristics = listOf(bleCharacteristic, bleCharacteristic.copy(characteristicUuid = "other"))))
            }
        }
        compose.onNodeWithText("SUBSCRIBED · WAITING FOR BYTES").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("ble-receive-other").performScrollTo().assertIsNotEnabled()
    }

    @Test fun bleConnectingCanBeCancelled() {
        var cancelled = false
        compose.setContent {
            MamaGcsTheme(ThemeMode.DARK) {
                VescBluetoothDiscoveryScreen(VescDiscoveryUiState(gattConnecting = true),
                    onDisconnectGatt = { cancelled = true })
            }
        }
        compose.onNodeWithTag("ble-disconnect").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(cancelled) }
    }

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
        compose.onNodeWithText("NO MAVLINK SESSION").performScrollTo().assertIsDisplayed()
    }

    @Test fun diagnosticsRetainsClosedBleSessionWithoutShowingLiveVehicleData() {
        launch(vehicle = VehicleState(mavlinkDiagnostics = MavlinkDiagnostics(
            linkKind = TelemetryLinkKind.BLE, startedAtEpochMillis = 100,
            receivedChunks = 77, receivedBytes = 8888, checksumFailures = 3, signedPacketsRejected = 2)))
        compose.onNodeWithTag("nav-more").performClick()
        compose.onNodeWithText("Diagnostics").performScrollTo().performClick()
        compose.onNodeWithText("LAST SESSION · BLE · CLOSED").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("8888").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Signed packets rejected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Do not disable a live rover's signing", substring = true).assertExists()
        compose.onNodeWithText("■  EMERGENCY STOP · UNAVAILABLE").assertIsNotEnabled()
    }

    @Test fun bleScreenShowsDecoderCountersButNotAnotherTransportsHistory() {
        var kind by mutableStateOf(TelemetryLinkKind.BLE)
        compose.setContent {
            MamaGcsTheme(ThemeMode.DARK) {
                VescBluetoothDiscoveryScreen(diagnostics = MavlinkDiagnostics(
                    active = true, linkKind = kind, startedAtEpochMillis = 100, receivedBytes = 5432))
            }
        }
        compose.onNodeWithText("ACTIVE SESSION · BLE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("5432").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { kind = TelemetryLinkKind.UDP }
        compose.onNodeWithText("MAVLINK DECODER").assertDoesNotExist()
    }
    @Test fun initialDashboardDoesNotEnableStop() {
        launch()
        compose.onNodeWithText("NOT CONNECTED").assertIsDisplayed()
        compose.onNodeWithText("■  EMERGENCY STOP · UNAVAILABLE").assertIsNotEnabled()
        compose.onNodeWithTag("operation-map").assertIsDisplayed()
        compose.onNodeWithText("GROUND SPEED", substring = false).assertIsDisplayed()
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
        compose.onNodeWithText("BLE MAVLink").performScrollTo().performClick()
        compose.onNodeWithText("BLE MAVLink link").assertIsDisplayed()
        compose.onNodeWithText("READ-ONLY · NO VEHICLE COMMANDS").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Scan for BLE devices").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("‹ Back").performClick()
        compose.onNodeWithTag("nav-mission").performClick()
        compose.onNodeWithText("Import route file").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Export GPX").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tap a numbered point to edit it", substring = true).performScrollTo().assertIsDisplayed()
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
