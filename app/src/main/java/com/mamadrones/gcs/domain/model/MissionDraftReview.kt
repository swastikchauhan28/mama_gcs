package com.mamadrones.gcs.domain.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Local geometric observations; never a vehicle-readiness or safety verdict. */
data class MissionDraftReview(
    val hasRouteGeometry: Boolean,
    /** One-based index of each leg whose adjacent waypoints have identical coordinates. */
    val zeroLengthLegs: List<Int>,
    val hasUsableKeepInOutline: Boolean,
    val keepInOutlineIssue: String?,
    /** One-based route waypoint indices outside the compact local polygon. */
    val waypointsOutsideKeepIn: List<Int>,
    /** One-based leg indices whose straight coordinate segment leaves the polygon. */
    val legsOutsideKeepIn: List<Int>,
) {
    companion object {
        fun inspect(draft: MissionDraft): MissionDraftReview {
            val geometry = LocalFenceGeometry.create(draft.keepInFence)
            val usable = geometry != null && geometry.issue == null
            return MissionDraftReview(
                hasRouteGeometry = draft.waypoints.size >= 2,
                zeroLengthLegs = draft.waypoints.zipWithNext().mapIndexedNotNull { index, (from, to) ->
                    (index + 1).takeIf { from.latitude == to.latitude && from.longitude == to.longitude }
                },
                hasUsableKeepInOutline = usable,
                keepInOutlineIssue = if (draft.keepInFence.isEmpty()) null else geometry?.issue,
                waypointsOutsideKeepIn = if (!usable) emptyList() else draft.waypoints.mapIndexedNotNull { index, point ->
                    (index + 1).takeIf { !geometry!!.contains(geometry.project(point)) }
                },
                legsOutsideKeepIn = if (!usable) emptyList() else draft.waypoints.zipWithNext().mapIndexedNotNull { index, (from, to) ->
                    (index + 1).takeIf { !geometry!!.containsSegment(geometry.project(from), geometry.project(to)) }
                },
            )
        }
    }
}

/** Geometry is limited to compact field outlines using a local equirectangular plane. */
private class LocalFenceGeometry private constructor(
    private val vertices: List<PlanePoint>,
    private val baseLongitude: Double,
    private val longitudeScale: Double,
    val issue: String?,
) {
    fun project(point: DraftWaypoint): PlanePoint {
        val delta = ((point.longitude - baseLongitude + 540.0) % 360.0) - 180.0
        return PlanePoint(delta * longitudeScale, point.latitude)
    }

    fun contains(point: PlanePoint): Boolean {
        var inside = false
        for (index in vertices.indices) {
            val a = vertices[index]
            val b = vertices[(index + 1) % vertices.size]
            if (onSegment(a, b, point)) return true
            if ((a.y > point.y) != (b.y > point.y)) {
                val crossingX = a.x + (point.y - a.y) * (b.x - a.x) / (b.y - a.y)
                if (point.x < crossingX) inside = !inside
            }
        }
        return inside
    }

    /** Splits a straight draft leg at outline intersections and tests each resulting interval. */
    fun containsSegment(start: PlanePoint, end: PlanePoint): Boolean {
        if (!contains(start) || !contains(end)) return false
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= EPSILON) return true

        val cuts = mutableListOf(0.0, 1.0)
        for (index in vertices.indices) {
            addIntersectionParameters(start, end, vertices[index], vertices[(index + 1) % vertices.size], cuts)
        }
        val ordered = cuts.map { it.coerceIn(0.0, 1.0) }.sorted().fold(mutableListOf<Double>()) { result, value ->
            if (result.isEmpty() || abs(result.last() - value) > EPSILON) result += value
            result
        }
        return ordered.zipWithNext().all { (from, to) ->
            val fraction = (from + to) / 2.0
            contains(PlanePoint(start.x + dx * fraction, start.y + dy * fraction))
        }
    }

    private fun addIntersectionParameters(p: PlanePoint, q: PlanePoint, a: PlanePoint, b: PlanePoint, cuts: MutableList<Double>) {
        val rx = q.x - p.x
        val ry = q.y - p.y
        val sx = b.x - a.x
        val sy = b.y - a.y
        val denominator = cross(rx, ry, sx, sy)
        val qpx = a.x - p.x
        val qpy = a.y - p.y
        if (abs(denominator) > EPSILON) {
            val t = cross(qpx, qpy, sx, sy) / denominator
            val u = cross(qpx, qpy, rx, ry) / denominator
            if (t in -EPSILON..(1.0 + EPSILON) && u in -EPSILON..(1.0 + EPSILON)) cuts += t
        } else if (abs(cross(qpx, qpy, rx, ry)) <= EPSILON) {
            val lengthSquared = rx * rx + ry * ry
            if (lengthSquared > EPSILON) {
                cuts += ((a.x - p.x) * rx + (a.y - p.y) * ry) / lengthSquared
                cuts += ((b.x - p.x) * rx + (b.y - p.y) * ry) / lengthSquared
            }
        }
    }

    companion object {
        private const val EPSILON = 1e-10

        fun create(fence: List<DraftWaypoint>): LocalFenceGeometry? {
            if (fence.isEmpty()) return null
            val distinct = fence.distinctBy { it.latitude to it.longitude }
            val baseLongitude = distinct.first().longitude
            val latitudeScale = cos(Math.toRadians(distinct.map { it.latitude }.average())).coerceAtLeast(1e-6)
            val vertices = fence.map { point ->
                val delta = ((point.longitude - baseLongitude + 540.0) % 360.0) - 180.0
                PlanePoint(delta * latitudeScale, point.latitude)
            }
            val issue = when {
                distinct.size < 3 -> "ADD AT LEAST 3 DISTINCT VERTICES"
                distinct.size != fence.size -> "OUTLINE HAS REPEATED VERTICES"
                abs(signedArea(vertices)) <= EPSILON -> "OUTLINE AREA IS ZERO"
                hasSelfIntersection(vertices) -> "OUTLINE CROSSES ITSELF"
                else -> null
            }
            return LocalFenceGeometry(vertices, baseLongitude, latitudeScale, issue)
        }

        private fun hasSelfIntersection(points: List<PlanePoint>): Boolean {
            for (first in points.indices) {
                val firstNext = (first + 1) % points.size
                for (second in first + 1 until points.size) {
                    val secondNext = (second + 1) % points.size
                    if (firstNext == second || secondNext == first) continue
                    if (segmentsIntersect(points[first], points[firstNext], points[second], points[secondNext])) return true
                }
            }
            return false
        }

        private fun signedArea(points: List<PlanePoint>): Double = points.indices.sumOf { index ->
            val a = points[index]
            val b = points[(index + 1) % points.size]
            a.x * b.y - b.x * a.y
        } / 2.0

        private fun segmentsIntersect(a: PlanePoint, b: PlanePoint, c: PlanePoint, d: PlanePoint): Boolean {
            val o1 = cross(b.x - a.x, b.y - a.y, c.x - a.x, c.y - a.y)
            val o2 = cross(b.x - a.x, b.y - a.y, d.x - a.x, d.y - a.y)
            val o3 = cross(d.x - c.x, d.y - c.y, a.x - c.x, a.y - c.y)
            val o4 = cross(d.x - c.x, d.y - c.y, b.x - c.x, b.y - c.y)
            return (o1 * o2 < 0.0 && o3 * o4 < 0.0) ||
                (abs(o1) <= EPSILON && onSegment(a, b, c)) ||
                (abs(o2) <= EPSILON && onSegment(a, b, d)) ||
                (abs(o3) <= EPSILON && onSegment(c, d, a)) ||
                (abs(o4) <= EPSILON && onSegment(c, d, b))
        }

        private fun cross(ax: Double, ay: Double, bx: Double, by: Double) = ax * by - ay * bx

        private fun onSegment(a: PlanePoint, b: PlanePoint, p: PlanePoint): Boolean {
            val value = cross(p.x - a.x, p.y - a.y, b.x - a.x, b.y - a.y)
            return abs(value) <= EPSILON && p.x >= min(a.x, b.x) - EPSILON && p.x <= max(a.x, b.x) + EPSILON &&
                p.y >= min(a.y, b.y) - EPSILON && p.y <= max(a.y, b.y) + EPSILON
        }
    }
}

private data class PlanePoint(val x: Double, val y: Double)
