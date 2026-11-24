package com.mamadrones.gcs.presentation.map

/** Geographic rectangles used only for a conservative offline-availability hint. */
internal data class OfflineMapRectangle(
    val south: Double, val west: Double, val north: Double, val east: Double,
) {
    fun isValid(): Boolean = south.isFinite() && west.isFinite() && north.isFinite() && east.isFinite() &&
        south >= -90.0 && north <= 90.0 && west >= -180.0 && east <= 180.0 &&
        south < north && west < east

    fun contains(other: OfflineMapRectangle): Boolean =
        south <= other.south && west <= other.west && north >= other.north && east >= other.east
}

/** A strip is covered only if its complete regions span every latitude in the viewport. */
internal object OfflineViewportCoverage {
    fun contains(viewport: OfflineMapRectangle, completeRegions: List<OfflineMapRectangle>): Boolean {
        if (!viewport.isValid()) return false // Wrapped or invalid viewports must not claim coverage.
        val regions = completeRegions.filter { it.isValid() }
        if (regions.any { it.contains(viewport) }) return true
        val clipped = regions.mapNotNull { region ->
            val south = maxOf(viewport.south, region.south)
            val west = maxOf(viewport.west, region.west)
            val north = minOf(viewport.north, region.north)
            val east = minOf(viewport.east, region.east)
            OfflineMapRectangle(south, west, north, east).takeIf { it.isValid() }
        }
        val cuts = (listOf(viewport.west, viewport.east) + clipped.flatMap { listOf(it.west, it.east) })
            .distinct().sorted()
        for (index in 0 until cuts.lastIndex) {
            val west = cuts[index]
            val east = cuts[index + 1]
            if (west == east) continue
            var coveredNorth = viewport.south
            val intervals = clipped.asSequence().filter { it.west <= west && it.east >= east }
                .sortedBy { it.south }
            for (region in intervals) {
                if (region.south > coveredNorth) break
                coveredNorth = maxOf(coveredNorth, region.north)
                if (coveredNorth >= viewport.north) break
            }
            if (coveredNorth < viewport.north) return false
        }
        return true
    }
}
