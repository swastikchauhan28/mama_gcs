package com.mamadrones.gcs.domain.model

enum class SpeedObservation { UNAVAILABLE, BELOW_LIMIT, AT_LIMIT, ABOVE_LIMIT }
enum class SpeedReceiptStatus { NOT_RECEIVED, INVALID_TIME, FRESH, STALE }

/** One selected-source receive timestamp assessed against the same elapsed clock as the speed value. */
data class SpeedReceiptEvidence(
    val status: SpeedReceiptStatus = SpeedReceiptStatus.NOT_RECEIVED,
    val ageMillis: Long? = null,
)

data class RoverSpeedAssessment(
    val observation: SpeedObservation,
    val speedKmh: Double? = null,
    val reason: String? = null,
    val linkLive: Boolean = false,
    val heartbeatReceipt: SpeedReceiptEvidence = SpeedReceiptEvidence(),
    val speedReceipt: SpeedReceiptEvidence = SpeedReceiptEvidence(),
)

/** Display-only comparison. Never grants drive permission, transmits or changes parameters. */
object RoverSpeedMonitor {
    const val MAXIMUM_KMH = 8.0
    // Display freshness policy, not a hardware-approved failsafe deadline.
    const val FRESH_FOR_MILLIS = 5_000L

    fun assess(state: VehicleState, nowElapsedMillis: Long): RoverSpeedAssessment {
        val heartbeat = receipt(state.lastHeartbeatAtElapsedMillis, nowElapsedMillis)
        val speedSample = receipt(state.kinematicsLastUpdatedAtElapsedMillis, nowElapsedMillis)
        fun result(observation: SpeedObservation, speedKmh: Double? = null, reason: String? = null) =
            RoverSpeedAssessment(observation, speedKmh, reason, state.connected, heartbeat, speedSample)
        fun unavailable(reason: String) = result(SpeedObservation.UNAVAILABLE, reason = reason)
        if (!state.connected) return unavailable("No live vehicle connection")
        val missing = listOfNotNull(
            receiptIssue("Heartbeat", heartbeat),
            receiptIssue("GLOBAL_POSITION_INT speed sample", speedSample),
        )
        if (missing.isNotEmpty()) return unavailable(missing.joinToString("; "))
        val speed = state.speedMetersPerSecond
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: return unavailable("Valid ground speed not received")
        val kmh = speed * 3.6
        if (!kmh.isFinite()) return unavailable("Invalid ground-speed conversion")
        return result(when {
            kmh > MAXIMUM_KMH -> SpeedObservation.ABOVE_LIMIT
            kmh == MAXIMUM_KMH -> SpeedObservation.AT_LIMIT
            else -> SpeedObservation.BELOW_LIMIT
        }, kmh)
    }

    // Both arguments are elapsed-realtime values; future or invalid times cannot look fresh.
    private fun receipt(receivedAt: Long?, now: Long): SpeedReceiptEvidence {
        if (receivedAt == null) return SpeedReceiptEvidence()
        if (now < 0 || receivedAt < 0 || receivedAt > now)
            return SpeedReceiptEvidence(SpeedReceiptStatus.INVALID_TIME)
        val age = now - receivedAt
        return SpeedReceiptEvidence(if (age <= FRESH_FOR_MILLIS) SpeedReceiptStatus.FRESH
            else SpeedReceiptStatus.STALE, age)
    }

    private fun receiptIssue(name: String, evidence: SpeedReceiptEvidence): String? = when (evidence.status) {
        SpeedReceiptStatus.NOT_RECEIVED -> "$name not received"
        SpeedReceiptStatus.INVALID_TIME -> "$name has invalid receive time"
        SpeedReceiptStatus.STALE -> "$name older than 5 seconds"
        SpeedReceiptStatus.FRESH -> null
    }
}
