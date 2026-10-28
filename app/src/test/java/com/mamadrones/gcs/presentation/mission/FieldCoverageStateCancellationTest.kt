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
class FieldCoverageStateCancellationTest {
    @Test fun assumptionChangesAndClearsCannotPublishAnOlderComparison() = runTest {
        val vertices = listOf(DraftWaypoint("a", 0.0, 0.0), DraftWaypoint("b", 0.0, 0.0001),
            DraftWaypoint("c", 0.0001, 0.0001), DraftWaypoint("d", 0.0001, 0.0))
        val initial = CoverageDirectionComparisonPlanner.calculate(vertices, 2.0, 10.0, 0.0)
        val stale = CoverageDirectionComparison(CoverageCalculation.Invalid("stale"), CoverageCalculation.Invalid("stale"))
        val latest = CoverageDirectionComparison(CoverageCalculation.Invalid("latest"), CoverageCalculation.Invalid("latest"))
        val oldStarted = CompletableDeferred<Unit>()
        val oldRelease = CompletableDeferred<Unit>()
        var calls = 0
        val state = FieldCoverageState(this, vertices, true) { _, _, _, _ ->
            when (++calls) {
                1 -> initial
                2 -> withContext(NonCancellable) { oldStarted.complete(Unit); oldRelease.await(); stale }
                else -> latest
            }
        }
        state.updateWidth("2")
        state.updateSidewaysError("0.05")
        state.compareDirections(); runCurrent()
        assertSame(initial, state.comparison)
        state.showDirectionOnMap(false)
        assertTrue("initial map preview must exist", state.mapLines.isNotEmpty())
        state.compareDirections(); runCurrent()
        assertTrue(oldStarted.isCompleted)
        assertTrue(state.busy)
        state.updateSidewaysError("")
        assertFalse(state.busy)
        assertNull(state.comparison)
        assertNull(state.sidewaysEstimate)
        assertTrue(state.mapLines.isEmpty())
        assertNull(state.mapGapSamples)
        assertNull(state.mapEastWest)
        state.updateSidewaysError("0.15")
        state.compareDirections(); runCurrent()
        assertSame(latest, state.comparison)
        assertEquals(-0.1, state.sidewaysEstimate!!.marginMeters, 1e-12)
        oldRelease.complete(Unit); runCurrent()
        assertSame("the cancelled calculation must not overwrite the newest result", latest, state.comparison)
        assertFalse(state.busy)
        assertTrue(state.mapLines.isEmpty())
    }
}
