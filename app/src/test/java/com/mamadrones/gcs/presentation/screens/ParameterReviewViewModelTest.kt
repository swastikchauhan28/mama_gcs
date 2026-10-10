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
