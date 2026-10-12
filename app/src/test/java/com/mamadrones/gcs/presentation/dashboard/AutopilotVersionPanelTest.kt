package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class AutopilotVersionPanelTest {
    private val state = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
        lastHeartbeatAtEpochMillis = 20_000,
        mavlinkDiagnostics = MavlinkDiagnostics(active = true, startedAtEpochMillis = 1_000),
        autopilotVersion = AutopilotVersionState(0x040700FF, 0, 0, 0xFFFFFFFF, 0, 123, 2_000))

    @Test fun firmwareIncludesSemanticVersionAndRawReleaseType() {
        assertEquals("4.7.0 · official (type 255)", AutopilotVersionPanel.firmware(0x040700FF))
        mapOf(0 to "development", 63 to "development", 64 to "alpha", 127 to "alpha",
            128 to "beta", 191 to "beta", 192 to "release candidate", 254 to "release candidate").forEach { (type, label) ->
            assertEquals("4.7.0 · $label (type $type)", AutopilotVersionPanel.firmware(0x04070000L + type))
        }
        assertEquals("255.255.255 · official (type 255)", AutopilotVersionPanel.firmware(0xFFFFFFFF))
    }

    @Test fun zeroDoesNotInventKnownFirmwareOrBoard() {
        assertEquals("ZERO / UNSPECIFIED (0)", AutopilotVersionPanel.firmware(0))
        val rows = AutopilotVersionPanel.panel(state, 20_000).rows.toMap()
        assertEquals("ZERO / UNSPECIFIED (0)", rows["Vendor ID · raw"])
        assertEquals("0xFFFFFFFF", rows["Board version · raw"])
    }

    @Test fun metadataIsRetainedWithoutPretendingToBeContinuousTelemetry() {
        val panel = AutopilotVersionPanel.panel(state, 20_000)
        assertEquals("OBSERVED THIS SESSION · NOT VERIFIED", panel.status)
        assertTrue(panel.note!!.contains("18 s ago"))
        assertTrue(panel.rows.first().second.startsWith("4.7.0"))
    }

    @Test fun lossOfLivenessKeepsExplicitlyHistoricalObservation() {
        listOf(state.copy(connectionStatus = VehicleConnectionState.DISCONNECTED),
            state.copy(connectionStatus = VehicleConnectionState.DEGRADED),
            state.copy(lastHeartbeatAtEpochMillis = 2_000), state.copy(lastHeartbeatAtEpochMillis = 21_000),
            state.copy(mavlinkDiagnostics = state.mavlinkDiagnostics.copy(active = false))).forEach {
            val panel = AutopilotVersionPanel.panel(it, 20_000)
            assertEquals("LAST OBSERVATION · LINK NOT LIVE", panel.status)
            assertTrue(panel.rows.first().second.startsWith("4.7.0"))
        }
    }

    @Test fun absentFutureOrWrongSessionNeverShowsFirmware() {
        assertEquals("NOT RECEIVED", AutopilotVersionPanel.panel(VehicleState(), 20_000).status)
        listOf(state.copy(autopilotVersion = state.autopilotVersion!!.copy(receivedAtEpochMillis = 999)),
            state.copy(autopilotVersion = state.autopilotVersion!!.copy(receivedAtEpochMillis = 21_000)),
            state.copy(mavlinkDiagnostics = MavlinkDiagnostics())).forEach {
            assertTrue(AutopilotVersionPanel.panel(it, 20_000).rows.all { row -> row.second == "—" })
        }
    }
}
