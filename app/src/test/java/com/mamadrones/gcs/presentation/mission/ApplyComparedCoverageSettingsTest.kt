package com.mamadrones.gcs.presentation.mission

import com.mamadrones.gcs.domain.model.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ApplyComparedCoverageSettingsTest {
    private val field = listOf(DraftWaypoint("a", 0.0, 0.0), DraftWaypoint("b", 0.0, 0.0001),
        DraftWaypoint("c", 0.0002, 0.0001), DraftWaypoint("d", 0.0002, 0.0))

    @Test fun bothChoicesApplyExactInputsAndClearExistingComparisonAndMap() = runTest {
        for (first in listOf(true, false)) {
            val direction = CoverageDirectionComparisonPlanner.calculate(field, 2.0, 10.0, 1.0)
            val coverage = FieldCoverageState(this, field, true) { _, _, _, _ -> direction }
            coverage.updateWidth("2"); coverage.updateOverlap("10"); coverage.updateHeadland("1"); coverage.setDirection(true)
            coverage.compareDirections(); runCurrent(); coverage.showDirectionOnMap(true)
            assertNotNull(coverage.comparison); assertTrue(coverage.mapLines.isNotEmpty())
            val options = CoverageSettingsComparisonState(this, field, "2", "10", "1", true) { vertices, width, overlap, eastWest, headland ->
                FieldCoveragePlanner.calculate(vertices, width, overlap, eastWest, headland)
            }
            options.updateWidthA("1.25"); options.updateOverlapA("5")
            options.updateWidthB("2.004"); options.updateOverlapB("50.25")
            options.compare(); runCurrent()
            assertTrue(options.applyOption(first, coverage))
            assertEquals(if (first) "1.25" else "2.004", coverage.width)
            assertEquals(if (first) "5" else "50.25", coverage.overlap)
            assertEquals("1", coverage.headland); assertTrue(coverage.eastWest)
            assertNull(coverage.result); assertNull(coverage.comparison); assertTrue(coverage.mapLines.isEmpty())
            assertNull(options.result); assertTrue(coverage.comparedSettingsApplied)
        }
    }

    @Test fun staleOrInvalidChoicesCannotChangeCoverage() = runTest {
        val ready = FieldCoveragePlanner.calculate(field, 2.0, 10.0)
        assertTrue(ready is CoverageCalculation.Ready)
        val coverage = FieldCoverageState(this, field, true)
        coverage.updateWidth("2"); coverage.updateOverlap("10")
        val options = CoverageSettingsComparisonState(this, field, "2", "10", "0", false) { _, _, overlap, _, _ ->
            if (overlap == 20.0) CoverageCalculation.Invalid("invalid option") else ready
        }
        options.updateOverlapB("20"); options.compare(); runCurrent()
        assertFalse(options.applyOption(false, coverage))
        assertEquals("10", coverage.overlap); assertNotNull(options.result)
        coverage.setDirection(true)
        assertFalse(options.applyOption(true, coverage))
        assertEquals("2", coverage.width); assertEquals("10", coverage.overlap)
        assertTrue(coverage.eastWest); assertFalse(coverage.comparedSettingsApplied)
        val differentField = FieldCoverageState(this, field.dropLast(1), true)
        differentField.updateWidth("2"); differentField.updateOverlap("10")
        assertFalse(options.applyOption(true, differentField))
        assertEquals("10", differentField.overlap)
    }

    @Test fun sameSettingsStillClearOldComparisonAndRejectInvalidDirectInputs() = runTest {
        val ready = FieldCoveragePlanner.calculate(field, 2.0, 10.0)
        val coverage = FieldCoverageState(this, field, true)
        coverage.updateWidth("2"); coverage.updateOverlap("10")
        val options = CoverageSettingsComparisonState(this, field, "2", "10", "0", false) { _, _, _, _, _ -> ready }
        options.compare(); runCurrent()
        assertTrue(options.applyOption(true, coverage)); assertNull(options.result)
        assertFalse(coverage.applyComparedSettings(field, "2", "10", "0", false, "NaN", "10"))
        assertFalse(coverage.applyComparedSettings(field, "2", "10", "0", false, "000000000000000010", "10"))
        assertEquals("2", coverage.width); assertEquals("10", coverage.overlap)
    }
}
