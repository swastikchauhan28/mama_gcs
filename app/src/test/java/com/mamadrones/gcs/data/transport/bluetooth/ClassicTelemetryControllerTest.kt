package com.mamadrones.gcs.data.transport.bluetooth

import com.mamadrones.gcs.data.mavlink.*
import com.mamadrones.gcs.data.repository.VehicleRepositoryImpl
import com.mamadrones.gcs.data.transport.TelemetryLinkGate
import com.mamadrones.gcs.domain.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ClassicTelemetryControllerTest {
    private val peer = ClassicBluetoothPeer("00:11:22:33:44:55", "HC-05")

    @Test fun `fragmented heartbeat reaches dashboard and EOF ends vehicle session and releases lease`() = runBlocking {
        val socket = FakeClassicSocket()
        val repository = VehicleRepositoryImpl()
        val gate = TelemetryLinkGate()
        val controller = controller(socket, repository, gate, this)
        try {
            controller.connect(peer)
            awaitCondition { controller.state.value.connection.status == TransportStatus.OPEN }
            assertEquals(VehicleConnectionState.CONNECTING, repository.vehicleState.value.connectionStatus)
            val heartbeat = heartbeatFrame()
            socket.offer(heartbeat.copyOfRange(0, 5))
            socket.offer(heartbeat.copyOfRange(5, heartbeat.size))
            awaitCondition { repository.vehicleState.value.connected }
            assertEquals(TelemetryLinkKind.CLASSIC, repository.vehicleState.value.mavlinkDiagnostics.linkKind)
            assertEquals(1L, repository.vehicleState.value.mavlinkDiagnostics.acceptedMessages)
            socket.offer(byteArrayOf())
            awaitCondition { !controller.state.value.active }
            assertEquals(TransportStatus.ERROR, controller.state.value.connection.status)
            assertFalse(repository.vehicleState.value.connected)
            assertFalse(repository.vehicleState.value.mavlinkDiagnostics.active)
            val next = gate.acquire("UDP")
            gate.release(next)
        } finally { controller.close() }
    }

    @Test fun `competing link cannot open Classic or reset existing telemetry`() = runBlocking {
        val gate = TelemetryLinkGate()
        val existing = gate.acquire("BLE")
        val socket = FakeClassicSocket()
        val repository = VehicleRepositoryImpl()
        val controller = controller(socket, repository, gate, this)
        try {
            try { controller.connect(peer); fail("Other link must be closed first") }
            catch (error: IllegalStateException) { assertTrue(error.message!!.contains("BLE")) }
            assertEquals(0, socket.connects.get())
            assertNull(repository.vehicleState.value.mavlinkDiagnostics.startedAtEpochMillis)
            controller.close()
            try { gate.acquire("UDP"); fail("Unowned lease must not be released") }
            catch (_: IllegalStateException) { }
        } finally { gate.release(existing) }
    }

    @Test fun `cancel pending connection releases lease and late completion cannot close next session`() = runBlocking {
        val first = FakeClassicSocket(blockConnect = true)
        val second = FakeClassicSocket()
        val repository = VehicleRepositoryImpl()
        val gate = TelemetryLinkGate()
        var attempts = 0
        val controller = ClassicTelemetryController(
            { BluetoothTransport({ if (attempts++ == 0) first else second }) },
            factory(repository), this, gate,
        )
        try {
            controller.connect(peer)
            awaitCondition { first.connects.get() == 1 }
            controller.disconnect()
            controller.connect(peer)
            awaitCondition { controller.state.value.connection.status == TransportStatus.OPEN }
            second.offer(heartbeatFrame())
            awaitCondition { repository.vehicleState.value.connected }
            assertEquals(1, first.closes.get())
            assertEquals(0, second.closes.get())
            assertTrue(controller.state.value.active)
        } finally { controller.close() }
        assertEquals(1, second.closes.get())
    }

    @Test fun `failed connection releases lease and user can retry`() = runBlocking {
        val gate = TelemetryLinkGate()
        val repository = VehicleRepositoryImpl()
        val controller = ClassicTelemetryController({ BluetoothTransport({ throw SecurityException("Permission revoked") }) },
            factory(repository), this, gate)
        try {
            controller.connect(peer)
            awaitCondition { !controller.state.value.active }
            assertEquals(TransportStatus.ERROR, controller.state.value.connection.status)
            assertFalse(repository.vehicleState.value.connected)
            val lease = gate.acquire("UDP")
            gate.release(lease)
        } finally { controller.close() }
    }

    private fun controller(socket: FakeClassicSocket, repository: VehicleRepositoryImpl,
        gate: TelemetryLinkGate, scope: CoroutineScope) = ClassicTelemetryController(
        { BluetoothTransport({ socket }) }, factory(repository), scope, gate)

    private fun factory(repository: VehicleRepositoryImpl) = MavlinkSessionFactory { transport, scope ->
        MavlinkSession(transport, MavlinkParser(), MavlinkMessageRouter(repository), repository, scope = scope)
    }
}
