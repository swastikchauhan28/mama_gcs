package com.mamadrones.gcs.core.diagnostics

import com.mamadrones.gcs.domain.model.ClassicCheckStage
import com.mamadrones.gcs.domain.model.ClassicConnectionAssessment
import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import com.mamadrones.gcs.domain.model.PacketStatistics
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import com.mamadrones.gcs.domain.model.TransportStatus
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class ClassicLinkEvidenceReportTest {
    private val assessment = ClassicConnectionAssessment(
        stage = ClassicCheckStage.RECEIVING,
        socketOpen = true,
        freshBytes = true,
        freshDecodedMessage = true,
        freshHeartbeat = true,
        measurementMessages = listOf("GPS", "Attitude", "GPS", "private\naddress"),
        freshRcChannels = true,
    )
    private val connection = ConnectionState(TransportStatus.OPEN,
        packetStatistics = PacketStatistics(receivedPackets = 7))
    private val diagnostics = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.CLASSIC,
        startedAtEpochMillis = 1, receivedChunks = 5, receivedBytes = 11,
        mavlinkV1StartBytes = 3, mavlinkV2StartBytes = 4, decodedV1Messages = 2,
        decodedV2Messages = 3, acceptedMessages = 4, checksumFailures = 1, malformedPayloads = 2,
        unsupportedMessages = 3, signedPacketsRejected = 4)

    @Test fun capturesChecklistCountersAndLimitsItsClaims() {
        val text = ClassicLinkEvidenceReport.capture(assessment, connection, diagnostics, 0)
        assertTrue(text.contains("1970-01-01T00:00:00Z"))
        assertTrue(text.contains("Checklist stage: RECEIVING"))
        assertTrue(text.contains("Fresh measurement groups: GPS, Attitude"))
        assertTrue(text.contains("Fresh selected-autopilot RC_CHANNELS: YES"))
        assertTrue(text.contains("Input bytes: 11"))
        assertTrue(text.contains("Raw 0xFE bytes (MAVLink 1 start candidates): 3"))
        assertTrue(text.contains("Raw 0xFD bytes (MAVLink 2 start candidates): 4"))
        assertTrue(text.contains("not checksum-validated MAVLink frames"))
        assertTrue(text.contains("Decoded MAVLink 1 messages: 2"))
        assertTrue(text.contains("Recent validated MAVLink: YES"))
        assertTrue(text.contains("Unverified unsupported-ID candidates: 3"))
        assertTrue(text.contains("Expected receive route"))
        assertTrue(text.contains("does not prove"))
        assertTrue(text.contains("RC receipt does not prove stick movement"))
        assertFalse(text.contains("private"))
    }

    @Test fun neverFormatsNegativeCountersOrInvalidGroupsAsEvidence() {
        val text = ClassicLinkEvidenceReport.capture(
            assessment.copy(stage = ClassicCheckStage.WAITING_BYTES, measurementMessages = listOf("\n", "x".repeat(41))),
            connection.copy(packetStatistics = PacketStatistics(receivedPackets = -1)),
            diagnostics.copy(receivedChunks = -2, receivedBytes = -3, decodedV1Messages = -4), 0)
        assertTrue(text.contains("Fresh measurement groups: NONE"))
        assertTrue(text.contains("Socket packet chunks received: 0"))
        assertTrue(text.contains("Input chunks: 0"))
        assertTrue(text.contains("Input bytes: 0"))
        assertTrue(text.contains("Decoded MAVLink 1 messages: 0"))
        assertTrue(text.contains("without current serial-byte evidence"))
    }

    @Test fun requiresNonNegativeCaptureTime() {
        assertThrows(IllegalArgumentException::class.java) {
            ClassicLinkEvidenceReport.capture(assessment, connection, diagnostics, -1)
        }
    }

    @Test fun writesUtf8ClosesStreamAndRejectsOversizeBeforeOpening() {
        var closed = false
        val stream = object : ByteArrayOutputStream() { override fun close() { closed = true; super.close() } }
        val text = ClassicLinkEvidenceReport.capture(assessment, connection, diagnostics, 0)
        ClassicLinkEvidenceReport.write(text) { stream }
        assertEquals(text, stream.toString("UTF-8"))
        assertTrue(closed)
        var opened = false
        assertThrows(IllegalArgumentException::class.java) {
            ClassicLinkEvidenceReport.write("x".repeat(ClassicLinkEvidenceReport.MAX_BYTES + 1)) { opened = true; stream }
        }
        assertFalse(opened)
        assertThrows(IOException::class.java) { ClassicLinkEvidenceReport.write(text) { null } }
    }
}
