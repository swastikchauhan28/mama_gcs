package com.mamadrones.gcs.data.transport

import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.TransportStatus
import com.mamadrones.gcs.domain.model.UdpEndpoint
import com.mamadrones.gcs.data.mavlink.MavlinkSessionFactory
import com.mamadrones.gcs.data.mavlink.VehicleMavlinkSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportConnectionManagerTest {
    @Test
    fun `manager forwards an explicit endpoint state and closes the owned transport`() = runBlocking {
        val fake = FakeTransport()
        val manager = managerFor(fake)
        val endpoint = UdpEndpoint("127.0.0.1", remotePort = 14550, localPort = 0)

        manager.connect(endpoint)

        assertEquals(endpoint, manager.state.value.endpoint)
        assertEquals(TransportStatus.OPEN, manager.state.value.connection.status)
        manager.disconnect()
        assertTrue(fake.disconnected)
        assertTrue(fake.closed)
        assertEquals(TransportStatus.DISCONNECTED, manager.state.value.connection.status)
    }

    @Test
    fun `manager rejects a second endpoint while a transport is active`() = runBlocking {
        val manager = managerFor(FakeTransport())
        manager.connect(UdpEndpoint("127.0.0.1", 14550, 0))
        try {
            manager.connect(UdpEndpoint("127.0.0.2", 14550, 0))
            throw AssertionError("Expected an active transport exception")
        } catch (_: TransportException.AlreadyConnected) {
            // Explicit disconnect is required before switching endpoint.
        } finally {
            manager.close()
        }
    }

    @Test
    fun `failed opening is reported and releases the attempted transport`() = runBlocking {
        val failed = FailingTransport()
        val manager = managerFor(failed)
        try {
            manager.connect(UdpEndpoint("127.0.0.1", 14550, 0))
            throw AssertionError("Expected connection failure")
        } catch (_: TransportException.ConnectionFailed) {
            assertEquals(TransportStatus.ERROR, manager.state.value.connection.status)
            assertTrue(failed.closed)
        } finally {
            manager.close()
        }
    }

    private class FakeTransport : VehicleTransport, AutoCloseable {
        private val mutableConnection = MutableStateFlow(ConnectionState())
        override val connectionState = mutableConnection
        var disconnected = false
        var closed = false

        override suspend fun connect() { mutableConnection.value = ConnectionState(status = TransportStatus.OPEN) }
        override suspend fun disconnect() { disconnected = true; mutableConnection.value = ConnectionState() }
        override suspend fun send(data: ByteArray) = Unit
        override fun receive(): Flow<ByteArray> = emptyFlow()
        override fun close() { closed = true }
    }

    private fun managerFor(transport: VehicleTransport): TransportConnectionManager = TransportConnectionManager(
        factory = UdpTransportFactory { transport },
        sessionFactory = MavlinkSessionFactory { sessionTransport, _ ->
            object : VehicleMavlinkSession {
                override suspend fun start() = sessionTransport.connect()
                override suspend fun stop() = sessionTransport.disconnect()
                override fun close() { (sessionTransport as? AutoCloseable)?.close() }
            }
        },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    )

    private class FailingTransport : VehicleTransport, AutoCloseable {
        override val connectionState = MutableStateFlow(ConnectionState())
        var closed = false

        override suspend fun connect(): Nothing = throw TransportException.ConnectionFailed(IllegalStateException("test failure"))
        override suspend fun disconnect() = Unit
        override suspend fun send(data: ByteArray) = Unit
        override fun receive(): Flow<ByteArray> = emptyFlow()
        override fun close() { closed = true }
    }
}
