package com.mamadrones.gcs.data.transport

import com.mamadrones.gcs.data.transport.serial.SerialTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UnsupportedTransportTest {
    @Test
    fun `serial is explicit extension point without a socket`() = runBlocking {
        assertUnsupported(SerialTransport())
    }

    private suspend fun assertUnsupported(transport: VehicleTransport) {
        try {
            transport.connect()
            throw AssertionError("Expected unsupported transport")
        } catch (error: TransportException.UnsupportedTransport) {
            assertEquals("DISCONNECTED", transport.connectionState.value.status.name)
        }
    }
}
