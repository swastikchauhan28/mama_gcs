package com.mamadrones.gcs.presentation.screens

import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import org.junit.Assert.*
import org.junit.Test

class DecoderWindowObservationTest {
    private val sample = MavlinkDiagnostics(
        active = true,
        linkKind = TelemetryLinkKind.TCP,
        startedAtEpochMillis = 1_000,
        startedAtElapsedMillis = 100,
        acceptedMessages = 10,
        checksumFailures = 8,
        overlappingRescanChecksumFailures = 3,
    )

    @Test fun `requires valid active session and consistent counters`() {
        assertFalse(DecoderWindowObservation.canStart(MavlinkDiagnostics(), 1_000))
        assertFalse(DecoderWindowObservation.canStart(sample.copy(active = false), 1_000))
        assertFalse(DecoderWindowObservation.canStart(sample.copy(startedAtElapsedMillis = null), 1_000))
        assertFalse(DecoderWindowObservation.canStart(sample.copy(overlappingRescanChecksumFailures = 9), 1_000))
        assertFalse(DecoderWindowObservation.canStart(sample, 99))
        assertNull(DecoderWindowObservation.start(sample.copy(active = false), 1_000))
    }

    @Test fun `thirty second result freezes three scalar differences`() {
        val started = requireNotNull(DecoderWindowObservation.start(sample, 1_000))
        val later = sample.copy(acceptedMessages = 15, checksumFailures = 17,
            overlappingRescanChecksumFailures = 7)
        assertEquals(started, DecoderWindowObservation.advance(started, later, 30_999))
        val finished = DecoderWindowObservation.advance(started, later, 31_000) as DecoderWindowState.Finished
        assertTrue(finished.completed)
        assertEquals(DecoderWindowCounts(5, 4, 5), finished.differences)
        assertEquals(finished, DecoderWindowObservation.advance(finished, later.copy(acceptedMessages = 100), 50_000))
        val panel = DecoderWindowObservation.panel(finished, later, 50_000)
        assertEquals("COMPLETE", panel.status)
        assertEquals("5", panel.rows.toMap()["Accepted selected-autopilot messages"])
        assertEquals("4", panel.rows.toMap()["Checksum candidates inside rescan span"])
        assertEquals("5", panel.rows.toMap()["Checksum candidates outside rescan span"])
    }

    @Test fun `session loss and replacement finish incomplete`() {
        val started = requireNotNull(DecoderWindowObservation.start(sample, 1_000))
        val ended = DecoderWindowObservation.advance(started, sample.copy(active = false), 2_000) as DecoderWindowState.Finished
        assertFalse(ended.completed)
        assertTrue(ended.reason!!.contains("ended or changed"))
        val replaced = DecoderWindowObservation.advance(started,
            sample.copy(startedAtElapsedMillis = 1_500), 2_000) as DecoderWindowState.Finished
        assertFalse(replaced.completed)
        assertEquals(ended.reason, replaced.reason)
        val otherLink = DecoderWindowObservation.advance(started,
            sample.copy(linkKind = TelemetryLinkKind.BLE), 2_000) as DecoderWindowState.Finished
        assertFalse(otherLink.completed)
    }

    @Test fun `counter reset and elapsed clock reversal finish incomplete`() {
        val started = requireNotNull(DecoderWindowObservation.start(sample, 1_000))
        val reset = DecoderWindowObservation.advance(started,
            sample.copy(checksumFailures = 7), 2_000) as DecoderWindowState.Finished
        assertFalse(reset.completed)
        assertTrue(reset.reason!!.contains("counters reset"))
        val reversed = DecoderWindowObservation.advance(started, sample, 999) as DecoderWindowState.Finished
        assertFalse(reversed.completed)
        assertEquals(0L, reversed.elapsedMillis)
    }
}
