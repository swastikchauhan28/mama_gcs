package com.mamadrones.gcs.domain.model

import org.junit.Assert.*
import org.junit.Test

class MapPositionEvidenceTest {
    private fun vehicle(received: Long? = 10_000L) = VehicleState(
        connectionStatus = VehicleConnectionState.CONNECTED,
        lastHeartbeatAtEpochMillis = 20_000L,
        position = GlobalPositionState(12.0, 34.0, lastUpdatedAtEpochMillis = received))

    @Test fun positionExpiresIndependentlyOfFreshHeartbeat() {
        assertEquals(MapPositionStatus.FRESH, MapPositionEvidence.status(vehicle(), 15_000L))
        assertEquals(MapPositionStatus.STALE, MapPositionEvidence.status(vehicle(), 15_001L))
        assertEquals(MapPositionStatus.FRESH, MapPositionEvidence.status(vehicle(20_000L), 20_000L))
    }

    @Test fun missingFutureInvalidOrDisconnectedPositionIsUnavailable() {
        assertEquals(MapPositionStatus.UNAVAILABLE, MapPositionEvidence.status(vehicle(null), 20_000L))
        assertEquals(MapPositionStatus.UNAVAILABLE, MapPositionEvidence.status(vehicle(20_001L), 20_000L))
        assertEquals(MapPositionStatus.UNAVAILABLE, MapPositionEvidence.status(vehicle(-1L), 20_000L))
        assertEquals(MapPositionStatus.UNAVAILABLE, MapPositionEvidence.status(vehicle().copy(connectionStatus = VehicleConnectionState.DISCONNECTED), 20_000L))
        assertEquals(MapPositionStatus.UNAVAILABLE, MapPositionEvidence.status(vehicle().copy(position = GlobalPositionState(Double.NaN, 34.0, lastUpdatedAtEpochMillis = 10_000L)), 20_000L))
        assertEquals(MapPositionStatus.UNAVAILABLE, MapPositionEvidence.status(vehicle().copy(position = GlobalPositionState(91.0, 34.0, lastUpdatedAtEpochMillis = 10_000L)), 20_000L))
    }
}
