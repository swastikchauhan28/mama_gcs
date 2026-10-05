package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ConsolePanelsTest {
    @Test fun `coordinates retain navigation precision and reject nonfinite values`() {
        assertEquals("-35.3632621 °", (-35.3632621).coordinateReading())
        assertEquals("149.1652374 °", 149.1652374.coordinateReading())
        assertEquals("UNKNOWN", Double.NaN.coordinateReading())
        assertEquals("UNKNOWN", (null as Double?).coordinateReading())
    }

    @Test fun `observed system is not described as paired`() {
        val state = VehicleState(systemId = 1, connectionStatus = VehicleConnectionState.CONNECTED)
        assertEquals("OBSERVED SYSTEM 1 · NOT PAIRED", ConsolePanels.vehicle(state).status)
        assertEquals("NO LIVE VEHICLE", ConsolePanels.vehicle(state.copy(connectionStatus = VehicleConnectionState.DEGRADED)).status)
    }

    @Test fun `equipment exposes fault evidence and individual nozzle states`() {
        val state = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            spray = SprayState(nozzles = listOf(NozzleState("A", SwitchState.ON)), fault = "pressure sensor"),
            hydraulic = HydraulicState(fault = "valve"))
        assertTrue(ConsolePanels.spray(state).rows.contains("Nozzle A" to "ON"))
        assertTrue(ConsolePanels.spray(state).rows.contains("System fault" to "pressure sensor"))
        assertTrue(ConsolePanels.hydraulic(state).rows.contains("Fault" to "valve"))
    }

    @Test fun `lost connection hides stale operational values while keeping application identity`() {
        val old = VehicleState(vehicleId = "test-only", armed = false, mode = "HOLD",
            connectionStatus = VehicleConnectionState.DEGRADED,
            spray = SprayState(pump = SprayPumpState(SwitchState.OFF)),
            hydraulic = HydraulicState(enabled = SwitchState.OFF))
        val displayed = old.forDisplay()
        assertEquals("test-only", displayed.vehicleId)
        assertNull(displayed.armed)
        assertNull(displayed.mode)
        assertEquals(SwitchState.UNKNOWN, displayed.spray.pump.power)
        assertEquals(SwitchState.UNKNOWN, displayed.hydraulic.enabled)
    }
    @Test fun `nonfinite telemetry cannot appear as measured data`() {
        assertEquals("UNKNOWN", Double.NaN.reading("V"))
        assertEquals("UNKNOWN", Double.POSITIVE_INFINITY.reading("V"))
        assertEquals("UNKNOWN", (null as Double?).reading("V"))
        assertEquals("0.0 V", 0.0.reading("V"))
    }

    @Test fun `telemetry panels show source receive age`() {
        assertEquals("Received 3s ago", sampleAge(1_000L, nowEpochMillis = 4_900L))
        assertEquals("No sample received", sampleAge(null, nowEpochMillis = 4_900L))
        assertEquals("Received 0s ago", sampleAge(5_000L, nowEpochMillis = 4_000L))
    }

    @Test fun `stale motor panel never displays retained measurements or a no fault claim`() {
        val motor = MotorState("left", SubsystemConnection.STALE, voltage = 48.0,
            faultCode = "NONE", lastUpdatedAtEpochMillis = 1_000)
        val panel = ConsolePanels.motor(motor)
        assertEquals("STALE", panel.status)
        assertTrue(panel.rows.contains("Input voltage" to "UNKNOWN"))
        assertTrue(panel.rows.contains("Fault" to "UNKNOWN"))
    }
}
