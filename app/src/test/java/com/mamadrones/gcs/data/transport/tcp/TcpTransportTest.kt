package com.mamadrones.gcs.data.transport.tcp

import com.mamadrones.gcs.domain.model.TransportStatus
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Test

class TcpTransportTest {
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
