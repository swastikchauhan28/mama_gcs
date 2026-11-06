package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import com.mamadrones.gcs.domain.model.RadioStatusObservation
import org.junit.Assert.*
import org.junit.Test

class MavlinkDiagnosticsPanelTest {
    @Test fun `heartbeat timing does not confuse open bytes with accepted heartbeats`() {
        val base = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.TCP,
            startedAtElapsedMillis = 100, timingHeartbeatAtElapsedMillis = 100,
            lastBytesAtElapsedMillis = 5_102)
        assertEquals("ACCEPTED HEARTBEAT RECENT", ConsolePanels.heartbeatTiming(base, 5_100).status)
        val gap = ConsolePanels.heartbeatTiming(base, 5_102)
        assertEquals("HEARTBEAT GAP IN PROGRESS", gap.status)
        assertEquals("OBSERVED", gap.rows.toMap()["Input after 5 seconds in current gap"])
        assertEquals("NOT OBSERVED", ConsolePanels.heartbeatTiming(base.copy(lastBytesAtElapsedMillis = 100), 5_102)
            .rows.toMap()["Input after 5 seconds in current gap"])
        assertEquals("ELAPSED TIME UNAVAILABLE", ConsolePanels.heartbeatTiming(base, 99).status)
        assertEquals("SESSION CLOSED OR NOT STARTED", ConsolePanels.heartbeatTiming(base.copy(active = false), 5_102).status)
    }
    @Test fun `three simulated link health states stay semantically distinct`() {
        val steady = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.TCP,
            startedAtElapsedMillis = 100, timingHeartbeatAtElapsedMillis = 6_000,
            lastBytesAtElapsedMillis = 6_000)
        assertEquals("ACCEPTED HEARTBEAT RECENT", ConsolePanels.heartbeatTiming(steady, 6_001).status)

        val delayed = steady.copy(timingHeartbeatAtElapsedMillis = 100, lastBytesAtElapsedMillis = 5_102)
        val delayedPanel = ConsolePanels.heartbeatTiming(delayed, 5_102)
        assertEquals("HEARTBEAT GAP IN PROGRESS", delayedPanel.status)
        assertEquals("OBSERVED", delayedPanel.rows.toMap()["Input after 5 seconds in current gap"])

        val closedPanel = ConsolePanels.heartbeatTiming(steady.copy(active = false), 6_001)
        assertEquals("SESSION CLOSED OR NOT STARTED", closedPanel.status)
        assertEquals("—", closedPanel.rows.toMap()["TCP socket"])

        val recovered = steady.copy(
            timingHeartbeatAtElapsedMillis = 6_200,
            lastBytesAtElapsedMillis = 6_200,
            completedHeartbeatGapsOverFiveSeconds = 1,
            completedGapsWithInputBeforeRecovery = 1,
            longestCompletedHeartbeatIntervalMillis = 6_100,
            lastCompletedHeartbeatIntervalMillis = 6_100,
            lastLongGapHadInputBeforeRecovery = true,
        )
        val recoveredPanel = ConsolePanels.heartbeatTiming(recovered, 6_201)
        assertEquals("ACCEPTED HEARTBEAT RECENT", recoveredPanel.status)
        assertEquals("1", recoveredPanel.rows.toMap()["Completed gaps over 5 seconds"])
        assertEquals("6100 ms", recoveredPanel.rows.toMap()["Latest completed heartbeat interval"])
        assertEquals("OBSERVED", recoveredPanel.rows.toMap()["Input before recovery in latest long gap"])
    }

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

    @Test fun `radio only panel shows bounded raw evidence without Cube claim`() {
        val sample = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.CLASSIC,
            startedAtEpochMillis = 100, receivedBytes = 18, mavlinkV1StartBytes = 1,
            validatedRadioStatusFrames = 1,
            latestRadioStatus = RadioStatusObservation(68, 68, 3, 4, 95, 100, 80, 1, 2, 1_000))
        val panel = ConsolePanels.mavlinkDiagnostics(sample, 2_000)
        assertEquals("1", panel.rows.first { it.first == "Validated RADIO_STATUS (radio only)" }.second)
        assertEquals("Received 1s ago", panel.rows.first { it.first == "Latest radio status" }.second)
        assertEquals("95 / 100", panel.rows.first { it.first == "Radio RSSI / remote RSSI (raw)" }.second)
        assertTrue(panel.note.orEmpty().contains("not a Cube heartbeat"))
        val hints = ConsolePanels.mavlinkDiagnosticHints(sample)
        assertTrue(hints.contains("Checksum-valid RADIO_STATUS arrived"))
        assertFalse(hints.contains("stream may be incomplete, unsupported"))
    }
}
