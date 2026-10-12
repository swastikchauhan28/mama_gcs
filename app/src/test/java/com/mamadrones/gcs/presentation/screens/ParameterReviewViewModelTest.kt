package com.mamadrones.gcs.presentation.screens

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ParameterReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun referenceResultWithoutCurrentSnapshotIsNotImported() = runTest(dispatcher) {
        val model = ParameterReviewViewModel(dispatcher)
        var opened = false
        model.importReference { opened = true; "A 1".byteInputStream() }
        advanceUntilIdle()
        assertFalse(opened)
        assertNull(model.state.value.reference)
        assertTrue(model.state.value.error!!.contains("current parameter file"))
    }

    @Test fun referenceImportAndCurrentReplacementRecomputeWithoutMixingRoles() = runTest(dispatcher) {
        val model = ParameterReviewViewModel(dispatcher)
        model.importFile { "A 2".byteInputStream() }
        advanceUntilIdle()
        val current = model.state.value.snapshot
        model.importReference { "A 1".byteInputStream() }
        advanceUntilIdle()
        assertSame(current, model.state.value.snapshot)
        assertEquals("1", model.state.value.comparison!!.differences.single().reference!!.rawValue)
        assertEquals("2", model.state.value.comparison!!.differences.single().current!!.rawValue)
        val reference = model.state.value.reference
        model.importFile { "A 3".byteInputStream() }
        advanceUntilIdle()
        assertSame(reference, model.state.value.reference)
        assertEquals("3", model.state.value.comparison!!.differences.single().current!!.rawValue)
    }

    @Test fun failedReferenceReplacementRetainsBothSnapshotsAndComparison() = runTest(dispatcher) {
        val model = ParameterReviewViewModel(dispatcher)
        model.importFile { "A 2".byteInputStream() }; advanceUntilIdle()
        model.importReference { "A 1".byteInputStream() }; advanceUntilIdle()
        val previous = model.state.value
        model.importReference { "A 4\nA 5".byteInputStream() }; advanceUntilIdle()
        assertSame(previous.snapshot, model.state.value.snapshot)
        assertSame(previous.reference, model.state.value.reference)
        assertSame(previous.comparison, model.state.value.comparison)
        assertTrue(model.state.value.error!!.startsWith("Reference file:"))
        model.importReference { throw SecurityException("private path") }; advanceUntilIdle()
        assertFalse(model.state.value.error!!.contains("private path"))
    }

    @Test fun failedCurrentReplacementAlsoRetainsReferenceAndComparison() = runTest(dispatcher) {
        val model = ParameterReviewViewModel(dispatcher)
        model.importFile { "A 2".byteInputStream() }; advanceUntilIdle()
        model.importReference { "A 1".byteInputStream() }; advanceUntilIdle()
        val previous = model.state.value
        model.importFile { null }; advanceUntilIdle()
        assertSame(previous.snapshot, model.state.value.snapshot)
        assertSame(previous.reference, model.state.value.reference)
        assertSame(previous.comparison, model.state.value.comparison)
    }

    @Test fun removingReferenceDoesNotClearCurrentAndClearAllCancelsReferenceImport() = runTest(dispatcher) {
        val model = ParameterReviewViewModel(dispatcher)
        model.importFile { "A 2".byteInputStream() }; advanceUntilIdle()
        model.importReference { "A 1".byteInputStream() }; advanceUntilIdle()
        val current = model.state.value.snapshot
        model.clearReference()
        assertSame(current, model.state.value.snapshot)
        assertNull(model.state.value.reference)
        assertNull(model.state.value.comparison)
        var opened = false
        model.importReference { opened = true; "A 3".byteInputStream() }
        model.clear()
        advanceUntilIdle()
        assertFalse(opened)
        assertEquals(ParameterReviewState(), model.state.value)
    }

    @Test fun supersededReferenceCannotReplaceLatestCurrentImport() = runTest(dispatcher) {
        val model = ParameterReviewViewModel(dispatcher)
        model.importFile { "A 2".byteInputStream() }; advanceUntilIdle()
        model.importReference { "A 1".byteInputStream() }; advanceUntilIdle()
        val reference = model.state.value.reference
        model.importReference { "A 99".byteInputStream() }
        model.importFile { "A 3".byteInputStream() }
        advanceUntilIdle()
        assertSame(reference, model.state.value.reference)
        assertEquals("3", model.state.value.comparison!!.differences.single().current!!.rawValue)
    }

    @Test fun pickerFailurePreservesReviewAndRemovalCannotRaceActiveImport() = runTest(dispatcher) {
        val model = ParameterReviewViewModel(dispatcher)
        model.importFile { "A 2".byteInputStream() }; advanceUntilIdle()
        model.importReference { "A 1".byteInputStream() }; advanceUntilIdle()
        val previous = model.state.value
        model.pickerFailed(true)
        assertSame(previous.comparison, model.state.value.comparison)
        assertTrue(model.state.value.error!!.contains("reference"))
        model.importReference { "A 4".byteInputStream() }
        model.clearReference()
        assertSame(previous.reference, model.state.value.reference)
        advanceUntilIdle()
        assertEquals("4", model.state.value.reference!!.entries.single().rawValue)
    }

    @Test fun successfulImportAndClearAreMemoryOnly() = runTest(dispatcher) {
        val model = ParameterReviewViewModel(dispatcher)
        model.importFile { "A 1".byteInputStream() }
        assertTrue(model.state.value.loading)
        advanceUntilIdle()
        assertEquals("1", model.state.value.snapshot!!.entries.single().rawValue)
        assertFalse(model.state.value.loading)
        model.clear()
        assertEquals(ParameterReviewState(), model.state.value)
    }

    @Test fun rejectedReplacementKeepsPreviousSnapshot() = runTest(dispatcher) {
        val model = ParameterReviewViewModel(dispatcher)
        model.importFile { "A 1".byteInputStream() }
        advanceUntilIdle()
        val previous = model.state.value.snapshot
        model.importFile { "A 2\nA 3".byteInputStream() }
        advanceUntilIdle()
        assertSame(previous, model.state.value.snapshot)
        assertNotNull(model.state.value.error)
        assertFalse(model.state.value.loading)
    }

    @Test fun clearCancelsPendingImportWithoutRepopulating() = runTest(dispatcher) {
        val model = ParameterReviewViewModel(dispatcher)
        var opened = false
        model.importFile { opened = true; "A 1".byteInputStream() }
        model.clear()
        advanceUntilIdle()
        assertFalse(opened)
        assertEquals(ParameterReviewState(), model.state.value)
    }

    @Test fun latestImportWinsAndProviderFailureDoesNotExposeDetails() = runTest(dispatcher) {
        val model = ParameterReviewViewModel(dispatcher)
        model.importFile { "A 1".byteInputStream() }
        model.importFile { "A 2".byteInputStream() }
        advanceUntilIdle()
        assertEquals("2", model.state.value.snapshot!!.entries.single().rawValue)
        model.importFile { throw SecurityException("private provider details") }
        advanceUntilIdle()
        assertFalse(model.state.value.error!!.contains("private provider details"))
        assertEquals("2", model.state.value.snapshot!!.entries.single().rawValue)
    }
}
