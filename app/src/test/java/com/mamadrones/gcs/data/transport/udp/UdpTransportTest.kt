package com.mamadrones.gcs.data.transport.udp

import com.mamadrones.gcs.data.transport.TransportException
import com.mamadrones.gcs.domain.model.VehicleConnectionState
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class UdpTransportTest {
    @Test
    fun `send transmits bytes and records packet statistics`() = runBlocking {
        DatagramSocket(0).use { listener ->
            listener.soTimeout = 2_000
            val transport = UdpTransport(
                UdpTransportConfig(
                    remoteHost = "127.0.0.1",
                    remotePort = listener.localPort,
                    localPort = 0
                )
            )
            try {
                transport.connect()
                transport.send(byteArrayOf(0xFD.toByte(), 0x01, 0x02))

                val received = DatagramPacket(ByteArray(32), 32)
                listener.receive(received)

                assertArrayEquals(byteArrayOf(0xFD.toByte(), 0x01, 0x02), received.data.copyOf(received.length))
                assertEquals(1, transport.connectionState.value.packetStatistics.transmittedPackets)
                assertNotNull(transport.connectionState.value.packetStatistics.lastTransmittedAtEpochMillis)
            } finally {
                transport.disconnect()
                transport.close()
            }
        }
    }

    @Test
    fun `send requires an active connection`() = runBlocking {
        val transport = UdpTransport(UdpTransportConfig(remoteHost = "127.0.0.1", localPort = 0))
        try {
            try {
                transport.send(byteArrayOf(1))
            } catch (_: TransportException.NotConnected) {
                return@runBlocking
            }
            throw AssertionError("Expected a not-connected transport exception")
        } finally {
            transport.close()
        }
    }

    @Test
    fun `disconnect clears transport connection state`() = runBlocking {
        val transport = UdpTransport(UdpTransportConfig(remoteHost = "127.0.0.1", localPort = 0))
        try {
            transport.connect()
            transport.disconnect()
            assertEquals(VehicleConnectionState.DISCONNECTED, transport.connectionState.value.status)
        } finally {
            transport.close()
        }
    }
}
