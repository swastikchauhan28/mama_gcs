package com.mamadrones.gcs.domain.model

enum class SpeedObservation { UNAVAILABLE, BELOW_LIMIT, AT_LIMIT, ABOVE_LIMIT }

data class RoverSpeedAssessment(
    val observation: SpeedObservation,
    val speedKmh: Double? = null,
    val reason: String? = null,
)

/** Display-only comparison. Never grants drive permission, transmits or changes parameters. */
object RoverSpeedMonitor {
    const val MAXIMUM_KMH = 8.0
    // Display freshness policy, not a hardware-approved failsafe deadline.
    const val FRESH_FOR_MILLIS = 5_000L

    fun assess(state: VehicleState, nowEpochMillis: Long): RoverSpeedAssessment {
        fun unavailable(reason: String) = RoverSpeedAssessment(SpeedObservation.UNAVAILABLE, reason = reason)
        if (!state.connected) return unavailable("No live vehicle connection")
        if (!fresh(state.lastHeartbeatAtEpochMillis, nowEpochMillis)) return unavailable("Heartbeat missing or older than 5 seconds")
        if (!fresh(state.kinematicsLastUpdatedAtEpochMillis, nowEpochMillis)) return unavailable("Speed sample missing or older than 5 seconds")
        val speed = state.speedMetersPerSecond
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: return unavailable("Valid ground speed not received")
        val kmh = speed * 3.6
        if (!kmh.isFinite()) return unavailable("Invalid ground-speed conversion")
        return RoverSpeedAssessment(when {
            kmh > MAXIMUM_KMH -> SpeedObservation.ABOVE_LIMIT
            kmh == MAXIMUM_KMH -> SpeedObservation.AT_LIMIT
            else -> SpeedObservation.BELOW_LIMIT
        }, kmh)
    }

    private fun fresh(receivedAt: Long?, now: Long): Boolean =
        receivedAt != null && receivedAt >= 0 && now >= receivedAt && now - receivedAt <= FRESH_FOR_MILLIS
}
