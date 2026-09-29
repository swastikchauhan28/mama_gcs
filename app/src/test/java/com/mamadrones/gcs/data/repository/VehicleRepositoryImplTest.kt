package com.mamadrones.gcs.data.repository

import com.mamadrones.gcs.data.mavlink.MavlinkMessage
import com.mamadrones.gcs.domain.model.VehicleConnectionState
import org.junit.Assert.assertEquals
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
}
