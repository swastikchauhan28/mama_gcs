package com.mamadrones.gcs.data.transport.bluetooth

import com.mamadrones.gcs.data.transport.TransportException
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
    @Test fun `receives ordered independent chunks and counts reads without writing`() = runBlocking {
        val socket = FakeClassicSocket()
        val transport = BluetoothTransport({ socket })
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

    @Test fun `remote EOF closes socket and reports error`() = runBlocking {
        val socket = FakeClassicSocket()
        val transport = BluetoothTransport({ socket })
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
        val transport = BluetoothTransport({ socket })
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
        val transport = BluetoothTransport({ socket }, connectTimeoutMillis = 100)
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
        val transport = BluetoothTransport({ socket })
        try {
            transport.connect()
            socket.offer(byteArrayOf(42))
            awaitCondition { transport.connectionState.value.status == TransportStatus.ERROR }
            assertTrue(transport.connectionState.value.detail!!.contains("buffer"))
        } finally { transport.close() }
    }

    @Test fun `blocked consumer overflow fails and releases the socket`() = runBlocking {
        val socket = FakeClassicSocket()
        val transport = BluetoothTransport({ socket })
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
