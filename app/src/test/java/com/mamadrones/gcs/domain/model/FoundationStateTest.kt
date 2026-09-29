package com.mamadrones.gcs.domain.model

import org.junit.Assert.*
import org.junit.Test

class FoundationStateTest {
    @Test fun `initial state does not imply any machinery is safe or off`() {
        val state = VehicleState()
        assertFalse(state.connected)
        assertNull(state.vehicleId)
        assertNull(state.armed)
        assertNull(state.speedMetersPerSecond)
        assertEquals(Direction.UNKNOWN, state.direction)
        assertEquals(GpsFix.UNKNOWN, state.gps.fix)
        assertNull(state.battery.percentage)
        assertTrue(state.motors.isEmpty())
        assertNull(state.spray.nozzles)
        assertEquals(SwitchState.UNKNOWN, state.spray.pump.power)
        assertEquals(SwitchState.UNKNOWN, state.hydraulic.enabled)
        assertEquals(SwitchState.UNKNOWN, state.hydraulic.pump)
        assertEquals(HealthLevel.UNKNOWN, state.health.overall)
    }
    @Test fun `domain supports arbitrary motors and keeps RPM distinct from electrical RPM`() {
        val state = VehicleState(motors = (1..4).map { MotorState(id = it.toString(), electricalRpm = 6000.0) })
        assertEquals(4, state.motors.size)
        state.motors.forEach { assertNull(it.rpm); assertEquals(6000.0, it.electricalRpm!!, 0.0) }
    }
}
