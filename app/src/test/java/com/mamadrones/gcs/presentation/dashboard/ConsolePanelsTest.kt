package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ConsolePanelsTest {
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
}
