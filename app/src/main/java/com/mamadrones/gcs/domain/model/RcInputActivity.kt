package com.mamadrones.gcs.domain.model

enum class RcActivityStatus { IDLE, OBSERVING, STOPPED, INTERRUPTED }

data class RcActivitySession(val startedAt: Long, val link: TelemetryLinkKind, val system: Int, val component: Int)
data class RcChannelRange(val minimum: Int, val maximum: Int) {
    val span: Int get() = maximum - minimum
}

/** At most 18 ranges, held only while the page exists. No raw history or calibration. */
data class RcActivitySnapshot(
    val status: RcActivityStatus = RcActivityStatus.IDLE,
    val session: RcActivitySession? = null,
    val ranges: List<RcChannelRange?> = emptyList(),
    val lastSampleAt: Long? = null,
    val lastCheckedAt: Long? = null,
    val reason: String? = null,
)

/** Consumes the repository's already source-filtered RC samples; never accesses a transport. */
object RcInputActivity {
    private const val FRESHNESS_MS = 5_000L

    fun unavailableReason(vehicle: VehicleState, now: Long): String? {
        val key = session(vehicle) ?: return "Waiting for a selected autopilot in an active receive session."
        if (!vehicle.connected || !vehicle.mavlinkDiagnostics.active)
            return "The telemetry link is not live."
        fun fresh(timestamp: Long?) = timestamp != null && timestamp >= key.startedAt &&
            timestamp <= now && now - timestamp <= FRESHNESS_MS
        if (!fresh(vehicle.lastHeartbeatAtEpochMillis)) return "Waiting for a fresh autopilot heartbeat."
        if (!fresh(vehicle.rcInput.lastUpdatedAtEpochMillis)) return "Waiting for fresh RC_CHANNELS from this session."
        if (values(vehicle).none { it != null }) return "No usable RC channel values were reported."
        return null
    }

    fun start(vehicle: VehicleState, now: Long): RcActivitySnapshot {
        unavailableReason(vehicle, now)?.let { return RcActivitySnapshot(reason = it) }
        return RcActivitySnapshot(RcActivityStatus.OBSERVING, session(vehicle),
            values(vehicle).map { value -> value?.let { RcChannelRange(it, it) } },
            vehicle.rcInput.lastUpdatedAtEpochMillis, now)
    }

    fun observe(current: RcActivitySnapshot, vehicle: VehicleState, now: Long): RcActivitySnapshot {
        if (current.status != RcActivityStatus.OBSERVING) return current
        val reason = when {
            current.lastCheckedAt == null || now < current.lastCheckedAt -> "The observation clock moved backwards."
            current.session != session(vehicle) -> "The receive session or selected autopilot changed."
            else -> unavailableReason(vehicle, now)
        }
        if (reason != null) return current.copy(status = RcActivityStatus.INTERRUPTED, reason = reason)
        val sampleAt = requireNotNull(vehicle.rcInput.lastUpdatedAtEpochMillis)
        if (current.lastSampleAt != null && sampleAt < current.lastSampleAt)
            return current.copy(status = RcActivityStatus.INTERRUPTED, reason = "The RC sample timestamp moved backwards.")
        if (sampleAt == current.lastSampleAt) return current.copy(lastCheckedAt = now)
        val next = values(vehicle).mapIndexed { index, value ->
            val previous = current.ranges.getOrNull(index)
            when {
                value == null -> previous
                previous == null -> RcChannelRange(value, value)
                else -> RcChannelRange(minOf(previous.minimum, value), maxOf(previous.maximum, value))
            }
        }
        return current.copy(ranges = next, lastSampleAt = sampleAt, lastCheckedAt = now)
    }

    /** Validate the final snapshot before honoring a stop so stale data is never called live. */
    fun stop(current: RcActivitySnapshot, vehicle: VehicleState, now: Long): RcActivitySnapshot {
        val checked = observe(current, vehicle, now)
        return if (checked.status == RcActivityStatus.OBSERVING)
            checked.copy(status = RcActivityStatus.STOPPED, reason = "Stopped by the operator.") else checked
    }

    private fun session(vehicle: VehicleState): RcActivitySession? {
        val started = vehicle.mavlinkDiagnostics.startedAtEpochMillis?.takeIf { it >= 0 } ?: return null
        val system = vehicle.systemId?.takeIf { it in 1..255 } ?: return null
        val component = vehicle.componentId?.takeIf { it in 1..255 } ?: return null
        return RcActivitySession(started, vehicle.mavlinkDiagnostics.linkKind, system, component)
    }

    private fun values(vehicle: VehicleState): List<Int?> {
        val sample = vehicle.rcInput
        val count = sample.channelCount?.takeIf { it in 1..255 } ?: 0
        return List(18) { index ->
            sample.channelsMicroseconds.getOrNull(index)?.takeIf { index < count && it in 0..65_534 }
        }
    }
}
