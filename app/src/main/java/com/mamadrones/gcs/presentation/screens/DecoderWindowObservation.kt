package com.mamadrones.gcs.presentation.screens

import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import com.mamadrones.gcs.presentation.components.PanelSpec

/** Screen-local comparison of one receive session's scalar counters; never a packet-loss estimate. */
sealed interface DecoderWindowState {
    data class Running(
        val sessionStartedAtEpochMillis: Long,
        val sessionStartedAtElapsedMillis: Long,
        val linkKind: TelemetryLinkKind,
        val observationStartedAtElapsedMillis: Long,
        val baseline: DecoderWindowCounts,
    ) : DecoderWindowState

    data class Finished(
        val completed: Boolean,
        val elapsedMillis: Long,
        val reason: String? = null,
        val differences: DecoderWindowCounts? = null,
    ) : DecoderWindowState
}

data class DecoderWindowCounts(
    val acceptedMessages: Long,
    val insideRescanCandidates: Long,
    val outsideRescanCandidates: Long,
) {
    fun minus(other: DecoderWindowCounts) = DecoderWindowCounts(
        acceptedMessages - other.acceptedMessages,
        insideRescanCandidates - other.insideRescanCandidates,
        outsideRescanCandidates - other.outsideRescanCandidates,
    )

    fun isAtLeast(other: DecoderWindowCounts) = acceptedMessages >= other.acceptedMessages &&
        insideRescanCandidates >= other.insideRescanCandidates &&
        outsideRescanCandidates >= other.outsideRescanCandidates
}

object DecoderWindowObservation {
    const val DURATION_MILLIS = 30_000L

    private fun counts(sample: MavlinkDiagnostics): DecoderWindowCounts? {
        val total = sample.checksumFailures
        val inside = sample.overlappingRescanChecksumFailures
        if (sample.acceptedMessages < 0 || total < 0 || inside < 0 || inside > total) return null
        return DecoderWindowCounts(sample.acceptedMessages, inside, total - inside)
    }

    fun canStart(sample: MavlinkDiagnostics, nowElapsedMillis: Long): Boolean = sample.active &&
        sample.startedAtEpochMillis != null && sample.startedAtElapsedMillis?.let {
            it >= 0 && nowElapsedMillis >= it
        } == true && counts(sample) != null

    fun start(sample: MavlinkDiagnostics, nowElapsedMillis: Long): DecoderWindowState.Running? {
        if (!canStart(sample, nowElapsedMillis)) return null
        return DecoderWindowState.Running(
            sessionStartedAtEpochMillis = requireNotNull(sample.startedAtEpochMillis),
            sessionStartedAtElapsedMillis = requireNotNull(sample.startedAtElapsedMillis),
            linkKind = sample.linkKind,
            observationStartedAtElapsedMillis = nowElapsedMillis,
            baseline = requireNotNull(counts(sample)),
        )
    }

    fun advance(state: DecoderWindowState?, sample: MavlinkDiagnostics, nowElapsedMillis: Long): DecoderWindowState? {
        if (state !is DecoderWindowState.Running) return state
        if (nowElapsedMillis < state.observationStartedAtElapsedMillis) {
            return DecoderWindowState.Finished(false, 0, "Elapsed clock became inconsistent")
        }
        val elapsed = nowElapsedMillis - state.observationStartedAtElapsedMillis
        if (!sample.active || sample.startedAtEpochMillis != state.sessionStartedAtEpochMillis ||
            sample.startedAtElapsedMillis != state.sessionStartedAtElapsedMillis || sample.linkKind != state.linkKind) {
            return DecoderWindowState.Finished(false, elapsed, "Receive session ended or changed")
        }
        val current = counts(sample)
        if (current == null || !current.isAtLeast(state.baseline)) {
            return DecoderWindowState.Finished(false, elapsed, "Decoder counters reset or became inconsistent")
        }
        if (elapsed < DURATION_MILLIS) return state
        return DecoderWindowState.Finished(true, elapsed, differences = current.minus(state.baseline))
    }

    fun panel(state: DecoderWindowState?, sample: MavlinkDiagnostics, nowElapsedMillis: Long): PanelSpec {
        val note = "Receive-only changes in one session's counters. Completion may lag the 30-second target. " +
            "These are not packet-loss measurements, proof of physical corruption, authenticated source or vehicle readiness. " +
            "Leaving this page discards the observation."
        val status: String
        val rows: List<Pair<String, String>>
        when (state) {
            null -> {
                status = if (canStart(sample, nowElapsedMillis)) "READY" else "ACTIVE SESSION REQUIRED"
                rows = listOf("Target duration" to "30 s")
            }
            is DecoderWindowState.Running -> {
                status = "OBSERVING · ${state.linkKind}"
                rows = listOf("Elapsed" to "${(nowElapsedMillis - state.observationStartedAtElapsedMillis).coerceAtLeast(0) / 1_000} / 30 s")
            }
            is DecoderWindowState.Finished -> {
                status = if (state.completed) "COMPLETE" else "INCOMPLETE"
                rows = listOf("Observed duration" to "${state.elapsedMillis} ms") +
                    if (state.completed && state.differences != null) {
                        val differences = state.differences
                        listOf(
                            "Accepted selected-autopilot messages" to differences.acceptedMessages.toString(),
                            "Checksum candidates inside rescan span" to differences.insideRescanCandidates.toString(),
                            "Checksum candidates outside rescan span" to differences.outsideRescanCandidates.toString(),
                        )
                    } else listOf("Reason" to (state.reason ?: "Observation unavailable"))
            }
        }
        return PanelSpec("30-second decoder observation", status, rows, note)
    }
}
