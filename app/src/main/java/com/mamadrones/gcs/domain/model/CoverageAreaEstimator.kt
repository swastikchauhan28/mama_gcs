package com.mamadrones.gcs.domain.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.hypot
import kotlin.math.sqrt

/** Volatile cell-center sample counts and estimates; no statistical error bound is implied. */
data class CoverageAreaEstimate(
    val cellWidthMeters: Double,
    val cellHeightMeters: Double,
    val fieldSamples: Int,
    val headlandSamples: Int,
    val unplannedMainSamples: Int,
    val headlandDefinitelyCells: Int,
    val headlandPossiblyCells: Int,
    val mainGapDefinitelyCells: Int,
    val mainGapPossiblyCells: Int,
) {
    val headlandSquareMeters: Double get() = headlandSamples * cellWidthMeters * cellHeightMeters
    val unplannedMainSquareMeters: Double get() = unplannedMainSamples * cellWidthMeters * cellHeightMeters
    val headlandMinimum: Double get() = headlandDefinitelyCells * cellWidthMeters * cellHeightMeters
    val headlandMaximum: Double get() = headlandPossiblyCells * cellWidthMeters * cellHeightMeters
    val mainGapMinimum: Double get() = mainGapDefinitelyCells * cellWidthMeters * cellHeightMeters
    val mainGapMaximum: Double get() = mainGapPossiblyCells * cellWidthMeters * cellHeightMeters
}

/** Deterministic bounded geometry sampling, independent of diagram pixel size. */
object CoverageAreaEstimator {
    const val GRID_SIDE = 128
    // Coordinates are bounded to a 2 km local plane; borderline results remain uncertain.
    private const val MARGIN_METERS = 0.0000001

    private data class Strip(val left: Double, val right: Double, val bottom: Double, val top: Double)

    fun estimate(preview: FieldCoveragePreview): CoverageAreaEstimate? {
        val polygon = preview.outline
        val minX = polygon.minOf { it.x }; val maxX = polygon.maxOf { it.x }
        val minY = polygon.minOf { it.y }; val maxY = polygon.maxOf { it.y }
        val dx = (maxX - minX) / GRID_SIDE; val dy = (maxY - minY) / GRID_SIDE
        var inside = 0; var headland = 0; var gap = 0
        var headMin = 0; var headMax = 0; var gapMin = 0; var gapMax = 0
        val halfWidth = preview.toolWidthMeters / 2
        val radius = hypot(dx, dy) / 2 + MARGIN_METERS
        val strips = preview.passes.map { pass ->
            if (abs(pass.start.x - pass.end.x) < 1e-7) Strip(pass.start.x - halfWidth, pass.start.x + halfWidth,
                min(pass.start.y, pass.end.y), max(pass.start.y, pass.end.y))
            else Strip(min(pass.start.x, pass.end.x), max(pass.start.x, pass.end.x),
                pass.start.y - halfWidth, pass.start.y + halfWidth)
        }
        for (ix in 0 until GRID_SIDE) for (iy in 0 until GRID_SIDE) {
            val x = minX + (ix + 0.5) * dx; val y = minY + (iy + 0.5) * dy
            val centerInside = insidePolygon(x, y, polygon)
            val distance = sqrt(polygon.indices.minOf { i -> distanceSquared(x, y, polygon[i], polygon[(i + 1) % polygon.size]) })
            // Distance to a boundary is 1-Lipschitz. If the half-diagonal cannot reach
            // any edge, the entire cell has the same inside/outside classification.
            val whollyInside = centerInside && distance > radius
            val whollyOutside = !centerInside && distance > radius
            if (whollyOutside) continue
            val definitelyHead = preview.headlandMeters > 0 && whollyInside && distance + radius < preview.headlandMeters
            val definitelyMain = whollyInside && (preview.headlandMeters == 0.0 || distance - radius > preview.headlandMeters)
            val x0 = x - dx / 2; val x1 = x + dx / 2; val y0 = y - dy / 2; val y1 = y + dy / 2
            var intersectsStrip = false; var insideStrip = false; var centerCovered = false
            for (strip in strips) {
                if (x >= strip.left && x <= strip.right && y >= strip.bottom && y <= strip.top) centerCovered = true
                if (x1 >= strip.left - MARGIN_METERS && x0 <= strip.right + MARGIN_METERS &&
                    y1 >= strip.bottom - MARGIN_METERS && y0 <= strip.top + MARGIN_METERS) intersectsStrip = true
                if (x0 > strip.left + MARGIN_METERS && x1 < strip.right - MARGIN_METERS &&
                    y0 > strip.bottom + MARGIN_METERS && y1 < strip.top - MARGIN_METERS) insideStrip = true
            }
            if (preview.headlandMeters > 0) {
                if (definitelyHead) headMin++
                if (!definitelyMain) headMax++
            }
            if (definitelyMain && !intersectsStrip) gapMin++
            if (!definitelyHead && !insideStrip) gapMax++
            if (centerInside) {
                inside++
                if (preview.headlandMeters > 0 && distance < preview.headlandMeters) headland++
                else if (!centerCovered) gap++
            }
        }
        if (inside == 0) return null
        return CoverageAreaEstimate(dx, dy, inside, headland, gap, headMin, headMax, gapMin, gapMax)
    }

    private fun insidePolygon(x: Double, y: Double, polygon: List<CoveragePoint>): Boolean {
        var inside = false
        for (i in polygon.indices) {
            val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
        }
        return inside
    }

    private fun distanceSquared(x: Double, y: Double, a: CoveragePoint, b: CoveragePoint): Double {
        val dx = b.x - a.x; val dy = b.y - a.y
        val t = (((x - a.x) * dx + (y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        val px = x - a.x - t * dx; val py = y - a.y - t * dy
        return px * px + py * py
    }
}
