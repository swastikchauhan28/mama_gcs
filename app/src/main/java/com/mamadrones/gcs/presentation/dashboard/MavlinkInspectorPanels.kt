package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.components.PanelSpec

object MavlinkInspectorPanels {
    // Display recency only; not an expected stream rate or safety deadline.
    const val RECENT_MILLIS = 5_000L

    fun visibleTypes(diagnostics: MavlinkDiagnostics, query: String, observedOnly: Boolean) =
        ObservedMavlinkMessage.entries.filter { type ->
            val term = query.trim()
            (type.name.contains(term, ignoreCase = true) || type.messageId.toString().contains(term)) &&
                (!observedOnly || diagnostics.messageObservations.any { it.type == type && it.decoded > 0 })
        }

    fun panel(type: ObservedMavlinkMessage, diagnostics: MavlinkDiagnostics, now: Long): PanelSpec {
        val sample = diagnostics.messageObservations.firstOrNull { it.type == type } ?: MavlinkMessageObservation(type)
        val started = diagnostics.startedAtEpochMillis
        fun age(timestamp: Long?): Long? = timestamp?.takeIf {
            started != null && started >= 0 && it >= started && it <= now
        }?.let { now - it }
        fun ageLabel(timestamp: Long?): String = when {
            timestamp == null -> "NOT RECEIVED"
            age(timestamp) == null -> "TIMESTAMP UNAVAILABLE"
            else -> "${age(timestamp)!! / 1_000}s ago${if (diagnostics.active) "" else " · HISTORICAL"}"
        }
        val acceptedAge = age(sample.lastAcceptedAtEpochMillis)
        val status = when {
            sample.decoded == 0L -> "NOT OBSERVED"
            !diagnostics.active -> "LAST SESSION · HISTORICAL"
            sample.accepted == 0L -> "DECODED · NOT ACCEPTED"
            acceptedAge == null -> "ACCEPTED · TIME UNAVAILABLE"
            acceptedAge <= RECENT_MILLIS -> "RECENT ACCEPTED MESSAGE"
            else -> "NO RECENT ACCEPTED MESSAGE"
        }
        return PanelSpec("${type.name} · ID ${type.messageId}", status, listOf(
            "Decoded (all sources)" to sample.decoded.toString(),
            "MAVLink 1 / 2" to "${sample.decodedV1} / ${sample.decodedV2}",
            "Accepted (selected autopilot)" to sample.accepted.toString(),
            "Ignored before selection" to sample.beforeHeartbeat.toString(),
            "Ignored other source" to sample.otherSource.toString(),
            "Last decoded (any source)" to ageLabel(sample.lastDecodedAtEpochMillis),
            "Last accepted" to ageLabel(sample.lastAcceptedAtEpochMillis),
        ), "Counts are complete decoded messages, not serial chunks, rates or packet-loss estimates. Receipt does not verify sensor values or authenticate a rover.")
    }
}
