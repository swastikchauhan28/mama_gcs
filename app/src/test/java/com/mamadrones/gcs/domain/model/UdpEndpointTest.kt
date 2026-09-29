package com.mamadrones.gcs.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UdpEndpointTest {
    @Test
    fun `accepts a local ephemeral port and displays remote peer`() {
        val endpoint = UdpEndpoint("192.168.4.1", remotePort = 14550, localPort = 0)
        assertEquals("192.168.4.1:14550", endpoint.displayName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects an out of range remote port`() {
        UdpEndpoint("192.168.4.1", remotePort = 0)
    }
}
