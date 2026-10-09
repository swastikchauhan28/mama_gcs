package com.mamadrones.gcs.data.mavlink

import com.mamadrones.gcs.data.repository.VehicleRepositoryImpl
import com.mamadrones.gcs.data.transport.VehicleTransport
import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.TransportStatus
import com.mamadrones.gcs.domain.model.VehicleConnectionState
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MavlinkSessionTest {
    @Test fun `cancelled closed startup cannot clear a replacement session`() = runBlocking {
        val repository = VehicleRepositoryImpl()
        val stalled = object : VehicleTransport by FakeTransport() {
            override suspend fun connect(): Unit = awaitCancellation()
        }
        val old = MavlinkSession(stalled, MavlinkParser(), MavlinkMessageRouter(repository), repository, scope = this)
        val pending = launch(start = CoroutineStart.UNDISPATCHED) { old.start() }
        val replacement = MavlinkSession(FakeTransport(), MavlinkParser(), MavlinkMessageRouter(repository), repository, scope = this)
        try {
            // Queue cancellation, then start the replacement before the old catch block runs.
            pending.cancel()
            old.close()
            replacement.start()
            pending.join()
            assertEquals(VehicleConnectionState.CONNECTING, repository.vehicleState.value.connectionStatus)
            assertTrue(repository.vehicleState.value.mavlinkDiagnostics.active)
            old.close()
            assertEquals(VehicleConnectionState.CONNECTING, repository.vehicleState.value.connectionStatus)
        } finally { pending.cancel(); old.close(); replacement.close() }
    }

    @Test fun `session counts parser outcomes and source filtering without admitting rejected data`() = runBlocking {
        val transport = FakeTransport()
        val repository = VehicleRepositoryImpl()
        val session = newSession(transport, repository)
        try {
            session.start()
            val corrupt = heartbeatFrame().also { it[5] = 9 }
            val signed = heartbeatFrame().also { it[2] = 1 } + ByteArray(13)
            val flags = heartbeatFrame().also { it[2] = 2 }
            val bytes = byteArrayOf(1, 2, 3) + globalPositionFrame(42, 451_000_000) +
                heartbeatFrame(autopilot = MavlinkMessage.MAV_AUTOPILOT_INVALID) + corrupt + signed + flags +
                mavlinkTestFrame(999, byteArrayOf(1), 0) + mavlinkTestFrame(74, ByteArray(21), 20) +
                heartbeatFrame(systemId = 42) + heartbeatFrame(systemId = 99) + globalPositionFrame(42, 451_000_000)
            transport.emit(bytes)
            waitUntil { repository.vehicleState.value.mavlinkDiagnostics.acceptedMessages == 2L }
            val d = repository.vehicleState.value.mavlinkDiagnostics
            assertEquals(TelemetryLinkKind.BLE, d.linkKind)
            assertEquals(1L, d.receivedChunks)
            assertEquals(bytes.size.toLong(), d.receivedBytes)
            assertEquals(5L, d.decodedMessages)
            assertEquals(2L, d.ignoredBeforeHeartbeat)
            assertEquals(1L, d.ignoredOtherSource)
            assertEquals(1L, d.checksumFailures)
            assertEquals(1L, d.malformedPayloads)
            assertEquals(1L, d.signedPacketsRejected)
            assertEquals(1L, d.unsupportedFlags)
            assertEquals(1L, d.unsupportedMessages)
            assertEquals(999, d.lastUnsupportedMessageId)
            assertEquals(42, repository.vehicleState.value.systemId)
            assertEquals(45.1, repository.vehicleState.value.position.latitude!!, 0.000001)
        } finally { session.stop(); session.close() }
    }

    @Test fun `fragment counters remain after close and reset on a new receive session`() = runBlocking {
        val transport = FakeTransport()
        val repository = VehicleRepositoryImpl()
        val session = newSession(transport, repository)
        try {
            session.start()
            val frame = heartbeatFrame()
            transport.emit(frame.copyOfRange(0, 5))
            waitUntil { repository.vehicleState.value.mavlinkDiagnostics.receivedChunks == 1L }
            assertEquals(0L, repository.vehicleState.value.mavlinkDiagnostics.decodedMessages)
            assertEquals(0L, repository.vehicleState.value.mavlinkDiagnostics.parserErrors)
            transport.emit(frame.copyOfRange(5, frame.size))
            waitUntil { repository.vehicleState.value.mavlinkDiagnostics.acceptedMessages == 1L }
            session.stop()
            val closed = repository.vehicleState.value.mavlinkDiagnostics
            assertTrue(!closed.active)
            assertEquals(2L, closed.receivedChunks)
            assertEquals(frame.size.toLong(), closed.receivedBytes)
            assertEquals(1L, closed.decodedMessages)
            session.start()
            val fresh = repository.vehicleState.value.mavlinkDiagnostics
            assertTrue(fresh.active)
            assertEquals(0L, fresh.receivedChunks)
            assertEquals(0L, fresh.acceptedMessages)
            assertNull(fresh.lastAcceptedAtEpochMillis)
        } finally { session.stop(); session.close() }
    }

    @Test fun `unframed noise is counted as bytes but never invented parser failures`() = runBlocking {
        val transport = FakeTransport()
        val repository = VehicleRepositoryImpl()
        val session = newSession(transport, repository)
        try {
            session.start()
            transport.emit("not mavlink".encodeToByteArray())
            waitUntil { repository.vehicleState.value.mavlinkDiagnostics.receivedBytes > 0L }
            val counters = repository.vehicleState.value.mavlinkDiagnostics
            assertEquals(0L, counters.decodedMessages)
            assertEquals(0L, counters.parserErrors)
            assertEquals(VehicleConnectionState.CONNECTING, repository.vehicleState.value.connectionStatus)
        } finally { session.stop(); session.close() }
    }

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
    fun `only selected autopilot telemetry is routed after its heartbeat`() = runBlocking {
        val transport = FakeTransport()
        val repository = VehicleRepositoryImpl()
        val session = newSession(transport, repository)
        try {
            session.start()
            val positionBeforeHeartbeat = globalPositionFrame(systemId = 42, latitudeE7 = 451_000_000)
            transport.emit(positionBeforeHeartbeat)
            assertNull(repository.vehicleState.value.position.latitude)

            transport.emit(heartbeat(systemId = 42, componentId = 1, autopilot = 3))
            waitUntil { repository.vehicleState.value.connectionStatus == VehicleConnectionState.CONNECTED }
            transport.emit(globalPositionFrame(systemId = 99, latitudeE7 = 123_000_000))
            transport.emit(globalPositionFrame(systemId = 42, latitudeE7 = 451_000_000))

            waitUntil { repository.vehicleState.value.position.latitude != null }
            assertEquals(45.1, repository.vehicleState.value.position.latitude!!, 0.000001)
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
        repeat(1_000) {
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
    ): ByteArray = heartbeatFrame(customMode, baseMode, systemId, componentId, autopilot)

    private fun globalPositionFrame(systemId: Int, latitudeE7: Int): ByteArray {
        val payload = ByteArray(28)
        payload.putInt32(4, latitudeE7)
        payload.putInt32(8, 1_512_000_000)
        payload.putInt16(20, 50)
        return mavlinkTestFrame(33, payload, 104, systemId = systemId, componentId = 1)
    }

    private class FakeTransport : VehicleTransport {
        override val linkKind = TelemetryLinkKind.BLE
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
