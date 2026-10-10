package com.mamadrones.gcs.presentation.screens

import com.mamadrones.gcs.domain.model.VehicleState
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
class TelemetryReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun capturedSnapshotDoesNotChangeWhileChoosingOrWriting() = runTest(dispatcher) {
        val model = TelemetryReportViewModel(dispatcher)
        model.prepare(VehicleState(), 1_000)
        val frozen = model.state.value.preview!!
        assertTrue(model.chooseDestination())
        assertFalse(model.chooseDestination())
        model.prepare(VehicleState(), 2_000)
        assertEquals(frozen, model.state.value.preview)
        val output = ByteArrayOutputStream()
        model.save { output }
        assertTrue(model.state.value.writing)
        model.prepare(VehicleState(), 3_000)
        model.dismiss()
        assertEquals(frozen, model.state.value.preview)
        advanceUntilIdle()
        assertEquals(frozen, output.toString("UTF-8"))
        assertNull(model.state.value.preview)
        assertFalse(model.state.value.writing)
        assertTrue(model.state.value.message!!.contains("saved"))
    }

    @Test fun cancellationRetainsPreviewWithoutWriting() = runTest(dispatcher) {
        val model = TelemetryReportViewModel(dispatcher)
        model.prepare(VehicleState(), 1_000)
        model.chooseDestination()
        model.pickerCancelled()
        assertNotNull(model.state.value.preview)
        assertFalse(model.state.value.choosingDestination)
        var opened = false
        model.save { opened = true; ByteArrayOutputStream() }
        advanceUntilIdle()
        assertFalse(opened)
    }

    @Test fun writeFailureRetainsPreviewForRetryAndHidesProviderDetails() = runTest(dispatcher) {
        val model = TelemetryReportViewModel(dispatcher)
        model.prepare(VehicleState(), 1_000)
        val frozen = model.state.value.preview
        model.chooseDestination()
        model.save { throw IOException("private-provider-details") }
        advanceUntilIdle()
        assertEquals(frozen, model.state.value.preview)
        assertTrue(model.state.value.message!!.contains("partial file"))
        assertFalse(model.state.value.message!!.contains("private-provider-details"))
        assertTrue(model.chooseDestination())
        model.save { ByteArrayOutputStream() }
        advanceUntilIdle()
        assertNull(model.state.value.preview)
    }

    @Test fun duplicateSaveDoesNotWriteSecondDocument() = runTest(dispatcher) {
        val model = TelemetryReportViewModel(dispatcher)
        model.prepare(VehicleState(), 1_000)
        model.chooseDestination()
        var writes = 0
        model.save { writes++; ByteArrayOutputStream() }
        model.save { writes++; ByteArrayOutputStream() }
        advanceUntilIdle()
        assertEquals(1, writes)
    }

    @Test fun lostSnapshotDoesNotWriteToDestination() = runTest(dispatcher) {
        val model = TelemetryReportViewModel(dispatcher)
        var opened = false
        model.save { opened = true; ByteArrayOutputStream() }
        advanceUntilIdle()
        assertFalse(opened)
        assertTrue(model.state.value.message!!.contains("Snapshot no longer available"))
    }

    @Test fun pickerFailureAndDismissalDoNotDiscardSourceTelemetry() = runTest(dispatcher) {
        val model = TelemetryReportViewModel(dispatcher)
        val vehicle = VehicleState()
        model.prepare(vehicle, 1_000)
        model.chooseDestination()
        model.pickerFailed()
        assertNotNull(model.state.value.preview)
        assertFalse(model.state.value.choosingDestination)
        model.dismiss()
        assertEquals(TelemetryReportState(), model.state.value)
        assertEquals(VehicleState(), vehicle)
    }
}
