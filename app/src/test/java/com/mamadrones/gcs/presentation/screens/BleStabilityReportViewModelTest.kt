package com.mamadrones.gcs.presentation.screens

import com.mamadrones.gcs.data.local.datastore.BleStabilityRun
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
class BleStabilityReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val source = VescDiscoveryUiState(stabilityHistoryReady = true,
        savedStabilityRuns = listOf(BleStabilityRun(1000, 601000, "PASSED", 600000, null)))
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun activeOrUnavailableHistoryCannotPrepareOrLaunchPicker() = runTest(dispatcher) {
        val blocked = listOf(source.copy(gattConnected = true), source.copy(gattConnecting = true),
            source.copy(scanning = true), source.copy(subscribing = true), source.copy(closing = true),
            source.copy(stabilityHistoryReady = false), source.copy(stabilityHistorySaving = true),
            source.copy(stabilityHistoryError = "error"), source.copy(savedStabilityRuns = emptyList()),
            source.copy(stabilityTrial = BleStabilityTrialUiState(BleStabilityTrialStatus.RUNNING)))
        blocked.forEach { input ->
            val model = BleStabilityReportViewModel(dispatcher)
            model.prepare(input, 700000)
            assertNull(model.state.value.preview)
            model.prepare(source, 700000)
            assertFalse(model.chooseDestination(input))
        }
    }

    @Test fun snapshotFrozenThroughHistoryChangesPickerAndWriteAndDuplicateSaveIgnored() = runTest(dispatcher) {
        val model = BleStabilityReportViewModel(dispatcher)
        model.prepare(source, 700000)
        val frozen = model.state.value.preview!!
        assertTrue(model.chooseDestination(source))
        assertFalse(model.chooseDestination(source))
        model.prepare(source.copy(savedStabilityRuns = emptyList()), 800000)
        val output = ByteArrayOutputStream()
        var writes = 0
        model.save { writes++; output }
        model.save { writes++; ByteArrayOutputStream() }
        model.prepare(source, 900000)
        model.dismiss()
        assertEquals(frozen, model.state.value.preview)
        advanceUntilIdle()
        assertEquals(1, writes)
        assertEquals(frozen, output.toString("UTF-8"))
        assertNull(model.state.value.preview)
        assertTrue(model.state.value.message!!.contains("saved"))
    }

    @Test fun cancellationAndPickerFailureRetainPreviewAndNeverWrite() = runTest(dispatcher) {
        val model = BleStabilityReportViewModel(dispatcher)
        model.prepare(source, 700000)
        val frozen = model.state.value.preview
        model.chooseDestination(source)
        model.pickerCancelled()
        var opened = false
        model.save { opened = true; ByteArrayOutputStream() }
        advanceUntilIdle()
        assertFalse(opened)
        assertEquals(frozen, model.state.value.preview)
        model.chooseDestination(source)
        model.pickerFailed()
        assertEquals(frozen, model.state.value.preview)
        assertFalse(model.state.value.choosingDestination)
        model.dismiss()
        assertNull(model.state.value.preview)
        assertEquals(1, source.savedStabilityRuns.size)
    }

    @Test fun failedWriteHidesProviderDetailsAndAllowsRetry() = runTest(dispatcher) {
        val model = BleStabilityReportViewModel(dispatcher)
        model.prepare(source, 700000)
        val frozen = model.state.value.preview
        model.chooseDestination(source)
        model.save { throw IOException("private provider path") }
        advanceUntilIdle()
        assertEquals(frozen, model.state.value.preview)
        assertTrue(model.state.value.message!!.contains("partial file"))
        assertFalse(model.state.value.message!!.contains("private provider path"))
        assertTrue(model.chooseDestination(source))
        model.save { ByteArrayOutputStream() }
        advanceUntilIdle()
        assertNull(model.state.value.preview)
    }

    @Test fun processRecreationDoesNotWriteWithoutSnapshot() = runTest(dispatcher) {
        val model = BleStabilityReportViewModel(dispatcher)
        var opened = false
        model.save { opened = true; ByteArrayOutputStream() }
        advanceUntilIdle()
        assertFalse(opened)
        assertTrue(model.state.value.message!!.contains("Snapshot no longer available"))
    }
}
