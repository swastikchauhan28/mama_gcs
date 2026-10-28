package com.mamadrones.gcs.core.diagnostics

import com.mamadrones.gcs.domain.model.ClassicConnectionAssessment
import com.mamadrones.gcs.domain.model.ClassicCheckStage
import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import java.io.IOException
import java.io.OutputStream
import java.time.Instant

/**
 * A bounded, explicit observation of the Classic receive checklist. It deliberately excludes
 * the paired-device identity and MAVLink contents, and must not be treated as a radio trace.
 */
object ClassicLinkEvidenceReport {
    const val MAX_BYTES = 12 * 1024

    fun capture(
        assessment: ClassicConnectionAssessment,
        connection: ConnectionState,
        diagnostics: MavlinkDiagnostics,
        capturedAtEpochMillis: Long,
    ): String = buildString {
        require(capturedAtEpochMillis >= 0)
        appendLine("MAMA GCS - CLASSIC RECEIVE EVIDENCE v1")
        appendLine("Captured UTC: ${Instant.ofEpochMilli(capturedAtEpochMillis)}")
        appendLine("Frozen in-memory observation; it does not update after capture.")
        appendLine("Transport: Bluetooth Classic / SPP")
        appendLine("Checklist stage: ${assessment.stage.name}")
        appendLine()
        appendLine("RECEIVE CHECKLIST")
        appendLine("Bluetooth socket: ${yesNo(assessment.socketOpen)}")
        appendLine("Recent serial bytes: ${yesNo(assessment.freshBytes)}")
        appendLine("Recent validated MAVLink: ${yesNo(assessment.freshDecodedMessage)}")
        appendLine("Fresh selected-autopilot heartbeat: ${yesNo(assessment.freshHeartbeat)}")
        appendLine("Fresh measurement groups: ${assessment.measurementMessages.safeGroups().ifEmpty { "NONE" }}")
        appendLine("Fresh selected-autopilot RC_CHANNELS: ${yesNo(assessment.freshRcChannels)}")
        appendLine("Socket packet chunks received: ${nonNegative(connection.packetStatistics.receivedPackets)}")
        appendLine()
        appendLine("CURRENT DECODER OBSERVATION")
        appendLine("Classic decoder active: ${yesNo(diagnostics.active && diagnostics.linkKind == TelemetryLinkKind.CLASSIC)}")
        appendLine("Decoder session start recorded: ${yesNo(diagnostics.startedAtEpochMillis != null && diagnostics.startedAtEpochMillis >= 0)}")
        appendLine("Input chunks: ${nonNegative(diagnostics.receivedChunks)}")
        appendLine("Input bytes: ${nonNegative(diagnostics.receivedBytes)}")
        appendLine("Raw 0xFE bytes (MAVLink 1 start candidates): ${nonNegative(diagnostics.mavlinkV1StartBytes)}")
        appendLine("Raw 0xFD bytes (MAVLink 2 start candidates): ${nonNegative(diagnostics.mavlinkV2StartBytes)}")
        appendLine("Decoded MAVLink 1 messages: ${nonNegative(diagnostics.decodedV1Messages)}")
        appendLine("Decoded MAVLink 2 messages: ${nonNegative(diagnostics.decodedV2Messages)}")
        appendLine("Accepted messages: ${nonNegative(diagnostics.acceptedMessages)}")
        appendLine("Checksum failures: ${nonNegative(diagnostics.checksumFailures)}")
        appendLine("Malformed payloads: ${nonNegative(diagnostics.malformedPayloads)}")
        appendLine("Unverified unsupported-ID candidates: ${nonNegative(diagnostics.unsupportedMessages)}")
        appendLine("Signed packets rejected: ${nonNegative(diagnostics.signedPacketsRejected)}")
        appendLine()
        appendLine("INTERPRETATION")
        appendLine(stageGuidance(assessment.stage))
        appendLine("Expected receive route is Cube -> R12 -> T12 / HC-05 -> phone. This report cannot detect, authenticate or prove each hop.")
        appendLine("Start-byte counts are raw byte patterns, not checksum-validated MAVLink frames; unrelated data may contain them.")
        appendLine("Bytes and heartbeat use elapsed time; measurement and RC freshness use calendar time and may change if the phone clock changes.")
        appendLine("RC receipt does not prove stick movement, SBUS wiring, neutral position or safe control.")
        appendLine("It does not prove vehicle identity, sensor accuracy, VESC data, command capability, stopping behaviour or speed enforcement.")
        appendLine("Paired device name/address, MAVLink bytes and payloads, coordinates, account data and vehicle commands are excluded.")
        appendLine("Capture is read-only. Save only after disconnecting all telemetry links, review before sharing, and share manually.")
    }

    fun write(content: String, openStream: () -> OutputStream?) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES)
        (openStream() ?: throw IOException("No document stream")).use { stream ->
            stream.write(bytes)
            stream.flush()
        }
    }

    private fun List<String>.safeGroups(): String =
        filter { it.matches(Regex("[A-Za-z ]{1,40}")) }.distinct().take(16).joinToString(", ")

    private fun nonNegative(value: Long): Long = value.coerceAtLeast(0)
    private fun yesNo(value: Boolean): String = if (value) "YES" else "NO"

    private fun stageGuidance(stage: ClassicCheckStage): String = when (stage) {
        ClassicCheckStage.RECEIVING -> "Fresh heartbeat and one or more measurement groups were observed in the current Classic session."
        ClassicCheckStage.WAITING_MEASUREMENTS -> "A fresh selected-autopilot heartbeat was observed, but no configured measurement group was fresh at capture."
        ClassicCheckStage.WAITING_HEARTBEAT -> "Recent serial bytes were observed, but selected-autopilot heartbeat evidence was missing or stale."
        ClassicCheckStage.WAITING_MAVLINK -> "Recent serial bytes were observed, but no recent supported MAVLink message passed validation. Unsupported IDs are unverified candidates."
        ClassicCheckStage.BYTES_STALE -> "A Classic decoder session existed, but recent serial bytes were stale at capture."
        ClassicCheckStage.WAITING_BYTES -> "The Bluetooth socket was open without current serial-byte evidence."
        ClassicCheckStage.WAITING_SESSION -> "The Bluetooth socket was open without a matching current Classic decoder session."
        ClassicCheckStage.CONNECTING -> "Bluetooth was still opening at capture."
        ClassicCheckStage.ERROR -> "Bluetooth reported an error at capture; see the app status, not this report, for the transient error text."
        ClassicCheckStage.CLOSED -> "No open Classic session was present at capture."
    }
}
