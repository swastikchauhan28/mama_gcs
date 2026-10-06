package com.mamadrones.gcs.domain.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Local planning coordinates, never a downloaded mission or permission to execute one. */
data class DraftWaypoint(val id: String, val latitude: Double, val longitude: Double) {
    init {
        require(id.isNotBlank() && id.length <= 80)
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

data class MissionDraft(
    val name: String = "Untitled route",
    val waypoints: List<DraftWaypoint> = emptyList(),
    /** Optional device-local keep-in reference polygon; not uploaded or enforced by a vehicle. */
    val keepInFence: List<DraftWaypoint> = emptyList(),
) {
    init {
        require(name.isNotBlank() && name.length <= MAX_NAME_LENGTH)
        require(waypoints.size <= MAX_WAYPOINTS)
        require(waypoints.map { it.id }.distinct().size == waypoints.size)
        require(keepInFence.size <= MAX_FENCE_VERTICES)
        require(keepInFence.map { it.id }.distinct().size == keepInFence.size)
    }

    fun add(waypoint: DraftWaypoint) = copy(waypoints = waypoints + waypoint)
    fun edit(waypoint: DraftWaypoint): MissionDraft {
        require(waypoints.any { it.id == waypoint.id })
        return copy(waypoints = waypoints.map { if (it.id == waypoint.id) waypoint else it })
    }
    fun remove(id: String) = copy(waypoints = waypoints.filterNot { it.id == id })
    fun move(id: String, offset: Int): MissionDraft {
        require(offset == -1 || offset == 1)
        val from = waypoints.indexOfFirst { it.id == id }
        require(from >= 0)
        val to = from + offset
        if (to !in waypoints.indices) return this
        return copy(waypoints = waypoints.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Spherical straight-line surface distance; not terrain distance or a drivable route. */
    val distanceMeters: Double get() = waypoints.zipWithNext().sumOf { (a, b) ->
        val latA = Math.toRadians(a.latitude)
        val latB = Math.toRadians(b.latitude)
        val dLat = latB - latA
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = (sin(dLat / 2) * sin(dLat / 2) +
            cos(latA) * cos(latB) * sin(dLon / 2) * sin(dLon / 2)).coerceIn(0.0, 1.0)
        6_371_000.0 * 2 * atan2(sqrt(h), sqrt(1 - h))
    }

    companion object {
        const val MAX_WAYPOINTS = 250 // Local editor/storage bound, not an autopilot capability.
        const val MAX_FENCE_VERTICES = 64
        const val MAX_NAME_LENGTH = 80
    }
}

data class MissionLibraryEntry(
    val id: String,
    val draft: MissionDraft,
    val savedAtEpochMillis: Long
) {
    init {
        require(id.isNotBlank() && id.length <= 80)
        require(savedAtEpochMillis >= 0)
    }

    companion object {
        const val MAX_ENTRIES = 25
    }
}
