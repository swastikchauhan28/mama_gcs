package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class RcPwmPanelsTest {
    private val heartbeat = MavlinkMessageObservation(ObservedMavlinkMessage.HEARTBEAT,
        accepted = 1, lastAcceptedAtElapsedMillis = 10_000)
    private val rcObservation = MavlinkMessageObservation(ObservedMavlinkMessage.RC_CHANNELS,
        accepted = 1, lastAcceptedAtElapsedMillis = 10_000)
    private val state = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
        systemId = 1, componentId = 1, lastHeartbeatAtEpochMillis = 10_000,
        lastHeartbeatAtElapsedMillis = 10_000,
        mavlinkDiagnostics = MavlinkDiagnostics(active = true, startedAtEpochMillis = 1_000,
            startedAtElapsedMillis = 1_000, messageObservations = listOf(heartbeat, rcObservation)),
        rcInput = RcInputState(2, listOf(1500, 2000), 200, 10_000, 10_000),
        pwmOutputs = listOf(PwmOutputState(0, listOf(1501, 0), 10_000)))

    @Test fun freshRawValuesAreNumberedNotMappedOrScaled() {
        val rc = RcPwmPanels.rc(state, 11_000)
        assertEquals("1500 µs", rc.rows.toMap()["RC 1"])
        assertEquals("200", rc.rows.toMap()["RSSI · device-specific raw scale"])
        val pwm = RcPwmPanels.pwm(state, 0, 11_000)
        assertEquals("0 µs", pwm.rows.toMap()["Output field 2"])
        assertEquals("ZERO / NOT PROVIDED", pwm.rows.toMap()["Output field 9"])
        assertFalse(pwm.rows.any { it.first.contains("left", true) || it.first.contains("right", true) })
        assertTrue(pwm.note!!.contains("does not prove"))
    }

    @Test fun samplesExpireEvenWhileHeartbeatKeepsArriving() {
        val freshHeartbeat = state.copy(lastHeartbeatAtEpochMillis = 20_000, lastHeartbeatAtElapsedMillis = 20_000,
            mavlinkDiagnostics = state.mavlinkDiagnostics.copy(messageObservations = listOf(heartbeat.copy(lastAcceptedAtElapsedMillis = 20_000), rcObservation)))
        val rc = RcPwmPanels.rc(freshHeartbeat, 20_000)
        assertEquals("STALE · VALUES HIDDEN", rc.status)
        assertTrue(rc.rows.all { it.second == "—" })
        assertTrue(RcPwmPanels.pwm(freshHeartbeat, 0, 20_000).rows.all { it.second == "—" })
    }

    @Test fun disconnectedDegradedInactiveAndOldHeartbeatHideNumbers() {
        val variants = listOf(state.copy(connectionStatus = VehicleConnectionState.DISCONNECTED),
            state.copy(connectionStatus = VehicleConnectionState.DEGRADED),
            state.copy(mavlinkDiagnostics = state.mavlinkDiagnostics.copy(active = false)),
            state.copy(lastHeartbeatAtEpochMillis = 2_000, lastHeartbeatAtElapsedMillis = 2_000))
        variants.forEach { sample ->
            assertTrue(RcPwmPanels.rc(sample, 11_000).rows.all { it.second == "—" })
            assertTrue(RcPwmPanels.pwm(sample, 0, 11_000).rows.all { it.second == "—" })
        }
    }

    @Test fun absentPreviousSessionAndFutureSamplesAreNotRecent() {
        assertEquals("NOT RECEIVED", RcPwmPanels.rc(VehicleState(), 11_000).status)
        assertEquals("NOT RECEIVED", RcPwmPanels.pwm(state, 99, 11_000).status)
        assertEquals("NOT FROM CURRENT SESSION", RcPwmPanels.rc(
            state.copy(rcInput = state.rcInput.copy(lastUpdatedAtElapsedMillis = 999)), 11_000).status)
        val future = RcPwmPanels.rc(state.copy(rcInput = state.rcInput.copy(lastUpdatedAtElapsedMillis = 12_000)), 11_000)
        assertTrue(future.status.startsWith("CLOCK INCONSISTENT"))
        assertTrue(future.rows.all { it.second == "—" })
    }
    @Test fun wallClockJumpsDoNotChangeRcFreshness() {
        for (wall in listOf(1L, Long.MAX_VALUE)) {
            val sample = state.copy(lastHeartbeatAtEpochMillis = wall,
                rcInput = state.rcInput.copy(lastUpdatedAtEpochMillis = wall),
                mavlinkDiagnostics = state.mavlinkDiagnostics.copy(startedAtEpochMillis = wall))
            assertEquals("1500 µs", RcPwmPanels.rc(sample, 11_000).rows.toMap()["RC 1"])
            val stale = sample.copy(lastHeartbeatAtElapsedMillis = 20_001,
                mavlinkDiagnostics = sample.mavlinkDiagnostics.copy(messageObservations = listOf(
                    heartbeat.copy(lastAcceptedAtElapsedMillis = 20_001), rcObservation)))
            assertEquals("STALE · VALUES HIDDEN", RcPwmPanels.rc(stale, 20_001).status)
        }
    }
    @Test fun missingOrInvalidSessionAndSelectedSourceEvidenceHideRcValues() {
        val variants = listOf(
            state.copy(systemId = null), state.copy(componentId = 0),
            state.copy(lastHeartbeatAtElapsedMillis = null),
            state.copy(rcInput = state.rcInput.copy(lastUpdatedAtElapsedMillis = null)),
            state.copy(mavlinkDiagnostics = state.mavlinkDiagnostics.copy(startedAtElapsedMillis = null)),
            state.copy(mavlinkDiagnostics = state.mavlinkDiagnostics.copy(startedAtElapsedMillis = -1)),
            state.copy(mavlinkDiagnostics = state.mavlinkDiagnostics.copy(messageObservations = listOf(heartbeat))),
            state.copy(mavlinkDiagnostics = state.mavlinkDiagnostics.copy(messageObservations = listOf(heartbeat, rcObservation.copy(accepted = 0, otherSource = 1)))),
            state.copy(mavlinkDiagnostics = state.mavlinkDiagnostics.copy(messageObservations = listOf(heartbeat.copy(lastAcceptedAtElapsedMillis = 11_001), rcObservation))),
            state.copy(mavlinkDiagnostics = state.mavlinkDiagnostics.copy(messageObservations = listOf(heartbeat, rcObservation.copy(lastAcceptedAtElapsedMillis = 999)))),
        )
        for (sample in variants) assertTrue(RcPwmPanels.rc(sample, 11_000).rows.all { it.second == "—" })
        assertTrue(RcPwmPanels.rc(state, null).rows.all { it.second == "—" })
    }
    @Test fun acceptedEvidenceExpiresIndependentlyAndRecoveryIsVisible() {
        val heartbeatAt = heartbeat.copy(lastAcceptedAtElapsedMillis = 15_001)
        val current = state.copy(lastHeartbeatAtElapsedMillis = 15_001,
            mavlinkDiagnostics = state.mavlinkDiagnostics.copy(messageObservations = listOf(heartbeatAt, rcObservation)))
        assertTrue(RcPwmPanels.rc(current, 15_001).rows.all { it.second == "—" })
        val recovered = current.copy(rcInput = current.rcInput.copy(lastUpdatedAtElapsedMillis = 15_001),
            mavlinkDiagnostics = current.mavlinkDiagnostics.copy(messageObservations = listOf(heartbeatAt, rcObservation.copy(lastAcceptedAtElapsedMillis = 15_001))))
        assertEquals("1500 µs", RcPwmPanels.rc(recovered, 15_001).rows.toMap()["RC 1"])
        assertEquals("1500 µs", RcPwmPanels.rc(state, 15_000).rows.toMap()["RC 1"])
        assertTrue(RcPwmPanels.rc(state.copy(mavlinkDiagnostics = state.mavlinkDiagnostics.copy(startedAtElapsedMillis = 10_001)), 11_000).rows.all { it.second == "—" })
    }

}
