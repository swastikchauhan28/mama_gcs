package com.mamadrones.gcs.domain.model

/** Fixed labels and monotonic times only; callers decide how to display or export ages. */
data class TcpHeartbeatTimelineEvent(val label: String, val atElapsedMillis: Long)

object TcpHeartbeatTimeline {
    const val MAX_EVENTS = 8

    fun sessionAvailable(sample: MavlinkDiagnostics, nowElapsedMillis: Long): Boolean {
        val started = sample.startedAtElapsedMillis
        return sample.linkKind == TelemetryLinkKind.TCP && started != null &&
            started >= 0 && nowElapsedMillis >= started
    }

    /** Newest first, and restricted to observations from the displayed MAVLink session. */
    fun recent(sample: MavlinkDiagnostics, nowElapsedMillis: Long,
        transitions: List<TcpSocketTransition>): List<TcpHeartbeatTimelineEvent> {
        if (!sessionAvailable(sample, nowElapsedMillis)) return emptyList()
        val started = checkNotNull(sample.startedAtElapsedMillis)
        return buildList {
            transitions.takeLast(TcpSocketTimeline.MAX_TRANSITIONS).forEach { transition ->
                if (transition.atElapsedMillis in started..nowElapsedMillis)
                    add(TcpHeartbeatTimelineEvent("TCP ${transition.status}", transition.atElapsedMillis))
            }
            sample.recentHeartbeatGaps.takeLast(3).forEach { gap ->
                if (gap.startedAtElapsedMillis >= started &&
                    gap.recoveredAtElapsedMillis in gap.startedAtElapsedMillis..nowElapsedMillis &&
                    gap.recoveredAtElapsedMillis - gap.startedAtElapsedMillis > 5_000L) {
                    add(TcpHeartbeatTimelineEvent("Heartbeat exceeded 5 seconds", gap.startedAtElapsedMillis + 5_000L))
                    add(TcpHeartbeatTimelineEvent("Heartbeat recovered", gap.recoveredAtElapsedMillis))
                }
            }
            val last = sample.timingHeartbeatAtElapsedMillis
            if (last != null && last in started..nowElapsedMillis) {
                if (sample.active && nowElapsedMillis - last > 5_000L)
                    add(TcpHeartbeatTimelineEvent("Heartbeat exceeded 5 seconds", last + 5_000L))
                else if (sample.recentHeartbeatGaps.none { it.recoveredAtElapsedMillis == last })
                    add(TcpHeartbeatTimelineEvent("Latest accepted heartbeat", last))
            }
        }.sortedBy { it.atElapsedMillis }.takeLast(MAX_EVENTS).asReversed()
    }
}
