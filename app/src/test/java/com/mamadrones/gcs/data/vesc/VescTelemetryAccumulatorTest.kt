package com.mamadrones.gcs.data.vesc

import com.mamadrones.gcs.domain.model.SubsystemConnection
import org.junit.Assert.*
import org.junit.Test

class VescTelemetryAccumulatorTest {
    private val profile = VescTelemetryProfile(listOf("left", "right"), staleAfterMillis = 2_000)

    @Test fun `only provisioned controllers appear and keep distinct mechanical and electrical speeds`() {
        val accumulator = VescTelemetryAccumulator(profile)
        assertFalse(accumulator.accept(VescTelemetryReading("unmapped", mechanicalRpm = 42.0), 1_000))
        assertTrue(accumulator.accept(VescTelemetryReading("right", mechanicalRpm = 100.0, electricalRpm = 700.0), 1_000))

        val motors = accumulator.snapshot(1_500)
        assertEquals(listOf("left", "right"), motors.map { it.id })
        assertEquals(SubsystemConnection.NOT_CONNECTED, motors[0].connection)
        assertEquals(SubsystemConnection.CONNECTED, motors[1].connection)
        assertEquals(100.0, motors[1].rpm!!, 0.0)
        assertEquals(700.0, motors[1].electricalRpm!!, 0.0)
        assertNull(motors[1].faultCode)
    }

    @Test fun `stale samples lose readings and a new session clears old values`() {
        val accumulator = VescTelemetryAccumulator(profile)
        accumulator.accept(VescTelemetryReading("left", inputVoltage = 48.0, faultCode = "OVER_TEMP"), 1_000)
        assertEquals(SubsystemConnection.CONNECTED, accumulator.snapshot(3_000)[0].connection)
        val stale = accumulator.snapshot(3_001)[0]
        assertEquals(SubsystemConnection.STALE, stale.connection)
        assertNull(stale.voltage)
        assertNull(stale.faultCode)
        assertEquals(1_000L, stale.lastUpdatedAtEpochMillis)

        accumulator.clear()
        assertEquals(SubsystemConnection.NOT_CONNECTED, accumulator.snapshot(3_001)[0].connection)
    }

    @Test fun `invalid and out of order samples cannot overwrite good data`() {
        val accumulator = VescTelemetryAccumulator(profile)
        assertTrue(accumulator.accept(VescTelemetryReading("left", inputVoltage = 50.0), 2_000))
        assertFalse(accumulator.accept(VescTelemetryReading("left", inputVoltage = 40.0), 1_999))
        assertFalse(accumulator.accept(VescTelemetryReading("left", mechanicalRpm = Double.NaN), 2_001))
        assertFalse(accumulator.accept(VescTelemetryReading("left", inputVoltage = -1.0), 2_001))
        assertFalse(accumulator.accept(VescTelemetryReading("left", dutyCycle = 1.1), 2_001))
        assertFalse(accumulator.accept(VescTelemetryReading("left"), 2_001))
        assertEquals(50.0, accumulator.snapshot(2_000)[0].voltage!!, 0.0)
    }

    @Test fun `profile requires explicit unique inventory and positive freshness policy`() {
        assertThrows(IllegalArgumentException::class.java) { VescTelemetryProfile(emptyList(), 1_000) }
        assertThrows(IllegalArgumentException::class.java) { VescTelemetryProfile(listOf("A", "A"), 1_000) }
        assertThrows(IllegalArgumentException::class.java) { VescTelemetryProfile(listOf("A"), 0) }
    }
}
