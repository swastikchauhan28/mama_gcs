package com.mamadrones.gcs.domain.model

import org.junit.Assert.*
import org.junit.Test

class RoverSpeedMonitorTest {
    private val now = 20_000L
    private fun sample(speed: Double? = 1.0) = VehicleState(
        connectionStatus = VehicleConnectionState.CONNECTED, lastHeartbeatAtEpochMillis = now,
        kinematicsLastUpdatedAtEpochMillis = now, speedMetersPerSecond = speed)

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
        unavailable(sample().copy(lastHeartbeatAtEpochMillis = now - 5_001))
        unavailable(sample().copy(kinematicsLastUpdatedAtEpochMillis = now - 5_001))
        assertEquals(SpeedObservation.BELOW_LIMIT, RoverSpeedMonitor.assess(sample().copy(
            lastHeartbeatAtEpochMillis = now - 5_000, kinematicsLastUpdatedAtEpochMillis = now - 5_000), now).observation)
    }
    @Test fun missingFutureOrNegativeTimestampsAreUnavailable() {
        listOf(null, now + 1, -1L, Long.MIN_VALUE, Long.MAX_VALUE).forEach {
            unavailable(sample().copy(lastHeartbeatAtEpochMillis = it))
            unavailable(sample().copy(kinematicsLastUpdatedAtEpochMillis = it))
        }
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
