package com.mamadrones.gcs.data.transport.bluetooth

import com.mamadrones.gcs.data.transport.TransportException
import com.mamadrones.gcs.data.mavlink.MavlinkChecksum
import com.mamadrones.gcs.data.rtk.MavlinkRtcmEncoder
import com.mamadrones.gcs.data.rtk.Rtcm3Framer
import com.mamadrones.gcs.domain.model.TransportStatus
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test

class BluetoothTransportTest {
    @Test fun `only validated RTCM can be written as MAVLink 233 on the selected socket`() = runBlocking {
        val socket = FakeClassicSocket()
        val transport = BluetoothTransport({ socket }, elapsedClock = { System.nanoTime() / 1_000_000 })
        val raw = byteArrayOf(0xd3.toByte(), 0, 1, 0x55, 0, 0, 0)
        val crc = Rtcm3Framer.crc24q(raw, 4)
        raw[4] = (crc shr 16).toByte(); raw[5] = (crc shr 8).toByte(); raw[6] = crc.toByte()
        val frame = Rtcm3Framer().feed(raw).single()
        try {
            transport.connect()
            try { transport.send(byteArrayOf(1, 2)); fail("Generic write must remain blocked") }
            catch (_: TransportException.UnsupportedTransport) { }
            transport.sendCorrection(frame, MavlinkRtcmEncoder())
            val packet = synchronized(socket.writes) { socket.writes.single() }
            assertEquals(194, packet.size)
            assertEquals(233, packet[7].toInt() and 0xff)
            assertEquals(raw.size, packet[11].toInt() and 0xff)
            assertEquals(MavlinkChecksum.calculate(packet.copyOfRange(1, 192), 35),
                (packet[192].toInt() and 0xff) or ((packet[193].toInt() and 0xff) shl 8))
        } finally { transport.close() }
        try { transport.sendCorrection(frame, MavlinkRtcmEncoder()); fail("Closed link must reject correction") }
        catch (_: IllegalStateException) { }
        assertEquals(1, socket.closes.get())
    }
    @Test fun `receives ordered independent chunks and counts reads without writing`() = runBlocking {
        val socket = FakeClassicSocket()
        val transport = BluetoothTransport({ socket }, elapsedClock = { System.nanoTime() / 1_000_000 })
        val chunks = Channel<ByteArray>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { transport.receive().collect { chunks.send(it) } }
        try {
            transport.connect()
            socket.offer(byteArrayOf(1, 2))
            socket.offer(byteArrayOf(3, 4, 5))
            assertArrayEquals(byteArrayOf(1, 2), withTimeout(3_000) { chunks.receive() })
            assertArrayEquals(byteArrayOf(3, 4, 5), withTimeout(3_000) { chunks.receive() })
            awaitCondition { transport.connectionState.value.packetStatistics.receivedPackets == 2L }
            assertEquals(0L, transport.connectionState.value.packetStatistics.transmittedPackets)
            try { transport.send(byteArrayOf(9)); fail("Transmit must be disabled") }
            catch (_: TransportException.UnsupportedTransport) { }
        } finally { transport.close(); collector.cancelAndJoin() }
        assertEquals(1, socket.closes.get())
        assertEquals(TransportStatus.DISCONNECTED, transport.connectionState.value.status)
    }

    @Test fun `elapsed socket open time is recorded and cleared when closed`() = runBlocking {
        val socket = FakeClassicSocket()
        val transport = BluetoothTransport({ socket }, elapsedClock = { 1234L })
        try {
            transport.connect()
            assertEquals(1234L, transport.connectionState.value.connectedAtElapsedMillis)
        } finally { transport.close() }
        assertNull(transport.connectionState.value.connectedAtElapsedMillis)
    }

    @Test fun `remote EOF closes socket and reports error`() = runBlocking {
        val socket = FakeClassicSocket()
        val transport = BluetoothTransport({ socket }, elapsedClock = { System.nanoTime() / 1_000_000 })
        try {
            transport.connect()
            socket.offer(byteArrayOf())
            awaitCondition { transport.connectionState.value.status == TransportStatus.ERROR }
            awaitCondition { socket.closes.get() == 1 }
            assertTrue(transport.connectionState.value.detail!!.contains("disconnected"))
        } finally { transport.close() }
    }

    @Test fun `cancel aborts blocking connect and cannot reopen the closed transport`() = runBlocking {
        val socket = FakeClassicSocket(blockConnect = true)
        val transport = BluetoothTransport({ socket }, elapsedClock = { System.nanoTime() / 1_000_000 })
        val connecting = launch { transport.connect() }
        awaitCondition { socket.connects.get() == 1 }
        connecting.cancelAndJoin()
        assertEquals(1, socket.closes.get())
        assertEquals(TransportStatus.DISCONNECTED, transport.connectionState.value.status)
        try { transport.connect(); fail("A closed session must not reopen") }
        catch (_: IllegalStateException) { }
    }

    @Test fun `timeout aborts blocking connect`() = runBlocking {
        val socket = FakeClassicSocket(blockConnect = true)
        val transport = BluetoothTransport({ socket }, connectTimeoutMillis = 100, elapsedClock = { System.nanoTime() / 1_000_000 })
        try { transport.connect(); fail("Expected timeout") }
        catch (_: IOException) { }
        finally { transport.close() }
        assertEquals(1, socket.closes.get())
        assertEquals(TransportStatus.ERROR, transport.connectionState.value.status)
        assertTrue(transport.connectionState.value.detail!!.contains("timed out"))
    }

    @Test fun `permission or pairing failure is visible and does not leave connecting state`() = runBlocking {
        val transport = BluetoothTransport({ throw SecurityException("Permission revoked") })
        try { transport.connect(); fail("Expected permission error") }
        catch (_: SecurityException) { }
        finally { transport.close() }
        assertEquals(TransportStatus.ERROR, transport.connectionState.value.status)
        assertEquals("Permission revoked", transport.connectionState.value.detail)
    }

    @Test fun `data without a subscriber fails instead of silently discarding telemetry`() = runBlocking {
        val socket = FakeClassicSocket()
        val transport = BluetoothTransport({ socket }, elapsedClock = { System.nanoTime() / 1_000_000 })
        try {
            transport.connect()
            socket.offer(byteArrayOf(42))
            awaitCondition { transport.connectionState.value.status == TransportStatus.ERROR }
            assertTrue(transport.connectionState.value.detail!!.contains("buffer"))
        } finally { transport.close() }
    }

    @Test fun `blocked consumer overflow fails and releases the socket`() = runBlocking {
        val socket = FakeClassicSocket()
        val transport = BluetoothTransport({ socket }, elapsedClock = { System.nanoTime() / 1_000_000 })
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { transport.receive().collect { awaitCancellation() } }
        try {
            transport.connect()
            repeat(140) { socket.offer(byteArrayOf(1)) }
            awaitCondition { transport.connectionState.value.status == TransportStatus.ERROR }
            awaitCondition { socket.closes.get() == 1 }
        } finally { transport.close(); collector.cancelAndJoin() }
    }
}

internal class FakeClassicSocket(private val blockConnect: Boolean = false) : ClassicSerialSocket {
    val connects = AtomicInteger()
    val closes = AtomicInteger()
    private val connectLatch = CountDownLatch(1)
    private val input = LinkedBlockingQueue<ByteArray>()
    val writes = mutableListOf<ByteArray>()
    override fun connect() {
        connects.incrementAndGet()
        if (blockConnect) connectLatch.await()
        if (closes.get() > 0) throw IOException("Socket closed")
    }
    override fun read(buffer: ByteArray): Int {
        val chunk = input.take()
        if (chunk.isEmpty()) return -1
        chunk.copyInto(buffer)
        return chunk.size
    }
    override fun write(data: ByteArray) { synchronized(writes) { writes.add(data.copyOf()) } }
    fun offer(chunk: ByteArray) { input.put(chunk) }
    override fun close() {
        closes.incrementAndGet()
        connectLatch.countDown()
        input.offer(byteArrayOf())
    }
}

internal suspend fun awaitCondition(predicate: () -> Boolean) {
    withTimeout(3_000) { while (!predicate()) delay(5) }
}
