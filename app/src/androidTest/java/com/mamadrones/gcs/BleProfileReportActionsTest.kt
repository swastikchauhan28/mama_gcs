package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.core.diagnostics.BleProfileReport
import com.mamadrones.gcs.data.transport.bluetooth.BleNotifyCharacteristic
import java.io.File
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.mamadrones.gcs.domain.model.ThemeMode
import com.mamadrones.gcs.presentation.screens.BleProfileReportActions
import com.mamadrones.gcs.presentation.screens.BleProfileReportState
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Callback fixtures only: no Bluetooth access, document picker or provider writes. */
class BleProfileReportActionsTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun connectedCaptureRequiresSeparateDisconnectedReview() {
        val state = mutableStateOf(BleProfileReportState())
        val idle = mutableStateOf(false)
        var captures = 0
        var reviews = 0
        compose.setContent { MamaGcsTheme(ThemeMode.DARK) {
            BleProfileReportActions(state.value, !idle.value, idle.value,
                onCapture = { captures++; state.value = BleProfileReportState(snapshot = "Frozen report") },
                onReview = { reviews++ }, onClose = {}, onDiscard = {}, onSave = {})
        } }
        awaitContent()
        compose.onNodeWithTag("capture-ble-profile").performClick()
        compose.onNodeWithTag("review-ble-profile").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, captures); assertEquals(0, reviews); idle.value = true }
        compose.onNodeWithTag("capture-ble-profile").assertIsNotEnabled()
        compose.onNodeWithTag("review-ble-profile").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, reviews) }
    }

    @Test fun longPreviewKeepsSaveReachableAndReconnectionDisablesIt() {
        val idle = mutableStateOf(true)
        var saved = 0
        val option = BleNotifyCharacteristic("000018f0-0000-1000-8000-00805f9b34fb",
            "0000fff1-0000-1000-8000-00805f9b34fb", false, 2, 3)
        val report = BleProfileReport.capture(List(64) { option.copy(serviceInstanceId = it) }, 4, 0)
        compose.setContent { MamaGcsTheme(ThemeMode.DARK) {
            BleProfileReportActions(BleProfileReportState(snapshot = report, reviewing = true),
                false, idle.value, {}, {}, {}, {}, { saved++ })
        } }
        awaitContent()
        compose.onNodeWithTag("save-ble-profile").assertIsDisplayed().assertIsEnabled()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "ble-profile-preview.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
        compose.runOnIdle { idle.value = false }
        compose.onNodeWithTag("save-ble-profile").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, saved); idle.value = true }
        compose.onNodeWithTag("save-ble-profile").performClick()
        compose.runOnIdle { assertEquals(1, saved) }
    }

    @Test fun closeAndDiscardAreSeparateActions() {
        val state = mutableStateOf(BleProfileReportState())
        var closes = 0
        var discards = 0
        var saves = 0
        compose.setContent { MamaGcsTheme(ThemeMode.DARK) {
            BleProfileReportActions(state.value, false, true, {}, {},
                onClose = { closes++; state.value = state.value.copy(reviewing = false) },
                onDiscard = { discards++; state.value = BleProfileReportState() }, onSave = { saves++ })
        } }
        awaitContent()
        repeat(5) { cycle ->
            val snapshot = "snapshot $cycle"
            compose.runOnIdle { state.value = BleProfileReportState(snapshot = snapshot, reviewing = true) }
            awaitDisplayed("close-ble-profile")
            compose.onNodeWithTag("close-ble-profile").assertIsEnabled().performClick()
            // Dialog removal and Android window-focus restoration are not the same event.
            // Wait for the host window, not a fixed sleep or a retry of the click/assertions.
            awaitHostWindow()
            awaitDisplayed("discard-ble-profile")
            compose.onNodeWithTag("save-ble-profile").assertDoesNotExist()
            compose.onNodeWithTag("review-ble-profile").assertIsDisplayed().assertIsEnabled()
            compose.runOnIdle {
                assertEquals(snapshot, state.value.snapshot)
                assertFalse(state.value.reviewing)
                assertEquals(cycle + 1, closes)
                assertEquals(cycle, discards)
                assertEquals(0, saves)
            }
            compose.onNodeWithTag("discard-ble-profile").assertIsEnabled().performClick()
            compose.onNodeWithTag("review-ble-profile").assertDoesNotExist()
            compose.onNodeWithTag("discard-ble-profile").assertDoesNotExist()
            compose.runOnIdle {
                assertNull(state.value.snapshot)
                assertEquals(cycle + 1, discards)
                assertEquals(0, saves)
            }
        }
    }

    @Test fun pickerAndWriterHidePreviewAndBlockMutations() {
        val state = mutableStateOf(BleProfileReportState(snapshot = "snapshot", reviewing = true, choosingDestination = true))
        compose.setContent { MamaGcsTheme(ThemeMode.DARK) {
            BleProfileReportActions(state.value, true, true, {}, {}, {}, {}, {})
        } }
        awaitContent()
        listOf(state.value, state.value.copy(choosingDestination = false, writing = true)).forEach { busy ->
            compose.runOnIdle { state.value = busy }
            compose.onNodeWithTag("save-ble-profile").assertDoesNotExist()
            compose.onNodeWithTag("capture-ble-profile").assertIsNotEnabled()
            compose.onNodeWithTag("review-ble-profile").assertIsNotEnabled()
            compose.onNodeWithTag("discard-ble-profile").assertIsNotEnabled()
        }
    }

    private fun awaitContent() {
        compose.waitUntil(10_000) {
            runCatching { compose.onAllNodes(hasTestTag("capture-ble-profile") or hasTestTag("save-ble-profile"))
                .fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }

    private fun awaitHostWindow() {
        compose.waitUntil(10_000) {
            var ready = false
            compose.activityRule.scenario.onActivity { activity ->
                ready = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    activity.window.decorView.isShown && activity.window.decorView.hasWindowFocus()
            }
            ready
        }
        compose.waitForIdle()
    }

    private fun awaitDisplayed(tag: String) {
        compose.waitUntil(10_000) {
            try {
                compose.onNodeWithTag(tag).assertIsDisplayed()
                true
            } catch (_: AssertionError) { false }
            catch (_: IllegalStateException) { false }
        }
    }
}
