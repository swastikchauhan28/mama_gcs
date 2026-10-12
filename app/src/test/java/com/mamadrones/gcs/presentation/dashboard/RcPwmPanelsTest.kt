package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class RcPwmPanelsTest {
    private val state = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
        lastHeartbeatAtEpochMillis = 10_000,
        mavlinkDiagnostics = MavlinkDiagnostics(active = true, startedAtEpochMillis = 1_000),
        rcInput = RcInputState(2, listOf(1500, 2000), 200, 10_000),
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
        val freshHeartbeat = state.copy(lastHeartbeatAtEpochMillis = 20_000)
        val rc = RcPwmPanels.rc(freshHeartbeat, 20_000)
        assertEquals("STALE · VALUES HIDDEN", rc.status)
        assertTrue(rc.rows.all { it.second == "—" })
        assertTrue(RcPwmPanels.pwm(freshHeartbeat, 0, 20_000).rows.all { it.second == "—" })
    }

    @Test fun disconnectedDegradedInactiveAndOldHeartbeatHideNumbers() {
        val variants = listOf(state.copy(connectionStatus = VehicleConnectionState.DISCONNECTED),
            state.copy(connectionStatus = VehicleConnectionState.DEGRADED),
            state.copy(mavlinkDiagnostics = state.mavlinkDiagnostics.copy(active = false)),
            state.copy(lastHeartbeatAtEpochMillis = 2_000))
        variants.forEach { sample ->
            assertTrue(RcPwmPanels.rc(sample, 11_000).rows.all { it.second == "—" })
            assertTrue(RcPwmPanels.pwm(sample, 0, 11_000).rows.all { it.second == "—" })
        }
    }

    @Test fun absentPreviousSessionAndFutureSamplesAreNotRecent() {
        assertEquals("NOT RECEIVED", RcPwmPanels.rc(VehicleState(), 11_000).status)
        assertEquals("NOT RECEIVED", RcPwmPanels.pwm(state, 99, 11_000).status)
        assertEquals("NOT FROM CURRENT SESSION", RcPwmPanels.rc(
            state.copy(rcInput = state.rcInput.copy(lastUpdatedAtEpochMillis = 999)), 11_000).status)
        val future = RcPwmPanels.rc(state.copy(rcInput = state.rcInput.copy(lastUpdatedAtEpochMillis = 12_000)), 11_000)
        assertTrue(future.status.startsWith("CLOCK INCONSISTENT"))
        assertTrue(future.rows.all { it.second == "—" })
    }
}
