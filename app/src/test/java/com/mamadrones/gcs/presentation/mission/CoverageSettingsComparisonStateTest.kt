package com.mamadrones.gcs.presentation.mission

import com.mamadrones.gcs.domain.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CoverageSettingsComparisonStateTest {
    private val field = listOf(DraftWaypoint("a", 0.0, 0.0), DraftWaypoint("b", 0.0, 0.0001),
        DraftWaypoint("c", 0.0002, 0.0001), DraftWaypoint("d", 0.0002, 0.0))

    @Test fun optionsUseIndependentSettingsAndRejectInvalidInput() = runTest {
        val state = CoverageSettingsComparisonState(this, field, "2", "0", "0", false) { vertices, width, overlap, direction, headland ->
            FieldCoveragePlanner.calculate(vertices, width, overlap, direction, headland)
        }
        state.updateWidthB("1"); state.updateOverlapB("50")
        state.compare(); runCurrent()
        val first = (state.result!!.first as CoverageCalculation.Ready).preview
        val second = (state.result!!.second as CoverageCalculation.Ready).preview
        assertEquals(0.0, first.overlapPercent, 0.0)
        assertEquals(50.0, second.overlapPercent, 0.0)
        assertEquals(1.0, second.toolWidthMeters, 0.0)
        assertTrue(second.passes.size > first.passes.size)
        state.updateWidthB("NaN")
        assertNull(state.result)
        assertNotNull(state.errorB)
        state.compare(); runCurrent(); assertNull(state.result)
        state.updateWidthB("000000000000000010")
        assertNotNull(state.errorB)
        state.updateWidthB("2")
        assertNull(state.errorB)
    }

    @Test fun canceledOldCalculationCannotPublishAfterInputsChange() = runTest {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var calls = 0
        val state = CoverageSettingsComparisonState(this, field, "2", "10", "0", false) { _, _, overlap, _, _ ->
            if (++calls == 1) withContext(NonCancellable) { started.complete(Unit); release.await() }
            CoverageCalculation.Invalid("overlap=$overlap")
        }
        state.compare(); runCurrent(); assertTrue(started.isCompleted)
        state.updateOverlapB("20")
        assertFalse(state.busy); assertNull(state.result)
        state.compare(); runCurrent()
        assertEquals("overlap=20.0", (state.result!!.second as CoverageCalculation.Invalid).reason)
        release.complete(Unit); runCurrent()
        assertEquals("overlap=20.0", (state.result!!.second as CoverageCalculation.Invalid).reason)
        assertFalse(state.busy)
    }
}
