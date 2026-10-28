package com.mamadrones.gcs.domain.model

import kotlin.math.abs
import kotlin.math.cos

data class CoverageMapPoint(val latitude: Double, val longitude: Double)
data class CoverageMapLine(val start: CoverageMapPoint, val end: CoverageMapPoint)

sealed interface CoverageMapProjectionResult {
    data class Ready(val lines: List<CoverageMapLine>, val gapSamples: List<CoverageMapPoint>?) : CoverageMapProjectionResult
    data class Unavailable(val reason: String) : CoverageMapProjectionResult
}

/** Inverse of the planner's local plane, for temporary display only. */
object CoverageMapProjection {
    fun project(preview: FieldCoveragePreview, outline: List<DraftWaypoint>): CoverageMapProjectionResult {
        if (outline.size < 3 || outline.indices.any { i ->
            abs(outline[i].longitude - outline[(i + 1) % outline.size].longitude) > 180.0
        }) return CoverageMapProjectionResult.Unavailable("Map preview is unavailable for date-line-crossing fields.")
        val origin = outline.first()
        val scale = cos(Math.toRadians(origin.latitude))
        fun toGeographic(point: CoveragePoint): CoverageMapPoint = CoverageMapPoint(
            origin.latitude + Math.toDegrees(point.y / FieldCoveragePlanner.METERS_PER_RADIAN),
            origin.longitude + Math.toDegrees(point.x / (FieldCoveragePlanner.METERS_PER_RADIAN * scale)),
        )
        val lines = preview.passes.map { CoverageMapLine(toGeographic(it.start), toGeographic(it.end)) }
        val gaps = preview.areaEstimate?.gapSampleCenters?.map(::toGeographic)
        fun invalid(point: CoverageMapPoint) =
            !point.latitude.isFinite() || !point.longitude.isFinite() || point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0
        if (lines.any { invalid(it.start) || invalid(it.end) } || gaps?.any(::invalid) == true)
            return CoverageMapProjectionResult.Unavailable("Map preview coordinates are outside supported map bounds.")
        return CoverageMapProjectionResult.Ready(lines, gaps)
    }
}
