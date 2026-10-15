package com.mamadrones.gcs

import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.presentation.screens.BleStabilityTrialStatus
import com.mamadrones.gcs.presentation.screens.VescBluetoothDiscoveryViewModel
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in physical test: requires -e bleRemoteName T12_485. GATT inspection only;
 * never subscribes, writes a characteristic, or opens a MAVLink session. */
class BleRemoteStabilityAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: VescBluetoothDiscoveryViewModel

    @Test fun interruptActiveTrialByEndingProcess() {
        assumeTrue("Process-death test requires explicit opt-in",
            InstrumentationRegistry.getArguments().getString("bleInterruptTest") == "T12_485")
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            model = ViewModelProvider(it)[VescBluetoothDiscoveryViewModel::class.java]
        }
        compose.openWorkspace("vesc-discovery")
        compose.waitUntil(10_000) { model.state.value.stabilityHistoryReady }
        connectRemote()
        startTrial()
        progress("INTERRUPTING_ACTIVE_TRIAL=${model.state.value.stabilityTrial.startedAtEpochMillis}")
        // Deliberately ends instrumentation too. The separate recovery test checks the saved result.
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    @Test fun reopenedAppRecoversInterruptedTrialExactlyOnce() {
        val startedAt = InstrumentationRegistry.getArguments().getString("interruptedTrialStart")?.toLongOrNull()
        assumeTrue("Requires the timestamp from the explicit process-death test", startedAt != null)
        compose.activityRule.scenario.onActivity {
            model = ViewModelProvider(it)[VescBluetoothDiscoveryViewModel::class.java]
        }
        compose.openWorkspace("vesc-discovery")
        compose.waitUntil(10_000) { model.state.value.stabilityHistoryReady }
        val records = model.state.value.savedStabilityRuns.filter { it.startedAtEpochMillis == startedAt }
        assertEquals(1, records.size)
        assertEquals("INTERRUPTED", records.single().outcome)
        assertFalse(model.state.value.gattConnected)
        compose.onAllNodesWithText("App process ended while the test was running.", substring = true)[0]
            .performScrollTo().assertIsDisplayed()
        capture("ble-interrupted-recovered")
        progress("Recovered trial $startedAt exactly once as INTERRUPTED")
    }

    @Test fun remotePowerLossIsFailedAndSaved() {
        assumeTrue("Power-loss check requires separate explicit opt-in",
            InstrumentationRegistry.getArguments().getString("blePowerLossTest") == "T12_485")
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            model = ViewModelProvider(it)[VescBluetoothDiscoveryViewModel::class.java]
        }
        try {
            compose.openWorkspace("vesc-discovery")
            compose.waitUntil(10_000) { model.state.value.stabilityHistoryReady }
            connectRemote()
            startTrial()
            progress("READY FOR REMOTE POWER OFF")
            compose.waitUntil(180_000) { model.state.value.stabilityTrial.status != BleStabilityTrialStatus.RUNNING }
            awaitSaved(BleStabilityTrialStatus.FAILED)
            assertFalse(model.state.value.gattConnected)
            assertEquals(1L, model.state.value.unexpectedDisconnects)
            assertNotNull(model.state.value.savedStabilityRuns.last().disconnectReason)
            compose.onNodeWithText("STABILITY TEST FAILED · LINK DROPPED").performScrollTo().assertIsDisplayed()
            capture("ble-remote-power-loss")
            progress("Remote loss correctly saved as FAILED with one unexpected disconnect")
        } finally {
            compose.activityRule.scenario.onActivity {
                model.disconnectGatt()
                it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    @Test fun remoteStopBackgroundReconnectAndTenMinuteContinuity() {
        assumeTrue("Physical remote tests require explicit opt-in",
            InstrumentationRegistry.getArguments().getString("bleRemoteName") == "T12_485")
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            model = ViewModelProvider(it)[VescBluetoothDiscoveryViewModel::class.java]
        }
        try {
            compose.openWorkspace("vesc-discovery")
            compose.waitUntil(10_000) { model.state.value.stabilityHistoryReady }
            connectRemote()
            startTrial()
            SystemClock.sleep(2_000)
            compose.onNodeWithTag("ble-stability-stop").performScrollTo().performClick()
            awaitSaved(BleStabilityTrialStatus.STOPPED)
            assertTrue(model.state.value.gattConnected)
            assertEquals("Stopped manually", model.state.value.savedStabilityRuns.last().disconnectReason)
            progress("Manual stop and saved result passed")

            resetTrial()
            startTrial()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.waitUntil(10_000) { !model.state.value.gattConnected }
            awaitSaved(BleStabilityTrialStatus.STOPPED)
            assertEquals(BleStabilityTrialStatus.STOPPED, model.state.value.stabilityTrial.status)
            assertEquals("App moved to background", model.state.value.savedStabilityRuns.last().disconnectReason)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            progress("Background stop and saved reason passed")

            connectRemote()
            resetTrial()
            startTrial()
            val started = SystemClock.elapsedRealtime()
            for (minute in 1..10) {
                compose.waitUntil(65_000) {
                    model.state.value.stabilityTrial.status != BleStabilityTrialStatus.RUNNING ||
                        SystemClock.elapsedRealtime() - started >= minute * 60_000L
                }
                val state = model.state.value
                assertTrue("Remote dropped at minute $minute: ${state.lastDisconnectReason}", state.gattConnected)
                assertEquals(0L, state.unexpectedDisconnects)
                if (minute < 10) assertEquals(BleStabilityTrialStatus.RUNNING, state.stabilityTrial.status)
                progress("Foreground connection: $minute/10 minutes; unexpected drops: ${state.unexpectedDisconnects}")
            }
            awaitSaved(BleStabilityTrialStatus.PASSED)
            compose.onNodeWithText("STABILITY TEST PASSED · 10 MINUTES").performScrollTo().assertIsDisplayed()
            assertTrue(model.state.value.savedStabilityRuns.last().durationMillis >= 600_000L)
            assertEquals(0L, model.state.value.receivedNotifications)
            assertEquals(0L, model.state.value.receivedBytes)
            assertNull(model.state.value.receivingFrom)
            capture("ble-ten-minute-passed")
            compose.onNodeWithTag("ble-disconnect").performScrollTo().performClick()
            compose.waitUntil(10_000) { !model.state.value.gattConnected && !model.state.value.closing }
            connectRemote()
            assertEquals(3L, model.state.value.successfulConnections)
            assertEquals(0L, model.state.value.unexpectedDisconnects)
            capture("ble-three-successful-connections")
            progress("Ten-minute pass, three successful connections, zero unexpected drops")
        } finally {
            compose.activityRule.scenario.onActivity {
                model.disconnectGatt()
                it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun connectRemote() {
        compose.onNodeWithTag("vesc-ble-scan").performScrollTo().performClick()
        compose.waitUntil(20_000) { model.state.value.advertisements.any { it.name == "T12_485" } }
        val remote = model.state.value.advertisements.single { it.name == "T12_485" }
        compose.onNodeWithTag("ble-connect-${remote.key}").performScrollTo().performClick()
        compose.waitUntil(25_000) { model.state.value.gattConnected || model.state.value.gattError != null }
        assertTrue("GATT failed: ${model.state.value.gattError}", model.state.value.gattConnected)
        assertNull(model.state.value.receivingFrom)
    }

    private fun startTrial() {
        compose.onNodeWithTag("ble-stability-start").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10_000) { model.state.value.stabilityTrial.status == BleStabilityTrialStatus.RUNNING }
    }

    private fun resetTrial() {
        compose.onNodeWithTag("ble-stability-reset").performScrollTo().performClick()
    }

    private fun awaitSaved(status: BleStabilityTrialStatus) {
        compose.waitUntil(10_000) {
            val state = model.state.value
            state.stabilityTrial.status == status && !state.stabilityHistorySaving &&
                state.savedStabilityRuns.lastOrNull()?.startedAtEpochMillis == state.stabilityTrial.startedAtEpochMillis
        }
        assertNull(model.state.value.stabilityHistoryError)
        assertEquals(status.name, model.state.value.savedStabilityRuns.last().outcome)
    }

    private fun progress(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("stream", "$message\n") })
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { image.recycle() }
    }
}
