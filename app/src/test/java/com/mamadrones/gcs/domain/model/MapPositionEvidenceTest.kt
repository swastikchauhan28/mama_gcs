package com.mamadrones.gcs.domain.model

import org.junit.Assert.*
import org.junit.Test

class MapPositionEvidenceTest {
    private fun vehicle(received: Long? = 10_000L) = VehicleState(
        connectionStatus = VehicleConnectionState.CONNECTED,
        lastHeartbeatAtEpochMillis = 20_000L,
        position = GlobalPositionState(12.0, 34.0, lastUpdatedAtEpochMillis = 123_456L, lastUpdatedAtElapsedMillis = received))

    @Test fun positionExpiresIndependentlyOfFreshHeartbeat() {
        assertEquals(MapPositionStatus.FRESH, MapPositionEvidence.status(vehicle(), 15_000L))
        assertEquals(MapPositionStatus.STALE, MapPositionEvidence.status(vehicle(), 15_001L))
        assertEquals(MapPositionStatus.FRESH, MapPositionEvidence.status(vehicle(20_000L), 20_000L))
    }

    @Test fun wallClockChangesDoNotChangeElapsedFreshness() {
        val original = vehicle(10_000L)
        val movedBackward = original.copy(position = original.position.copy(lastUpdatedAtEpochMillis = 1L))
        val movedForward = original.copy(position = original.position.copy(lastUpdatedAtEpochMillis = Long.MAX_VALUE))
        for (state in listOf(original, movedBackward, movedForward)) {
            assertEquals(MapPositionStatus.FRESH, MapPositionEvidence.status(state, 15_000L))
            assertEquals(MapPositionStatus.STALE, MapPositionEvidence.status(state, 15_001L))
        }
    }
    @Test fun missingFutureInvalidOrDisconnectedPositionIsUnavailable() {
        assertEquals(MapPositionStatus.UNAVAILABLE, MapPositionEvidence.status(vehicle(null), 20_000L))
        assertEquals(MapPositionStatus.UNAVAILABLE, MapPositionEvidence.status(vehicle(20_001L), 20_000L))
        assertEquals(MapPositionStatus.UNAVAILABLE, MapPositionEvidence.status(vehicle(-1L), 20_000L))
        assertEquals(MapPositionStatus.UNAVAILABLE, MapPositionEvidence.status(vehicle().copy(connectionStatus = VehicleConnectionState.DISCONNECTED), 20_000L))
        assertEquals(MapPositionStatus.UNAVAILABLE, MapPositionEvidence.status(vehicle().copy(position = GlobalPositionState(Double.NaN, 34.0, lastUpdatedAtEpochMillis = 123_456L, lastUpdatedAtElapsedMillis = 10_000L)), 20_000L))
        assertEquals(MapPositionStatus.UNAVAILABLE, MapPositionEvidence.status(vehicle().copy(position = GlobalPositionState(91.0, 34.0, lastUpdatedAtEpochMillis = 123_456L, lastUpdatedAtElapsedMillis = 10_000L)), 20_000L))
    }
}
