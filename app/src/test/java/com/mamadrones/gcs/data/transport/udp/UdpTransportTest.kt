package com.mamadrones.gcs.data.transport.udp

import com.mamadrones.gcs.data.transport.TransportException
import com.mamadrones.gcs.domain.model.TransportStatus
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
            assertEquals(TransportStatus.DISCONNECTED, transport.connectionState.value.status)
        } finally {
            transport.close()
        }
    }

    @Test
    fun `receives a complete datagram from the configured peer`() = runBlocking {
        DatagramSocket(0).use { peer ->
            val localPort = DatagramSocket(0).use { it.localPort }
            val transport = UdpTransport(
                UdpTransportConfig(
                    remoteHost = "127.0.0.1",
                    remotePort = peer.localPort,
                    localPort = localPort
                )
            )
            try {
                transport.connect()
                val expected = ByteArray(4_096) { index -> (index % 127).toByte() }
                val received = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(2_000) { transport.receive().first() } }
                peer.send(DatagramPacket(expected, expected.size, InetSocketAddress("127.0.0.1", localPort)))
                assertArrayEquals(expected, received.await())
                assertEquals(1, transport.connectionState.value.packetStatistics.receivedPackets)
            } finally {
                transport.close()
            }
        }
    }

    @Test
    fun `closed transport cannot reopen or send`() = runBlocking {
        val transport = UdpTransport(UdpTransportConfig(remoteHost = "127.0.0.1", localPort = 0))
        transport.close()
        try {
            transport.connect()
            throw AssertionError("Expected a closed transport exception")
        } catch (_: TransportException.Closed) {
            // Expected terminal state.
        }
    }
}
