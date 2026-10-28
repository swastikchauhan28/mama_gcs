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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MavlinkSessionTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `calendar clock jumps do not alter elapsed heartbeat timeout or position receipt`() = runTest {
        val transport = FakeTransport()
        val repository = VehicleRepositoryImpl()
        var wallTime = 1_000_000L
        val session = MavlinkSession(transport, MavlinkParser(), MavlinkMessageRouter(repository), repository,
            heartbeatTimeoutMillis = 100, heartbeatCheckIntervalMillis = 10, scope = this,
            elapsedClock = { testScheduler.currentTime }, epochClock = { wallTime })
        try {
            session.start()
            transport.emit(heartbeatFrame(systemId = 42) + globalPositionFrame(42, -353_632_621))
            runCurrent()
            assertTrue(repository.vehicleState.value.connected)
            assertEquals(1_000_000L, repository.vehicleState.value.position.lastUpdatedAtEpochMillis)
            assertEquals(0L, repository.vehicleState.value.position.lastUpdatedAtElapsedMillis)
            wallTime = 1L
            advanceTimeBy(90); runCurrent()
            assertTrue("backward calendar jump must not expire heartbeat", repository.vehicleState.value.connected)
            wallTime = 9_000_000L
            runCurrent()
            assertTrue("forward calendar jump must not expire heartbeat", repository.vehicleState.value.connected)
            advanceTimeBy(20); runCurrent()
            assertEquals(VehicleConnectionState.DEGRADED, repository.vehicleState.value.connectionStatus)
            transport.emit(heartbeatFrame(systemId = 42) + globalPositionFrame(42, -353_632_621))
            runCurrent()
            assertTrue(repository.vehicleState.value.connected)
            assertEquals(9_000_000L, repository.vehicleState.value.position.lastUpdatedAtEpochMillis)
            assertEquals(110L, repository.vehicleState.value.position.lastUpdatedAtElapsedMillis)
            session.stop()
            session.start()
            assertNull("new session must clear previous elapsed position", repository.vehicleState.value.position.lastUpdatedAtElapsedMillis)
        } finally { session.stop(); session.close() }
    }
    @Test fun `radio status is observed without selecting autopilot or refreshing heartbeat and resets next session`() = runBlocking {
        val transport = FakeTransport()
        val repository = VehicleRepositoryImpl()
        val session = MavlinkSession(transport, MavlinkParser(), MavlinkMessageRouter(repository), repository, scope = this, elapsedClock = { System.nanoTime() / 1_000_000 })
        val radio = mavlink1TestFrame(109, ByteArray(9).apply {
            putUInt16(0, 3); putUInt16(2, 4); this[4] = 95.toByte()
        }, 185, systemId = 68, componentId = 68)
        try {
            session.start()
            transport.emit(radio)
            waitUntil { repository.vehicleState.value.mavlinkDiagnostics.validatedRadioStatusFrames == 1L }
            val state = repository.vehicleState.value
            assertEquals(0L, state.mavlinkDiagnostics.decodedMessages)
            assertEquals(0L, state.mavlinkDiagnostics.acceptedMessages)
            assertTrue(state.mavlinkDiagnostics.messageObservations.isEmpty())
            assertNull(state.lastHeartbeatAtEpochMillis)
            assertNull(state.rcInput.lastUpdatedAtEpochMillis)
            assertEquals(68, state.mavlinkDiagnostics.latestRadioStatus?.componentId)
            transport.emit(heartbeatFrame(systemId = 42, componentId = 1))
            waitUntil { repository.vehicleState.value.mavlinkDiagnostics.acceptedMessages == 1L }
            assertEquals(42, repository.vehicleState.value.systemId)
            assertEquals(1L, repository.vehicleState.value.mavlinkDiagnostics.validatedRadioStatusFrames)
            session.stop()
            session.start()
            assertEquals(0L, repository.vehicleState.value.mavlinkDiagnostics.validatedRadioStatusFrames)
            assertNull(repository.vehicleState.value.mavlinkDiagnostics.latestRadioStatus)
        } finally { session.stop(); session.close() }
    }

    @Test fun `home is selected source only resets on reconnect and never transmits`() = runBlocking {
        val base = FakeTransport()
        var sends = 0
        val transport = object : VehicleTransport by base {
            override suspend fun send(data: ByteArray) { sends++ }
        }
        val repository = VehicleRepositoryImpl()
        val session = MavlinkSession(transport, MavlinkParser(), MavlinkMessageRouter(repository), repository, scope = this, elapsedClock = { System.nanoTime() / 1_000_000 })
        fun home(system: Int = 42, component: Int = 1, lat: Long = -353632621) =
            mavlinkTestFrame(242, ByteArray(52).apply { putUInt32(0, lat); putUInt32(4, 1491652374) }, 104, system, component)
        try {
            session.start()
            base.emit(home() + heartbeatFrame(systemId = 42) + home() + home(99, lat = 1) + home(component = 2, lat = 2))
            waitUntil { repository.vehicleState.value.mavlinkDiagnostics.decodedMessages == 5L }
            val state = repository.vehicleState.value
            assertEquals(-35.3632621, state.homePosition!!.latitude!!, 0.00000001)
            assertEquals(1L, state.mavlinkDiagnostics.ignoredBeforeHeartbeat)
            assertEquals(2L, state.mavlinkDiagnostics.ignoredOtherSource)
            assertEquals(2L, state.mavlinkDiagnostics.acceptedMessages)
            val observation = state.mavlinkDiagnostics.messageObservations.single { it.type.messageId == 242 }
            assertEquals(1L, observation.accepted)
            session.stop()
            assertEquals(state.homePosition, repository.vehicleState.value.homePosition)
            session.start()
            assertNull(repository.vehicleState.value.homePosition)
            assertEquals(0, sends)
        } finally { session.stop(); session.close() }
    }

    @Test fun `firmware is selected source only resets on reconnect and never transmits`() = runBlocking {
        val base = FakeTransport()
        var sends = 0
        val transport = object : VehicleTransport by base {
            override suspend fun send(data: ByteArray) { sends++ }
        }
        val repository = VehicleRepositoryImpl()
        val session = MavlinkSession(transport, MavlinkParser(), MavlinkMessageRouter(repository), repository, scope = this, elapsedClock = { System.nanoTime() / 1_000_000 })
        fun version(system: Int = 42, component: Int = 1, value: Long = 0x040700FF) =
            mavlinkTestFrame(148, ByteArray(60).apply { putUInt32(16, value) }, 178, system, component)
        try {
            session.start()
            base.emit(version() + heartbeatFrame(systemId = 42) + version() + version(99, value = 1) + version(component = 2, value = 2))
            waitUntil { repository.vehicleState.value.mavlinkDiagnostics.decodedMessages == 5L }
            val state = repository.vehicleState.value
            assertEquals(0x040700FFL, state.autopilotVersion!!.firmwareVersion)
            assertEquals(1L, state.mavlinkDiagnostics.ignoredBeforeHeartbeat)
            assertEquals(2L, state.mavlinkDiagnostics.ignoredOtherSource)
            assertEquals(2L, state.mavlinkDiagnostics.acceptedMessages)
            session.stop()
            assertEquals(state.autopilotVersion, repository.vehicleState.value.autopilotVersion)
            session.start()
            assertNull(repository.vehicleState.value.autopilotVersion)
            assertEquals(0, sends)
        } finally { session.stop(); session.close() }
    }

    @Test fun `rc and pwm require selected heartbeat identity and never transmit`() = runBlocking {
        val base = FakeTransport()
        var sends = 0
        val transport = object : VehicleTransport by base {
            override suspend fun send(data: ByteArray) { sends++ }
        }
        val repository = VehicleRepositoryImpl()
        val session = MavlinkSession(transport, MavlinkParser(), MavlinkMessageRouter(repository), repository,
            scope = this, elapsedClock = { System.nanoTime() / 1_000_000 })
        fun rc(system: Int, component: Int = 1, value: Int = 1500): ByteArray {
            val payload = ByteArray(42).apply { putUInt16(4, value); this[40] = 1; this[41] = 255.toByte() }
            return mavlinkTestFrame(65, payload, 118, system, component)
        }
        fun pwm(system: Int, value: Int = 1500) = mavlink1TestFrame(36,
            ByteArray(21).apply { putUInt16(4, value) }, 222, systemId = system)
        try {
            session.start()
            base.emit(rc(42) + pwm(42) + heartbeatFrame(systemId = 42) +
                rc(42) + pwm(42) + rc(99, value = 2000) + pwm(99, 2000) + rc(42, component = 2))
            waitUntil { repository.vehicleState.value.mavlinkDiagnostics.decodedMessages == 8L }
            val state = repository.vehicleState.value
            assertEquals(1500, state.rcInput.channelsMicroseconds[0])
            assertEquals(1500, state.pwmOutputs.single().outputsMicroseconds[0])
            assertEquals(2L, state.mavlinkDiagnostics.ignoredBeforeHeartbeat)
            assertEquals(3L, state.mavlinkDiagnostics.ignoredOtherSource)
            assertEquals(3L, state.mavlinkDiagnostics.acceptedMessages)
            val observed = state.mavlinkDiagnostics.messageObservations.associateBy { it.type.messageId }
            assertEquals(1L, observed.getValue(0).accepted)
            assertEquals(1L, observed.getValue(65).accepted)
            assertEquals(1L, observed.getValue(65).beforeHeartbeat)
            assertEquals(2L, observed.getValue(65).otherSource)
            assertEquals(1L, observed.getValue(36).accepted)
            assertEquals(1L, observed.getValue(36).otherSource)
            session.stop()
            assertFalse(repository.vehicleState.value.mavlinkDiagnostics.active)
            assertEquals(state.mavlinkDiagnostics.messageObservations,
                repository.vehicleState.value.mavlinkDiagnostics.messageObservations)
            session.start()
            assertTrue(repository.vehicleState.value.mavlinkDiagnostics.messageObservations.isEmpty())
            assertEquals(0, sends)
        } finally { session.stop(); session.close() }
    }

    @Test fun `version1 heartbeat still expires`() = runBlocking {
        val transport = FakeTransport()
        val repository = VehicleRepositoryImpl()
        val session = newSession(transport, repository, timeoutMillis = 80, checkIntervalMillis = 5)
        try {
            session.start()
            transport.emit(heartbeat1Frame())
            waitUntil { repository.vehicleState.value.mavlinkDiagnostics.acceptedMessages == 1L }
            waitUntil { repository.vehicleState.value.connectionStatus == VehicleConnectionState.DEGRADED }
            assertEquals(TransportStatus.OPEN, transport.connectionState.value.status)
        } finally { session.stop(); session.close() }
    }
    @Test fun `version1 selects autopilot without loosening mixed version source filtering`() = runBlocking {
        val transport = FakeTransport()
        val repository = VehicleRepositoryImpl()
        val session = newSession(transport, repository)
        try {
            session.start()
            transport.emit(heartbeat1Frame(42, MavlinkMessage.MAV_AUTOPILOT_INVALID) +
                heartbeat1Frame(42) + heartbeatFrame(systemId = 99) +
                globalPositionFrame(42, 451_000_000) + heartbeat1Frame(99))
            waitUntil { repository.vehicleState.value.mavlinkDiagnostics.decodedMessages == 5L }
            val state = repository.vehicleState.value
            assertEquals(42, state.systemId)
            assertEquals(VehicleConnectionState.CONNECTED, state.connectionStatus)
            assertEquals(45.1, state.position.latitude!!, 0.000001)
            assertEquals(3L, state.mavlinkDiagnostics.decodedV1Messages)
            assertEquals(2L, state.mavlinkDiagnostics.decodedV2Messages)
            assertEquals(2L, state.mavlinkDiagnostics.acceptedMessages)
            assertEquals(1L, state.mavlinkDiagnostics.ignoredBeforeHeartbeat)
            assertEquals(2L, state.mavlinkDiagnostics.ignoredOtherSource)
            session.stop()
            assertFalse(repository.vehicleState.value.mavlinkDiagnostics.active)
            assertEquals(3L, repository.vehicleState.value.mavlinkDiagnostics.decodedV1Messages)
        } finally { session.close() }
    }
    @Test fun `cancelled closed startup cannot clear a replacement session`() = runBlocking {
        val repository = VehicleRepositoryImpl()
        val stalled = object : VehicleTransport by FakeTransport() {
            override suspend fun connect(): Unit = awaitCancellation()
        }
        val old = MavlinkSession(stalled, MavlinkParser(), MavlinkMessageRouter(repository), repository, scope = this, elapsedClock = { System.nanoTime() / 1_000_000 })
        val pending = launch(start = CoroutineStart.UNDISPATCHED) { old.start() }
        val replacement = MavlinkSession(FakeTransport(), MavlinkParser(), MavlinkMessageRouter(repository), repository, scope = this, elapsedClock = { System.nanoTime() / 1_000_000 })
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
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        elapsedClock = { System.nanoTime() / 1_000_000 }
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
