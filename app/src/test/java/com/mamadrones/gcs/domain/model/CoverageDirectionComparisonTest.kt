package com.mamadrones.gcs.domain.model

import org.junit.Assert.*
import org.junit.Test

class CoverageDirectionComparisonTest {
    private fun field(vararg xy: Pair<Double, Double>) = xy.mapIndexed { i, (x, y) ->
        DraftWaypoint("c$i", Math.toDegrees(y / 6_371_000.0), Math.toDegrees(x / 6_371_000.0))
    }

    @Test fun rectangleTriangleAndConcaveMatchIndependentCalculations() {
        val shapes = listOf(
            field(0.0 to 0.0, 10.0 to 0.0, 10.0 to 20.0, 0.0 to 20.0),
            field(0.0 to 0.0, 12.0 to 0.0, 0.0 to 12.0),
            field(0.0 to 0.0, 12.0 to 0.0, 12.0 to 12.0, 8.0 to 12.0, 8.0 to 4.0, 4.0 to 4.0, 4.0 to 12.0, 0.0 to 12.0),
        )
        for (shape in shapes) {
            val pair = CoverageDirectionComparisonPlanner.calculate(shape, 1.0, 10.0, 0.5)
            assertEquals(FieldCoveragePlanner.calculate(shape, 1.0, 10.0, false, 0.5), pair.northSouth)
            assertEquals(FieldCoveragePlanner.calculate(shape, 1.0, 10.0, true, 0.5), pair.eastWest)
            assertTrue(pair.northSouth is CoverageCalculation.Ready)
            assertTrue(pair.eastWest is CoverageCalculation.Ready)
        }
        val rectangle = CoverageDirectionComparisonPlanner.calculate(shapes.first(), 2.0, 0.0, 0.0)
        assertEquals(5, (rectangle.northSouth as CoverageCalculation.Ready).preview.passes.size)
        assertEquals(10, (rectangle.eastWest as CoverageCalculation.Ready).preview.passes.size)
    }

    @Test fun oneDirectionCanExceedPassLimitWhileOtherRemainsAvailable() {
        val longField = field(0.0 to 0.0, 10.0 to 0.0, 10.0 to 1200.0, 0.0 to 1200.0)
        val pair = CoverageDirectionComparisonPlanner.calculate(longField, 2.0, 0.0, 0.0)
        assertTrue(pair.northSouth is CoverageCalculation.Ready)
        assertTrue(pair.eastWest is CoverageCalculation.Invalid)
        assertTrue((pair.eastWest as CoverageCalculation.Invalid).reason.contains("500"))
    }

    @Test fun invalidSettingsAndOutlineAreReportedForBothDirections() {
        val pair = CoverageDirectionComparisonPlanner.calculate(emptyList(), 1.0, 0.0, 0.0)
        assertTrue(pair.northSouth is CoverageCalculation.Invalid && pair.eastWest is CoverageCalculation.Invalid)
        val invalidWidth = CoverageDirectionComparisonPlanner.calculate(emptyList(), 0.0, 0.0, 0.0)
        assertTrue((invalidWidth.northSouth as CoverageCalculation.Invalid).reason.contains("tool width"))
        assertTrue((invalidWidth.eastWest as CoverageCalculation.Invalid).reason.contains("tool width"))
    }
}
