package com.mamadrones.gcs.data.repository

import com.mamadrones.gcs.data.mavlink.MavlinkMessage
import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import org.junit.Assert.*
import org.junit.Test

class RcPwmRepositoryTest {
    @Test fun unusedAndUnreportedRcChannelsAreUnknownAndRssiIsRaw() {
        val repo = VehicleRepositoryImpl()
        repo.onRcChannels(MavlinkMessage.RcChannels(1, 1, 2, listOf(1500, 65535, 2000), 254), 100)
        val rc = repo.vehicleState.value.rcInput
        assertEquals(2, rc.channelCount)
        assertEquals(listOf(1500, null) + List<Int?>(16) { null }, rc.channelsMicroseconds)
        assertEquals(254, rc.rssiRaw)
        assertEquals(100L, rc.lastUpdatedAtEpochMillis)
        repo.onRcChannels(MavlinkMessage.RcChannels(1, 1, 0, List(18) { 1500 }, 255), 200)
        assertTrue(repo.vehicleState.value.rcInput.channelsMicroseconds.all { it == null })
        assertNull(repo.vehicleState.value.rcInput.rssiRaw)
    }

    @Test fun largerRcCountIsPreservedWithoutAllocatingMoreThan18Readings() {
        val repo = VehicleRepositoryImpl()
        repo.onRcChannels(MavlinkMessage.RcChannels(1, 1, 255, List(255) { 600 }, 0), 100)
        assertEquals(255, repo.vehicleState.value.rcInput.channelCount)
        assertEquals(18, repo.vehicleState.value.rcInput.channelsMicroseconds.size)
        assertEquals(600, repo.vehicleState.value.rcInput.channelsMicroseconds[0])
    }

    @Test fun outputPortsStaySeparateAndBoundedAndExistingPortsCanUpdate() {
        val repo = VehicleRepositoryImpl()
        repeat(6) { port -> repo.onServoOutputRaw(MavlinkMessage.ServoOutputRaw(1, 1, port, listOf(1000 + port)), 100) }
        assertEquals(listOf(0, 1, 2, 3), repo.vehicleState.value.pwmOutputs.map { it.port })
        repo.onServoOutputRaw(MavlinkMessage.ServoOutputRaw(1, 1, 1, listOf(1500, 0)), 200)
        val ports = repo.vehicleState.value.pwmOutputs
        assertEquals(1000, ports[0].outputsMicroseconds[0])
        assertEquals(listOf(1500, 0) + List<Int?>(14) { null }, ports[1].outputsMicroseconds)
        assertEquals(200L, ports[1].lastUpdatedAtEpochMillis)
    }

    @Test fun restartClearsRcAndPwmWhileDisconnectRetainsOnlyHistoricalSamples() {
        val repo = VehicleRepositoryImpl()
        repo.onRcChannels(MavlinkMessage.RcChannels(1, 1, 1, listOf(1500), 50), 100)
        repo.onServoOutputRaw(MavlinkMessage.ServoOutputRaw(1, 1, 0, listOf(1500)), 100)
        repo.onSessionStopped()
        assertNotNull(repo.vehicleState.value.rcInput.lastUpdatedAtEpochMillis)
        repo.onSessionStarting(MavlinkDiagnostics(startedAtEpochMillis = 200, active = true))
        assertNull(repo.vehicleState.value.rcInput.lastUpdatedAtEpochMillis)
        assertTrue(repo.vehicleState.value.pwmOutputs.isEmpty())
    }
}
