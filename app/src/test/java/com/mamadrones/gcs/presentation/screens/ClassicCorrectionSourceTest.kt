package com.mamadrones.gcs.presentation.screens

import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import com.mamadrones.gcs.domain.model.TransportStatus
import com.mamadrones.gcs.domain.model.VehicleConnectionState
import com.mamadrones.gcs.domain.model.VehicleState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicCorrectionSourceTest {
    private val open = ConnectionState(status = TransportStatus.OPEN, connectedAtElapsedMillis = 1_000)
    private val vehicle = VehicleState(systemId = 1, connectionStatus = VehicleConnectionState.CONNECTED,
        lastHeartbeatAtElapsedMillis = 2_000,
        mavlinkDiagnostics = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.CLASSIC))

    @Test fun `requires fresh selected Cube heartbeat from the same open Classic session`() {
        assertTrue(freshClassicCorrectionSource(true, open, vehicle, 6_999))
        assertFalse(freshClassicCorrectionSource(true, open, vehicle, 7_001))
        assertFalse(freshClassicCorrectionSource(true, open, vehicle, 1_999))
        assertFalse(freshClassicCorrectionSource(false, open, vehicle, 2_100))
        assertFalse(freshClassicCorrectionSource(true, open.copy(status = TransportStatus.DISCONNECTED), vehicle, 2_100))
        assertFalse(freshClassicCorrectionSource(true, open.copy(connectedAtElapsedMillis = 2_001), vehicle, 2_100))
        assertFalse(freshClassicCorrectionSource(true, open, vehicle.copy(mavlinkDiagnostics =
            vehicle.mavlinkDiagnostics.copy(linkKind = TelemetryLinkKind.TCP)), 2_100))
        assertFalse(freshClassicCorrectionSource(true, open, vehicle.copy(connectionStatus =
            VehicleConnectionState.DISCONNECTED), 2_100))
    }
}
