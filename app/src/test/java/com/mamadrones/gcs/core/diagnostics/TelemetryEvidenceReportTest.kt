package com.mamadrones.gcs.core.diagnostics

import com.mamadrones.gcs.domain.model.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test

class TelemetryEvidenceReportTest {
    private val vehicle = VehicleState(systemId = 42, componentId = 1,
        connectionStatus = VehicleConnectionState.CONNECTED, lastHeartbeatAtEpochMillis = 1_200,
        mavlinkDiagnostics = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.CLASSIC,
            startedAtEpochMillis = 1_000, receivedBytes = 128, decodedMessages = 3,
            decodedV1Messages = 2, decodedV2Messages = 1, acceptedMessages = 2, ignoredOtherSource = 1))

    @Test fun capturesVersionCountersSourceAndAgeWithoutInferringSafety() {
        val text = TelemetryEvidenceReport.capture(vehicle, 2_000)
        listOf("ACTIVE AT CAPTURE", "Transport: CLASSIC", "system 42, component 1",
            "age at capture 800 ms", "Decoded MAVLink 1: 2", "Decoded MAVLink 2: 1",
            "Accepted from selected autopilot: 2", "Ignored other source: 1",
            "Not authentication", "commands and app emergency stop remain unavailable").forEach { assertTrue(it, text.contains(it)) }
    }

    @Test fun emptyStateUsesNotRecordedNotFakeZero() {
        val text = TelemetryEvidenceReport.capture(VehicleState(), 2_000)
        assertTrue(text.contains("State: NO SESSION"))
        assertTrue(text.contains("Input bytes: NOT RECORDED"))
        assertTrue(text.contains("not paired identity): NOT OBSERVED"))
        assertTrue(text.contains("Last heartbeat: NOT RECEIVED"))
    }

    @Test fun closedSessionRemainsExplicitHistory() {
        val closed = vehicle.copy(connectionStatus = VehicleConnectionState.DISCONNECTED,
            mavlinkDiagnostics = vehicle.mavlinkDiagnostics.copy(active = false))
        val text = TelemetryEvidenceReport.capture(closed, 3_000)
        assertTrue(text.contains("LAST SESSION - CLOSED"))
        assertTrue(text.contains("connection at capture: DISCONNECTED"))
        assertTrue(text.contains("Input bytes: 128"))
    }

    @Test fun futureAndPreviousSessionTimesAreNotPresentedAsFresh() {
        val text = TelemetryEvidenceReport.capture(vehicle.copy(
            gps = GpsState(lastUpdatedAtEpochMillis = 3_000),
            attitude = AttitudeState(lastUpdatedAtEpochMillis = 500)), 2_000)
        assertTrue(text.contains("GPS: CLOCK INCONSISTENT"))
        assertTrue(text.contains("Attitude: NOT RECORDED IN THIS SESSION"))
    }

    @Test fun privateFieldsAndArbitraryTextCannotLeakIntoExport() {
        val text = TelemetryEvidenceReport.capture(vehicle.copy(
            vehicleId = "private-vehicle-id", displayName = "private-name",
            mode = "private-mode", gps = GpsState(latitude = 12.3456789, longitude = 98.7654321),
            position = GlobalPositionState(latitude = 12.3456789, longitude = 98.7654321),
            positionTrack = listOf(GeoTrackPoint(12.3456789, 98.7654321, 1_500)),
            statusTexts = listOf(VehicleStatusText(1, "private-raw-payload", 1_500)),
            motors = listOf(MotorState("private-motor", faultCode = "private-fault"))), 2_000)
        listOf("private-", "12.3456789", "98.7654321").forEach { assertFalse(text.contains(it)) }
        assertTrue(text.toByteArray().size < TelemetryEvidenceReport.MAX_BYTES)
    }

    @Test fun streamWriterWritesExactUtf8AndCloses() {
        var closed = false
        val output = object : ByteArrayOutputStream() { override fun close() { closed = true; super.close() } }
        val content = TelemetryEvidenceReport.capture(vehicle, 2_000)
        TelemetryEvidenceReport.write(content) { output }
        assertEquals(content, output.toString("UTF-8"))
        assertTrue(closed)
    }

    @Test fun oversizedReportDoesNotOpenDestination() {
        var opened = false
        assertThrows(IllegalArgumentException::class.java) {
            TelemetryEvidenceReport.write("a".repeat(TelemetryEvidenceReport.MAX_BYTES + 1)) { opened = true; ByteArrayOutputStream() }
        }
        assertFalse(opened)
    }

    @Test fun failedWriteClosesStreamAndNullProviderFails() {
        var closed = false
        assertThrows(IOException::class.java) {
            TelemetryEvidenceReport.write("report") { object : OutputStream() {
                override fun write(value: Int) { throw IOException("storage full") }
                override fun close() { closed = true }
            } }
        }
        assertTrue(closed)
        assertThrows(IOException::class.java) { TelemetryEvidenceReport.write("report") { null } }
    }
}
