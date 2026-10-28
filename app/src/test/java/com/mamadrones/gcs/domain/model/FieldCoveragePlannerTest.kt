package com.mamadrones.gcs.domain.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class FieldCoveragePlannerTest {
    private fun polygon(vararg xy: Pair<Double, Double>) = xy.mapIndexed { i, (x, y) ->
        DraftWaypoint("p$i", Math.toDegrees(y / 6_371_000.0), Math.toDegrees(x / 6_371_000.0))
    }
    private fun rectangle(x: Double = 10.0, y: Double = 20.0) = polygon(0.0 to 0.0, x to 0.0, x to y, 0.0 to y)
    private fun ready(vertices: List<DraftWaypoint>, width: Double = 2.0, overlap: Double = 0.0, eastWest: Boolean = false) =
        (FieldCoveragePlanner.calculate(vertices, width, overlap, eastWest) as CoverageCalculation.Ready).preview
    private fun invalid(vertices: List<DraftWaypoint>, width: Double = 2.0, overlap: Double = 0.0) =
        assertTrue(FieldCoveragePlanner.calculate(vertices, width, overlap) is CoverageCalculation.Invalid)

    @Test fun rectangleHasExpectedAreaSpacingLengthAndAlternatingPasses() {
        val p = ready(rectangle())
        assertEquals(200.0, p.fieldAreaSquareMeters, 0.0001)
        assertEquals(5, p.passes.size)
        assertEquals(100.0, p.workingLengthMeters, 0.0001)
        assertEquals(2.0, p.maximumSpacingMeters, 0.00001)
        p.passes.forEachIndexed { i, pass ->
            assertEquals(1.0 + 2 * i, pass.start.x, 0.00001)
            assertEquals(pass.start.x, pass.end.x, 0.00001)
            assertEquals(i % 2 == 0, pass.end.y > pass.start.y)
        }
    }

    @Test fun overlapAddsPassesAndLastPassDoesNotLeaveRectangleEdgeGap() {
        val p = ready(rectangle(), overlap = 10.0)
        assertEquals(6, p.passes.size)
        assertEquals(1.8, p.maximumSpacingMeters, 0.00001)
        val xs = p.passes.map { it.start.x }
        assertTrue(xs.zipWithNext().all { (a, b) -> b - a <= 1.800001 })
        assertEquals(9.0, xs.last(), 0.00001)
        assertEquals(1.0, xs.first(), 0.00001)
    }

    @Test fun eastWestPassesRemainNorthUpAndCoverLongAxis() {
        val p = ready(rectangle(), eastWest = true)
        assertEquals(10, p.passes.size)
        assertEquals(100.0, p.workingLengthMeters, 0.0001)
        assertTrue(p.passes.all { abs(it.start.y - it.end.y) < 0.00001 })
    }

    @Test fun concaveOutlineSplitsPassesWithoutCrossingNotch() {
        val u = polygon(0.0 to 0.0, 12.0 to 0.0, 12.0 to 12.0, 8.0 to 12.0,
            8.0 to 4.0, 4.0 to 4.0, 4.0 to 12.0, 0.0 to 12.0)
        val p = ready(u, eastWest = true)
        assertEquals(112.0, p.fieldAreaSquareMeters, 0.0001)
        assertEquals(10, p.passes.size)
        assertTrue(p.passes.filter { it.start.y > 4 }.all {
            maxOf(it.start.x, it.end.x) <= 4.00001 || minOf(it.start.x, it.end.x) >= 7.99999
        })
    }

    @Test fun triangleClipsEndpointsInsteadOfClaimingFullRectangleCoverage() {
        val p = ready(polygon(0.0 to 0.0, 12.0 to 0.0, 0.0 to 12.0))
        assertEquals(72.0, p.fieldAreaSquareMeters, 0.0001)
        p.passes.forEach { pass ->
            assertEquals(12.0 - pass.start.x, maxOf(pass.start.y, pass.end.y), 0.00001)
        }
    }

    @Test fun windingDoesNotChangePassLengths() {
        val a = ready(rectangle()); val b = ready(rectangle().reversed())
        assertEquals(a.passes.size, b.passes.size)
        assertEquals(a.workingLengthMeters, b.workingLengthMeters, 0.0001)
        assertEquals(a.fieldAreaSquareMeters, b.fieldAreaSquareMeters, 0.0001)
    }

    @Test fun widerToolProducesOneCenteredPass() {
        val p = ready(rectangle(), width = 12.0)
        assertEquals(1, p.passes.size)
        assertEquals(5.0, p.passes.single().start.x, 0.00001)
    }

    @Test fun invalidWidthsAndOverlapsNeverProduceGeometry() {
        for (width in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0, 0.09, 50.01)) invalid(rectangle(), width)
        for (overlap in listOf(Double.NaN, Double.NEGATIVE_INFINITY, -0.1, 80.01, 100.0)) invalid(rectangle(), overlap = overlap)
        assertNotNull(FieldCoveragePlanner.settingsError(null, 10.0))
    }

    @Test fun emptyDegenerateDuplicateAndSelfCrossingOutlinesAreRejected() {
        invalid(emptyList())
        invalid(polygon(0.0 to 0.0, 5.0 to 0.0))
        invalid(polygon(0.0 to 0.0, 5.0 to 0.0, 10.0 to 0.0))
        invalid(rectangle() + rectangle().first())
        invalid(polygon(0.0 to 0.0, 10.0 to 10.0, 0.0 to 10.0, 10.0 to 0.0))
        invalid(rectangle(0.5, 0.5))
    }

    @Test fun computationLimitsRejectInsteadOfTruncatingPasses() {
        invalid(rectangle(2_001.0, 20.0))
        invalid(rectangle(100.0, 10.0), width = 0.1)
        invalid(rectangle().map { it.copy(latitude = it.latitude + 81) })
        invalid(List(65) { DraftWaypoint("v$it", it * 0.0001, 0.0) })
    }

    @Test fun compactDatelineFieldUsesLocalLongitudeDifference() {
        val field = listOf(DraftWaypoint("a", 0.0, 179.9999), DraftWaypoint("b", 0.0, -179.9999),
            DraftWaypoint("c", 0.0001, -179.9999), DraftWaypoint("d", 0.0001, 179.9999))
        val p = ready(field)
        assertTrue(p.fieldAreaSquareMeters in 240.0..250.0)
        assertTrue(p.passes.isNotEmpty())
    }

    @Test fun headlandReservesRectangleAndZeroPreservesExistingPreview() {
        val field = rectangle()
        assertEquals(ready(field), (FieldCoveragePlanner.calculate(field, 2.0, 0.0, headland = 0.0) as CoverageCalculation.Ready).preview)
        val p = (FieldCoveragePlanner.calculate(field, 2.0, 0.0, headland = 1.0) as CoverageCalculation.Ready).preview
        assertEquals(1.0, p.headlandMeters, 0.0)
        assertEquals(4, p.passes.size)
        assertEquals(64.0, p.workingLengthMeters, 0.001)
        assertClearance(p, 2.0)
    }

    @Test fun slopedAndConcaveFieldsKeepPassesAwayFromEveryEdge() {
        val triangle = polygon(0.0 to 0.0, 12.0 to 0.0, 0.0 to 12.0)
        val concave = polygon(0.0 to 0.0, 12.0 to 0.0, 12.0 to 12.0, 8.0 to 12.0,
            8.0 to 4.0, 4.0 to 4.0, 4.0 to 12.0, 0.0 to 12.0)
        for (field in listOf(triangle, concave)) for (eastWest in listOf(false, true)) {
            val p = (FieldCoveragePlanner.calculate(field, 1.0, 10.0, eastWest, 0.5) as CoverageCalculation.Ready).preview
            assertTrue(p.passes.isNotEmpty())
            assertClearance(p, 1.0)
        }
    }

    @Test fun invalidOrOversizedHeadlandIsRejected() {
        for (h in listOf(-0.1, 100.1, Double.NaN, Double.POSITIVE_INFINITY))
            assertTrue(FieldCoveragePlanner.calculate(rectangle(), 2.0, 0.0, headland = h) is CoverageCalculation.Invalid)
        assertNotNull(FieldCoveragePlanner.settingsError(2.0, 10.0, null))
        assertTrue(FieldCoveragePlanner.calculate(rectangle(), 2.0, 0.0, headland = 10.0) is CoverageCalculation.Invalid)
    }

    @Test fun sampledRectangleSeparatesHeadlandFromUnplannedMainArea() {
        val full = ready(rectangle()).areaEstimate!!
        assertEquals(0, full.headlandSamples)
        assertEquals(0, full.unplannedMainSamples)
        val p = (FieldCoveragePlanner.calculate(rectangle(), 2.0, 0.0, headland = 1.0) as CoverageCalculation.Ready).preview
        val estimate = p.areaEstimate!!
        // Exact rectangle: 200 - (8 × 18) = 56 m² headland;
        // main field has 8 × 18 - 8 × 16 = 16 m² outside the ideal strips.
        assertEquals(56.0, estimate.headlandSquareMeters, 4.0)
        assertEquals(16.0, estimate.unplannedMainSquareMeters, 2.0)
        assertTrue(estimate.headlandSamples > 0 && estimate.unplannedMainSamples > 0)
        assertEquals(10.0 / 128, estimate.cellWidthMeters, 0.00001)
        assertEquals(estimate.unplannedMainSamples, estimate.gapSampleCenters.size)
        assertTrue(estimate.gapSampleCenters.size <= CoverageAreaEstimator.GRID_SIDE * CoverageAreaEstimator.GRID_SIDE)
        estimate.gapSampleCenters.forEach { center ->
            assertTrue("gap samples exclude the reserved headland", center.x >= 1.0 && center.x <= 9.0 && center.y >= 1.0 && center.y <= 19.0)
            assertTrue("gap samples are outside every ideal strip", p.passes.none { pass ->
                abs(center.x - pass.start.x) <= p.toolWidthMeters / 2 && center.y >= minOf(pass.start.y, pass.end.y) && center.y <= maxOf(pass.start.y, pass.end.y)
            })
        }
        assertTrue(full.gapSampleCenters.isEmpty())
    }

    @Test fun sampledTriangleFindsExpectedIdealStripGapsAndConcaveEstimatesStayBounded() {
        val triangle = polygon(0.0 to 0.0, 12.0 to 0.0, 0.0 to 12.0)
        val estimate = ready(triangle).areaEstimate!!
        assertEquals(3.0, estimate.unplannedMainSquareMeters, 0.6)
        val u = polygon(0.0 to 0.0, 12.0 to 0.0, 12.0 to 12.0, 8.0 to 12.0,
            8.0 to 4.0, 4.0 to 4.0, 4.0 to 12.0, 0.0 to 12.0)
        for (ew in listOf(false, true)) {
            val p = (FieldCoveragePlanner.calculate(u, 1.0, 10.0, ew, 0.5) as CoverageCalculation.Ready).preview
            val e = p.areaEstimate!!
            assertTrue(e.headlandSamples > 0)
            assertTrue(e.headlandSamples + e.unplannedMainSamples <= e.fieldSamples)
            assertTrue(e.fieldSamples <= CoverageAreaEstimator.GRID_SIDE * CoverageAreaEstimator.GRID_SIDE)
        }
    }

    @Test fun noInteriorGridSamplesAreExplicitlyUnavailable() {
        val outline = listOf(CoveragePoint(0.0, 0.0), CoveragePoint(128.0, 0.0), CoveragePoint(128.0, 0.01),
            CoveragePoint(0.01, 0.01), CoveragePoint(0.01, 128.0), CoveragePoint(0.0, 128.0))
        val p = FieldCoveragePreview(outline, emptyList(), 1.0, 0.0, 1.0, 2.5599)
        assertNull(CoverageAreaEstimator.estimate(p))
    }

    @Test fun wholeCellRangesEncloseKnownRectangleTriangleAndConcaveAreas() {
        val rectanglePreview = (FieldCoveragePlanner.calculate(rectangle(), 2.0, 0.0, headland = 1.0) as CoverageCalculation.Ready).preview
        val r = rectanglePreview.areaEstimate!!
        assertTrue(r.headlandMinimum <= 56.0 && r.headlandMaximum >= 56.0)
        assertTrue(r.mainGapMinimum <= 16.0 && r.mainGapMaximum >= 16.0)
        val triangle = ready(polygon(0.0 to 0.0, 12.0 to 0.0, 0.0 to 12.0)).areaEstimate!!
        assertEquals(0.0, triangle.headlandMinimum, 0.0)
        assertEquals(0.0, triangle.headlandMaximum, 0.0)
        assertTrue(triangle.mainGapMinimum <= 3.0 && triangle.mainGapMaximum >= 3.0)
        val u = polygon(0.0 to 0.0, 12.0 to 0.0, 12.0 to 12.0, 8.0 to 12.0,
            8.0 to 4.0, 4.0 to 4.0, 4.0 to 12.0, 0.0 to 12.0)
        val plannedU = ready(u)
        val oneStrip = plannedU.copy(passes = listOf(CoveragePass(CoveragePoint(1.0, 0.0), CoveragePoint(1.0, 12.0))), areaEstimate = null)
        val concave = CoverageAreaEstimator.estimate(oneStrip)!!
        // The U is 112 m²; the single 2 m × 12 m strip leaves 88 m² unplanned.
        assertTrue(concave.mainGapMinimum <= 88.0 && concave.mainGapMaximum >= 88.0)
        for (e in listOf(r, triangle, concave)) {
            assertTrue(e.headlandMinimum <= e.headlandSquareMeters && e.headlandSquareMeters <= e.headlandMaximum)
            assertTrue(e.mainGapMinimum <= e.unplannedMainSquareMeters && e.unplannedMainSquareMeters <= e.mainGapMaximum)
        }
    }

    @Test fun rangeStillIncludesGapsWhenEverySampleCenterMissesThem() {
        val outline = listOf(CoveragePoint(0.0, 0.0), CoveragePoint(128.0, 0.0), CoveragePoint(128.0, 128.0), CoveragePoint(0.0, 128.0))
        val passes = (0 until 128).map { CoveragePass(CoveragePoint(it + 0.5, 0.0), CoveragePoint(it + 0.5, 128.0)) }
        val p = FieldCoveragePreview(outline, passes, 0.5, 0.0, 1.0, 16384.0)
        val estimate = CoverageAreaEstimator.estimate(p)!!
        assertEquals(0, estimate.unplannedMainSamples)
        assertTrue(estimate.gapSampleCenters.isEmpty())
        // Half of the rectangle lies between the 0.5 m strips despite every center landing on one.
        assertTrue(estimate.mainGapMinimum <= 8192.0 && estimate.mainGapMaximum >= 8192.0)
    }

    @Test fun gapSampleStorageIsBoundedEvenWhenEveryGridCenterIsUncovered() {
        val outline = listOf(CoveragePoint(0.0, 0.0), CoveragePoint(128.0, 0.0), CoveragePoint(128.0, 128.0), CoveragePoint(0.0, 128.0))
        val p = FieldCoveragePreview(outline, emptyList(), 1.0, 0.0, 1.0, 16384.0)
        val estimate = CoverageAreaEstimator.estimate(p)!!
        assertEquals(CoverageAreaEstimator.GRID_SIDE * CoverageAreaEstimator.GRID_SIDE, estimate.gapSampleCenters.size)
        assertEquals(estimate.unplannedMainSamples, estimate.gapSampleCenters.size)
    }

    private fun assertClearance(preview: FieldCoveragePreview, clearance: Double) {
        for (pass in preview.passes) for (step in 0..20) {
            val t = step / 20.0
            val p = CoveragePoint(pass.start.x + t * (pass.end.x - pass.start.x), pass.start.y + t * (pass.end.y - pass.start.y))
            for (i in preview.outline.indices) {
                val a = preview.outline[i]; val b = preview.outline[(i + 1) % preview.outline.size]
                val dx = b.x - a.x; val dy = b.y - a.y
                val projection = (((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
                assertTrue("pass too close to boundary", hypot(p.x - a.x - projection * dx, p.y - a.y - projection * dy) >= clearance - 0.00001)
            }
        }
    }
}
