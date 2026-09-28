package com.mamadrones.gcs.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleConnectionStateTest {
    @Test fun `phase one begins disconnected`() {
        assertEquals(VehicleConnectionState.DISCONNECTED, VehicleConnectionState.DISCONNECTED)
    }
}
