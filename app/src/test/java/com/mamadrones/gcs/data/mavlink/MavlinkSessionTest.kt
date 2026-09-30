package com.mamadrones.gcs.data.mavlink

import com.mamadrones.gcs.data.repository.VehicleRepositoryImpl
import com.mamadrones.gcs.data.transport.VehicleTransport
import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.TransportStatus
import com.mamadrones.gcs.domain.model.VehicleConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MavlinkSessionTest {
    @Test
    fun `socket open does not connect vehicle until an autopilot heartbeat arrives`() = runBlocking {
        val transport = FakeTransport()
        val repository = VehicleRepositoryImpl()
        val session = newSession(transport, repository)
        try {
            session.start()
            assertEquals(TransportStatus.OPEN, transport.connectionState.value.status)
            assertEquals(VehicleConnectionState.CONNECTING, repository.vehicleState.value.connectionStatus)

            transport.emit(heartbeat(systemId = 1, componentId = 1, autopilot = MavlinkMessage.MAV_AUTOPILOT_INVALID))
            delay(20)
            assertEquals(VehicleConnectionState.CONNECTING, repository.vehicleState.value.connectionStatus)

            transport.emit(heartbeat(systemId = 1, componentId = 1, autopilot = 3, customMode = 10, baseMode = 128))
            waitUntil { repository.vehicleState.value.connectionStatus == VehicleConnectionState.CONNECTED }
            assertEquals(1, repository.vehicleState.value.systemId)
            assertEquals(1, repository.vehicleState.value.componentId)
            assertEquals("AUTO", repository.vehicleState.value.mode)
            assertTrue(repository.vehicleState.value.armed == true)
        } finally {
            session.stop()
            session.close()
        }
    }

    @Test
    fun `session pins its first autopilot identity and ignores other systems`() = runBlocking {
        val transport = FakeTransport()
        val repository = VehicleRepositoryImpl()
        val session = newSession(transport, repository)
        try {
            session.start()
            transport.emit(heartbeat(systemId = 42, componentId = 17, autopilot = 3, customMode = 4))
            waitUntil { repository.vehicleState.value.connectionStatus == VehicleConnectionState.CONNECTED }

            transport.emit(heartbeat(systemId = 99, componentId = 1, autopilot = 3, customMode = 10, baseMode = 128))
            delay(20)

            assertEquals(42, repository.vehicleState.value.systemId)
            assertEquals(17, repository.vehicleState.value.componentId)
            assertEquals("HOLD", repository.vehicleState.value.mode)
            assertEquals(false, repository.vehicleState.value.armed)
        } finally {
            session.stop()
            session.close()
        }
    }

    @Test
    fun `missing heartbeat degrades vehicle while socket remains open`() = runBlocking {
        val transport = FakeTransport()
        val repository = VehicleRepositoryImpl()
        val session = newSession(transport, repository, timeoutMillis = 30, checkIntervalMillis = 5)
        try {
            session.start()
            waitUntil { repository.vehicleState.value.connectionStatus == VehicleConnectionState.DEGRADED }
            assertEquals(TransportStatus.OPEN, transport.connectionState.value.status)
            assertNull(repository.vehicleState.value.armed)
        } finally {
            session.stop()
            session.close()
        }
    }

    private fun newSession(
        transport: VehicleTransport,
        repository: VehicleRepositoryImpl,
        timeoutMillis: Long = 5_000,
        checkIntervalMillis: Long = 10
    ) = MavlinkSession(
        transport = transport,
        parser = MavlinkParser(),
        router = MavlinkMessageRouter(repository),
        vehicleRepository = repository,
        heartbeatTimeoutMillis = timeoutMillis,
        heartbeatCheckIntervalMillis = checkIntervalMillis,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    )

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            delay(5)
        }
        assertTrue("Condition did not become true", predicate())
    }

    private fun heartbeat(
        systemId: Int,
        componentId: Int,
        autopilot: Int,
        customMode: Long = 0,
        baseMode: Int = 0
    ): ByteArray {
        val payload = byteArrayOf(
            customMode.toByte(), (customMode shr 8).toByte(), (customMode shr 16).toByte(), (customMode shr 24).toByte(),
            10, autopilot.toByte(), baseMode.toByte(), 4, 3
        )
        val headerAndPayload = byteArrayOf(
            0xFD.toByte(), 9, 0, 0, 1, systemId.toByte(), componentId.toByte(), 0, 0, 0
        ) + payload
        val crc = MavlinkChecksum.calculate(headerAndPayload.copyOfRange(1, headerAndPayload.size), 50)
        return headerAndPayload + byteArrayOf(crc.toByte(), (crc shr 8).toByte())
    }

    private class FakeTransport : VehicleTransport {
        override val connectionState = MutableStateFlow(ConnectionState())
        private val packets = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)
        override suspend fun connect() {
            connectionState.value = ConnectionState(status = TransportStatus.OPEN)
        }
        override suspend fun disconnect() {
            connectionState.value = ConnectionState()
        }
        override suspend fun send(data: ByteArray) = Unit
        override fun receive(): Flow<ByteArray> = packets.asSharedFlow()
        suspend fun emit(bytes: ByteArray) { packets.emit(bytes) }
    }
}
