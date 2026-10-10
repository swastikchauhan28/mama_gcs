package com.mamadrones.gcs.core.diagnostics

import com.mamadrones.gcs.domain.model.VehicleState
import java.io.OutputStream
import java.time.Instant

/** Fixed allowlist: never serialize VehicleState, endpoint settings or raw messages wholesale. */
object TelemetryEvidenceReport {
    const val MAX_BYTES = 16 * 1024

    fun capture(vehicle: VehicleState, capturedAt: Long): String = buildString {
        val d = vehicle.mavlinkDiagnostics
        val started = d.startedAtEpochMillis
        fun count(value: Long) = if (started == null) "NOT RECORDED" else value.toString()
        fun timestamp(value: Long?): String = when {
            value == null -> "NOT RECEIVED"
            started == null || value < started -> "NOT RECORDED IN THIS SESSION"
            value > capturedAt -> "CLOCK INCONSISTENT (timestamp after capture)"
            else -> "${Instant.ofEpochMilli(value)} | age at capture ${capturedAt - value} ms"
        }
        appendLine("MAMA GCS - TELEMETRY EVIDENCE REPORT v1")
        appendLine("Captured UTC: ${Instant.ofEpochMilli(capturedAt)}")
        appendLine("Frozen snapshot; not a live feed or a complete flight/rover log.")
        appendLine("Not authentication, a health verdict, safety approval or proof of control.")
        appendLine()
        appendLine("SESSION")
        appendLine("State: ${when { started == null -> "NO SESSION"; d.active -> "ACTIVE AT CAPTURE"; else -> "LAST SESSION - CLOSED" }}")
        appendLine("Transport: ${if (started == null) "NOT RECORDED" else d.linkKind.name}")
        appendLine("Session started UTC: ${started?.let { Instant.ofEpochMilli(it).toString() } ?: "NOT RECORDED"}")
        appendLine("Vehicle connection at capture: ${vehicle.connectionStatus.name}")
        val source = if (started != null && vehicle.lastHeartbeatAtEpochMillis?.let { it >= started } == true) {
            "system ${vehicle.systemId?.takeIf { it in 1..255 } ?: "UNKNOWN"}, component ${vehicle.componentId?.takeIf { it in 1..255 } ?: "UNKNOWN"}"
        } else "NOT OBSERVED"
        appendLine("Observed protocol source (not paired identity): $source")
        appendLine("Last heartbeat: ${timestamp(vehicle.lastHeartbeatAtEpochMillis)}")
        appendLine()
        appendLine("DECODER TOTALS - decoded includes source-filtered messages")
        listOf(
            "Input chunks" to d.receivedChunks, "Input bytes" to d.receivedBytes,
            "Decoded supported messages" to d.decodedMessages,
            "Decoded MAVLink 1" to d.decodedV1Messages, "Decoded MAVLink 2" to d.decodedV2Messages,
            "Accepted from selected autopilot" to d.acceptedMessages,
            "Ignored before selection" to d.ignoredBeforeHeartbeat, "Ignored other source" to d.ignoredOtherSource,
            "Checksum failures" to d.checksumFailures, "Malformed payloads" to d.malformedPayloads,
            "Unsupported message candidates" to d.unsupportedMessages, "Unsupported flags" to d.unsupportedFlags,
            "Signed candidates rejected" to d.signedPacketsRejected,
        ).forEach { (label, value) -> appendLine("$label: ${count(value)}") }
        appendLine("Last unsupported message ID: ${if (started == null) "NOT RECORDED" else d.lastUnsupportedMessageId ?: "NONE RECORDED"}")
        appendLine("Last input: ${timestamp(d.lastBytesAtEpochMillis)}")
        appendLine("Last accepted message: ${timestamp(d.lastAcceptedAtEpochMillis)}")
        appendLine("These are not packet-loss measurements. Rejected/unsupported candidates are not authenticated.")
        appendLine()
        appendLine("SAMPLE RECEIPT TIMES - do not establish valid sensor readings")
        listOf("GPS" to vehicle.gps.lastUpdatedAtEpochMillis,
            "Global position" to vehicle.position.lastUpdatedAtEpochMillis,
            "Attitude" to vehicle.attitude.lastUpdatedAtEpochMillis,
            "Rover instruments" to vehicle.roverHud.lastUpdatedAtEpochMillis,
            "System status" to vehicle.systemStatus.lastUpdatedAtEpochMillis,
            "Battery summary" to vehicle.battery.lastUpdatedAtEpochMillis,
        ).forEach { (label, value) -> appendLine("$label: ${timestamp(value)}") }
        appendLine()
        appendLine("LIMITATIONS / PRIVACY")
        appendLine("No GPS coordinates, track, device names/addresses, network endpoints, account data,")
        appendLine("parameter contents or STATUSTEXT/raw packet payloads are included.")
        appendLine("Firmware, wiring, remote interface and physical stop behavior are not verified by this report.")
        appendLine("Vehicle commands and app emergency stop remain unavailable. Use the physical safety system.")
        appendLine("Opening the save picker can close foreground-only telemetry; reconnect manually if needed.")
    }

    /** Closes streams on success or failure. A failed write can leave a partial document. */
    fun write(content: String, openStream: () -> OutputStream?) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Report exceeds the supported size." }
        val stream = openStream() ?: throw java.io.IOException("No document stream")
        stream.use { it.write(bytes); it.flush() }
    }
}
