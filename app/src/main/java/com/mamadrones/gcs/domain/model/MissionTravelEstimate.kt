package com.mamadrones.gcs.domain.model

data class DraftLegTravelEstimate(
    val fromIndex: Int,
    val toIndex: Int,
    val distanceMeters: Double,
    val travelSeconds: Double,
)

/** Constant-speed arithmetic for a local draft, never a speed command or operational ETA. */
data class MissionTravelEstimate(
    val planningSpeedKmh: Double,
    val legs: List<DraftLegTravelEstimate>,
) {
    val totalDistanceMeters: Double get() = legs.sumOf { it.distanceMeters }
    val totalTravelSeconds: Double? get() = legs.takeIf { it.isNotEmpty() }?.sumOf { it.travelSeconds }

    companion object {
        // UI/calculator bounds only. The operator's 8 km/h requirement is NOT enforced on hardware here.
        const val MIN_PLANNING_SPEED_KMH = 0.01
        const val MAX_PLANNING_SPEED_KMH = 8.0

        fun calculate(draft: MissionDraft, planningSpeedKmh: Double): MissionTravelEstimate {
            require(planningSpeedKmh.isFinite() &&
                planningSpeedKmh in MIN_PLANNING_SPEED_KMH..MAX_PLANNING_SPEED_KMH)
            val speedMetersPerSecond = planningSpeedKmh / 3.6
            return MissionTravelEstimate(planningSpeedKmh, draft.waypoints.zipWithNext().mapIndexed { index, (from, to) ->
                val distance = from.distanceMetersTo(to)
                DraftLegTravelEstimate(index + 1, index + 2, distance, distance / speedMetersPerSecond)
            })
        }
    }
}
