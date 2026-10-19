package com.mamadrones.gcs.domain.model

import org.junit.Assert.*
import org.junit.Test

class RcInputActivityTest {
    private fun vehicle(at: Long = 10_000, values: List<Int?> = listOf(1501, 1498)) = VehicleState(
        systemId = 1, componentId = 1, connectionStatus = VehicleConnectionState.CONNECTED,
        lastHeartbeatAtEpochMillis = at,
        mavlinkDiagnostics = MavlinkDiagnostics(active = true, startedAtEpochMillis = 1_000, linkKind = TelemetryLinkKind.CLASSIC),
        rcInput = RcInputState(channelCount = values.size, channelsMicroseconds = values, lastUpdatedAtEpochMillis = at))

    @Test fun startRequiresExplicitFreshEvidenceAndKeepsExactValues() {
        assertEquals(RcActivityStatus.IDLE, RcInputActivity.start(VehicleState(), 10_000).status)
        val started = RcInputActivity.start(vehicle(), 10_000)
        assertEquals(RcActivityStatus.OBSERVING, started.status)
        assertEquals(18, started.ranges.size)
        assertEquals(RcChannelRange(1501, 1501), started.ranges[0])
        assertNull(started.ranges[2])
    }

    @Test fun rejectsMissingStaleFutureAndPreviousSessionEvidence() {
        val v = vehicle()
        val bad = listOf(v.copy(systemId = null), v.copy(componentId = 0), v.copy(systemId = 256),
            v.copy(connectionStatus = VehicleConnectionState.DISCONNECTED),
            v.copy(mavlinkDiagnostics = v.mavlinkDiagnostics.copy(active = false)),
            v.copy(mavlinkDiagnostics = v.mavlinkDiagnostics.copy(startedAtEpochMillis = null)),
            v.copy(mavlinkDiagnostics = v.mavlinkDiagnostics.copy(startedAtEpochMillis = -1)),
            v.copy(lastHeartbeatAtEpochMillis = null), v.copy(lastHeartbeatAtEpochMillis = 4_999),
            v.copy(lastHeartbeatAtEpochMillis = 10_001), v.copy(lastHeartbeatAtEpochMillis = 999),
            v.copy(rcInput = v.rcInput.copy(lastUpdatedAtEpochMillis = null)),
            v.copy(rcInput = v.rcInput.copy(lastUpdatedAtEpochMillis = 4_999)),
            v.copy(rcInput = v.rcInput.copy(lastUpdatedAtEpochMillis = 10_001)),
            v.copy(rcInput = v.rcInput.copy(lastUpdatedAtEpochMillis = 999)))
        bad.forEach { assertNotNull(RcInputActivity.unavailableReason(it, 10_000)) }
        assertNull(RcInputActivity.unavailableReason(vehicle(5_000), 10_000))
    }

    @Test fun accumulatesOnlyNewSamplesAndNeverInventsNeutral() {
        val initial = RcInputActivity.start(vehicle(), 10_000)
        val duplicate = RcInputActivity.observe(initial, vehicle(values = listOf(2000)), 10_001)
        assertEquals(initial.ranges, duplicate.ranges)
        val low = RcInputActivity.observe(duplicate, vehicle(10_002, listOf(1101, null, 1234)), 10_002)
        val high = RcInputActivity.observe(low, vehicle(10_003, listOf(1899, 1499)), 10_003)
        assertEquals(RcChannelRange(1101, 1899), high.ranges[0])
        assertEquals(798, high.ranges[0]?.span)
        assertEquals(RcChannelRange(1498, 1499), high.ranges[1])
        assertEquals(RcChannelRange(1234, 1234), high.ranges[2])
    }

    @Test fun boundsChannelsAndRejectsSentinelsWithoutAssumingCalibratedRange() {
        val v = vehicle(values = listOf(0, 65_534, 65_535, -1, null) + List(20) { 1400 })
        val started = RcInputActivity.start(v, 10_000)
        assertEquals(18, started.ranges.size)
        assertEquals(RcChannelRange(0, 0), started.ranges[0])
        assertEquals(RcChannelRange(65_534, 65_534), started.ranges[1])
        assertNull(started.ranges[2]); assertNull(started.ranges[3]); assertNull(started.ranges[4])
        val limited = RcInputActivity.start(v.copy(rcInput = v.rcInput.copy(channelCount = 1)), 10_000)
        assertNull(limited.ranges[1])
        listOf(null, 0, -1, 256).forEach { count ->
            assertEquals(RcActivityStatus.IDLE, RcInputActivity.start(v.copy(rcInput = v.rcInput.copy(channelCount = count)), 10_000).status)
        }
    }

    @Test fun sessionTransportAndSourceChangesInterruptWithoutAutomaticResume() {
        val v = vehicle()
        val initial = RcInputActivity.start(v, 10_000)
        listOf(v.copy(systemId = 2), v.copy(componentId = 2),
            v.copy(mavlinkDiagnostics = v.mavlinkDiagnostics.copy(linkKind = TelemetryLinkKind.BLE)),
            v.copy(mavlinkDiagnostics = v.mavlinkDiagnostics.copy(startedAtEpochMillis = 2_000))).forEach { changed ->
            val interrupted = RcInputActivity.observe(initial, changed, 10_001)
            assertEquals(RcActivityStatus.INTERRUPTED, interrupted.status)
            assertEquals(initial.ranges, interrupted.ranges)
            assertEquals(interrupted, RcInputActivity.observe(interrupted, vehicle(10_002), 10_002))
        }
    }

    @Test fun staleRcStopsEvenWithFreshHeartbeat() {
        val initial = RcInputActivity.start(vehicle(), 10_000)
        val next = RcInputActivity.observe(initial, vehicle().copy(lastHeartbeatAtEpochMillis = 15_001), 15_001)
        assertEquals(RcActivityStatus.INTERRUPTED, next.status)
        assertEquals(initial.ranges, next.ranges)
    }

    @Test fun disconnectOrNoUsableChannelsInterrupts() {
        val initial = RcInputActivity.start(vehicle(), 10_000)
        listOf(vehicle().copy(connectionStatus = VehicleConnectionState.DISCONNECTED),
            vehicle(10_001, listOf(null, 65_535))).forEach {
            assertEquals(RcActivityStatus.INTERRUPTED, RcInputActivity.observe(initial, it, 10_001).status)
        }
    }

    @Test fun backwardsClockOrSampleInterrupts() {
        val initial = RcInputActivity.start(vehicle(), 10_000)
        assertEquals(RcActivityStatus.INTERRUPTED, RcInputActivity.observe(initial, vehicle(), 9_999).status)
        assertEquals(RcActivityStatus.INTERRUPTED, RcInputActivity.observe(initial, vehicle(9_999), 10_001).status)
    }

    @Test fun stopIncludesFinalFreshSampleAndFreezesRanges() {
        val initial = RcInputActivity.start(vehicle(), 10_000)
        val stopped = RcInputActivity.stop(initial, vehicle(10_001, listOf(1800)), 10_001)
        assertEquals(RcActivityStatus.STOPPED, stopped.status)
        assertEquals(RcChannelRange(1501, 1800), stopped.ranges[0])
        assertEquals(stopped, RcInputActivity.observe(stopped, vehicle(10_002, listOf(2000)), 10_002))
        assertEquals(stopped, RcInputActivity.stop(stopped, VehicleState(), 20_000))
    }

    @Test fun staleStopIsInterruptedAndNewStartResetsRanges() {
        val initial = RcInputActivity.start(vehicle(), 10_000)
        assertEquals(RcActivityStatus.INTERRUPTED, RcInputActivity.stop(initial, vehicle(), 15_001).status)
        val restarted = RcInputActivity.start(vehicle(20_000, listOf(1600)), 20_000)
        assertEquals(RcChannelRange(1600, 1600), restarted.ranges[0])
        assertEquals(RcActivitySnapshot(), RcInputActivity.observe(RcActivitySnapshot(), vehicle(), 10_000))
    }
}
