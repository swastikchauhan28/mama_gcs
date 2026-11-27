package com.mamadrones.gcs.data.transport.tcp

import com.mamadrones.gcs.domain.model.TransportStatus
import com.mamadrones.gcs.data.rtk.MavlinkRtcmEncoder
import com.mamadrones.gcs.data.rtk.Rtcm3Framer
import com.mamadrones.gcs.data.mavlink.MavlinkChecksum
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TcpTransportTest {
    @Test fun `loopback TCP writes only explicitly started RTCM packets`() = runBlocking {
        val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val transport = TcpTransport("127.0.0.1", listener.localPort, elapsedClock = { System.nanoTime() / 1_000_000 })
        val received = async(Dispatchers.IO) {
            listener.accept().use { socket -> socket.getInputStream().readNBytes(194) }
        }
        try {
            transport.connect()
            assertThrows(com.mamadrones.gcs.data.transport.TransportException.UnsupportedTransport::class.java) {
                runBlocking { transport.send(byteArrayOf(1, 2, 3)) }
            }
            val raw = byteArrayOf(0xd3.toByte(), 0, 1, 0x55, 0, 0, 0)
            val crc = Rtcm3Framer.crc24q(raw, 4)
            raw[4] = (crc shr 16).toByte(); raw[5] = (crc shr 8).toByte(); raw[6] = crc.toByte()
            val frame = Rtcm3Framer().feed(raw).single()
            transport.sendCorrection(frame, MavlinkRtcmEncoder())
            val packet = withTimeout(5_000) { received.await() }
            assertEquals(194, packet.size)
            assertEquals(233, packet[7].toInt() and 0xff)
            assertEquals(raw.size, packet[11].toInt() and 0xff)
            assertEquals(MavlinkChecksum.calculate(packet.copyOfRange(1, 192), 35),
                (packet[192].toInt() and 0xff) or ((packet[193].toInt() and 0xff) shl 8))
        } finally {
            transport.close()
            listener.close()
            received.cancel()
        }
    }

    @Test fun `saturated receive buffer fails link rather than silently dropping chunks`() = runBlocking {
        val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val transport = TcpTransport("127.0.0.1", listener.localPort, elapsedClock = { System.nanoTime() / 1_000_000 })
        val firstChunk = CompletableDeferred<Unit>()
        val receiver = launch(start = CoroutineStart.UNDISPATCHED) {
            transport.receive().collect {
                firstChunk.complete(Unit)
                awaitCancellation() // Simulate a decoder that stops consuming.
            }
        }
        val sender = launch(Dispatchers.IO) {
            try {
                listener.accept().use { socket ->
                    val chunk = ByteArray(4_096) { 0x55 }
                    repeat(512) { socket.getOutputStream().write(chunk) }
                }
            } catch (_: java.io.IOException) {
                // The transport closes the socket when its finite receive buffer fills.
            }
        }
        try {
            transport.connect()
            val failed = withTimeout(10_000) {
                firstChunk.await()
                transport.connectionState.first { it.status == TransportStatus.ERROR }
            }
            assertTrue(failed.detail.orEmpty().contains("buffer", ignoreCase = true))
        } finally {
            transport.close()
            listener.close()
            receiver.cancelAndJoin()
            sender.cancelAndJoin()
        }
    }
}
