package com.mamadrones.gcs.data.repository

import com.mamadrones.gcs.data.mavlink.MavlinkMessage
import com.mamadrones.gcs.domain.model.VehicleConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleRepositoryImplTest {
    @Test
    fun `heartbeat exposes identity armed state and Rover mode`() {
        val repository = VehicleRepositoryImpl()
        repository.onHeartbeat(
            MavlinkMessage.Heartbeat(1, 1, 10, 10, 3, 128, 4, 3),
            receivedAtEpochMillis = 100L
        )

        val state = repository.vehicleState.value
        assertEquals(VehicleConnectionState.CONNECTED, state.connectionStatus)
        assertEquals("AUTO", state.mode)
        assertTrue(state.armed == true)
    }

    @Test
    fun `heartbeat timeout degrades an otherwise connected vehicle`() {
        val repository = VehicleRepositoryImpl()
        repository.onHeartbeat(MavlinkMessage.Heartbeat(1, 1, 0, 10, 3, 0, 4, 3), 100L)
        repository.onHeartbeatTimeout()
        assertEquals(VehicleConnectionState.DEGRADED, repository.vehicleState.value.connectionStatus)
    }

    @Test
    fun `GPS raw maps fix units and keeps invalid fix data unknown`() {
        val repository = VehicleRepositoryImpl()
        repository.onGpsRawInt(
            MavlinkMessage.GpsRawInt(1, 1, 3, 451_234_567, -734_567_890, 123_456, 125, 12),
            1_000L
        )
        val valid = repository.vehicleState.value.gps
        assertEquals(com.mamadrones.gcs.domain.model.GpsFix.FIX_3D, valid.fix)
        assertEquals(45.1234567, valid.latitude!!, 0.0000001)
        assertEquals(-73.456789, valid.longitude!!, 0.0000001)
        assertEquals(1.25, valid.hdop!!, 0.0001)
        assertEquals(123.456, valid.altitudeMeters!!, 0.001)
        assertEquals(1_000L, valid.lastUpdatedAtEpochMillis)

        repository.onGpsRawInt(MavlinkMessage.GpsRawInt(1, 1, 1, 0, 0, 0, 65_535, 255), 2_000L)
        val unavailable = repository.vehicleState.value.gps
        assertEquals(com.mamadrones.gcs.domain.model.GpsFix.NO_FIX, unavailable.fix)
        assertNull(unavailable.latitude)
        assertNull(unavailable.longitude)
        assertNull(unavailable.altitudeMeters)
        assertNull(unavailable.hdop)
        assertNull(unavailable.satellites)
    }

    @Test
    fun `global position converts units and leaves invalid heading unknown`() {
        val repository = VehicleRepositoryImpl()
        repository.onGlobalPositionInt(
            MavlinkMessage.GlobalPositionInt(1, 1, 338_654_321, 1_512_345_678, 12_345, -300, 400, 0, 12_345),
            2_000L
        )
        val state = repository.vehicleState.value
        assertEquals(33.8654321, state.position.latitude!!, 0.0000001)
        assertEquals(151.2345678, state.position.longitude!!, 0.0000001)
        assertEquals(12.345, state.position.altitudeMetersMsl!!, 0.001)
        assertEquals(5.0, state.speedMetersPerSecond!!, 0.001)
        assertEquals(123.45, state.headingDegrees!!, 0.001)

        repository.onGlobalPositionInt(MavlinkMessage.GlobalPositionInt(1, 1, Int.MAX_VALUE, 0, 0, 0, 0, 0, 65_535), 3_000L)
        assertNull(repository.vehicleState.value.position.latitude)
        assertNull(repository.vehicleState.value.position.longitude)
        assertNull(repository.vehicleState.value.headingDegrees)
    }

    @Test
    fun `system and per-battery telemetry preserve sentinels and battery identities`() {
        val repository = VehicleRepositoryImpl()
        repository.onSystemStatus(
            MavlinkMessage.SystemStatus(1, 1, 7, 3, 1, 765, 24_600, -1, -1, 250, 4),
            4_000L
        )
        val summary = repository.vehicleState.value
        assertEquals(76.5, summary.systemStatus.cpuLoadPercent!!, 0.001)
        assertEquals(2.5, summary.systemStatus.communicationDropPercent!!, 0.001)
        assertEquals(24.6, summary.battery.voltage!!, 0.001)
        assertNull(summary.battery.currentAmps)
        assertNull(summary.battery.percentage)

        repository.onBatteryStatus(
            MavlinkMessage.BatteryStatus(1, 1, 2, 2_500, listOf(12_000, 12_100, 65_535), 325, 81, chargeState = 7),
            5_000L
        )
        repository.onBatteryStatus(
            MavlinkMessage.BatteryStatus(1, 1, 1, 32_767, listOf(24_000, 65_535), -1, -1),
            6_000L
        )
        val packs = repository.vehicleState.value.batteries
        assertEquals(listOf(1, 2), packs.map { it.batteryId })
        assertEquals(24.1, packs[1].voltage!!, 0.001)
        assertEquals(25.0, packs[1].temperatureCelsius!!, 0.001)
        assertEquals(7, packs[1].chargeState)
        assertNull(packs[0].temperatureCelsius)
        assertNull(packs[0].currentAmps)
        assertNull(packs[0].percentage)
    }

    @Test
    fun `invalid attitude floats remain unknown and status text is bounded`() {
        val repository = VehicleRepositoryImpl()
        repository.onAttitude(MavlinkMessage.Attitude(1, 1, 0.1f, Float.NaN, 4.0f, 0f, Float.POSITIVE_INFINITY, 0f), 7L)
        val attitude = repository.vehicleState.value.attitude
        assertEquals(0.1, attitude.rollRadians!!, 0.0001)
        assertNull(attitude.pitchRadians)
        assertNull(attitude.yawRadians)
        assertNull(attitude.pitchRateRadiansPerSecond)

        repeat(55) { index -> repository.onStatusText(6, "message $index", index.toLong()) }
        assertEquals(50, repository.vehicleState.value.statusTexts.size)
        assertEquals("message 54", repository.vehicleState.value.statusTexts.first().text)
    }
}
