package com.mamadrones.gcs.presentation.screens

import com.mamadrones.gcs.data.transport.bluetooth.BleNotifyCharacteristic
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class BleProfileReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val option = BleNotifyCharacteristic("000018f0-0000-1000-8000-00805f9b34fb",
        "0000fff1-0000-1000-8000-00805f9b34fb", false, 2, 3)
    private val connected = VescDiscoveryUiState(gattConnected = true, successfulConnections = 4,
        selectedDeviceName = "private remote name", gattError = "private error",
        notifyCharacteristics = listOf(option))
    private val idle = VescDiscoveryUiState()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun captureRequiresStableDiscoveredConnectionAndNoCompetingLinkOrTrial() = runTest(dispatcher) {
        val model = BleProfileReportViewModel(dispatcher)
        listOf(idle, connected.copy(successfulConnections = 0), connected.copy(gattConnecting = true),
            connected.copy(closing = true), connected.copy(scanning = true), connected.copy(subscribing = true),
            connected.copy(stabilityTrial = BleStabilityTrialUiState(BleStabilityTrialStatus.RUNNING))).forEach {
            model.capture(it, false, 0)
            assertNull(model.state.value.snapshot)
        }
        model.capture(connected, true, 0)
        assertNull(model.state.value.snapshot)
        model.capture(connected, false, 0)
        assertNotNull(model.state.value.snapshot)
        assertFalse(model.state.value.snapshot!!.contains("private"))
        assertFalse(model.state.value.reviewing)
    }

    @Test fun reviewAndPickerRequireAllLinksIdleButDoNotRequireLiveDiscovery() = runTest(dispatcher) {
        val model = BleProfileReportViewModel(dispatcher)
        model.capture(connected, false, 0)
        val blocked = listOf(connected, idle.copy(gattConnecting = true), idle.copy(closing = true),
            idle.copy(scanning = true), idle.copy(subscribing = true), idle.copy(receivingFrom = option),
            idle.copy(stabilityTrial = BleStabilityTrialUiState(BleStabilityTrialStatus.RUNNING)))
        blocked.forEach { model.review(it, false); assertFalse(model.state.value.reviewing) }
        model.review(idle, true)
        assertFalse(model.state.value.reviewing)
        model.review(idle, false)
        assertTrue(model.state.value.reviewing)
        blocked.forEach { assertFalse(model.chooseDestination(it, false)) }
        assertFalse(model.chooseDestination(idle, true))
        assertTrue(model.chooseDestination(idle, false))
    }

    @Test fun frozenSnapshotSurvivesDisconnectAndIgnoresDuplicateSaveAndRecaptureWhileBusy() = runTest(dispatcher) {
        val model = BleProfileReportViewModel(dispatcher)
        val options = mutableListOf(option)
        model.capture(connected.copy(notifyCharacteristics = options), false, 0)
        val frozen = model.state.value.snapshot!!
        options.clear()
        model.review(idle, false)
        assertTrue(model.chooseDestination(idle, false))
        model.capture(connected.copy(successfulConnections = 5), false, 1)
        model.discard()
        assertEquals(frozen, model.state.value.snapshot)
        var writes = 0
        val stream = ByteArrayOutputStream()
        model.save { writes++; stream }
        model.save { writes++; stream }
        model.closePreview()
        model.discard()
        advanceUntilIdle()
        assertEquals(1, writes)
        assertEquals(frozen, stream.toString("UTF-8"))
        assertNull(model.state.value.snapshot)
    }

    @Test fun closeRetainsSnapshotDiscardRemovesAndCancelDoesNotWrite() = runTest(dispatcher) {
        val model = BleProfileReportViewModel(dispatcher)
        model.capture(connected, false, 0)
        val frozen = model.state.value.snapshot
        assertFalse(model.chooseDestination(idle, false))
        model.review(idle, false)
        model.chooseDestination(idle, false)
        model.pickerCancelled()
        var opened = false
        model.save { opened = true; ByteArrayOutputStream() }
        advanceUntilIdle()
        assertFalse(opened)
        model.closePreview()
        assertEquals(frozen, model.state.value.snapshot)
        assertFalse(model.state.value.reviewing)
        model.discard()
        assertNull(model.state.value.snapshot)
    }

    @Test fun errorsRetainSnapshotHideProviderDetailsAndAllowRetry() = runTest(dispatcher) {
        val model = BleProfileReportViewModel(dispatcher)
        model.capture(connected, false, 0)
        model.review(idle, false)
        model.chooseDestination(idle, false)
        model.pickerFailed()
        assertFalse(model.state.value.choosingDestination)
        assertTrue(model.chooseDestination(idle, false))
        model.save { throw IOException("secret provider path") }
        advanceUntilIdle()
        assertNotNull(model.state.value.snapshot)
        assertTrue(model.state.value.message!!.contains("partial file"))
        assertFalse(model.state.value.message!!.contains("secret"))
        assertTrue(model.chooseDestination(idle, false))
        model.save { ByteArrayOutputStream() }
        advanceUntilIdle()
        assertNull(model.state.value.snapshot)
    }

    @Test fun processRecreationCannotSaveMissingCapture() = runTest(dispatcher) {
        val model = BleProfileReportViewModel(dispatcher)
        var opened = false
        model.save { opened = true; ByteArrayOutputStream() }
        advanceUntilIdle()
        assertFalse(opened)
        assertTrue(model.state.value.message!!.contains("Snapshot no longer available"))
    }
}
