package com.mamadrones.gcs.presentation.screens

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
class HardwareReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    private suspend fun TestScope.loaded(): ParameterReviewViewModel {
        val model = ParameterReviewViewModel(dispatcher)
        model.importFile { "SERVO1_FUNCTION,74".byteInputStream() }
        advanceUntilIdle()
        return model
    }

    @Test fun requiresCurrentFileAndCompletedImport() = runTest(dispatcher) {
        val model = ParameterReviewViewModel(dispatcher)
        model.prepareHardwareReport()
        assertFalse(model.chooseReportDestination())
        model.importFile { "SERVO1_FUNCTION,74".byteInputStream() }
        model.prepareHardwareReport()
        assertNull(model.state.value.report.preview)
        advanceUntilIdle()
        model.prepareHardwareReport()
        assertNotNull(model.state.value.report.preview)
    }

    @Test fun reportUsesCurrentOnlyAndReplacementOrClearInvalidatesPreview() = runTest(dispatcher) {
        val model = loaded()
        model.importReference { "SERVO1_FUNCTION,73".byteInputStream() }; advanceUntilIdle()
        model.prepareHardwareReport()
        assertTrue(model.state.value.report.preview!!.contains("SERVO1_FUNCTION: 74"))
        assertFalse(model.state.value.report.preview!!.contains("SERVO1_FUNCTION: 73"))
        model.importFile { "SERVO1_FUNCTION,70".byteInputStream() }
        assertNull(model.state.value.report.preview)
        advanceUntilIdle()
        model.prepareHardwareReport()
        assertTrue(model.state.value.report.preview!!.contains("SERVO1_FUNCTION: 70"))
        model.clear()
        assertEquals(ParameterReviewState(), model.state.value)
    }

    @Test fun pickerAndWriteFreezeSnapshotAndBlockDuplicateWritesAndMutation() = runTest(dispatcher) {
        val model = loaded()
        val source = model.state.value.snapshot
        model.prepareHardwareReport()
        val frozen = model.state.value.report.preview!!
        assertTrue(model.chooseReportDestination())
        assertFalse(model.chooseReportDestination())
        var imported = false
        model.importFile { imported = true; "A,1".byteInputStream() }
        model.clear(); model.dismissHardwareReport(); model.prepareHardwareReport()
        assertEquals(frozen, model.state.value.report.preview)
        val output = ByteArrayOutputStream()
        var writes = 0
        model.saveHardwareReport { writes++; output }
        model.saveHardwareReport { writes++; ByteArrayOutputStream() }
        model.reportPickerFinished(failed = true)
        model.clear()
        assertTrue(model.state.value.report.writing)
        advanceUntilIdle()
        assertFalse(imported)
        assertEquals(1, writes)
        assertEquals(frozen, output.toString("UTF-8"))
        assertSame(source, model.state.value.snapshot)
        assertNull(model.state.value.report.preview)
        assertTrue(model.state.value.report.message!!.contains("saved"))
    }

    @Test fun pickerCancellationOrFailureKeepsPreviewButDoesNotWrite() = runTest(dispatcher) {
        val model = loaded()
        model.prepareHardwareReport()
        val preview = model.state.value.report.preview
        for (failed in listOf(false, true)) {
            assertTrue(model.chooseReportDestination())
            model.reportPickerFinished(failed)
            var opened = false
            model.saveHardwareReport { opened = true; ByteArrayOutputStream() }
            advanceUntilIdle()
            assertFalse(opened)
            assertEquals(preview, model.state.value.report.preview)
            assertFalse(model.state.value.report.busy)
        }
        model.dismissHardwareReport()
        assertEquals(HardwareReportState(), model.state.value.report)
    }

    @Test fun failureHidesProviderDetailsAndRetainsPreviewForRetry() = runTest(dispatcher) {
        val model = loaded()
        model.prepareHardwareReport()
        val preview = model.state.value.report.preview
        model.chooseReportDestination()
        model.saveHardwareReport { throw IOException("private-provider-path") }
        advanceUntilIdle()
        assertEquals(preview, model.state.value.report.preview)
        assertTrue(model.state.value.report.message!!.contains("partial file"))
        assertFalse(model.state.value.report.message!!.contains("private-provider-path"))
        assertTrue(model.chooseReportDestination())
        model.saveHardwareReport { ByteArrayOutputStream() }
        advanceUntilIdle()
        assertNull(model.state.value.report.preview)
    }

    @Test fun processRecreationWithoutSnapshotDoesNotOpenDestination() = runTest(dispatcher) {
        val model = ParameterReviewViewModel(dispatcher)
        var opened = false
        model.saveHardwareReport { opened = true; ByteArrayOutputStream() }
        advanceUntilIdle()
        assertFalse(opened)
        assertTrue(model.state.value.report.message!!.contains("Snapshot no longer available"))
    }
}
