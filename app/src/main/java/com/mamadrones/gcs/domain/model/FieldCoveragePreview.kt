package com.mamadrones.gcs.domain.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Metres in a compact local plane: x east, y north. Preview geometry, never a vehicle mission. */
data class CoveragePoint(val x: Double, val y: Double)

data class CoveragePass(val start: CoveragePoint, val end: CoveragePoint) {
    val lengthMeters: Double get() = hypot(end.x - start.x, end.y - start.y)
}

data class FieldCoveragePreview(
    val outline: List<CoveragePoint>,
    val passes: List<CoveragePass>,
    val toolWidthMeters: Double,
    val overlapPercent: Double,
    val maximumSpacingMeters: Double,
    val fieldAreaSquareMeters: Double,
    val headlandMeters: Double = 0.0,
    val areaEstimate: CoverageAreaEstimate? = null,
) {
    val workingLengthMeters: Double get() = passes.sumOf { it.lengthMeters }
}

sealed interface CoverageCalculation {
    data class Ready(val preview: FieldCoveragePreview) : CoverageCalculation
    data class Invalid(val reason: String) : CoverageCalculation
}

/** Bounded scanline clipping. Concave fields produce separate passes, never cross-field connectors. */
object FieldCoveragePlanner {
    const val MAX_PASSES = 500
    internal const val METERS_PER_RADIAN = 6_371_000.0
    private const val EPS = 0.000001

    fun settingsError(width: Double?, overlap: Double?, headland: Double? = 0.0): String? = when {
        width == null || !width.isFinite() || width !in 0.1..50.0 -> "Enter a tool width from 0.1 to 50 metres."
        overlap == null || !overlap.isFinite() || overlap !in 0.0..80.0 -> "Enter overlap from 0 to 80 percent."
        headland == null || !headland.isFinite() || headland !in 0.0..100.0 -> "Enter a headland width from 0 to 100 metres."
        else -> null
    }

    fun calculate(
        vertices: List<DraftWaypoint>, width: Double, overlap: Double, eastWest: Boolean = false, headland: Double = 0.0,
    ): CoverageCalculation {
        settingsError(width, overlap, headland)?.let { return CoverageCalculation.Invalid(it) }
        if (vertices.size !in 3..MissionDraft.MAX_FENCE_VERTICES) {
            return CoverageCalculation.Invalid("Add 3 to 64 field-outline vertices first.")
        }
        if (vertices.any { abs(it.latitude) > 80 }) {
            return CoverageCalculation.Invalid("Coverage preview supports fields between 80° south and 80° north.")
        }
        val origin = vertices.first()
        val longitudeScale = cos(Math.toRadians(origin.latitude))
        val outline = vertices.map {
            val deltaLongitude = ((it.longitude - origin.longitude + 540.0) % 360.0) - 180.0
            CoveragePoint(Math.toRadians(deltaLongitude) * METERS_PER_RADIAN * longitudeScale,
                Math.toRadians(it.latitude - origin.latitude) * METERS_PER_RADIAN)
        }
        val spanX = outline.maxOf { it.x } - outline.minOf { it.x }
        val spanY = outline.maxOf { it.y } - outline.minOf { it.y }
        if (max(spanX, spanY) > 2_000) return CoverageCalculation.Invalid("Use a field outline no wider or taller than 2 km for this preview.")
        if (outline.indices.any { i -> (i + 1 until outline.size).any { j ->
            hypot(outline[i].x - outline[j].x, outline[i].y - outline[j].y) < 0.001
        } }) return CoverageCalculation.Invalid("Remove repeated field-outline coordinates.")
        for (i in outline.indices) for (j in i + 1 until outline.size) {
            if (j == i + 1 || i == 0 && j == outline.lastIndex) continue
            if (intersects(outline[i], outline[(i + 1) % outline.size], outline[j], outline[(j + 1) % outline.size])) {
                return CoverageCalculation.Invalid("The field outline crosses or touches itself. Reorder its vertices.")
            }
        }
        val area = abs(outline.indices.sumOf { i ->
            val a = outline[i]; val b = outline[(i + 1) % outline.size]
            a.x * b.y - b.x * a.y
        }) / 2
        if (area < 1.0 || min(spanX, spanY) < 0.01) {
            return CoverageCalculation.Invalid("The field outline needs at least 1 m² of usable area.")
        }
        // Swap the working axes for east/west passes; the returned diagram remains north-up.
        fun work(p: CoveragePoint) = if (eastWest) CoveragePoint(p.y, p.x) else p
        val polygon = outline.map(::work)
        val left = polygon.minOf { it.x }
        val right = polygon.maxOf { it.x }
        val spacing = width * (1 - overlap / 100)
        val rows = mutableListOf<Double>()
        val clearance = if (headland > 0) headland + width / 2 else width / 2
        if (right - left <= clearance * 2) rows.add((left + right) / 2) else {
            val first = left + clearance
            val last = right - clearance
            var x = first
            while (x < last - EPS) {
                if (rows.size >= MAX_PASSES) return tooManyPasses()
                rows.add(x)
                x = first + rows.size * spacing
            }
            rows.add(last) // A closer final pass covers the far edge of a rectangular field.
        }
        if (rows.size > MAX_PASSES) return tooManyPasses()
        val passes = mutableListOf<CoveragePass>()
        rows.forEachIndexed { row, x ->
            // Half-open edge crossings count a vertex once; vertical boundary edges do not cross.
            val crossings = polygon.indices.mapNotNull { i ->
                val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
                if ((a.x <= x && x < b.x) || (b.x <= x && x < a.x))
                    a.y + (x - a.x) * (b.y - a.y) / (b.x - a.x) else null
            }.sorted()
            if (crossings.size % 2 != 0) return CoverageCalculation.Invalid("Could not clip this field outline reliably. Check its vertices.")
            val rawPairs = crossings.chunked(2).map { it[0] to it[1] }
            // Removing boundary capsules keeps the whole ideal tool strip conservatively away
            // from the headland, including around concave corners. Zero preserves Phase 76.
            val clipped = if (headland == 0.0) rawPairs else subtractBoundary(rawPairs, polygon, x, clearance)
            val pairs = if (row % 2 == 0) clipped else clipped.reversed()
            for (pair in pairs) {
                if (pair.second - pair.first <= EPS) continue
                if (passes.size >= MAX_PASSES) return tooManyPasses()
                val a = CoveragePoint(x, pair.first); val b = CoveragePoint(x, pair.second)
                passes.add(if (row % 2 == 0) CoveragePass(work(a), work(b)) else CoveragePass(work(b), work(a)))
            }
        }
        if (passes.isEmpty()) return CoverageCalculation.Invalid(if (headland > 0) "No working passes fit outside the headland. Reduce headland or tool width." else "No working passes fit this outline. Check the outline and tool width.")
        val preview = FieldCoveragePreview(outline, passes, width, overlap, spacing, area, headland)
        return CoverageCalculation.Ready(preview.copy(areaEstimate = CoverageAreaEstimator.estimate(preview)))
    }

    private fun subtractBoundary(initial: List<Pair<Double, Double>>, polygon: List<CoveragePoint>, x: Double, clearance: Double): List<Pair<Double, Double>> {
        var result = initial
        // A microscopic tolerance allows tangent rows while staying within numerical precision.
        val radius = max(0.0, clearance - EPS)
        for (i in polygon.indices) {
            val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
            for (cut in capsuleIntervals(a, b, x, radius)) {
                result = result.flatMap { (lo, hi) ->
                    if (cut.second <= lo || cut.first >= hi) listOf(lo to hi) else buildList {
                        if (cut.first > lo + EPS) add(lo to min(hi, cut.first))
                        if (cut.second < hi - EPS) add(max(lo, cut.second) to hi)
                    }
                }
                if (result.isEmpty()) return result
            }
        }
        return result
    }

    /** Intersections of x=constant with a segment's radius-wide capsule (strip + end circles). */
    private fun capsuleIntervals(a: CoveragePoint, b: CoveragePoint, x: Double, radius: Double): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>()
        for (p in listOf(a, b)) {
            val dx = x - p.x
            if (abs(dx) < radius) {
                val dy = sqrt(max(0.0, radius * radius - dx * dx))
                result.add((p.y - dy) to (p.y + dy))
            }
        }
        val dx = b.x - a.x; val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        var lo = Double.NEGATIVE_INFINITY; var hi = Double.POSITIVE_INFINITY
        fun restrict(coefficient: Double, constant: Double, lower: Double, upper: Double): Boolean {
            if (abs(coefficient) < 1e-12) return constant in lower..upper
            val p = (lower - constant) / coefficient; val q = (upper - constant) / coefficient
            lo = max(lo, min(p, q)); hi = min(hi, max(p, q))
            return lo < hi
        }
        val dotConstant = dx * (x - a.x) - dy * a.y
        val crossConstant = dx * (-a.y) - dy * (x - a.x)
        val limit = radius * sqrt(len2)
        if (restrict(dy, dotConstant, 0.0, len2) && restrict(dx, crossConstant, -limit, limit) && lo.isFinite() && hi.isFinite()) result.add(lo to hi)
        return result
    }

    private fun tooManyPasses() = CoverageCalculation.Invalid("This preview exceeds 500 working passes. Use a smaller field section or revise width and overlap.")

    private fun intersects(a: CoveragePoint, b: CoveragePoint, c: CoveragePoint, d: CoveragePoint): Boolean {
        fun cross(p: CoveragePoint, q: CoveragePoint, r: CoveragePoint) =
            (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
        fun on(p: CoveragePoint, q: CoveragePoint, r: CoveragePoint) = abs(cross(p, q, r)) <= EPS &&
            r.x >= min(p.x, q.x) - EPS && r.x <= max(p.x, q.x) + EPS &&
            r.y >= min(p.y, q.y) - EPS && r.y <= max(p.y, q.y) + EPS
        val abC = cross(a, b, c); val abD = cross(a, b, d)
        val cdA = cross(c, d, a); val cdB = cross(c, d, b)
        return (abC * abD < 0 && cdA * cdB < 0) || on(a, b, c) || on(a, b, d) || on(c, d, a) || on(c, d, b)
    }
}
