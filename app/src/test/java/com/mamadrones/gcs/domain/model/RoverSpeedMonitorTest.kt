package com.mamadrones.gcs.domain.model

import org.junit.Assert.*
import org.junit.Test

class RoverSpeedMonitorTest {
    private val now = 20_000L
    private fun sample(speed: Double? = 1.0) = VehicleState(
        connectionStatus = VehicleConnectionState.CONNECTED, lastHeartbeatAtElapsedMillis = now,
        kinematicsLastUpdatedAtElapsedMillis = now, speedMetersPerSecond = speed)

    @Test fun convertsMetersPerSecondWithoutChangingVehicleState() {
        val state = sample()
        val result = RoverSpeedMonitor.assess(state, now)
        assertEquals(3.6, result.speedKmh!!, 0.0)
        assertEquals(SpeedObservation.BELOW_LIMIT, result.observation)
        assertEquals(1.0, state.speedMetersPerSecond!!, 0.0)
    }
    @Test fun exactBoundaryAndUnroundedAboveBoundary() {
        assertEquals(SpeedObservation.AT_LIMIT, RoverSpeedMonitor.assess(sample(8.0 / 3.6), now).observation)
        assertEquals(SpeedObservation.ABOVE_LIMIT, RoverSpeedMonitor.assess(sample(8.00001 / 3.6), now).observation)
        assertEquals(SpeedObservation.BELOW_LIMIT, RoverSpeedMonitor.assess(sample(7.99999 / 3.6), now).observation)
    }
    @Test fun cruiseTargetIsAboveOperatorMaximum() {
        assertEquals(SpeedObservation.ABOVE_LIMIT, RoverSpeedMonitor.assess(sample(4.0), now).observation)
    }
    @Test fun zeroIsAnObservationNotAStopVerification() {
        assertEquals(SpeedObservation.BELOW_LIMIT, RoverSpeedMonitor.assess(sample(0.0), now).observation)
    }
    @Test fun disconnectedDegradedConnectingAndErrorHideOldSpeed() {
        VehicleConnectionState.entries.filter { it != VehicleConnectionState.CONNECTED }.forEach {
            unavailable(sample(4.0).copy(connectionStatus = it))
        }
    }
    @Test fun invalidValuesNeverBecomeZeroOrBelowLimit() {
        listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE).forEach {
            unavailable(sample(it))
        }
    }
    @Test fun heartbeatAndSpeedMustBeIndependentlyFresh() {
        unavailable(sample().copy(lastHeartbeatAtElapsedMillis = now - 5_001))
        unavailable(sample().copy(kinematicsLastUpdatedAtElapsedMillis = now - 5_001))
        val boundary = RoverSpeedMonitor.assess(sample().copy(
            lastHeartbeatAtElapsedMillis = now - 5_000, kinematicsLastUpdatedAtElapsedMillis = now - 5_000), now)
        assertEquals(SpeedObservation.BELOW_LIMIT, boundary.observation)
        assertEquals(SpeedReceiptStatus.FRESH, boundary.heartbeatReceipt.status)
        assertEquals(5_000L, boundary.speedReceipt.ageMillis)
    }
    @Test fun evidenceIdentifiesBothExpiredSourcesAtSameInstant() {
        val expired = RoverSpeedMonitor.assess(sample().copy(
            lastHeartbeatAtElapsedMillis = now - 5_001,
            kinematicsLastUpdatedAtElapsedMillis = now - 7_200), now)
        assertEquals(SpeedObservation.UNAVAILABLE, expired.observation)
        assertEquals(SpeedReceiptEvidence(SpeedReceiptStatus.STALE, 5_001), expired.heartbeatReceipt)
        assertEquals(SpeedReceiptEvidence(SpeedReceiptStatus.STALE, 7_200), expired.speedReceipt)
        assertTrue(expired.reason!!.contains("Heartbeat"))
        assertTrue(expired.reason!!.contains("GLOBAL_POSITION_INT speed sample"))
    }
    @Test fun missingAndFutureReceiptsAreDistinctFromStaleAges() {
        val evidence = RoverSpeedMonitor.assess(sample().copy(
            lastHeartbeatAtElapsedMillis = null,
            kinematicsLastUpdatedAtElapsedMillis = now + 1), now)
        assertEquals(SpeedReceiptEvidence(), evidence.heartbeatReceipt)
        assertEquals(SpeedReceiptEvidence(SpeedReceiptStatus.INVALID_TIME), evidence.speedReceipt)
        assertNull(evidence.speedKmh)
    }
    @Test fun missingFutureOrNegativeTimestampsAreUnavailable() {
        listOf(null, now + 1, -1L, Long.MIN_VALUE, Long.MAX_VALUE).forEach {
            unavailable(sample().copy(lastHeartbeatAtElapsedMillis = it))
            unavailable(sample().copy(kinematicsLastUpdatedAtElapsedMillis = it))
        }
    }
    @Test fun wallClockChangeCannotInvalidateOrReviveElapsedSamples() {
        val shiftedWall = sample().copy(lastHeartbeatAtEpochMillis = Long.MAX_VALUE,
            kinematicsLastUpdatedAtEpochMillis = 0L)
        assertEquals(SpeedObservation.BELOW_LIMIT, RoverSpeedMonitor.assess(shiftedWall, now).observation)
        unavailable(shiftedWall.copy(lastHeartbeatAtElapsedMillis = null))
        unavailable(shiftedWall.copy(kinematicsLastUpdatedAtElapsedMillis = null))
    }
    @Test fun elapsedTimeAloneExpiresAnAboveLimitSample() {
        val state = sample(4.0)
        assertEquals(SpeedObservation.ABOVE_LIMIT, RoverSpeedMonitor.assess(state, now).observation)
        val expired = RoverSpeedMonitor.assess(state, now + 5_001)
        assertEquals(SpeedObservation.UNAVAILABLE, expired.observation)
        assertNull(expired.speedKmh)
    }
    @Test fun vfrHudDoesNotSilentlyReplaceMissingChosenSource() {
        unavailable(sample(null).copy(roverHud = RoverHudState(groundSpeedMetersPerSecond = 1.0, lastUpdatedAtEpochMillis = now)))
    }
    private fun unavailable(state: VehicleState) {
        val result = RoverSpeedMonitor.assess(state, now)
        assertEquals(SpeedObservation.UNAVAILABLE, result.observation)
        assertNull(result.speedKmh)
        assertNotNull(result.reason)
    }
}
