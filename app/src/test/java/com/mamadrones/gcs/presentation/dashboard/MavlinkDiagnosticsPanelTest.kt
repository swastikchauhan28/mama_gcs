package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import org.junit.Assert.*
import org.junit.Test

class MavlinkDiagnosticsPanelTest {
    @Test fun `initial panel does not claim measured zeros`() {
        val panel = ConsolePanels.mavlinkDiagnostics(MavlinkDiagnostics(), 100)
        assertEquals("NO MAVLINK SESSION", panel.status)
        assertEquals("—", panel.rows.first { it.first == "Parser errors (CRC + payload)" }.second)
    }

    @Test fun `closed panel keeps counts but clearly labels historical data`() {
        val panel = ConsolePanels.mavlinkDiagnostics(MavlinkDiagnostics(startedAtEpochMillis = 100,
            linkKind = TelemetryLinkKind.BLE, receivedBytes = 25, checksumFailures = 3,
            lastBytesAtEpochMillis = 1_000), 6_000)
        assertEquals("LAST SESSION · BLE · CLOSED", panel.status)
        assertEquals("3", panel.rows.first { it.first == "Parser errors (CRC + payload)" }.second)
        assertEquals("Received 5s ago", panel.rows.first { it.first == "Last input" }.second)
    }

    @Test fun `hints do not equate bytes with supported telemetry or advise disabling signing`() {
        val hints = ConsolePanels.mavlinkDiagnosticHints(MavlinkDiagnostics(active = true,
            startedAtEpochMillis = 100, receivedBytes = 80, signedPacketsRejected = 2))
        assertTrue(hints.contains("no supported message was decoded"))
        assertTrue(hints.contains("Do not disable a live rover's signing"))
    }

    @Test fun `raw start byte hints distinguish absence from unvalidated candidates`() {
        val noStarts = MavlinkDiagnostics(active = true, startedAtEpochMillis = 100,
            receivedBytes = 80)
        assertTrue(ConsolePanels.mavlinkDiagnosticHints(noStarts).contains("No MAVLink 1/2 start bytes"))
        val candidates = noStarts.copy(mavlinkV2StartBytes = 2)
        assertTrue(ConsolePanels.mavlinkDiagnosticHints(candidates).contains("no supported frame validated"))
        val panel = ConsolePanels.mavlinkDiagnostics(candidates)
        assertEquals("2", panel.rows.first { it.first.startsWith("Raw 0xFD") }.second)
        assertTrue(panel.note.orEmpty().contains("not validated frames"))
    }
}
