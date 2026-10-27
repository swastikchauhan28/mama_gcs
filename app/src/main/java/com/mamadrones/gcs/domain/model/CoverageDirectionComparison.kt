package com.mamadrones.gcs.domain.model

/** Independent volatile results; one direction can fail without hiding the other. */
data class CoverageDirectionComparison(val northSouth: CoverageCalculation, val eastWest: CoverageCalculation)

object CoverageDirectionComparisonPlanner {
    fun calculate(outline: List<DraftWaypoint>, width: Double, overlap: Double, headland: Double): CoverageDirectionComparison =
        CoverageDirectionComparison(
            FieldCoveragePlanner.calculate(outline, width, overlap, eastWest = false, headland = headland),
            FieldCoveragePlanner.calculate(outline, width, overlap, eastWest = true, headland = headland),
        )
}
